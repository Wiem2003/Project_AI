package learnifyapp.userandpreevaluation.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/oauth2")
public class OAuth2RoleController {

    @GetMapping("/authorize/google/signup/{role}")
    public ResponseEntity<Void> startGoogleSignup(@PathVariable String role,
                                                  HttpServletResponse response) throws IOException {
        return start(role, "SIGNUP", response);
    }

    @GetMapping("/authorize/google/login/{role}")
    public ResponseEntity<Void> startGoogleLogin(@PathVariable String role,
                                                 HttpServletResponse response) throws IOException {
        return start(role, "LOGIN", response);
    }

    private ResponseEntity<Void> start(String role, String mode, HttpServletResponse response) throws IOException {
        String normalizedRole = role.toUpperCase();

        // ✅ Continue with Google فقط student/candidate
        if (!normalizedRole.equals("STUDENT") && !normalizedRole.equals("CANDIDATE")) {
            return ResponseEntity.badRequest().build();
        }

        Cookie roleCookie = new Cookie("OAUTH2_ROLE", normalizedRole);
        roleCookie.setPath("/");
        roleCookie.setHttpOnly(true);
        roleCookie.setMaxAge(5 * 60);
        response.addCookie(roleCookie);

        Cookie modeCookie = new Cookie("OAUTH2_MODE", mode);
        modeCookie.setPath("/");
        modeCookie.setHttpOnly(true);
        modeCookie.setMaxAge(5 * 60);
        response.addCookie(modeCookie);

        response.sendRedirect("/oauth2/authorization/google");
        return ResponseEntity.ok().build();
    }
}