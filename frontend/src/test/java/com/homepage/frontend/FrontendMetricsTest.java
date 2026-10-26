package com.homepage.frontend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homepage.frontend.content.ContentClient;
import com.homepage.frontend.content.PostPage;
import com.homepage.frontend.content.Publications;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

/**
 * The frontend's own metrics, read as they would be exported: page views of shown pages only, and 503s while the
 * backend is down. The content comes from a stub; the proxy points at a closed port (see application-test).
 */
@SpringBootTest(properties = {
        "otel.sdk.disabled=false",
        "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FrontendMetricsTest.CapturedMetrics.class)
class FrontendMetricsTest {

    @Autowired MockMvc mvc;
    @Autowired InMemoryMetricReader metrics;
    @MockitoBean ContentClient content;

    @TestConfiguration
    static class CapturedMetrics {

        private final InMemoryMetricReader reader = InMemoryMetricReader.create();

        @Bean
        InMemoryMetricReader capturedMetricReader() {
            return reader;
        }

        @Bean
        AutoConfigurationCustomizerProvider captureMetrics() {
            return customizer -> customizer.addMeterProviderCustomizer(
                    (meterProvider, config) -> meterProvider.registerMetricReader(reader));
        }
    }

    /** The points of a counter, by their attributes, e.g. {@code {page=post, post.slug=…} -> 2}. */
    private Map<String, Long> points(String name) {
        MetricData metric = metrics.collectAllMetrics().stream().filter(m -> m.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No metric " + name));
        return metric.getLongSumData().getPoints().stream().collect(Collectors.toMap(
                p -> p.getAttributes().asMap().entrySet().stream()
                        .map(e -> e.getKey().getKey() + "=" + e.getValue()).sorted()
                        .collect(Collectors.joining(", ")),
                LongPointData::getValue));
    }

    @Test
    void countsShownPagesAndBackendOutages() throws Exception {
        when(content.site()).thenReturn(PagesTest.SITE);
        when(content.posts(anyInt())).thenReturn(Optional.empty());
        when(content.posts(1)).thenReturn(Optional.of(new PostPage(1, 1, List.of())));
        when(content.post(anyString())).thenReturn(Optional.empty());
        when(content.post("markdown-syntax")).thenReturn(Optional.of(PagesTest.PLAIN_POST));
        when(content.post("down")).thenThrow(new ResourceAccessException("Connection refused"));
        when(content.publications(any())).thenReturn(new Publications("none", List.of()));

        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/posts/")).andExpect(status().isOk());
        mvc.perform(get("/posts/markdown-syntax/")).andExpect(status().isOk());
        mvc.perform(get("/posts/markdown-syntax")).andExpect(status().isOk());
        mvc.perform(get("/publications/")).andExpect(status().isOk());
        mvc.perform(get("/posts/nope/")).andExpect(status().isNotFound());             // not a view
        mvc.perform(get("/posts/page/9/")).andExpect(status().isNotFound());           // not a view
        mvc.perform(get("/posts/down/")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/posts/markdown-syntax/comments")).andExpect(status().isServiceUnavailable());

        assertThat(points("homepage.page.views")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "page=home", 1L,
                "page=post_list", 1L,
                "page=post, post.slug=markdown-syntax", 2L,
                "page=publications", 1L));
        assertThat(points("homepage.backend.unavailable")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "source=page", 1L,
                "source=proxy", 1L));
    }
}
