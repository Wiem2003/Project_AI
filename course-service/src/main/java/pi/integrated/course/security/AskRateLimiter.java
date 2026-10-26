package pi.integrated.course.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pi.integrated.course.config.CourseRagProperties;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding-window limits per user (in memory, per instance): N questions per minute and per 24 h. */
@Component
@RequiredArgsConstructor
public class AskRateLimiter {

    private static final long MINUTE_MS = 60_000L;
    private static final long DAY_MS = 24 * 60 * MINUTE_MS;

    private final CourseRagProperties ragProperties;
    private final Map<String, Deque<Long>> calls = new ConcurrentHashMap<>();

    /** @return 0 if the call is allowed (and recorded), otherwise seconds to wait before retrying. */
    public long tryAcquire(String userKey) {
        return tryAcquire(userKey, System.currentTimeMillis());
    }

    long tryAcquire(String userKey, long now) {
        Deque<Long> history = calls.computeIfAbsent(userKey, k -> new ArrayDeque<>());
        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst() <= now - DAY_MS) {
                history.pollFirst();
            }
            if (history.size() >= ragProperties.getRateLimitPerDay()) {
                return secondsUntil(history.peekFirst() + DAY_MS, now);
            }
            int lastMinute = 0;
            Long oldestInMinute = null;
            for (var it = history.descendingIterator(); it.hasNext(); ) {
                long t = it.next();
                if (t <= now - MINUTE_MS) {
                    break;
                }
                lastMinute++;
                oldestInMinute = t;
            }
            if (lastMinute >= ragProperties.getRateLimitPerMinute()) {
                return secondsUntil(oldestInMinute + MINUTE_MS, now);
            }
            history.addLast(now);
            return 0;
        }
    }

    private static long secondsUntil(long instantMs, long now) {
        return Math.max(1, (instantMs - now + 999) / 1000);
    }
}
