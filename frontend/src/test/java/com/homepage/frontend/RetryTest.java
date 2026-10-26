package com.homepage.frontend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A backend that is still starting (as during a deploy): pages and comments wait for it instead of failing with 503,
 * and a comment reaches it exactly once.
 */
@SpringBootTest(properties = "frontend.backend-retry=10s")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RetryTest {

    static final int PORT = freePort();
    static final List<String> received = new CopyOnWriteArrayList<>();
    HttpServer backend;

    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void backendUrl(DynamicPropertyRegistry registry) {
        registry.add("frontend.backend-url", () -> "http://127.0.0.1:" + PORT);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Starts the fake backend on {@link #PORT} after {@code delay}, in the background. */
    private void startBackendAfter(Duration delay) {
        received.clear();
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(delay);
                backend = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
                backend.createContext("/", exchange -> {
                    received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                    String body = switch (exchange.getRequestURI().getPath()) {
                        case "/api/site" -> "{\"title\":\"Test Site\",\"author\":\"Jane Roe\"}";
                        case "/api/posts" -> "{\"page\":1,\"pages\":1,\"posts\":[]}";
                        default -> "{\"id\":1}";
                    };
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(exchange.getRequestMethod().equals("POST") ? 201 : 200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
                backend.start();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @AfterEach
    void stopBackend() {
        if (backend != null) {
            backend.stop(0);
        }
    }

    @Test
    void aPageWaitsForTheBackendInsteadOfFailing() throws Exception {
        startBackendAfter(Duration.ofMillis(1500));
        long start = System.nanoTime();
        mvc.perform(get("/posts/")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<title>Posts | Test Site</title>")));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThan(Duration.ofSeconds(1));
    }

    @Test
    void aCommentWaitsAndArrivesExactlyOnce() throws Exception {
        startBackendAfter(Duration.ofMillis(1500));
        mvc.perform(post("/api/posts/hello/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"A\",\"body\":\"x\"}"))
                .andExpect(status().isCreated());
        assertThat(received).containsExactly("POST /api/posts/hello/comments");
    }
}
