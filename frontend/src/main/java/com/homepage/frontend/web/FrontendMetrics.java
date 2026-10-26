package com.homepage.frontend.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.springframework.stereotype.Component;

/**
 * The frontend's own metrics, next to the HTTP and JVM metrics of the OpenTelemetry starter. Like all telemetry they
 * carry the service name, environment and version, so they can be filtered by them.
 *
 * <ul>
 *   <li>{@code homepage.page.views} (counter): pages shown, by {@code page} ({@code home}, {@code post_list},
 *       {@code post}, {@code publications}) and, for posts, {@code post.slug}. Only pages that were shown count, not
 *       404s or 503s, so the slugs are those of existing posts.
 *   <li>{@code homepage.backend.unavailable} (counter): answers with 503 because the backend could not be reached or
 *       failed, by {@code source} ({@code page} or {@code proxy} for forwarded {@code /api/} and {@code /images/})
 * </ul>
 */
@Component
class FrontendMetrics {

    private static final AttributeKey<String> PAGE = AttributeKey.stringKey("page");
    private static final AttributeKey<String> POST = AttributeKey.stringKey("post.slug");
    private static final AttributeKey<String> SOURCE = AttributeKey.stringKey("source");

    private final LongCounter pageViews;
    private final LongCounter backendUnavailable;

    FrontendMetrics(OpenTelemetry openTelemetry) {
        Meter meter = openTelemetry.getMeter("com.homepage.frontend");
        pageViews = meter.counterBuilder("homepage.page.views").setUnit("{view}")
                .setDescription("Pages shown, by page and post").build();
        backendUnavailable = meter.counterBuilder("homepage.backend.unavailable").setUnit("{response}")
                .setDescription("Answers with 503 because the backend was not available, by source").build();
    }

    void pageView(String page) {
        pageViews.add(1, Attributes.of(PAGE, page));
    }

    void postView(String slug) {
        pageViews.add(1, Attributes.of(PAGE, "post", POST, slug));
    }

    void backendUnavailable(String source) {
        backendUnavailable.add(1, Attributes.of(SOURCE, source));
    }
}
