package com.homepage.frontend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homepage.frontend.content.ContentClient;
import com.homepage.frontend.content.Post;
import com.homepage.frontend.content.Publications;
import com.homepage.frontend.content.Site;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
 * The frontend against a fake backend over real HTTP: the JSON of the content API is read as the backend writes it,
 * and {@code /api/**} and {@code /images/**} are passed on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendTest {

    /** The last request the fake backend got: method, path with query, body and the headers below. */
    static final Map<String, String> received = new ConcurrentHashMap<>();
    static final HttpServer backend = start();

    @Autowired MockMvc mvc;
    @Autowired ContentClient content;

    @DynamicPropertySource
    static void backendUrl(DynamicPropertyRegistry registry) {
        registry.add("frontend.backend-url", () -> "http://127.0.0.1:" + backend.getAddress().getPort() + "/");
    }

    @AfterAll
    static void stop() {
        backend.stop(0);
    }

    @BeforeEach
    void clear() {
        received.clear();
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                record(exchange);
                String path = exchange.getRequestURI().getRawPath();
                switch (path) {
                    case "/api/site" -> respond(exchange, 200, "application/json", """
                            {"title":"Test Site","author":"Jane Roe","description":null,"keywords":null,
                             "postsPerPage":6,"externalLinksInNewTab":false,
                             "home":{"headline":"Test Headline","avatar":null,"links":[],"html":"<p>Hi</p>"},
                             "publications":{"arxivPapers":"UNPUBLISHED","groupBy":"TYPE"}}""");
                    case "/api/posts/hello" -> respond(exchange, 200, "application/json", """
                            {"slug":"hello","title":"Hello","date":"2030-05-06","readingTime":"1-minute read",
                             "authors":["Jane Roe"],"categories":[],"tags":["x"],"body":"<p>Hi</p>",
                             "externalUrl":null,"math":true}""");
                    case "/api/publications" -> respond(exchange, 200, "application/json", """
                            {"groupBy":"year","groups":[{"heading":"2024","id":"2024","entries":[
                             {"authors":"A","title":"T","venue":"V","year":2024,"link":null,"preprint":null,
                              "type":"JOURNAL","id":"t","extensionOf":[],"extendedIn":[]}]}]}""");
                    case "/api/posts/hello/comments" -> respond(exchange,
                            exchange.getRequestMethod().equals("POST") ? 201 : 200, "application/json", "[]");
                    case "/api/comments/7" -> respond(exchange, 403, "application/problem+json",
                            "{\"status\":403,\"detail\":\"Not allowed\"}");
                    case "/images/me.jpg" -> {
                        exchange.getResponseHeaders().add("Cache-Control", "max-age=3600");
                        exchange.getResponseHeaders().add("Set-Cookie", "not=passed-on");
                        respond(exchange, 200, "image/jpeg", "JPEG");
                    }
                    default -> respond(exchange, 404, "application/json", "{\"status\":404}");
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void record(HttpExchange exchange) throws IOException {
        received.put("method", exchange.getRequestMethod());
        received.put("uri", exchange.getRequestURI().toString());
        received.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        for (String name : new String[] {"X-Forwarded-For", "X-Admin-Token", "Content-Type", "Cookie"}) {
            String value = exchange.getRequestHeaders().getFirst(name);
            if (value != null) {
                received.put(name, value);
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void readsTheContentApi() {
        Site site = content.site();
        assertThat(site.title()).isEqualTo("Test Site");
        assertThat(site.externalLinksInNewTab()).isFalse();
        assertThat(site.home().html()).isEqualTo("<p>Hi</p>");
        assertThat(site.home().hasCard()).isTrue();

        Post post = content.post("hello").orElseThrow();
        assertThat(post.date()).hasToString("2030-05-06");
        assertThat(post.tags()).containsExactly("x");
        assertThat(post.math()).isTrue();
        assertThat(content.post("nope")).isEmpty();
        assertThat(content.posts(3)).isEmpty();
        assertThat(received.get("uri")).isEqualTo("/api/posts?page=3");

        Publications publications = content.publications("year");
        assertThat(received.get("uri")).isEqualTo("/api/publications?group=year");
        assertThat(publications.groups().get(0).entries().get(0).title()).isEqualTo("T");
        content.publications(null);
        assertThat(received.get("uri")).isEqualTo("/api/publications");
    }

    @Test
    void rendersPagesFromTheBackend() throws Exception {
        mvc.perform(get("/posts/hello/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Hello | Test Site</title>")))
                .andExpect(content().string(containsString("tex-svg.js")));
        mvc.perform(get("/posts/nope/")).andExpect(status().isNotFound());
    }

    @Test
    void passesCommentsOnWithTheVisitorsAddress() throws Exception {
        mvc.perform(post("/api/posts/hello/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"A\",\"body\":\"x\"}")
                        .header("X-Forwarded-For", "6.6.6.6").cookie(new Cookie("a", "b"))
                        .with(r -> { r.setRemoteAddr("203.0.113.9"); return r; }))
                .andExpect(status().isCreated())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(content().string("[]"));
        assertThat(received).containsEntry("method", "POST")
                .containsEntry("uri", "/api/posts/hello/comments")
                .containsEntry("body", "{\"author\":\"A\",\"body\":\"x\"}")
                .containsEntry("X-Forwarded-For", "203.0.113.9")
                .doesNotContainKey("Cookie");
        assertThat(received.get("Content-Type")).startsWith("application/json");
    }

    @Test
    void passesOnTheBackendsErrorsAndTheAdminToken() throws Exception {
        mvc.perform(delete("/api/comments/7").header("X-Admin-Token", "secret"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(content().string(containsString("Not allowed")));
        assertThat(received).containsEntry("method", "DELETE").containsEntry("X-Admin-Token", "secret");
        mvc.perform(get("/api/nope?x=1")).andExpect(status().isNotFound());
        assertThat(received).containsEntry("uri", "/api/nope?x=1");
    }

    @Test
    void passesImagesOn() throws Exception {
        mvc.perform(get("/images/me.jpg")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", "max-age=3600"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().string("JPEG"));
    }

    @Test
    void refusesPathsThatLeaveTheirFolder() throws Exception {
        for (String p : new String[] {"/images/%2e%2e/api/site", "/images/a/%2E%2E/b", "/api/..%2Fsite", "/images/a%5C..%5Cb"}) {
            // As a URI, so the encoded dots and slashes reach the frontend as the browser sent them.
            mvc.perform(get(URI.create(p))).andExpect(status().isBadRequest());
        }
        assertThat(received).isEmpty();
    }
}
