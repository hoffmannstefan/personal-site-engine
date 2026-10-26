package com.homepage.frontend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RetryWhileBackendStartsTest {

    private final MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://b/x"));
    private final AtomicInteger calls = new AtomicInteger();

    /** Refuses the connection {@code failures} times, then answers with {@code status}. */
    private ClientHttpRequestExecution backend(int failures, HttpStatus status) {
        return (req, body) -> {
            if (calls.incrementAndGet() <= failures) {
                throw new ConnectException();
            }
            return new MockClientHttpResponse(new byte[0], status);
        };
    }

    @Test
    void waitsForABackendThatIsStarting() throws IOException {
        var response = new RetryWhileBackendStarts(Duration.ofSeconds(5)).intercept(request, new byte[0], backend(2, HttpStatus.OK));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(calls).hasValue(3);
    }

    @Test
    void givesUpAfterTheWindow() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> new RetryWhileBackendStarts(Duration.ofSeconds(1))
                .intercept(request, new byte[0], backend(Integer.MAX_VALUE, HttpStatus.OK)))
                .isInstanceOf(ConnectException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(calls.get()).isBetween(2, 3);
    }

    @Test
    void neverAsksABackendTwiceThatAnswered() throws IOException {
        var response = new RetryWhileBackendStarts(Duration.ofSeconds(5)).intercept(request, new byte[0],
                backend(0, HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(calls).hasValue(1);
    }

    @Test
    void withoutAWindowNothingIsRetried() {
        assertThatThrownBy(() -> new RetryWhileBackendStarts(Duration.ZERO)
                .intercept(request, new byte[0], backend(1, HttpStatus.OK)))
                .isInstanceOf(ConnectException.class);
        assertThat(calls).hasValue(1);
    }
}
