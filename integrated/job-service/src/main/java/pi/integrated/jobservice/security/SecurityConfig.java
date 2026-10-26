package pi.integrated.jobservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(request -> {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOriginPatterns(List.of("*"));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);
                return config;
            }))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/jobs", "/api/jobs/**").permitAll()
                .requestMatchers("/api/jobs/rag", "/api/jobs/rag/**").permitAll()
                .requestMatchers("/api/job-notifications", "/api/job-notifications/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/applications", "/api/applications/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/applications", "/api/applications/**").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/applications", "/api/applications/**").permitAll()
                .requestMatchers("/api/cv-profiles", "/api/cv-profiles/**").permitAll()
                .requestMatchers("/api/saved-jobs", "/api/saved-jobs/**").permitAll()
                .requestMatchers("/api/meetings", "/api/meetings/**").permitAll()
                .requestMatchers("/api/ratings", "/api/ratings/**").permitAll()
                .requestMatchers("/api/code-audit", "/api/code-audit/**").permitAll()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
