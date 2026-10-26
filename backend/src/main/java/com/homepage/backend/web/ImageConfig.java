package com.homepage.backend.web;

import com.homepage.backend.site.SiteProperties;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves {@code /images/} from the folder {@code site.images-dir} (avatar and link icons of the start page), so the
 * images can change without rebuilding. The frontend passes {@code /images/} on to here.
 */
@Configuration
class ImageConfig implements WebMvcConfigurer {

    private final SiteProperties site;
    private final WebProperties web;

    ImageConfig(SiteProperties site, WebProperties web) {
        this.site = site;
        this.web = web;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (site.imagesDir() == null || site.imagesDir().isBlank()) {
            return;
        }
        String folder = Path.of(site.imagesDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/images/**")
                .addResourceLocations(folder.endsWith("/") ? folder : folder + "/")
                .setCacheControl(web.getResources().getCache().getCachecontrol().toHttpCacheControl());
    }
}
