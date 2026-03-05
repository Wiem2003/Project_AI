package learnifyapp.userandpreevaluation.usermanagement.controller;

import jakarta.servlet.http.HttpServletRequest;
import learnifyapp.userandpreevaluation.usermanagement.dto.ForgotPasswordRequest;
import learnifyapp.userandpreevaluation.usermanagement.dto.LoginRequest;
import learnifyapp.userandpreevaluation.usermanagement.dto.LoginResponse;
import learnifyapp.userandpreevaluation.usermanagement.dto.RegisterRequest;
import learnifyapp.userandpreevaluation.usermanagement.dto.ResetPasswordRequest;
import learnifyapp.userandpreevaluation.usermanagement.entity.User;
import learnifyapp.userandpreevaluation.usermanagement.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // ================= REGISTER =================
    @PostMapping("/register/student")
    public User registerStudent(@RequestBody RegisterRequest request) {
        return userService.registerStudent(request);
    }

    @PostMapping("/register/candidate")
    public User registerCandidate(@RequestBody RegisterRequest request) {
        return userService.registerCandidate(request);
    }

    // ================= LOGIN =================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {

        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = httpRequest.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }

        String userAgent = (request.getUserAgent() != null && !request.getUserAgent().isBlank())
                ? request.getUserAgent()
                : httpRequest.getHeader("User-Agent");

        LoginResponse res = userService.login(
                request.getEmail(),
                request.getPassword(),
                request.getRole(),
                userAgent,
                ip
        );

        return ResponseEntity.ok(res);
    }

    // ================= FORGOT PASSWORD =================
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request.getEmail());
        return ResponseEntity.ok("PIN sent to email");
    }

    // ================= RESET PASSWORD =================
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPasswordWithPin(
                request.getEmail(),
                request.getPin(),
                request.getNewPassword(),
                request.getConfirmNewPassword()
        );
        return ResponseEntity.ok("Password updated");
    }
}
