package com.homepage.web;

import com.homepage.site.SiteProperties;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves {@code /images/} from the folder {@code site.images-dir} (avatar and link icons of the start page) and
 * then from the bundled {@code static/images/} (favicons), so the images can change without rebuilding.
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
        var handler = registry.addResourceHandler("/images/**");
        if (site.imagesDir() != null && !site.imagesDir().isBlank()) {
            String folder = Path.of(site.imagesDir()).toAbsolutePath().normalize().toUri().toString();
            handler.addResourceLocations(folder.endsWith("/") ? folder : folder + "/");
        }
        handler.addResourceLocations("classpath:/static/images/")
                .setCacheControl(web.getResources().getCache().getCachecontrol().toHttpCacheControl());
    }
}
