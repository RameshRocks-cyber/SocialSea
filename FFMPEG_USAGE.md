# FFmpeg / FFprobe Usage in SocialSea Backend

## Why We Need It

The backend uses `ffmpeg` and `ffprobe` to process reel videos on the server:

- `ffmpeg`:
  - Applies video edits (trim/effects/text/audio operations).
  - Exports the final edited reel output.
  - Generates a cover image (thumbnail) frame from the video.
- `ffprobe`:
  - Reads media metadata (duration, width/height, codec info).
  - Helps validate uploaded videos before or during processing.

Without these tools on the backend host, upload can work for raw files, but server-side editing and reliable cover image generation will fail.

## Timeline Support (Phase 1)

The upload flow now sends a timeline payload inside `videoSettings.timeline`:

- `timeline.version` (currently `1`)
- `timeline.duration` (source duration in seconds)
- `timeline.clips[]` (video/text/sticker clips with `start`, `end`, `trackId`, and optional `payload`)

Backend behavior:

- Uses timeline video clip bounds to derive trim window when meaningful.
- Applies timed text/sticker overlays with FFmpeg `drawtext` + `enable=between(t,...)`.
- Keeps current single-source render strategy (no multi-source concat yet).

## Blur Controls (Phase 2)

The video editor now sends blur settings inside `videoSettings.edits`:

- `effectBlur` (global full-frame blur)
- `blurTargetType` (`none`, `face`, `logo`, `object`, `custom`)
- `blurShape` (`rectangle`, `circle`)
- `blurIntensity` (0-100)
- `blurFeather` (edge softness)
- `blurTracking` (`off`, `smooth`, `aggressive`)
- `blurStart` / `blurEnd` (seconds; optional timed range)
- `blurX`, `blurY`, `blurWidth`, `blurHeight` (region in percent)
- `blurTrackPoints[]` (optional motion path points: `time`, `x`, `y`, `width`, `height`)

Backend behavior:

- Applies global blur using `gblur` when `effectBlur` is used.
- Applies region blur using FFmpeg `delogo` with absolute pixel coordinates derived from `%` values.
- Repeats `delogo` passes based on intensity for stronger blur.
- Applies timed blur windows with `enable='between(t,...)'`.
- Uses feather by expanding and softening the region boundaries.
- If `blurTrackPoints` are present, backend renders segmented time-sliced region blur so blur follows the moving face/object path.

## Download + Storage Estimate

Typical portable package used:

- Download size: `~103.7 MB` (108,771,139 bytes for essentials zip).
- Extracted disk usage: roughly `300-400 MB` depending on build.

## Backend Config

Configured via application properties:

- `app.video-editing.enabled=true`
- `app.video-editing.ffmpeg-bin=<path-to-ffmpeg>`
- `app.video-editing.ffprobe-bin=<path-to-ffprobe>`

Default fallback values are command names (`ffmpeg`, `ffprobe`) if binaries are available in system PATH.
