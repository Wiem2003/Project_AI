package pi.integrated.course.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/** Validates the JWT issued by user-service (same secret). */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** Null when jwt.secret is missing: every token is then rejected, public endpoints keep working. */
    private final Key key;

    public JwtUtil(@Value("${jwt.secret:}") String secret) {
        Key k = null;
        if (secret == null || secret.isBlank()) {
            log.error("jwt.secret is not set (env JWT_SECRET): protected endpoints will answer 401");
        } else {
            try {
                k = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.error("jwt.secret is invalid ({}): protected endpoints will answer 401", e.getMessage());
            }
        }
        this.key = k;
    }

    public boolean isConfigured() {
        return key != null;
    }

    public Claims extractClaims(String token) {
        if (key == null) {
            throw new IllegalStateException("JWT secret not configured");
        }
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public boolean isExpired(Claims claims) {
        return claims.getExpiration() != null && claims.getExpiration().before(new Date());
    }
}
