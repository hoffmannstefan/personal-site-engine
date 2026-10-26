package com.homepage.backend.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homepage.backend.comment.CommentRepository;
import com.homepage.backend.post.PostService;
import com.homepage.backend.publication.PublicationService;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The backend's own metrics, read as they would be exported. The comment limit is 2 per minute here. */
@SpringBootTest(properties = {
        "otel.sdk.disabled=false",
        "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none",
        "site.comments.max-per-minute=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BackendMetricsTest.CapturedMetrics.class)
class BackendMetricsTest {

    private static final String COMMENTS = "/api/posts/markdown-syntax/comments";

    @Autowired MockMvc mvc;
    @Autowired InMemoryMetricReader metrics;
    @Autowired CommentRepository repository;
    @Autowired PostService posts;
    @Autowired PublicationService publications;

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

    /** The points of a metric, by their attributes, e.g. {@code {outcome=accepted, post.slug=…} -> 1}. */
    private static Map<String, Long> points(Collection<MetricData> all, String name) {
        MetricData metric = all.stream().filter(m -> m.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No metric " + name));
        Collection<LongPointData> points = switch (metric.getType()) {
            case LONG_SUM -> metric.getLongSumData().getPoints();
            case LONG_GAUGE -> metric.getLongGaugeData().getPoints();
            default -> throw new AssertionError(name + " is a " + metric.getType());
        };
        return points.stream().collect(Collectors.toMap(p -> text(p.getAttributes()), LongPointData::getValue));
    }

    private static String text(Attributes attributes) {
        return attributes.asMap().entrySet().stream().map(e -> e.getKey().getKey() + "=" + e.getValue())
                .sorted().collect(Collectors.joining(", "));
    }

    private void comment(String json, int expectedStatus) throws Exception {
        mvc.perform(post(COMMENTS).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void countsCommentsByOutcomeAndContentAtStartup() throws Exception {
        repository.deleteAll();
        comment("{\"author\":\"Bot\",\"body\":\"spam\",\"website\":\"http://spam\"}", 201);   // honeypot, no limit used
        comment("{\"author\":\"A\",\"body\":\"hello\"}", 201);
        comment("{\"author\":\"B\",\"body\":\"reply\",\"parentId\":999999}", 400);            // second of two allowed
        comment("{\"author\":\"C\",\"body\":\"too fast\"}", 429);
        long id = repository.findAll().get(0).getId();
        mvc.perform(delete("/api/comments/" + id).header("X-Admin-Token", "wrong")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/" + id).header("X-Admin-Token", "secret")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/comments/" + id).header("X-Admin-Token", "secret")).andExpect(status().isNotFound());

        Collection<MetricData> all = metrics.collectAllMetrics();
        assertThat(points(all, "homepage.comment.submissions")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "outcome=spam, post.slug=markdown-syntax", 1L,
                "outcome=accepted, post.slug=markdown-syntax", 1L,
                "outcome=unknown_parent, post.slug=markdown-syntax", 1L,
                "outcome=rate_limited, post.slug=markdown-syntax", 1L));
        assertThat(points(all, "homepage.comment.deletions")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "outcome=forbidden", 1L, "outcome=deleted", 1L, "outcome=not_found", 1L));
        assertThat(points(all, "homepage.comment.stored")).containsEntry("", 0L);
        assertThat(points(all, "homepage.content.items")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "kind=post", (long) posts.all().size(),
                "kind=publication", (long) publications.all().size()));
        assertThat(posts.all()).isNotEmpty();
        assertThat(points(all, "homepage.content.skipped")).containsEntry("kind=post", 0L);
    }
}
