package learnifyapp.userandpreevaluation.usermanagement.config;

import learnifyapp.userandpreevaluation.usermanagement.entity.User;
import learnifyapp.userandpreevaluation.usermanagement.enums.Role;
import learnifyapp.userandpreevaluation.usermanagement.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {

            if (!userRepository.existsByEmail("admin@learnify.com")) {

                User admin = new User();

                admin.setFirstName("System");
                admin.setLastName("Admin");
                admin.setEmail("admin@learnify.com");

                // 🔥 CORRECTION ICI : ENCODAGE DU MOT DE PASSE
                admin.setPassword(passwordEncoder.encode("admin123"));

                admin.setRole(Role.ADMIN);

                userRepository.save(admin);

                System.out.println("Admin account created with encrypted password !");
            }
        };
    }
}
