package pi.integrated.course.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Authenticates requests carrying a valid user-service JWT; requests without one continue anonymously. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ") && jwtUtil.isConfigured()) {
            String token = header.substring(7).trim();
            try {
                Claims claims = jwtUtil.extractClaims(token);
                String email = jwtUtil.extractEmail(claims);
                if (email != null && !jwtUtil.isExpired(claims)) {
                    JwtUserPrincipal principal = new JwtUserPrincipal(
                            email, JwtUserPrincipal.normalizeRole(jwtUtil.extractRole(claims)), header);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                }
            } catch (Exception ignored) {
                // invalid token: continue without authentication
            }
        }
        filterChain.doFilter(request, response);
    }
}
