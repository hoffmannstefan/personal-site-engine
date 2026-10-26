package com.homepage.frontend.web;

import com.homepage.frontend.content.BackendProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

/**
 * Passes {@code /api/**} (the comments of {@code comments.js} and the content API) and {@code /images/**} (avatar and
 * link icons from {@code info/images/}) on to the backend, so the browser only ever talks to the frontend and the
 * backend needs no port of its own.
 *
 * <p>The visitor's address goes along as {@code X-Forwarded-For}, so the backend's comment rate limit counts per
 * visitor. Only the headers the API and images need are passed on, in both directions.
 */
@RestController
class BackendProxy {

    private static final Logger log = LoggerFactory.getLogger(BackendProxy.class);
    private static final List<String> REQUEST_HEADERS = List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
            HttpHeaders.IF_NONE_MATCH, HttpHeaders.IF_MODIFIED_SINCE, "X-Admin-Token");
    private static final List<String> RESPONSE_HEADERS = List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.CACHE_CONTROL,
            HttpHeaders.ETAG, HttpHeaders.LAST_MODIFIED);

    private final RestClient backend;
    private final String backendUrl;

    BackendProxy(RestClient backend, BackendProperties properties) {
        this.backend = backend;
        this.backendUrl = properties.backendUrl();
    }

    @RequestMapping("/api/**")
    ResponseEntity<byte[]> api(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        return forward(request, body);
    }

    @GetMapping("/images/**")
    ResponseEntity<byte[]> image(HttpServletRequest request) {
        return forward(request, null);
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail backendUnavailable(RestClientException e) {
        log.warn("Cannot pass the request on to the backend: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The site is temporarily unavailable. Please try again in a moment.");
    }

    private ResponseEntity<byte[]> forward(HttpServletRequest request, byte[] body) {
        // The path as the browser sent it, still encoded, so it reaches the backend unchanged.
        String path = request.getRequestURI();
        if (!isSafe(path)) {
            return ResponseEntity.badRequest().build();
        }
        URI uri = URI.create(backendUrl + path + (request.getQueryString() == null ? "" : "?" + request.getQueryString()));
        RestClient.RequestBodySpec call = backend.method(HttpMethod.valueOf(request.getMethod())).uri(uri)
                .headers(h -> {
                    for (String name : REQUEST_HEADERS) {
                        String value = request.getHeader(name);
                        if (value != null) {
                            h.set(name, value);
                        }
                    }
                    h.set("X-Forwarded-For", request.getRemoteAddr());
                });
        if (body != null) {
            call.body(body);
        }
        // exchange() hands over every status as it is, including the backend's 4xx and 5xx answers.
        return call.exchange((req, res) -> {
            HttpHeaders headers = new HttpHeaders();
            for (String name : RESPONSE_HEADERS) {
                List<String> values = res.getHeaders().get(name);
                if (values != null) {
                    headers.put(name, values);
                }
            }
            return new ResponseEntity<>(res.getBody().readAllBytes(), headers, res.getStatusCode());
        });
    }

    /** No {@code .} or {@code ..} segments, also not encoded, so a request cannot leave {@code /api/} or {@code /images/}. */
    private static boolean isSafe(String path) {
        for (String segment : path.split("/")) {
            String s = UriUtils.decode(segment, StandardCharsets.UTF_8);
            if (s.equals(".") || s.equals("..") || s.contains("/") || s.contains("\\")) {
                return false;
            }
        }
        return true;
    }
}
