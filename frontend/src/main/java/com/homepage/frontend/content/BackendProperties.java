package com.homepage.frontend.content;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the backend runs, from {@code frontend.backend-url} (env {@code BACKEND_URL}). */
@ConfigurationProperties(prefix = "frontend")
public record BackendProperties(String backendUrl) {

    public BackendProperties {
        backendUrl = backendUrl.replaceAll("/+$", "");
    }
}
