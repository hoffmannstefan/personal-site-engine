package com.homepage.backend.telemetry;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Without DEPLOYMENT_ENVIRONMENT and SERVICE_VERSION, as in a local run: environment local, the project version. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "otel.sdk.disabled=false",
        "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none"})
@ActiveProfiles("test")
@Import(CapturedSpans.class)
class TelemetryDefaultsTest {

    @Autowired TestRestTemplate http;
    @Autowired InMemorySpanExporter spans;

    @Test
    void localRunsReportEnvironmentLocalAndTheProjectVersion() throws Exception {
        http.getForEntity("/api/site", String.class);

        Resource resource = CapturedSpans.firstServerSpan(spans).getResource();
        assertThat(resource.getAttribute(stringKey("service.name"))).isEqualTo("homepage-backend");
        assertThat(resource.getAttribute(stringKey("deployment.environment.name"))).isEqualTo("local");
        assertThat(resource.getAttribute(stringKey("service.version"))).isEqualTo("0.0.1-SNAPSHOT");
    }
}
