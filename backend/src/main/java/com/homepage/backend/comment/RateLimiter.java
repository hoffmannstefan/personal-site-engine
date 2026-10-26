package com.homepage.backend.comment;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter, per key, kept in memory. Good enough for a single
 * instance; use a shared store if the site is ever scaled out.
 */
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maxPerWindow;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int maxPerWindow, Clock clock) {
        this.maxPerWindow = maxPerWindow;
        this.clock = clock;
    }

    /** Records an attempt and returns whether it is within the limit. */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        long cutoff = now - WINDOW.toMillis();
        boolean[] allowed = {false};
        hits.compute(key, (k, q) -> {
            Deque<Long> window = q != null ? q : new ArrayDeque<>();
            while (!window.isEmpty() && window.peekFirst() <= cutoff) {
                window.pollFirst();
            }
            if (window.size() < maxPerWindow) {
                window.addLast(now);
                allowed[0] = true;
            }
            return window.isEmpty() ? null : window;
        });
        return allowed[0];
    }
}
