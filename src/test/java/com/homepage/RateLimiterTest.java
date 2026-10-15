package com.homepage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homepage.comment.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void limitsPerKeyWithinSlidingWindow() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(2, clock);

        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"), "other clients are unaffected");

        clock.now = clock.now.plus(Duration.ofSeconds(61));
        assertTrue(limiter.tryAcquire("a"), "window has slid past the earlier hits");
    }
}
