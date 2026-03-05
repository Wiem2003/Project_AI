package learnifyapp.userandpreevaluation.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import learnifyapp.userandpreevaluation.usermanagement.enums.Role;
import learnifyapp.userandpreevaluation.usermanagement.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final GoogleUserService googleUserService;
    private final UserService userService;

    private final String redirectUrl = "http://localhost:4200/oauth2/redirect";

    public OAuth2SuccessHandler(JwtUtil jwtUtil,
                                GoogleUserService googleUserService,
                                UserService userService) {
        this.jwtUtil = jwtUtil;
        this.googleUserService = googleUserService;
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // 1) Read cookies (role + mode)
        String roleStr = readCookie(request, "OAUTH2_ROLE");
        String mode = readCookie(request, "OAUTH2_MODE"); // SIGNUP or LOGIN

        Role desiredRole = "CANDIDATE".equalsIgnoreCase(roleStr) ? Role.CANDIDATE : Role.STUDENT;
        String desiredMode = (mode == null) ? "LOGIN" : mode.toUpperCase();

        // 2) Google user info
        Object principal = authentication.getPrincipal();

        String email;
        String fullName;
        String pictureUrl;

        if (principal instanceof OidcUser oidcUser) {
            email = oidcUser.getEmail();
            fullName = oidcUser.getFullName();
            pictureUrl = oidcUser.getPicture();
        } else {
            var oauthUser = (org.springframework.security.oauth2.core.user.OAuth2User) principal;
            email = (String) oauthUser.getAttributes().get("email");
            fullName = (String) oauthUser.getAttributes().get("name");
            pictureUrl = (String) oauthUser.getAttributes().get("picture");
        }

        // 3) Check existence
        Optional<learnifyapp.userandpreevaluation.usermanagement.entity.User> existingOpt =
                googleUserService.findByEmail(email);

        // SIGNUP: if exists -> error
        if ("SIGNUP".equals(desiredMode) && existingOpt.isPresent()) {
            clearCookies(response);
            response.sendRedirect(redirectUrl + "?error=" + enc("account_exists"));
            return;
        }

        // LOGIN: if doesn't exist -> error
        if ("LOGIN".equals(desiredMode) && existingOpt.isEmpty()) {
            clearCookies(response);
            response.sendRedirect(redirectUrl + "?error=" + enc("account_not_found") + "&role=" + enc(desiredRole.name()));
            return;
        }

        // 4) Get/create final user
        var user = existingOpt.orElseGet(() ->
                googleUserService.createGoogleUser(email, fullName, pictureUrl, desiredRole)
        );

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        else ip = ip.split(",")[0].trim();

        String userAgent = request.getHeader("User-Agent");

        // Create active session immediately (no device check)
        userService.createSessionFor(user.getEmail(), userAgent, ip);

        // 5) JWT + redirect
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        clearCookies(response);
        response.sendRedirect(redirectUrl + "?token=" + enc(token));
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private void clearCookies(HttpServletResponse response) {
        Cookie c1 = new Cookie("OAUTH2_ROLE", "");
        c1.setPath("/");
        c1.setMaxAge(0);
        response.addCookie(c1);

        Cookie c2 = new Cookie("OAUTH2_MODE", "");
        c2.setPath("/");
        c2.setMaxAge(0);
        response.addCookie(c2);
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
