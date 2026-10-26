package pi.integrated.course.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import pi.integrated.course.config.CourseRagProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Ask the course" is reserved to students enrolled in the course (a COMPLETED payment in payment-service).
 * Admins are allowed so they can try the assistant. The JWT only carries the email, so the user id
 * is resolved once through user-service /api/users/me.
 */
@Service
public class CourseAccessService {

    private static final Logger log = LoggerFactory.getLogger(CourseAccessService.class);

    private static final String USER_ME_URL = "http://user-service/api/users/me";
    private static final String PAYMENT_EXISTS_URL =
            "http://payment-service/api/payments/exists?userId={userId}&courseId={courseId}";
    /** Positive answers only: a student who just paid must not wait for a cache expiry. */
    private static final Duration ENROLLMENT_CACHE_TTL = Duration.ofMinutes(10);

    private final RestClient restClient;
    private final CourseRagProperties ragProperties;
    private final Map<String, Long> userIdByEmail = new ConcurrentHashMap<>();
    private final Map<String, Instant> enrolledUntil = new ConcurrentHashMap<>();

    public CourseAccessService(RestClient.Builder loadBalancedRestClientBuilder, CourseRagProperties ragProperties) {
        this.restClient = loadBalancedRestClientBuilder.build();
        this.ragProperties = ragProperties;
    }

    /** The authenticated JWT user, or 401. */
    public JwtUserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserPrincipal p) {
            return p;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    /** Throws 403 unless the user is an admin or a student enrolled in the course. */
    public void checkCanUseCourseAssistant(JwtUserPrincipal user, Long courseId) {
        if (user.hasRole("ADMIN")) {
            return;
        }
        if (!user.hasRole("STUDENT")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserved to students enrolled in this course");
        }
        if (!ragProperties.isRequireEnrollment()) {
            return;
        }
        Long userId = resolveUserId(user);
        if (!isEnrolled(userId, courseId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You must be enrolled in this course");
        }
    }

    private Long resolveUserId(JwtUserPrincipal user) {
        Long cached = userIdByEmail.get(user.getEmail());
        if (cached != null) {
            return cached;
        }
        try {
            JsonNode me = restClient.get()
                    .uri(USER_ME_URL)
                    .header(HttpHeaders.AUTHORIZATION, user.getBearerToken())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode id = me == null ? null : me.get("id");
            if (id == null || !id.canConvertToLong()) {
                throw new IllegalStateException("user-service /api/users/me returned no id");
            }
            userIdByEmail.put(user.getEmail(), id.asLong());
            return id.asLong();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired, please sign in again");
            }
            log.warn("user-service lookup failed: HTTP {}", e.getStatusCode().value());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
        } catch (Exception e) {
            log.warn("user-service lookup failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
        }
    }

    private boolean isEnrolled(Long userId, Long courseId) {
        String key = userId + ":" + courseId;
        Instant until = enrolledUntil.get(key);
        if (until != null && until.isAfter(Instant.now())) {
            return true;
        }
        try {
            JsonNode body = restClient.get()
                    .uri(PAYMENT_EXISTS_URL, Map.of("userId", userId, "courseId", courseId))
                    .retrieve()
                    .body(JsonNode.class);
            boolean enrolled = body != null && body.path("data").asBoolean(false);
            if (enrolled) {
                enrolledUntil.put(key, Instant.now().plus(ENROLLMENT_CACHE_TTL));
            }
            return enrolled;
        } catch (Exception e) {
            log.warn("payment-service enrollment check failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Enrollment check unavailable");
        }
    }
}
