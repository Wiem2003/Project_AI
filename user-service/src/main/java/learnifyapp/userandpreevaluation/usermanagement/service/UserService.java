package learnifyapp.userandpreevaluation.usermanagement.service;

import learnifyapp.userandpreevaluation.security.JwtUtil;
import learnifyapp.userandpreevaluation.usermanagement.dto.ChangePasswordRequest;
import learnifyapp.userandpreevaluation.usermanagement.dto.LoginResponse;
import learnifyapp.userandpreevaluation.usermanagement.dto.RegisterRequest;
import learnifyapp.userandpreevaluation.usermanagement.dto.UpdateProfileRequest;
import learnifyapp.userandpreevaluation.usermanagement.entity.PasswordResetToken;
import learnifyapp.userandpreevaluation.usermanagement.entity.User;
import learnifyapp.userandpreevaluation.usermanagement.entity.UserSession;
import learnifyapp.userandpreevaluation.usermanagement.enums.Role;
import learnifyapp.userandpreevaluation.usermanagement.repository.PasswordResetTokenRepository;
import learnifyapp.userandpreevaluation.usermanagement.repository.UserRepository;
import learnifyapp.userandpreevaluation.usermanagement.repository.UserSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserSessionRepository userSessionRepository;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       EmailService emailService,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       UserSessionRepository userSessionRepository) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userSessionRepository = userSessionRepository;
    }

    // ================= REGISTER STUDENT =================
    public User registerStudent(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.STUDENT);

        User savedUser = userRepository.save(user);

        try {
            emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getFirstName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return savedUser;
    }

    // ================= REGISTER CANDIDATE =================
    public User registerCandidate(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CANDIDATE);

        return userRepository.save(user);
    }

    private static final String LEARNIFY_DOMAIN = "@learnify.com";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private void validatePersonalEmail(String personalEmail, String learnifyEmail) {
        if (personalEmail == null || personalEmail.isBlank()) {
            throw new RuntimeException("Personal email is required");
        }
        String pe = personalEmail.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(pe).matches()) {
            throw new RuntimeException("Invalid personal email format");
        }
        if (pe.endsWith("@learnify.com")) {
            throw new RuntimeException("Personal email must not end with @learnify.com");
        }
        if (learnifyEmail != null && !learnifyEmail.isBlank() && pe.equals(learnifyEmail.trim().toLowerCase())) {
            throw new RuntimeException("Personal email must be different from Learnify email");
        }
    }

    // ================= CREATE TUTOR =================
    public User createTutor(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        validatePersonalEmail(request.getPersonalEmail(), request.getEmail());

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.TUTOR);

        User savedUser = userRepository.save(user);

        try {
            emailService.sendLearnifyAccountCreated(
                    request.getPersonalEmail().trim(),
                    request.getEmail(),
                    request.getPassword()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return savedUser;
    }

    // ================= CREATE ADMIN =================
    public User createAdmin(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        validatePersonalEmail(request.getPersonalEmail(), request.getEmail());

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ADMIN);

        User savedUser = userRepository.save(user);

        try {
            emailService.sendLearnifyAccountCreated(
                    request.getPersonalEmail().trim(),
                    request.getEmail(),
                    request.getPassword()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return savedUser;
    }

    // ================= LOGIN =================
    public LoginResponse login(String email,
                               String password,
                               String role,
                               String userAgent,
                               String ip) {

        if ("ADMIN".equalsIgnoreCase(role) || "TUTOR".equalsIgnoreCase(role)) {
            if (email == null || !email.trim().toLowerCase().endsWith(LEARNIFY_DOMAIN)) {
                throw new RuntimeException("Admin and Tutor must login with their Learnify email (@learnify.com)");
            }
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (!user.getRole().name().equalsIgnoreCase(role)) {
            throw new RuntimeException("Access denied: wrong space for this account");
        }

        String token = jwtUtil.generateToken(email, user.getRole().name(), user.getId());

        UserSession s = new UserSession();
        s.setUser(user);
        s.setSessionId(UUID.randomUUID().toString());
        s.setUserAgent(userAgent);
        s.setIp(ip);
        s.setBrowser(parseBrowser(userAgent));
        s.setOs(parseOs(userAgent));
        s.setCreatedAt(LocalDateTime.now());
        s.setLastSeenAt(LocalDateTime.now());
        s.setRevoked(false);
        userSessionRepository.save(s);

        return new LoginResponse(token, user.getRole().name(), user.getEmail());
    }

    private String parseBrowser(String ua) {
        if (ua == null) return "Unknown";
        String u = ua.toLowerCase();
        if (u.contains("edg/")) return "Edge";
        if (u.contains("chrome/")) return "Chrome";
        if (u.contains("firefox/")) return "Firefox";
        if (u.contains("safari/") && !u.contains("chrome/")) return "Safari";
        return "Unknown";
    }

    private String parseOs(String ua) {
        if (ua == null) return "Unknown";
        String u = ua.toLowerCase();
        if (u.contains("windows")) return "Windows";
        if (u.contains("android")) return "Android";
        if (u.contains("iphone") || u.contains("ipad") || u.contains("ios")) return "iOS";
        if (u.contains("mac os") || u.contains("macintosh")) return "macOS";
        if (u.contains("linux")) return "Linux";
        return "Unknown";
    }

    // ================= FORGOT PASSWORD (SEND PIN) =================
    @Transactional
    public String forgotPassword(String email) {

        String genericMsg = "If this email exists, a PIN has been sent.";

        if (email == null || email.isBlank()) {
            return genericMsg;
        }

        email = email.trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return genericMsg;
        }

        // ✅ Invalidate all previous active PINs
        passwordResetTokenRepository.invalidateAllActivePins(email);

        // ✅ Generate a new PIN
        String pin = String.format("%06d", new Random().nextInt(1_000_000));

        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setPin(pin);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setUsed(false);

        passwordResetTokenRepository.save(token);

        // ✅ Send email (async)
        emailService.sendResetPinEmail(user.getEmail(), user.getFirstName(), pin);

        return genericMsg;
    }

    // ================= RESET PASSWORD (VERIFY PIN + UPDATE PASSWORD) =================
    @Transactional
    public void resetPasswordWithPin(String email, String pin, String newPassword, String confirmNewPassword) {

        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email is required");
        }
        if (pin == null || pin.isBlank()) {
            throw new RuntimeException("PIN is required");
        }

        email = email.trim().toLowerCase();
        pin = pin.trim();

        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters");
        }
        if (!newPassword.equals(confirmNewPassword)) {
            throw new RuntimeException("Passwords do not match");
        }

        PasswordResetToken token = passwordResetTokenRepository
                .findTopByEmailAndPinAndUsedFalseOrderByIdDesc(email, pin)
                .orElseThrow(() -> new RuntimeException("Invalid PIN"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("PIN expired");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
    }

    // ================= PROFILE =================
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User updateProfile(String currentEmail, UpdateProfileRequest req) {
        User user = getByEmail(currentEmail);

        if (req.getFirstName() != null) user.setFirstName(req.getFirstName());
        if (req.getLastName() != null) user.setLastName(req.getLastName());

        if (req.getEmail() != null && !req.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(req.getEmail())) {
                throw new RuntimeException("Email already exists");
            }
            user.setEmail(req.getEmail());
        }

        return userRepository.save(user);
    }

    public void changePassword(String email, ChangePasswordRequest req) {
        User user = getByEmail(email);

        if (req.getNewPassword() == null || req.getNewPassword().length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters");
        }
        if (!req.getNewPassword().equals(req.getConfirmNewPassword())) {
            throw new RuntimeException("Passwords do not match");
        }
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    // ================= AVATAR =================
    public User uploadAvatar(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !(contentType.equals("image/png") || contentType.equals("image/jpeg") || contentType.equals("image/jpg"))) {
            throw new RuntimeException("Only PNG/JPG images are allowed");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            Path uploadDir = Paths.get("uploads/avatars");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String ext = contentType.equals("image/png") ? ".png" : ".jpg";
            String filename = UUID.randomUUID() + ext;

            Path targetPath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            user.setAvatarUrl("/uploads/avatars/" + filename);
            return userRepository.save(user);

        } catch (Exception e) {
            throw new RuntimeException("Upload failed: " + e.getMessage());
        }
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getTutors() {
        return userRepository.findByRole(Role.TUTOR);
    }

    // ✅ UPDATED: block delete if user has active sessions
    @Transactional
    public void deleteUser(Long id) {

        User u = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("admin@learnify.com".equalsIgnoreCase(u.getEmail())) {
            throw new RuntimeException("Cannot delete system admin");
        }

        userSessionRepository.deleteAllByUserId(id);
        userRepository.deleteById(id);
    }

    public List<UserSession> getSessionsForUser(String email) {
        User user = getByEmail(email);
        return userSessionRepository.findByUserIdOrderByLastSeenAtDesc(user.getId());
    }

    public void createSessionFor(String email, String userAgent, String ip) {
        User user = getByEmail(email);

        UserSession s = new UserSession();
        s.setUser(user);
        s.setSessionId(UUID.randomUUID().toString());
        s.setUserAgent(userAgent);
        s.setIp(ip);
        s.setBrowser(parseBrowser(userAgent));
        s.setOs(parseOs(userAgent));
        s.setCreatedAt(LocalDateTime.now());
        s.setLastSeenAt(LocalDateTime.now());
        s.setRevoked(false);

        userSessionRepository.save(s);
    }
}