package com.socialsea.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class VideoEditingService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final double BLUR_TRACK_MAX_INTERPOLATION_GAP_SECONDS = 0.58d;

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String ffmpegBin;
    private final String ffprobeBin;
    private final long timeoutMillis;
    private final String configuredFontFile;

    public VideoEditingService(
        ObjectMapper objectMapper,
        @Value("${app.video-editing.enabled:true}") boolean enabled,
        @Value("${app.video-editing.ffmpeg-bin:ffmpeg}") String ffmpegBin,
        @Value("${app.video-editing.ffprobe-bin:ffprobe}") String ffprobeBin,
        @Value("${app.video-editing.timeout-seconds:240}") long timeoutSeconds,
        @Value("${app.video-editing.drawtext-font-file:}") String configuredFontFile
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.ffmpegBin = resolveVideoBinary(ffmpegBin, "ffmpeg", "ffmpeg.exe", "ffmpeg");
        this.ffprobeBin = resolveVideoBinary(ffprobeBin, "ffprobe", "ffprobe.exe", "ffprobe");
        this.timeoutMillis = Math.max(30_000L, timeoutSeconds * 1_000L);
        this.configuredFontFile = configuredFontFile == null ? "" : configuredFontFile.trim();
    }

    public ProcessingResult prepareForUpload(MultipartFile sourceFile, String rawVideoSettings) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        if (!isVideo(sourceFile)) {
            return ProcessingResult.noop(sourceFile, safeRawSettings(rawVideoSettings));
        }

        Map<String, Object> settings = parseSettings(rawVideoSettings);
        String normalizedSettings = normalizeSettings(rawVideoSettings, settings);
        Map<String, Object> edits = asMap(settings.get("edits"));
        List<TimelineClip> timelineClips = parseTimelineClips(settings);
        Map<String, Object> cover = asMap(settings.get("cover"));
        boolean hasSettingsPayload = !settings.isEmpty();

        boolean renderRequested = hasRenderableEditRequest(edits) || hasRenderableTimelineRequest(timelineClips);
        boolean wantsAutoCover = hasSettingsPayload && wantsAutoCover(cover);
        double coverTime = Math.max(0d, number(cover.get("frameTime"), number(edits.get("coverTime"), 0d)));

        if (!renderRequested && !wantsAutoCover) {
            return ProcessingResult.noop(sourceFile, normalizedSettings);
        }
        if (!enabled) {
            throw new IllegalStateException("Video editor is disabled on server. Enable app.video-editing.enabled=true.");
        }

        ensureBinaryAvailable(ffmpegBin, "ffmpeg");
        ensureBinaryAvailable(ffprobeBin, "ffprobe");

        List<Path> tempArtifacts = new ArrayList<>();
        try {
            Path inputPath = copyToTemp(sourceFile, "ss-video-in-", fileSuffix(sourceFile.getOriginalFilename(), ".mp4"));
            tempArtifacts.add(inputPath);
            MediaProbe probe = probe(inputPath);

            Path uploadPath = inputPath;
            boolean editsApplied = false;
            if (renderRequested) {
                Path rendered = Files.createTempFile("ss-video-edited-", ".mp4");
                tempArtifacts.add(rendered);
                runEditCommand(inputPath, rendered, edits, timelineClips, probe, tempArtifacts);
                uploadPath = rendered;
                editsApplied = true;
            }

            MultipartFile uploadFile = editsApplied
                ? new PathMultipartFile(
                    "file",
                    processedName(sourceFile.getOriginalFilename()),
                    "video/mp4",
                    uploadPath
                )
                : sourceFile;

            MultipartFile generatedCover = null;
            if (wantsAutoCover) {
                Path coverFile = Files.createTempFile("ss-video-cover-", ".jpg");
                tempArtifacts.add(coverFile);
                runCoverFrameCommand(uploadPath, coverFile, coverTime, probe.durationSeconds());
                generatedCover = new PathMultipartFile("coverImage", coverName(sourceFile.getOriginalFilename()), "image/jpeg", coverFile);
            }

            return new ProcessingResult(uploadFile, generatedCover, normalizedSettings, editsApplied, tempArtifacts);
        } catch (RuntimeException | IOException e) {
            for (Path path : tempArtifacts) {
                deleteQuietly(path);
            }
            String reason = e.getMessage() == null ? "Video editing failed." : e.getMessage();
            throw new IllegalStateException(reason);
        }
    }

    public double probeDurationSeconds(MultipartFile sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        if (!isVideo(sourceFile)) return 0d;

        ensureBinaryAvailable(ffprobeBin, "ffprobe");
        Path probePath = null;
        try {
            probePath = copyToTemp(sourceFile, "ss-video-probe-", fileSuffix(sourceFile.getOriginalFilename(), ".mp4"));
            MediaProbe probe = probe(probePath);
            return Math.max(0d, probe.durationSeconds());
        } catch (RuntimeException | IOException error) {
            throw new IllegalStateException("Unable to read video duration.", error);
        } finally {
            if (probePath != null) {
                deleteQuietly(probePath);
            }
        }
    }

    private void runEditCommand(
        Path inputPath,
        Path outputPath,
        Map<String, Object> edits,
        List<TimelineClip> timelineClips,
        MediaProbe probe,
        List<Path> tempArtifacts
    ) throws IOException {
        List<TimelineClip> normalizedTimelineClips = normalizeTimelineClips(timelineClips, probe.durationSeconds());
        edits = mergeTimelineIntoEdits(edits, normalizedTimelineClips, probe.durationSeconds());

        List<String> videoFilters = new ArrayList<>();
        List<String> audioFilters = new ArrayList<>();

        double trimStart = Math.max(0d, number(edits.get("trimStart"), 0d));
        double trimEndRaw = Math.max(0d, number(edits.get("trimEnd"), 0d));
        double trimEnd = trimEndRaw > trimStart + 0.05 ? trimEndRaw : 0d;
        if (trimStart > 0d || trimEnd > 0d) {
            if (trimEnd > 0d) {
                videoFilters.add("trim=start=" + f(trimStart) + ":end=" + f(trimEnd));
            } else {
                videoFilters.add("trim=start=" + f(trimStart));
            }
            videoFilters.add("setpts=PTS-STARTPTS");
            if (probe.hasAudio()) {
                if (trimEnd > 0d) {
                    audioFilters.add("atrim=start=" + f(trimStart) + ":end=" + f(trimEnd));
                } else {
                    audioFilters.add("atrim=start=" + f(trimStart));
                }
                audioFilters.add("asetpts=PTS-STARTPTS");
            }
        }

        if (booleanFlag(edits.get("reversePlayback"))) {
            videoFilters.add("reverse");
            if (probe.hasAudio()) {
                audioFilters.add("areverse");
            }
        }

        double speed = clamp(number(edits.get("playbackSpeed"), 1d), 0.5d, 2.0d);
        if (Math.abs(speed - 1d) > 0.0001d) {
            videoFilters.add("setpts=" + f(1d / speed) + "*PTS");
            if (probe.hasAudio()) {
                audioFilters.add("atempo=" + f(speed));
            }
        }

        double cropLeftPct = clamp(number(edits.get("cropLeft"), 0d), 0d, 90d);
        double cropRightPct = clamp(number(edits.get("cropRight"), 0d), 0d, 90d);
        double cropTopPct = clamp(number(edits.get("cropTop"), 0d), 0d, 90d);
        double cropBottomPct = clamp(number(edits.get("cropBottom"), 0d), 0d, 90d);
        double horizontalCropTotal = cropLeftPct + cropRightPct;
        if (horizontalCropTotal > 90d) {
            double ratio = 90d / horizontalCropTotal;
            cropLeftPct *= ratio;
            cropRightPct *= ratio;
        }
        double verticalCropTotal = cropTopPct + cropBottomPct;
        if (verticalCropTotal > 90d) {
            double ratio = 90d / verticalCropTotal;
            cropTopPct *= ratio;
            cropBottomPct *= ratio;
        }
        if (cropLeftPct + cropRightPct + cropTopPct + cropBottomPct > 0.01d) {
            double cropWidthRatio = Math.max(0.01d, 1d - (cropLeftPct + cropRightPct) / 100d);
            double cropHeightRatio = Math.max(0.01d, 1d - (cropTopPct + cropBottomPct) / 100d);
            double cropXRatio = clamp(cropLeftPct / 100d, 0d, 1d);
            double cropYRatio = clamp(cropTopPct / 100d, 0d, 1d);
            String crop = "crop=iw*" + f(cropWidthRatio)
                + ":ih*" + f(cropHeightRatio)
                + ":iw*" + f(cropXRatio)
                + ":ih*" + f(cropYRatio);
            videoFilters.add(crop);
        }

        double zoom = clamp(number(edits.get("cropZoom"), 100d), 100d, 220d);
        if (zoom > 100.01d) {
            double z = zoom / 100d;
            double cropX = clamp(number(edits.get("cropX"), 50d), 0d, 100d) / 100d;
            double cropY = clamp(number(edits.get("cropY"), 50d), 0d, 100d) / 100d;
            String crop = "crop=iw/" + f(z)
                + ":ih/" + f(z)
                + ":(iw-iw/" + f(z) + ")*" + f(cropX)
                + ":(ih-ih/" + f(z) + ")*" + f(cropY);
            videoFilters.add(crop);
            videoFilters.add("scale=iw:ih");
        }

        double rotate = number(edits.get("rotate"), 0d);
        if (Math.abs(rotate) > 0.1d) {
            videoFilters.add("rotate=" + f(rotate) + "*PI/180:fillcolor=black");
        }
        if (booleanFlag(edits.get("flipH"))) {
            videoFilters.add("hflip");
        }
        if (booleanFlag(edits.get("flipV"))) {
            videoFilters.add("vflip");
        }

        double brightnessPct = clamp(number(edits.get("brightness"), 100d), 40d, 220d);
        double contrastPct = clamp(number(edits.get("contrast"), 100d), 40d, 220d);
        double saturationPct = clamp(number(edits.get("saturation"), 100d), 0d, 260d);
        double vibrancePct = clamp(number(edits.get("vibrance"), 100d), 0d, 220d);
        double exposure = clamp(number(edits.get("exposure"), 0d), -40d, 40d);
        double highlights = clamp(number(edits.get("highlights"), 0d), -80d, 80d);
        double shadows = clamp(number(edits.get("shadows"), 0d), -80d, 80d);
        double blackPoint = clamp(number(edits.get("blackPoint"), 0d), 0d, 100d);
        double fadeAmount = clamp(number(edits.get("fade"), 0d), 0d, 60d);
        double eqBrightness = clamp(
            (brightnessPct - 100d) / 100d + exposure / 120d + highlights / 260d + shadows / 520d + fadeAmount / 260d,
            -1d,
            1d
        );
        double eqContrast = clamp(
            contrastPct / 100d + blackPoint / 220d + highlights / 500d - shadows / 700d - fadeAmount / 280d,
            0d,
            3d
        );
        double eqSaturation = clamp(
            saturationPct / 100d + (vibrancePct - 100d) / 180d,
            0d,
            3d
        );
        double eqGamma = clamp(1d - shadows / 240d + highlights / 300d, 0.4d, 2.6d);
        if (Math.abs(eqBrightness) > 0.0001d
                || Math.abs(eqContrast - 1d) > 0.0001d
                || Math.abs(eqSaturation - 1d) > 0.0001d
                || Math.abs(eqGamma - 1d) > 0.0001d) {
            videoFilters.add(
                "eq=brightness=" + f(eqBrightness)
                    + ":contrast=" + f(eqContrast)
                    + ":saturation=" + f(eqSaturation)
                    + ":gamma=" + f(eqGamma)
            );
        }

        double warmth = clamp(number(edits.get("warmth"), 0d), -60d, 60d);
        double tint = clamp(number(edits.get("tint"), 0d), -60d, 60d);
        double hue = clamp(number(edits.get("hue"), 0d), -180d, 180d);
        double hueRotate = clamp(hue + tint * 0.45d - warmth * 0.35d, -180d, 180d);
        if (Math.abs(hueRotate) > 0.1d) {
            videoFilters.add("hue=h=" + f(hueRotate));
        }
        if (Math.abs(warmth) > 0.1d || Math.abs(tint) > 0.1d) {
            double rs = clamp(warmth / 220d + tint / 280d, -1d, 1d);
            double gs = clamp(-Math.abs(tint) / 320d - warmth / 420d, -1d, 1d);
            double bs = clamp(-warmth / 220d + tint / 280d, -1d, 1d);
            videoFilters.add("colorbalance=rs=" + f(rs) + ":gs=" + f(gs) + ":bs=" + f(bs));
        }
        if (warmth > 0.1d) {
            double sepia = clamp(warmth / 100d, 0d, 1d);
            videoFilters.add("colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131");
            if (sepia < 0.95d) {
                videoFilters.add("eq=saturation=" + f(clamp(1d - sepia * 0.12d, 0.4d, 1d)));
            }
        }

        double grain = clamp(number(edits.get("grain"), 0d), 0d, 100d);
        if (grain > 0.1d) {
            double grainStrength = clamp(2d + grain / 6d, 2d, 20d);
            videoFilters.add("noise=alls=" + f(grainStrength) + ":allf=t");
        }
        double vignette = clamp(number(edits.get("vignette"), 0d), 0d, 100d);
        if (vignette > 0.1d) {
            videoFilters.add("vignette=PI/4");
        }

        double softness = clamp(number(edits.get("softness"), 0d), 0d, 60d);
        double sharpness = clamp(number(edits.get("sharpness"), 0d), 0d, 100d);
        double blurFx = clamp(number(edits.get("effectBlur"), 0d), 0d, 100d);
        // Keep server blur response closer to studio preview so global blur feels consistent.
        double softnessSigma = clamp(Math.max(0d, softness / 30d - sharpness / 260d), 0d, 2.2d);
        double blurSigma = clamp(blurFx / 14d, 0d, 8d);
        if ("none".equals(resolveBlurTargetType(edits))) {
            double legacyRegional = Math.max(
                clamp(number(edits.get("blurFace"), 0d), 0d, 100d) * 0.92d,
                Math.max(
                    clamp(number(edits.get("blurLogo"), 0d), 0d, 100d) * 0.88d,
                    clamp(number(edits.get("blurCustom"), 0d), 0d, 100d) * 0.95d
                )
            );
            blurSigma = Math.max(blurSigma, clamp(legacyRegional / 14d, 0d, 8d));
        }
        double globalSigma = clamp(softnessSigma + blurSigma, 0d, 10d);
        if (globalSigma > 0.03d) {
            videoFilters.add("gblur=sigma=" + f(globalSigma));
        }

        List<BlurRegion> blurRegions = resolveBlurRegions(edits, probe);
        for (BlurRegion blurRegion : blurRegions) {
            for (int pass = 0; pass < blurRegion.passes(); pass += 1) {
                videoFilters.add(buildDelogoFilter(blurRegion));
            }
        }

        String filterPreset = string(edits.get("filterPreset"));
        if ("mono".equalsIgnoreCase(filterPreset) || "noir".equalsIgnoreCase(filterPreset)) {
            videoFilters.add("hue=s=0");
        } else if ("vintage".equalsIgnoreCase(filterPreset) || "sunset".equalsIgnoreCase(filterPreset) || "cinematic".equalsIgnoreCase(filterPreset)) {
            videoFilters.add("colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131");
        } else if ("teal-orange".equalsIgnoreCase(filterPreset)) {
            videoFilters.add("hue=h=12");
            videoFilters.add("eq=saturation=1.12");
        } else if ("dream".equalsIgnoreCase(filterPreset)) {
            videoFilters.add("eq=brightness=0.08:contrast=0.88:saturation=1.2");
        } else if ("neon".equalsIgnoreCase(filterPreset)) {
            videoFilters.add("eq=brightness=0.06:contrast=1.2:saturation=1.4");
        } else if ("documentary".equalsIgnoreCase(filterPreset)) {
            videoFilters.add("eq=contrast=1.06:saturation=0.82");
        }

        List<TimelineClip> textTimelineClips = timelineByType(normalizedTimelineClips, "text");
        if (!textTimelineClips.isEmpty()) {
            for (TimelineClip clip : textTimelineClips) {
                String timelineText = string(clip.payload().get("text"));
                if (timelineText.isBlank()) continue;
                Path textFile = Files.createTempFile("ss-drawtext-", ".txt");
                Files.writeString(textFile, timelineText, StandardCharsets.UTF_8);
                tempArtifacts.add(textFile);
                videoFilters.add(drawTextFilter(textFile, clip.payload(), false, clip.startSeconds(), clip.endSeconds()));
            }
        } else {
            String overlayText = string(edits.get("overlayText"));
            if (!overlayText.isBlank()) {
                Path textFile = Files.createTempFile("ss-drawtext-", ".txt");
                Files.writeString(textFile, overlayText, StandardCharsets.UTF_8);
                tempArtifacts.add(textFile);
                String draw = drawTextFilter(textFile, edits, false, null, null);
                videoFilters.add(draw);
            }
        }

        List<TimelineClip> stickerTimelineClips = timelineByType(normalizedTimelineClips, "sticker");
        if (!stickerTimelineClips.isEmpty()) {
            for (TimelineClip clip : stickerTimelineClips) {
                String sticker = string(clip.payload().get("sticker"));
                if (sticker.isBlank() || "none".equalsIgnoreCase(sticker)) continue;
                String stickerText = stickerGlyph(sticker);
                Path stickerFile = Files.createTempFile("ss-sticker-", ".txt");
                Files.writeString(stickerFile, stickerText, StandardCharsets.UTF_8);
                tempArtifacts.add(stickerFile);
                Map<String, Object> stickerEdits = Map.of(
                    "textPosition", string(clip.payload().get("stickerPosition")),
                    "textSize", number(clip.payload().get("stickerSize"), 72d),
                    "overlayOpacity", 100d
                );
                videoFilters.add(drawTextFilter(stickerFile, stickerEdits, true, clip.startSeconds(), clip.endSeconds()));
            }
        } else {
            String sticker = string(edits.get("sticker"));
            if (!sticker.isBlank() && !"none".equalsIgnoreCase(sticker)) {
                String stickerText = stickerGlyph(sticker);
                Path stickerFile = Files.createTempFile("ss-sticker-", ".txt");
                Files.writeString(stickerFile, stickerText, StandardCharsets.UTF_8);
                tempArtifacts.add(stickerFile);
                videoFilters.add(drawTextFilter(stickerFile, Map.of(
                    "textPosition", string(edits.get("stickerPosition")),
                    "textSize", number(edits.get("stickerSize"), 72d),
                    "overlayOpacity", 100d
                ), true, null, null));
            }
        }

        boolean muted = booleanFlag(edits.get("muted"));
        if (!muted && probe.hasAudio()) {
            double volume = clamp(number(edits.get("volume"), 100d), 0d, 250d) / 100d;
            double voice = clamp(number(edits.get("voiceoverGain"), 100d), 0d, 300d) / 100d;
            double gain = volume * voice;
            if (Math.abs(gain - 1d) > 0.0001d) {
                audioFilters.add("volume=" + f(gain));
            }

            double noiseReduction = clamp(number(edits.get("noiseRemoval"), 0d), 0d, 100d);
            if (noiseReduction > 0.1d) {
                double strength = clamp(0.05d + noiseReduction / 400d, 0.05d, 0.28d);
                audioFilters.add("afftdn=nr=" + f(strength));
            }

            double deEss = clamp(number(edits.get("deEss"), 0d), 0d, 100d);
            if (deEss > 0.1d) {
                double cut = clamp(deEss / 100d * 8d, 0.5d, 8d);
                audioFilters.add("equalizer=f=6200:t=h:width=2400:g=-" + f(cut));
            }

            double loudnessTarget = clamp(number(edits.get("loudnessTarget"), -14d), -70d, -5d);
            if (Math.abs(loudnessTarget + 14d) > 0.01d) {
                audioFilters.add("loudnorm=I=" + f(loudnessTarget) + ":TP=-1.5:LRA=11");
            }
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBin);
        cmd.add("-y");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-i");
        cmd.add(inputPath.toString());
        if (!videoFilters.isEmpty()) {
            cmd.add("-vf");
            cmd.add(String.join(",", videoFilters));
        }
        if (muted) {
            cmd.add("-an");
        } else if (probe.hasAudio()) {
            if (!audioFilters.isEmpty()) {
                cmd.add("-af");
                cmd.add(String.join(",", audioFilters));
            }
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("160k");
        } else {
            cmd.add("-an");
        }
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("veryfast");
        cmd.add("-crf");
        cmd.add("22");
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add(outputPath.toString());

        runProcessOrThrow(cmd, "Video editing failed");
    }

    private void runCoverFrameCommand(Path videoPath, Path coverPath, double coverTime, double durationSeconds) {
        double safeTime = Math.max(0d, coverTime);
        if (durationSeconds > 0d) {
            safeTime = Math.min(safeTime, Math.max(0d, durationSeconds - 0.05d));
        }
        List<String> cmd = List.of(
            ffmpegBin,
            "-y",
            "-hide_banner",
            "-loglevel", "error",
            "-ss", f(safeTime),
            "-i", videoPath.toString(),
            "-frames:v", "1",
            "-q:v", "2",
            coverPath.toString()
        );
        runProcessOrThrow(cmd, "Cover frame generation failed");
    }

    private MediaProbe probe(Path videoPath) {
        List<String> cmd = List.of(
            ffprobeBin,
            "-v", "error",
            "-print_format", "json",
            "-show_streams",
            "-show_format",
            videoPath.toString()
        );
        String out = runProcessCaptureStdout(cmd, "Video metadata probe failed");
        try {
            JsonNode node = objectMapper.readTree(out);
            boolean hasAudio = false;
            double duration = 0d;
            int videoWidth = 0;
            int videoHeight = 0;
            JsonNode streams = node.path("streams");
            if (streams.isArray()) {
                for (JsonNode stream : streams) {
                    String codecType = stream.path("codec_type").asText("");
                    if ("audio".equalsIgnoreCase(codecType)) {
                        hasAudio = true;
                    } else if ("video".equalsIgnoreCase(codecType)) {
                        videoWidth = Math.max(videoWidth, stream.path("width").asInt(0));
                        videoHeight = Math.max(videoHeight, stream.path("height").asInt(0));
                    }
                }
            }
            JsonNode format = node.path("format");
            if (format.isObject()) {
                String d = format.path("duration").asText("");
                try {
                    duration = Double.parseDouble(d);
                } catch (Exception ignored) {
                    duration = 0d;
                }
            }
            return new MediaProbe(hasAudio, duration, videoWidth, videoHeight);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse ffprobe output");
        }
    }

    private String drawTextFilter(
        Path textFile,
        Map<String, Object> edits,
        boolean stickerMode,
        Double enableStart,
        Double enableEnd
    ) {
        String position = string(edits.get("textPosition"));
        if (position.isBlank()) position = "bottom-center";
        double baseSize = stickerMode
            ? clamp(number(edits.get("textSize"), 72d), 20d, 180d)
            : clamp(number(edits.get("textSize"), 34d), 16d, 128d);
        double opacity = clamp(number(edits.get("overlayOpacity"), 70d), 0d, 100d) / 100d;
        String fontColor = "white@" + f(Math.max(0.1d, opacity));

        String x;
        String y;
        switch (position.toLowerCase(Locale.ROOT)) {
            case "top-left" -> {
                x = "w*0.06";
                y = "h*0.07";
            }
            case "top-right" -> {
                x = "w-tw-w*0.06";
                y = "h*0.07";
            }
            case "bottom-left" -> {
                x = "w*0.06";
                y = "h-th-h*0.09";
            }
            case "bottom-right" -> {
                x = "w-tw-w*0.06";
                y = "h-th-h*0.09";
            }
            case "center" -> {
                x = "(w-tw)/2";
                y = "(h-th)/2";
            }
            case "bottom-center" -> {
                x = "(w-tw)/2";
                y = "h-th-h*0.09";
            }
            default -> {
                x = "(w-tw)/2";
                y = "h-th-h*0.09";
            }
        }

        String escapedPath = escapeForFilterPath(textFile.toAbsolutePath().toString());
        String fontPath = resolveFontPath();
        StringBuilder sb = new StringBuilder("drawtext=");
        if (!fontPath.isBlank()) {
            sb.append("fontfile='").append(escapeForFilterPath(fontPath)).append("':");
        }
        sb.append("textfile='").append(escapedPath).append("':");
        sb.append("fontsize=").append(f(baseSize)).append(":");
        sb.append("fontcolor=").append(fontColor).append(":");
        sb.append("box=1:boxcolor=black@0.35:boxborderw=8:");
        sb.append("x=").append(x).append(":");
        sb.append("y=").append(y);
        if (enableStart != null && enableEnd != null && enableEnd > enableStart) {
            sb.append(":enable='between(t\\,")
                .append(f(Math.max(0d, enableStart)))
                .append("\\,")
                .append(f(Math.max(0d, enableEnd)))
                .append(")'");
        }
        return sb.toString();
    }

    private String resolveFontPath() {
        if (!configuredFontFile.isBlank()) {
            Path p = Path.of(configuredFontFile).toAbsolutePath().normalize();
            if (Files.exists(p)) return p.toString();
        }
        List<String> defaults = List.of(
            "C:\\Windows\\Fonts\\arial.ttf",
            "C:\\Windows\\Fonts\\segoeui.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
        );
        for (String raw : defaults) {
            try {
                Path p = Path.of(raw);
                if (Files.exists(p)) return p.toAbsolutePath().normalize().toString();
            } catch (Exception ignored) {
                // noop
            }
        }
        return "";
    }

    private void ensureBinaryAvailable(String bin, String label) {
        if (isBinaryAvailable(bin)) return;
        throw new IllegalStateException(label + " is not available on server PATH.");
    }

    private String resolveVideoBinary(String configuredBin, String fallbackBin, String windowsName, String unixName) {
        String preferred = blankToDefault(configuredBin, fallbackBin);
        if (isBinaryAvailable(preferred)) return preferred;
        String bundled = findBundledVideoBinary(windowsName, unixName);
        if (!bundled.isBlank() && isBinaryAvailable(bundled)) return bundled;
        return preferred;
    }

    private String findBundledVideoBinary(String windowsName, String unixName) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        List<Path> roots = new ArrayList<>();
        roots.add(cwd);
        if (cwd.getParent() != null) roots.add(cwd.getParent().normalize());
        if (cwd.getParent() != null && cwd.getParent().getParent() != null) {
            roots.add(cwd.getParent().getParent().normalize());
        }
        List<String> binaryNames = List.of(
            windowsName == null ? "" : windowsName.trim(),
            unixName == null ? "" : unixName.trim()
        );
        for (Path root : roots) {
            Path binDir = root.resolve("tools").resolve("ffmpeg").resolve("current").resolve("bin");
            for (String name : binaryNames) {
                if (name.isBlank()) continue;
                Path candidate = binDir.resolve(name).toAbsolutePath().normalize();
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }
        return "";
    }

    private boolean isBinaryAvailable(String bin) {
        String command = string(bin);
        if (command.isBlank()) return false;
        try {
            Process process = new ProcessBuilder(command, "-version").start();
            boolean done = process.waitFor(Duration.ofSeconds(8).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            return done && process.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private String runProcessCaptureStdout(List<String> command, String errorPrefix) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 20_000) {
                        output.append(line).append('\n');
                    }
                }
            }
            boolean done = p.waitFor(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                throw new IllegalStateException(errorPrefix + ": timed out");
            }
            if (p.exitValue() != 0) {
                throw new IllegalStateException(errorPrefix + ": " + output.toString().trim());
            }
            return output.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(errorPrefix + ": " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(errorPrefix + ": " + e.getMessage());
        }
    }

    private void runProcessOrThrow(List<String> command, String errorPrefix) {
        runProcessCaptureStdout(command, errorPrefix);
    }

    private Path copyToTemp(MultipartFile file, String prefix, String suffix) throws IOException {
        Path temp = Files.createTempFile(prefix, suffix);
        try {
            Files.copy(file.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);
            return temp;
        } catch (IOException e) {
            deleteQuietly(temp);
            throw e;
        }
    }

    private String normalizeSettings(String rawVideoSettings, Map<String, Object> parsed) {
        if (parsed.isEmpty()) return safeRawSettings(rawVideoSettings);
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception ignored) {
            return safeRawSettings(rawVideoSettings);
        }
    }

    private Map<String, Object> parseSettings(String rawVideoSettings) {
        String raw = safeRawSettings(rawVideoSettings);
        if (raw.isBlank()) return Map.of();
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isObject()) return Map.of();
            return objectMapper.convertValue(node, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<TimelineClip> parseTimelineClips(Map<String, Object> settings) {
        Map<String, Object> timeline = asMap(settings.get("timeline"));
        Object rawClips = timeline.get("clips");
        if (!(rawClips instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }

        List<TimelineClip> clips = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) continue;
            Map<String, Object> clip = (Map<String, Object>) rawMap;
            String type = string(clip.get("type")).toLowerCase(Locale.ROOT);
            if (!"video".equals(type) && !"text".equals(type) && !"sticker".equals(type) && !"audio".equals(type)) {
                continue;
            }
            double start = Math.max(0d, number(clip.get("start"), 0d));
            double end = Math.max(start + 0.05d, number(clip.get("end"), start + 0.05d));
            String trackId = string(clip.get("trackId"));
            Map<String, Object> payload = asMap(clip.get("payload"));
            String label = string(clip.get("label"));
            clips.add(new TimelineClip(type, trackId, start, end, payload, label));
        }

        clips.sort((a, b) -> Double.compare(a.startSeconds(), b.startSeconds()));
        return clips;
    }

    private List<TimelineClip> normalizeTimelineClips(List<TimelineClip> clips, double durationSeconds) {
        if (clips == null || clips.isEmpty()) return List.of();
        double minClip = 0.05d;
        double safeDuration = durationSeconds > 0d ? durationSeconds : 0d;
        List<TimelineClip> normalized = new ArrayList<>();

        for (TimelineClip clip : clips) {
            double start = Math.max(0d, clip.startSeconds());
            double end = Math.max(start + minClip, clip.endSeconds());
            if (safeDuration > 0d) {
                double maxStart = Math.max(0d, safeDuration - minClip);
                start = clamp(start, 0d, maxStart);
                end = clamp(end, start + minClip, safeDuration);
            }
            if (end <= start) continue;
            normalized.add(clip.withRange(start, end));
        }

        normalized.sort((a, b) -> Double.compare(a.startSeconds(), b.startSeconds()));
        return normalized;
    }

    private List<TimelineClip> timelineByType(List<TimelineClip> clips, String type) {
        if (clips == null || clips.isEmpty()) return List.of();
        List<TimelineClip> out = new ArrayList<>();
        for (TimelineClip clip : clips) {
            if (type.equalsIgnoreCase(clip.type())) {
                out.add(clip);
            }
        }
        out.sort((a, b) -> Double.compare(a.startSeconds(), b.startSeconds()));
        return out;
    }

    private Map<String, Object> mergeTimelineIntoEdits(Map<String, Object> edits, List<TimelineClip> timelineClips, double durationSeconds) {
        Map<String, Object> merged = new java.util.HashMap<>();
        if (edits != null) {
            merged.putAll(edits);
        }

        List<TimelineClip> videoClips = timelineByType(timelineClips, "video");
        if (!videoClips.isEmpty()) {
            TimelineClip first = videoClips.get(0);
            TimelineClip last = videoClips.get(videoClips.size() - 1);
            double trimStart = Math.max(0d, first.startSeconds());
            double trimEnd = Math.max(trimStart + 0.05d, last.endSeconds());
            if (durationSeconds > 0d) {
                trimStart = clamp(trimStart, 0d, Math.max(0d, durationSeconds - 0.05d));
                trimEnd = clamp(trimEnd, trimStart + 0.05d, durationSeconds);
            }
            boolean shouldApplyTrim = videoClips.size() > 1
                || trimStart > 0.01d
                || (durationSeconds > 0d && trimEnd < durationSeconds - 0.05d);
            if (shouldApplyTrim) {
                merged.put("trimStart", trimStart);
                merged.put("trimEnd", trimEnd);
            }
        }
        return merged;
    }

    private BlurRegion resolveBlurRegion(Map<String, Object> edits, MediaProbe probe) {
        if (edits == null || edits.isEmpty()) return null;
        int videoWidth = Math.max(0, probe.videoWidth());
        int videoHeight = Math.max(0, probe.videoHeight());
        if (videoWidth <= 0 || videoHeight <= 0) return null;

        String target = resolveBlurTargetType(edits);
        if ("none".equals(target)) return null;

        double intensityFallback;
        if ("face".equals(target)) {
            intensityFallback = number(edits.get("blurFace"), 0d);
        } else if ("logo".equals(target)) {
            intensityFallback = number(edits.get("blurLogo"), 0d);
        } else {
            intensityFallback = number(edits.get("blurCustom"), 0d);
        }
        double intensity = clamp(number(edits.get("blurIntensity"), intensityFallback), 0d, 100d);
        if (intensity <= 0.1d) return null;

        double[] preset = defaultBlurRegionForTarget(target);
        double widthPct = clamp(number(edits.get("blurWidth"), number(edits.get("blurCustomWidth"), preset[2])), 1d, 100d);
        double heightPct = clamp(number(edits.get("blurHeight"), number(edits.get("blurCustomHeight"), preset[3])), 1d, 100d);
        double centerXPct = clamp(number(edits.get("blurCustomX"), preset[0] + preset[2] / 2d), 0d, 100d);
        double centerYPct = clamp(number(edits.get("blurCustomY"), preset[1] + preset[3] / 2d), 0d, 100d);
        double xPctFallback = centerXPct - widthPct / 2d;
        double yPctFallback = centerYPct - heightPct / 2d;
        double xPct = clamp(number(edits.get("blurX"), xPctFallback), 0d, Math.max(0d, 100d - widthPct));
        double yPct = clamp(number(edits.get("blurY"), yPctFallback), 0d, Math.max(0d, 100d - heightPct));

        String shape = string(edits.get("blurShape")).toLowerCase(Locale.ROOT);
        if (shape.isBlank()) {
            shape = string(edits.get("maskShape")).toLowerCase(Locale.ROOT);
        }
        if ("circle".equals(shape) || "ellipse".equals(shape)) {
            xPct = clamp(xPct + widthPct * 0.06d, 0d, 100d);
            yPct = clamp(yPct + heightPct * 0.06d, 0d, 100d);
            widthPct = clamp(widthPct * 0.88d, 1d, 100d);
            heightPct = clamp(heightPct * 0.88d, 1d, 100d);
        }

        String tracking = string(edits.get("blurTracking")).toLowerCase(Locale.ROOT);
        if (tracking.isBlank() && booleanFlag(edits.get("motionTrackingEnabled"))) {
            tracking = "smooth";
        }
        double trackingPadPct = switch (tracking) {
            case "aggressive" -> 6d;
            case "smooth" -> 3d;
            default -> 0d;
        };
        if (trackingPadPct > 0.01d) {
            double cx = xPct + widthPct / 2d;
            double cy = yPct + heightPct / 2d;
            widthPct = clamp(widthPct + trackingPadPct, 1d, 100d);
            heightPct = clamp(heightPct + trackingPadPct, 1d, 100d);
            xPct = clamp(cx - widthPct / 2d, 0d, Math.max(0d, 100d - widthPct));
            yPct = clamp(cy - heightPct / 2d, 0d, Math.max(0d, 100d - heightPct));
        }

        double featherPct = clamp(number(edits.get("blurFeather"), number(edits.get("maskFeather"), 0d)), 0d, 40d);
        if (featherPct > 0.01d) {
            double featherPadPct = featherPct * 0.25d;
            double cx = xPct + widthPct / 2d;
            double cy = yPct + heightPct / 2d;
            widthPct = clamp(widthPct + featherPadPct, 1d, 100d);
            heightPct = clamp(heightPct + featherPadPct, 1d, 100d);
            xPct = clamp(cx - widthPct / 2d, 0d, Math.max(0d, 100d - widthPct));
            yPct = clamp(cy - heightPct / 2d, 0d, Math.max(0d, 100d - heightPct));
        }

        int x = (int) Math.round(videoWidth * xPct / 100d);
        int y = (int) Math.round(videoHeight * yPct / 100d);
        int w = (int) Math.round(videoWidth * widthPct / 100d);
        int h = (int) Math.round(videoHeight * heightPct / 100d);

        x = clampInt(x, 0, Math.max(0, videoWidth - 1));
        y = clampInt(y, 0, Math.max(0, videoHeight - 1));
        w = clampInt(w, 1, Math.max(1, videoWidth - x));
        h = clampInt(h, 1, Math.max(1, videoHeight - y));

        double start = Math.max(0d, number(edits.get("blurStart"), 0d));
        double endRaw = Math.max(0d, number(edits.get("blurEnd"), 0d));
        double end = endRaw > start + 0.05d ? endRaw : 0d;

        int passes = intensity >= 80d ? 3 : intensity >= 45d ? 2 : 1;
        return new BlurRegion(x, y, w, h, 0, start, end, passes);
    }

    private List<BlurRegion> resolveBlurRegions(Map<String, Object> edits, MediaProbe probe) {
        List<BlurSubject> subjects = parseBlurSubjects(edits);
        if (!subjects.isEmpty()) {
            List<BlurRegion> fromSubjects = new ArrayList<>();
            for (BlurSubject subject : subjects) {
                boolean hasWindow = subject.endSeconds() > subject.startSeconds() + 0.05d;
                List<BlurRegion> regions = buildTrackedBlurRegions(
                    subject.trackPoints(),
                    subject.startSeconds(),
                    subject.endSeconds(),
                    hasWindow,
                    subject.passes(),
                    probe
                );
                if (!regions.isEmpty()) {
                    fromSubjects.addAll(regions);
                    continue;
                }
                BlurTrackPoint fallbackPoint = firstVisibleTrackPoint(subject.trackPoints());
                if (fallbackPoint != null) {
                    BlurRegion staticRegion = blurRegionFromPercent(
                        fallbackPoint.xPct(),
                        fallbackPoint.yPct(),
                        fallbackPoint.widthPct(),
                        fallbackPoint.heightPct(),
                        subject.startSeconds(),
                        subject.endSeconds(),
                        subject.passes(),
                        probe
                    );
                    if (staticRegion != null) {
                        fromSubjects.add(staticRegion);
                    }
                }
            }
            if (!fromSubjects.isEmpty()) {
                return fromSubjects;
            }
        }

        BlurRegion baseRegion = resolveBlurRegion(edits, probe);
        if (baseRegion == null) return List.of();
        List<BlurTrackPoint> trackPoints = parseBlurTrackPoints(edits);
        if (trackPoints.size() < 2) {
            return List.of(baseRegion);
        }

        double baseStart = baseRegion.startSeconds();
        double baseEnd = baseRegion.endSeconds();
        boolean hasBaseWindow = baseEnd > baseStart + 0.05d;
        List<BlurRegion> regions = buildTrackedBlurRegions(
            trackPoints,
            baseStart,
            baseEnd,
            hasBaseWindow,
            baseRegion.passes(),
            probe
        );

        if (!regions.isEmpty()) {
            return regions;
        }
        return List.of(baseRegion);
    }

    private BlurRegion blurRegionFromPercent(
        double xPctRaw,
        double yPctRaw,
        double wPctRaw,
        double hPctRaw,
        double startSeconds,
        double endSeconds,
        int passes,
        MediaProbe probe
    ) {
        int videoWidth = Math.max(0, probe.videoWidth());
        int videoHeight = Math.max(0, probe.videoHeight());
        if (videoWidth <= 0 || videoHeight <= 0) return null;

        double wPct = clamp(wPctRaw, 1d, 100d);
        double hPct = clamp(hPctRaw, 1d, 100d);
        double xPct = clamp(xPctRaw, 0d, Math.max(0d, 100d - wPct));
        double yPct = clamp(yPctRaw, 0d, Math.max(0d, 100d - hPct));

        int x = clampInt((int) Math.round(videoWidth * xPct / 100d), 0, Math.max(0, videoWidth - 1));
        int y = clampInt((int) Math.round(videoHeight * yPct / 100d), 0, Math.max(0, videoHeight - 1));
        int w = clampInt((int) Math.round(videoWidth * wPct / 100d), 1, Math.max(1, videoWidth - x));
        int h = clampInt((int) Math.round(videoHeight * hPct / 100d), 1, Math.max(1, videoHeight - y));

        double start = Math.max(0d, startSeconds);
        double end = Math.max(0d, endSeconds);
        if (end <= start + 0.02d) return null;

        return new BlurRegion(x, y, w, h, 0, start, end, Math.max(1, passes));
    }

    private List<BlurRegion> buildTrackedBlurRegions(
        List<BlurTrackPoint> trackPoints,
        double windowStart,
        double windowEnd,
        boolean hasWindow,
        int passes,
        MediaProbe probe
    ) {
        if (trackPoints == null || trackPoints.size() < 2) return List.of();
        int maxSegments = 420;
        List<BlurRegion> regions = new ArrayList<>();
        for (int i = 1; i < trackPoints.size() && regions.size() < maxSegments; i += 1) {
            BlurTrackPoint prev = trackPoints.get(i - 1);
            BlurTrackPoint next = trackPoints.get(i);
            if (!prev.visible() || !next.visible()) continue;
            if (next.timeSeconds() - prev.timeSeconds() > BLUR_TRACK_MAX_INTERPOLATION_GAP_SECONDS) continue;
            double segStart = prev.timeSeconds();
            double segEnd = next.timeSeconds();
            if (hasWindow) {
                segStart = Math.max(segStart, windowStart);
                segEnd = Math.min(segEnd, windowEnd);
            }
            if (segEnd <= segStart + 0.02d) continue;

            double midX = (prev.xPct() + next.xPct()) / 2d;
            double midY = (prev.yPct() + next.yPct()) / 2d;
            double midW = (prev.widthPct() + next.widthPct()) / 2d;
            double midH = (prev.heightPct() + next.heightPct()) / 2d;
            BlurRegion tracked = blurRegionFromPercent(midX, midY, midW, midH, segStart, segEnd, passes, probe);
            if (tracked != null) {
                regions.add(tracked);
            }
        }
        return regions;
    }

    private BlurTrackPoint firstVisibleTrackPoint(List<BlurTrackPoint> trackPoints) {
        if (trackPoints == null || trackPoints.isEmpty()) return null;
        for (BlurTrackPoint point : trackPoints) {
            if (point.visible()) return point;
        }
        return trackPoints.get(0);
    }

    private List<BlurTrackPoint> parseBlurTrackPoints(Map<String, Object> edits) {
        return parseBlurTrackPointsList(asList(edits.get("blurTrackPoints")));
    }

    private List<BlurTrackPoint> parseBlurTrackPointsList(List<Object> rawPoints) {
        if (rawPoints.isEmpty()) return List.of();
        List<BlurTrackPoint> points = new ArrayList<>();
        for (Object raw : rawPoints) {
            Map<String, Object> item = asMap(raw);
            if (item.isEmpty()) continue;
            double time = Math.max(0d, number(item.get("time"), -1d));
            if (time < 0d) continue;
            double width = clamp(number(item.get("width"), 0d), 1d, 100d);
            double height = clamp(number(item.get("height"), 0d), 1d, 100d);
            double x = clamp(number(item.get("x"), number(item.get("left"), 0d)), 0d, Math.max(0d, 100d - width));
            double y = clamp(number(item.get("y"), number(item.get("top"), 0d)), 0d, Math.max(0d, 100d - height));
            boolean visible = !item.containsKey("visible") || booleanFlag(item.get("visible"));
            points.add(new BlurTrackPoint(time, x, y, width, height, visible));
        }
        points.sort((a, b) -> Double.compare(a.timeSeconds(), b.timeSeconds()));
        return points;
    }

    private List<BlurSubject> parseBlurSubjects(Map<String, Object> edits) {
        List<Object> rawSubjects = asList(edits.get("blurSubjects"));
        if (rawSubjects.isEmpty()) return List.of();
        List<BlurSubject> subjects = new ArrayList<>();
        for (Object raw : rawSubjects) {
            Map<String, Object> item = asMap(raw);
            if (item.isEmpty()) continue;
            List<BlurTrackPoint> points = parseBlurTrackPointsList(
                asList(item.containsKey("trackPoints") ? item.get("trackPoints") : item.get("points"))
            );
            if (points.size() < 2) continue;
            double intensity = clamp(number(item.get("intensity"), number(edits.get("blurIntensity"), 0d)), 0d, 100d);
            if (intensity <= 0.1d) continue;
            int passes = intensity >= 80d ? 3 : intensity >= 45d ? 2 : 1;
            double start = Math.max(0d, number(item.get("start"), number(edits.get("blurStart"), 0d)));
            double endRaw = Math.max(0d, number(item.get("end"), number(edits.get("blurEnd"), 0d)));
            double end = endRaw > start + 0.05d ? endRaw : 0d;
            subjects.add(new BlurSubject(points, start, end, passes));
        }
        return subjects;
    }

    private String buildDelogoFilter(BlurRegion region) {
        StringBuilder sb = new StringBuilder("delogo=x=")
            .append(region.x())
            .append(":y=").append(region.y())
            .append(":w=").append(region.w())
            .append(":h=").append(region.h());
        if (region.band() > 0) {
            sb.append(":band=").append(region.band());
        }
        if (region.endSeconds() > region.startSeconds() + 0.05d) {
            sb.append(":enable='between(t\\,")
                .append(f(region.startSeconds()))
                .append("\\,")
                .append(f(region.endSeconds()))
                .append(")'");
        }
        return sb.toString();
    }

    private String resolveBlurTargetType(Map<String, Object> edits) {
        String target = string(edits.get("blurTargetType")).toLowerCase(Locale.ROOT);
        if (target.isBlank()) {
            target = string(edits.get("blurMode")).toLowerCase(Locale.ROOT);
        }
        if ("off".equals(target) || "global".equals(target)) return "none";
        if ("face".equals(target) || "logo".equals(target) || "custom".equals(target) || "object".equals(target)) {
            return target;
        }
        double face = clamp(number(edits.get("blurFace"), 0d), 0d, 100d);
        double logo = clamp(number(edits.get("blurLogo"), 0d), 0d, 100d);
        double custom = clamp(number(edits.get("blurCustom"), 0d), 0d, 100d);
        if (face > 0.1d || logo > 0.1d || custom > 0.1d) {
            if (face >= logo && face >= custom) return "face";
            if (logo >= custom) return "logo";
            return "custom";
        }
        return "none";
    }

    private double[] defaultBlurRegionForTarget(String target) {
        return switch (target) {
            case "face" -> new double[] { 36d, 16d, 30d, 34d };
            case "logo" -> new double[] { 72d, 5d, 22d, 14d };
            case "object" -> new double[] { 34d, 34d, 30d, 28d };
            case "custom" -> new double[] { 33d, 24d, 34d, 34d };
            default -> new double[] { 33d, 24d, 34d, 34d };
        };
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean hasRenderableTimelineRequest(List<TimelineClip> timelineClips) {
        if (timelineClips == null || timelineClips.isEmpty()) return false;
        for (TimelineClip clip : timelineClips) {
            String type = clip.type();
            if ("text".equalsIgnoreCase(type) && !string(clip.payload().get("text")).isBlank()) return true;
            if ("sticker".equalsIgnoreCase(type)) {
                String sticker = string(clip.payload().get("sticker"));
                if (!sticker.isBlank() && !"none".equalsIgnoreCase(sticker)) return true;
            }
            if ("video".equalsIgnoreCase(type) && clip.startSeconds() > 0.01d) return true;
        }
        return false;
    }

    private String stickerGlyph(String sticker) {
        return switch (string(sticker).toLowerCase(Locale.ROOT)) {
            case "love" -> "<3";
            case "spark" -> "*";
            case "glow" -> "o";
            case "chat" -> "[]";
            case "frame" -> "#";
            default -> "*";
        };
    }

    private boolean hasRenderableEditRequest(Map<String, Object> edits) {
        if (edits.isEmpty()) return false;
        if (Math.max(0d, number(edits.get("trimStart"), 0d)) > 0.01d) return true;
        if (Math.max(0d, number(edits.get("trimEnd"), 0d)) > 0.01d) return true;
        if (booleanFlag(edits.get("reversePlayback"))) return true;
        if (Math.abs(clamp(number(edits.get("playbackSpeed"), 1d), 0.5d, 2d) - 1d) > 0.001d) return true;
        if (Math.abs(number(edits.get("cropZoom"), 100d) - 100d) > 0.01d) return true;
        if (Math.abs(number(edits.get("cropLeft"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("cropRight"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("cropTop"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("cropBottom"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("rotate"), 0d)) > 0.1d) return true;
        if (booleanFlag(edits.get("flipH")) || booleanFlag(edits.get("flipV"))) return true;
        if (Math.abs(number(edits.get("brightness"), 100d) - 100d) > 0.5d) return true;
        if (Math.abs(number(edits.get("contrast"), 100d) - 100d) > 0.5d) return true;
        if (Math.abs(number(edits.get("saturation"), 100d) - 100d) > 0.5d) return true;
        if (Math.abs(number(edits.get("vibrance"), 100d) - 100d) > 0.5d) return true;
        if (Math.abs(number(edits.get("exposure"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("highlights"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("shadows"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("blackPoint"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("warmth"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("tint"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("hue"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("fade"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("grain"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("vignette"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("softness"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("effectBlur"), 0d)) > 0.1d) return true;
        if (!"none".equals(resolveBlurTargetType(edits)) && Math.abs(number(edits.get("blurIntensity"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("blurFace"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("blurLogo"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("blurCustom"), 0d)) > 0.1d) return true;
        if (!asList(edits.get("blurSubjects")).isEmpty()) return true;
        if (asList(edits.get("blurTrackPoints")).size() > 1) return true;
        if (!string(edits.get("overlayText")).isBlank()) return true;
        String sticker = string(edits.get("sticker"));
        if (!sticker.isBlank() && !"none".equalsIgnoreCase(sticker)) return true;
        if (booleanFlag(edits.get("muted"))) return true;
        if (Math.abs(number(edits.get("volume"), 100d) - 100d) > 0.1d) return true;
        if (Math.abs(number(edits.get("voiceoverGain"), 100d) - 100d) > 0.1d) return true;
        if (Math.abs(number(edits.get("noiseRemoval"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("deEss"), 0d)) > 0.1d) return true;
        if (Math.abs(number(edits.get("loudnessTarget"), -14d) + 14d) > 0.1d) return true;
        String preset = string(edits.get("filterPreset"));
        return !preset.isBlank() && !"normal".equalsIgnoreCase(preset);
    }

    private boolean wantsAutoCover(Map<String, Object> cover) {
        String mode = string(cover.get("mode"));
        if (mode.isBlank()) return true;
        return "video_frame".equalsIgnoreCase(mode);
    }

    private boolean isVideo(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("video/");
    }

    private String fileSuffix(String originalFilename, String fallback) {
        if (originalFilename == null || originalFilename.isBlank()) return fallback;
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return fallback;
        String ext = originalFilename.substring(dot).trim();
        if (ext.length() > 12) return fallback;
        return ext;
    }

    private String processedName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "edited-video.mp4";
        }
        int dot = originalFilename.lastIndexOf('.');
        String base = dot > 0 ? originalFilename.substring(0, dot) : originalFilename;
        return base + "_edited.mp4";
    }

    private String coverName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "cover.jpg";
        }
        int dot = originalFilename.lastIndexOf('.');
        String base = dot > 0 ? originalFilename.substring(0, dot) : originalFilename;
        return base + "_cover.jpg";
    }

    private static String escapeForFilterPath(String raw) {
        return raw
            .replace("\\", "/")
            .replace(":", "\\:")
            .replace("'", "\\'");
    }

    private static String blankToDefault(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return raw.trim();
    }

    private static String safeRawSettings(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String string(Object value) {
        if (value == null) return "";
        return String.valueOf(value).trim();
    }

    private static boolean booleanFlag(Object value) {
        if (value instanceof Boolean b) return b;
        String v = string(value).toLowerCase(Locale.ROOT);
        return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v) || "on".equals(v);
    }

    private static double number(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String f(double value) {
        String text = String.format(Locale.ROOT, "%.6f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // noop
        }
    }

    private static final class TimelineClip {
        private final String type;
        private final String trackId;
        private final double startSeconds;
        private final double endSeconds;
        private final Map<String, Object> payload;
        private final String label;

        private TimelineClip(
            String type,
            String trackId,
            double startSeconds,
            double endSeconds,
            Map<String, Object> payload,
            String label
        ) {
            this.type = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
            this.trackId = trackId == null ? "" : trackId.trim();
            this.startSeconds = Math.max(0d, startSeconds);
            this.endSeconds = Math.max(this.startSeconds, endSeconds);
            this.payload = payload == null ? Map.of() : payload;
            this.label = label == null ? "" : label.trim();
        }

        private TimelineClip withRange(double start, double end) {
            return new TimelineClip(type, trackId, start, end, payload, label);
        }

        public String type() {
            return type;
        }

        public String trackId() {
            return trackId;
        }

        public double startSeconds() {
            return startSeconds;
        }

        public double endSeconds() {
            return endSeconds;
        }

        public Map<String, Object> payload() {
            return payload;
        }

        public String label() {
            return label;
        }
    }

    private static final class MediaProbe {
        private final boolean hasAudio;
        private final double durationSeconds;
        private final int videoWidth;
        private final int videoHeight;

        private MediaProbe(boolean hasAudio, double durationSeconds, int videoWidth, int videoHeight) {
            this.hasAudio = hasAudio;
            this.durationSeconds = durationSeconds;
            this.videoWidth = Math.max(0, videoWidth);
            this.videoHeight = Math.max(0, videoHeight);
        }

        public boolean hasAudio() {
            return hasAudio;
        }

        public double durationSeconds() {
            return durationSeconds;
        }

        public int videoWidth() {
            return videoWidth;
        }

        public int videoHeight() {
            return videoHeight;
        }
    }

    private static final class BlurSubject {
        private final List<BlurTrackPoint> trackPoints;
        private final double startSeconds;
        private final double endSeconds;
        private final int passes;

        private BlurSubject(List<BlurTrackPoint> trackPoints, double startSeconds, double endSeconds, int passes) {
            this.trackPoints = trackPoints == null ? List.of() : trackPoints;
            this.startSeconds = Math.max(0d, startSeconds);
            this.endSeconds = Math.max(0d, endSeconds);
            this.passes = Math.max(1, passes);
        }

        public List<BlurTrackPoint> trackPoints() {
            return trackPoints;
        }

        public double startSeconds() {
            return startSeconds;
        }

        public double endSeconds() {
            return endSeconds;
        }

        public int passes() {
            return passes;
        }
    }

    private static final class BlurTrackPoint {
        private final double timeSeconds;
        private final double xPct;
        private final double yPct;
        private final double widthPct;
        private final double heightPct;
        private final boolean visible;

        private BlurTrackPoint(double timeSeconds, double xPct, double yPct, double widthPct, double heightPct, boolean visible) {
            this.timeSeconds = Math.max(0d, timeSeconds);
            this.xPct = clampPct(xPct);
            this.yPct = clampPct(yPct);
            this.widthPct = clampPct(widthPct);
            this.heightPct = clampPct(heightPct);
            this.visible = visible;
        }

        private static double clampPct(double raw) {
            return Math.max(0d, Math.min(100d, raw));
        }

        public double timeSeconds() {
            return timeSeconds;
        }

        public double xPct() {
            return xPct;
        }

        public double yPct() {
            return yPct;
        }

        public double widthPct() {
            return widthPct;
        }

        public double heightPct() {
            return heightPct;
        }

        public boolean visible() {
            return visible;
        }
    }

    private static final class BlurRegion {
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final int band;
        private final double startSeconds;
        private final double endSeconds;
        private final int passes;

        private BlurRegion(int x, int y, int w, int h, int band, double startSeconds, double endSeconds, int passes) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.band = Math.max(0, band);
            this.startSeconds = Math.max(0d, startSeconds);
            this.endSeconds = Math.max(0d, endSeconds);
            this.passes = Math.max(1, passes);
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int w() {
            return w;
        }

        public int h() {
            return h;
        }

        public int band() {
            return band;
        }

        public double startSeconds() {
            return startSeconds;
        }

        public double endSeconds() {
            return endSeconds;
        }

        public int passes() {
            return passes;
        }
    }

    public static final class ProcessingResult implements AutoCloseable {
        private final MultipartFile uploadFile;
        private final MultipartFile generatedCoverImage;
        private final String normalizedVideoSettings;
        private final boolean editsApplied;
        private final List<Path> tempArtifacts;

        private ProcessingResult(
            MultipartFile uploadFile,
            MultipartFile generatedCoverImage,
            String normalizedVideoSettings,
            boolean editsApplied,
            List<Path> tempArtifacts
        ) {
            this.uploadFile = uploadFile;
            this.generatedCoverImage = generatedCoverImage;
            this.normalizedVideoSettings = normalizedVideoSettings;
            this.editsApplied = editsApplied;
            this.tempArtifacts = tempArtifacts;
        }

        private static ProcessingResult noop(MultipartFile uploadFile, String normalizedVideoSettings) {
            return new ProcessingResult(uploadFile, null, normalizedVideoSettings, false, List.of());
        }

        public MultipartFile getUploadFile() {
            return uploadFile;
        }

        public MultipartFile getGeneratedCoverImage() {
            return generatedCoverImage;
        }

        public String getNormalizedVideoSettings() {
            return normalizedVideoSettings;
        }

        public boolean isEditsApplied() {
            return editsApplied;
        }

        @Override
        public void close() {
            for (Path path : tempArtifacts) {
                deleteQuietly(path);
            }
        }
    }
}
