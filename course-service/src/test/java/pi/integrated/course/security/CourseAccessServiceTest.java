package pi.integrated.course.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pi.integrated.course.config.CourseRagProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CourseAccessServiceTest {

    private static final String BEARER = "Bearer test-token";
    private static final String ME = "http://user-service/api/users/me";

    private MockRestServiceServer server;
    private CourseAccessService service;
    private CourseRagProperties props;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        props = new CourseRagProperties();
        props.setRequireEnrollment(true);
        service = new CourseAccessService(builder, props);
    }

    @Test
    void withoutEnrollmentRequirementAnyStudentIsAllowedWithoutRemoteCalls() {
        props.setRequireEnrollment(false);

        assertDoesNotThrow(() -> service.checkCanUseCourseAssistant(user("STUDENT"), 7L));
        server.verify(); // neither user-service nor payment-service called
        assertThrows(ResponseStatusException.class,
                () -> service.checkCanUseCourseAssistant(user("CANDIDATE"), 7L));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static JwtUserPrincipal user(String role) {
        return new JwtUserPrincipal("student@test.tn", role, BEARER);
    }

    private static String exists(long userId, long courseId) {
        return "http://payment-service/api/payments/exists?userId=" + userId + "&courseId=" + courseId;
    }

    @Test
    void enrolledStudentIsAllowedAndResultsAreCached() {
        server.expect(once(), requestTo(ME)).andExpect(header(HttpHeaders.AUTHORIZATION, BEARER))
                .andRespond(withSuccess("{\"id\":42,\"email\":\"student@test.tn\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(exists(42, 7)))
                .andRespond(withSuccess("{\"success\":true,\"data\":true}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> service.checkCanUseCourseAssistant(user("STUDENT"), 7L));
        assertDoesNotThrow(() -> service.checkCanUseCourseAssistant(user("STUDENT"), 7L));
        server.verify(); // second check served from cache
    }

    @Test
    void studentWithoutPaymentIsForbidden() {
        server.expect(requestTo(ME)).andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(exists(42, 8)))
                .andRespond(withSuccess("{\"success\":true,\"data\":false}", MediaType.APPLICATION_JSON));

        var e = assertThrows(ResponseStatusException.class,
                () -> service.checkCanUseCourseAssistant(user("STUDENT"), 8L));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void adminIsAllowedWithoutRemoteCalls() {
        assertDoesNotThrow(() -> service.checkCanUseCourseAssistant(user("ADMIN"), 7L));
        server.verify();
    }

    @Test
    void otherRolesAreForbidden() {
        var e = assertThrows(ResponseStatusException.class,
                () -> service.checkCanUseCourseAssistant(user("CANDIDATE"), 7L));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void paymentServiceDownGives503() {
        server.expect(requestTo(ME)).andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(exists(42, 7))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        var e = assertThrows(ResponseStatusException.class,
                () -> service.checkCanUseCourseAssistant(user("STUDENT"), 7L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }

    @Test
    void currentUserReadsTheJwtPrincipalOr401() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class, service::currentUser).getStatusCode());

        JwtUserPrincipal p = user("STUDENT");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        assertSame(p, service.currentUser());
    }
}
