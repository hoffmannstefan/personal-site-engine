package com.homepage.site;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Technical settings, bound by Spring from the {@code site.*} properties in {@code application.properties} (or the
 * matching environment variables): where the content lives and how comments behave.
 *
 * <p>What the site shows is in {@link Site}, read from the YAML file {@code configFile} by {@link SiteLoader}.
 */
@ConfigurationProperties(prefix = "site")
public record SiteProperties(
        /** YAML file with the name, texts and start page of the site, read at startup. */
        String configFile,
        /** Folder with the Markdown posts, read at startup. */
        String postsDir,
        Comments comments,
        /** BibTeX file with the list on the Publications page, read at startup. */
        String publicationsFile,
        /** Folder with the images of the start page, served under {@code /images/}. */
        String imagesDir) {

    public record Comments(
            /** Maximum comments one client address may post per minute. */
            int maxPerMinute,
            /** Token for deleting comments; deletion is disabled while blank. */
            String adminToken) {
    }
}
