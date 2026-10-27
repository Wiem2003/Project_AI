package pi.integrated.course.security;

import org.junit.jupiter.api.Test;
import pi.integrated.course.config.CourseRagProperties;

import static org.junit.jupiter.api.Assertions.*;

class AskRateLimiterTest {

    private static AskRateLimiter limiter(int perMinute, int perDay) {
        CourseRagProperties props = new CourseRagProperties();
        props.setRateLimitPerMinute(perMinute);
        props.setRateLimitPerDay(perDay);
        return new AskRateLimiter(props);
    }

    @Test
    void enforcesPerMinuteLimitWithSlidingWindow() {
        AskRateLimiter limiter = limiter(2, 100);
        long t0 = 1_000_000L;

        assertEquals(0, limiter.tryAcquire("a", t0));
        assertEquals(0, limiter.tryAcquire("a", t0 + 1_000));
        long wait = limiter.tryAcquire("a", t0 + 2_000);
        assertEquals(58, wait, "must wait until the first call leaves the minute window");
        assertEquals(0, limiter.tryAcquire("a", t0 + 60_001));
    }

    @Test
    void enforcesPerDayLimit() {
        AskRateLimiter limiter = limiter(100, 3);
        long t0 = 1_000_000L;
        for (int i = 0; i < 3; i++) {
            assertEquals(0, limiter.tryAcquire("a", t0 + i * 120_000L));
        }
        assertTrue(limiter.tryAcquire("a", t0 + 3_600_000L) > 0);
        assertEquals(0, limiter.tryAcquire("a", t0 + 24 * 3_600_000L + 1));
    }

    @Test
    void limitsArePerUser() {
        AskRateLimiter limiter = limiter(1, 10);
        assertEquals(0, limiter.tryAcquire("a", 0));
        assertTrue(limiter.tryAcquire("a", 10) > 0);
        assertEquals(0, limiter.tryAcquire("b", 10));
    }
}
