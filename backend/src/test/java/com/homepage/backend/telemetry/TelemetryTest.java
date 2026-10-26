package com.homepage.backend.telemetry;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** The telemetry names the service, the environment and the deployed version, as set by the environment variables. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "otel.sdk.disabled=false",
        "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none",
        "DEPLOYMENT_ENVIRONMENT=production", "SERVICE_VERSION=abc1234"})
@ActiveProfiles("test")
@Import(CapturedSpans.class)
class TelemetryTest {

    @Autowired TestRestTemplate http;
    @Autowired InMemorySpanExporter spans;

    @Test
    void spansCarryServiceEnvironmentAndVersion() throws Exception {
        http.getForEntity("/api/site", String.class);

        SpanData span = CapturedSpans.firstServerSpan(spans);
        Resource resource = span.getResource();
        assertThat(resource.getAttribute(stringKey("service.name"))).isEqualTo("homepage-backend");
        assertThat(resource.getAttribute(stringKey("deployment.environment.name"))).isEqualTo("production");
        assertThat(resource.getAttribute(stringKey("service.version"))).isEqualTo("abc1234");
        assertThat(span.getName()).isEqualTo("GET /api/site");
    }
}
