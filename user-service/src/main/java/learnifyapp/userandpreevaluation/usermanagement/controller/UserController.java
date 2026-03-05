package learnifyapp.userandpreevaluation.usermanagement.controller;

import learnifyapp.userandpreevaluation.usermanagement.dto.RegisterRequest;
import learnifyapp.userandpreevaluation.usermanagement.entity.User;
import learnifyapp.userandpreevaluation.usermanagement.service.UserService;
import org.springframework.web.bind.annotation.*;
import learnifyapp.userandpreevaluation.usermanagement.dto.LoginRequest;
import learnifyapp.userandpreevaluation.usermanagement.dto.AuthResponse;
import learnifyapp.userandpreevaluation.usermanagement.dto.LoginResponse;

import learnifyapp.userandpreevaluation.usermanagement.dto.UpdateProfileRequest;
import learnifyapp.userandpreevaluation.usermanagement.dto.ChangePasswordRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;



@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register/student")
    public User registerStudent(@RequestBody RegisterRequest request) {
        return userService.registerStudent(request);
    }

    @PostMapping("/register/candidate")
    public User registerCandidate(@RequestBody RegisterRequest request) {
        return userService.registerCandidate(request);
    }

    @PostMapping("/admin/create-tutor")
    public User createTutor(@RequestBody RegisterRequest request) {
        return userService.createTutor(request);
    }



    @GetMapping("/me")
    public User me(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.getByEmail(userDetails.getUsername());
    }

    @PutMapping("/me")
    public User updateMe(@AuthenticationPrincipal UserDetails userDetails,
                         @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(userDetails.getUsername(), request);
    }

    @PutMapping("/me/password")
    public ResponseEntity<?> changeMyPassword(@AuthenticationPrincipal UserDetails userDetails,
                                              @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }


    @PostMapping("/me/avatar")
    public ResponseEntity<?> uploadAvatar(@AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails,
                                          @RequestParam("file") MultipartFile file) {
        User user = userService.uploadAvatar(userDetails.getUsername(), file);
        return ResponseEntity.ok(Map.of("avatarUrl", user.getAvatarUrl()));
    }

    /** Any authenticated user can list tutors (for rating, browsing, etc.) */
    @GetMapping("/tutors")
    public List<User> getTutors() {
        return userService.getTutors();
    }

}
