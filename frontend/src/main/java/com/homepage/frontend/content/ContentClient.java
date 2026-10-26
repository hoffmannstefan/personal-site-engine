package com.homepage.frontend.content;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the content of the pages from the backend's JSON API. Nothing is cached: the backend reads its content at
 * startup, so a restart of the backend alone publishes changes. A backend that is down or answers with an error
 * throws a {@link RestClientException}, which the pages turn into 503.
 */
@Component
public class ContentClient {

    private final RestClient backend;

    ContentClient(RestClient backend) {
        this.backend = backend;
    }

    public Site site() {
        return backend.get().uri("/api/site").retrieve().body(Site.class);
    }

    /** Page {@code page} of the blog list, or empty past the last page. */
    public Optional<PostPage> posts(int page) {
        return unlessNotFound(() -> backend.get().uri("/api/posts?page={page}", page).retrieve().body(PostPage.class));
    }

    /** The post, or empty if there is none with this slug. */
    public Optional<Post> post(String slug) {
        return unlessNotFound(() -> backend.get().uri("/api/posts/{slug}", slug).retrieve().body(Post.class));
    }

    /** Grouped by {@code group} (year, type or none); null or an unknown value gives the configured grouping. */
    public Publications publications(String group) {
        return backend.get()
                .uri(b -> b.path("/api/publications").queryParamIfPresent("group", Optional.ofNullable(group)).build())
                .retrieve().body(Publications.class);
    }

    private static <T> Optional<T> unlessNotFound(Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
