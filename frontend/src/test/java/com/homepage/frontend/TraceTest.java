package com.homepage.frontend;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A page request is one trace: the frontend's server span, a client span for every call to the backend under it, and
 * the trace passed on to the backend in {@code traceparent}. The spans name the frontend, its environment and version.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "otel.sdk.disabled=false",
        "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none",
        "DEPLOYMENT_ENVIRONMENT=production", "SERVICE_VERSION=abc1234"})
@ActiveProfiles("test")
@Import(TraceTest.CapturedSpans.class)
class TraceTest {

    /** The {@code traceparent} headers the fake backend received, in order. */
    static final List<String> traceparents = new CopyOnWriteArrayList<>();
    static final HttpServer backend = start();

    @Autowired TestRestTemplate http;
    @Autowired InMemorySpanExporter spans;

    @TestConfiguration
    static class CapturedSpans {

        private final InMemorySpanExporter exporter = InMemorySpanExporter.create();

        @Bean
        InMemorySpanExporter capturedSpanExporter() {
            return exporter;
        }

        @Bean
        AutoConfigurationCustomizerProvider captureSpans() {
            return customizer -> customizer.addTracerProviderCustomizer(
                    (tracerProvider, config) -> tracerProvider.addSpanProcessor(SimpleSpanProcessor.create(exporter)));
        }
    }

    @DynamicPropertySource
    static void backendUrl(DynamicPropertyRegistry registry) {
        registry.add("frontend.backend-url", () -> "http://127.0.0.1:" + backend.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        backend.stop(0);
    }

    @BeforeEach
    void clear() {
        traceparents.clear();
        spans.reset();
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String traceparent = exchange.getRequestHeaders().getFirst("traceparent");
                if (traceparent != null) {
                    traceparents.add(traceparent);
                }
                String body = switch (exchange.getRequestURI().getPath()) {
                    case "/api/site" -> "{\"title\":\"Test Site\",\"author\":\"Jane Roe\",\"externalLinksInNewTab\":true}";
                    case "/api/posts/hello" -> """
                            {"slug":"hello","title":"Hello","date":"2030-05-06","authors":[],"categories":[],"tags":[],
                             "body":"<p>Hi</p>","math":false}""";
                    default -> "[]";
                };
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The server span of the request; it ends just after the response is sent, so wait for it briefly. */
    private SpanData serverSpan() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            var span = spans.getFinishedSpanItems().stream().filter(s -> s.getKind() == SpanKind.SERVER).findFirst();
            if (span.isPresent()) {
                return span.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No server span was exported");
    }

    /** The frontend's calls to the backend; the test's own HTTP client is instrumented too and is left out. */
    private List<SpanData> backendCalls() {
        long port = backend.getAddress().getPort();
        return spans.getFinishedSpanItems().stream()
                .filter(s -> s.getKind() == SpanKind.CLIENT)
                .filter(s -> Long.valueOf(port).equals(s.getAttributes().get(longKey("server.port"))))
                .toList();
    }

    @Test
    void aPageRequestIsOneTraceThatReachesTheBackend() throws Exception {
        assertThat(http.getForEntity("/posts/hello/", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        SpanData page = serverSpan();
        List<SpanData> calls = backendCalls();
        // the site for the layout and the post itself
        assertThat(calls).hasSize(2).allSatisfy(call -> {
            assertThat(call.getTraceId()).isEqualTo(page.getTraceId());
            assertThat(call.getParentSpanId()).isEqualTo(page.getSpanId());
        });
        // W3C traceparent: version-traceid-parentid-flags, the parent being the client span of the call
        assertThat(traceparents).hasSize(2).allSatisfy(header -> {
            String[] parts = header.split("-");
            assertThat(parts[1]).isEqualTo(page.getTraceId());
            assertThat(calls).extracting(SpanData::getSpanId).contains(parts[2]);
        });
    }

    @Test
    void forwardedApiCallsContinueTheTraceToo() throws Exception {
        http.getForEntity("/api/posts/hello/comments", String.class);

        SpanData request = serverSpan();
        assertThat(backendCalls()).singleElement()
                .satisfies(call -> {
                    assertThat(call.getTraceId()).isEqualTo(request.getTraceId());
                    assertThat(call.getParentSpanId()).isEqualTo(request.getSpanId());
                });
        assertThat(traceparents).singleElement()
                .satisfies(header -> assertThat(header.split("-")[1]).isEqualTo(request.getTraceId()));
    }

    @Test
    void spansNameTheFrontendItsEnvironmentAndVersion() throws Exception {
        http.getForEntity("/posts/hello/", String.class);

        Resource resource = serverSpan().getResource();
        assertThat(resource.getAttribute(stringKey("service.name"))).isEqualTo("homepage-frontend");
        assertThat(resource.getAttribute(stringKey("deployment.environment.name"))).isEqualTo("production");
        assertThat(resource.getAttribute(stringKey("service.version"))).isEqualTo("abc1234");
    }
}
