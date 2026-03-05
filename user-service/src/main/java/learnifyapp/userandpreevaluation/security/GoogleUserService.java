package learnifyapp.userandpreevaluation.security;

import learnifyapp.userandpreevaluation.usermanagement.entity.User;
import learnifyapp.userandpreevaluation.usermanagement.enums.Role;
import learnifyapp.userandpreevaluation.usermanagement.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GoogleUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public GoogleUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User createGoogleUser(String email, String fullName, String pictureUrl, Role desiredRole) {

        String firstName = "Google";
        String lastName = "User";

        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().split("\\s+");
            firstName = parts[0];
            lastName = (parts.length > 1)
                    ? String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length))
                    : "User";
        }

        User u = new User();
        u.setEmail(email);
        u.setFirstName(firstName);
        u.setLastName(lastName);

        // password NOT NULL -> dummy
        u.setPassword(passwordEncoder.encode("GOOGLE_OAUTH2_ACCOUNT"));

        u.setRole(desiredRole);
        u.setAvatarUrl(pictureUrl);

        return userRepository.save(u);
    }
}