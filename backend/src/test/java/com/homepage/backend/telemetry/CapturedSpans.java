package com.homepage.backend.telemetry;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Keeps the spans the backend exports in memory, next to the configured exporters (none in the tests). */
@TestConfiguration
class CapturedSpans {

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

    /** The server span of the first request; it ends just after the response is sent, so wait for it briefly. */
    static SpanData firstServerSpan(InMemorySpanExporter exporter) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            var span = exporter.getFinishedSpanItems().stream().filter(s -> s.getKind() == SpanKind.SERVER).findFirst();
            if (span.isPresent()) {
                return span.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No server span was exported");
    }
}
