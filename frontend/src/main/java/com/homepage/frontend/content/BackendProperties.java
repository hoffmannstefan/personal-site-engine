package com.homepage.frontend.content;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the backend runs, from {@code frontend.backend-url} (env {@code BACKEND_URL}), and how long a call waits for
 * a backend that cannot be reached, e.g. while it restarts, from {@code frontend.backend-retry} (env
 * {@code BACKEND_RETRY}).
 */
@ConfigurationProperties(prefix = "frontend")
public record BackendProperties(String backendUrl, @DefaultValue("20s") Duration backendRetry) {

    public BackendProperties {
        backendUrl = backendUrl.replaceAll("/+$", "");
    }
}
