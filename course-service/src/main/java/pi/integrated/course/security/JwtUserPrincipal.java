package pi.integrated.course.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

@Getter
public class JwtUserPrincipal {

    private final String email;
    /** STUDENT, ADMIN, TUTOR, CANDIDATE (ROLE_ prefix removed). */
    private final String role;
    /** Raw "Bearer …" header, forwarded to user-service to resolve the user id. */
    private final String bearerToken;

    public JwtUserPrincipal(String email, String role, String bearerToken) {
        this.email = email;
        this.role = role;
        this.bearerToken = bearerToken;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority(role));
    }

    public boolean hasRole(String expected) {
        return expected.equalsIgnoreCase(role);
    }

    /** Removes a Spring "ROLE_" prefix if the token carries one. */
    static String normalizeRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String r = raw.trim();
        if (r.length() > 5 && r.regionMatches(true, 0, "ROLE_", 0, 5)) {
            r = r.substring(5);
        }
        return r.toUpperCase();
    }
}
