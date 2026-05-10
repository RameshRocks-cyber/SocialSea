package com.socialsea.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.serve-local:false}")
    private boolean serveLocalUploads;

    @Value("${app.upload.local-cache-max-age-seconds:604800}")
    private long localUploadCacheMaxAgeSeconds;

    @Value("${app.upload.local-cache-public:true}")
    private boolean localUploadCachePublic;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        if (!serveLocalUploads) {
            return;
        }

        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCacheControl(resolveUploadCacheControl());
    }

    private CacheControl resolveUploadCacheControl() {
        long seconds = Math.max(1L, localUploadCacheMaxAgeSeconds);
        CacheControl cacheControl = CacheControl.maxAge(Duration.ofSeconds(seconds)).mustRevalidate();
        return localUploadCachePublic ? cacheControl.cachePublic() : cacheControl.cachePrivate();
    }
}
