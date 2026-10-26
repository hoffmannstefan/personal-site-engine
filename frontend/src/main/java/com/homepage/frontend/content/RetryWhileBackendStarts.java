package com.homepage.frontend.content;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Tries a call to the backend again while no connection can be made, for up to {@code frontend.backend-retry}. While
 * the backend restarts (every deploy, about 10 seconds) pages and comments wait instead of failing with 503.
 *
 * <p>Only a failed connection is retried: the request never reached the backend, so this is safe for every method,
 * including POST. A backend that answers, even with an error, is never asked twice.
 */
final class RetryWhileBackendStarts implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RetryWhileBackendStarts.class);
    private static final long PAUSE_MILLIS = 500;

    private final Duration window;

    RetryWhileBackendStarts(Duration window) {
        this.window = window;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long deadline = System.nanoTime() + window.toNanos();
        int attempt = 1;
        while (true) {
            try {
                ClientHttpResponse response = execution.execute(request, body);
                if (attempt > 1) {
                    log.info("Reached the backend after {} attempts: {} {}", attempt, request.getMethod(), request.getURI());
                }
                return response;
            } catch (ConnectException | HttpConnectTimeoutException e) {
                if (System.nanoTime() + PAUSE_MILLIS * 1_000_000 > deadline) {
                    throw e;
                }
                pause(e);
                attempt++;
            }
        }
    }

    private static void pause(IOException cause) throws IOException {
        try {
            Thread.sleep(PAUSE_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }
}
