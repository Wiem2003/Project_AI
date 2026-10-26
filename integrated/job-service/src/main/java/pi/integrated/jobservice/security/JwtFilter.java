package pi.integrated.jobservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * JWT filter: extracts claims and ensures userId and userName are always populated.
 * If userId is not in the token, falls back to querying user_db.users by email.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;

    public JwtFilter(JwtUtil jwtUtil, JdbcTemplate jdbcTemplate) {
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (!jwtUtil.isTokenExpired(token)) {
                    String email = jwtUtil.extractEmail(token);
                    String role = jwtUtil.extractRole(token);
                    Long userId = jwtUtil.extractUserId(token);
                    String name = jwtUtil.extractName(token);

                    // If userId or name is not present in token, resolve from user_db.users
                    if ((userId == null || name == null) && email != null) {
                        try {
                            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                                    "SELECT id, first_name, last_name, role FROM user_db.users WHERE LOWER(email) = LOWER(?) LIMIT 1",
                                    email.trim()
                            );
                            if (!rows.isEmpty()) {
                                Map<String, Object> row = rows.get(0);
                                if (userId == null && row.get("id") != null) {
                                    userId = ((Number) row.get("id")).longValue();
                                }
                                if (name == null) {
                                    String fn = (String) row.get("first_name");
                                    String ln = (String) row.get("last_name");
                                    name = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                                }
                                if (role == null && row.get("role") != null) {
                                    role = row.get("role").toString();
                                }
                            }
                        } catch (Exception ex) {
                            // In case user_db query is unavailable, fallback to deterministic hash
                        }
                    }

                    // Fallback if still null
                    if (userId == null && email != null) {
                        userId = Math.abs((long) email.hashCode()) % 1000000L + 1L;
                    }
                    if (name == null || name.isBlank()) {
                        name = email != null ? email.split("@")[0] : "User";
                    }

                    if (email != null && role != null
                            && SecurityContextHolder.getContext().getAuthentication() == null) {
                        String cleanRole = role.startsWith("ROLE_") ? role.substring(5) : role;
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        email,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + cleanRole.toUpperCase()))
                                );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }

                    // Store as request attributes for controller convenience
                    request.setAttribute("userId", userId);
                    request.setAttribute("userEmail", email);
                    request.setAttribute("userRole", role);
                    request.setAttribute("userName", name);
                }
            } catch (Exception e) {
                // Invalid token — proceed unauthenticated
            }
        }

        filterChain.doFilter(request, response);
    }
}
