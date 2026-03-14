package com.system.SchoolManagementSystem.config;

import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            log.info("Initializing database with default users...");

            // List of default users to create
            List<User> defaultUsers = Arrays.asList(
                    createUser("principal", "principal123", "ADMIN", "admin@schoolsystem.com", "System Administrator"),
                    createUser("teacher1", "teacher123", "TEACHER", "teacher1@schoolsystem.com", "John Teacher"),
                    createUser("teacher2", "teacher123", "TEACHER", "teacher2@schoolsystem.com", "Jane Teacher"),
                    createUser("student1", "student123", "STUDENT", "student1@schoolsystem.com", "Alice Student"),
                    createUser("student2", "student123", "STUDENT", "student2@schoolsystem.com", "Bob Student"),
                    createUser("parent1", "parent123", "PARENT", "parent1@schoolsystem.com", "Carol Parent"),
                    createUser("parent2", "parent123", "PARENT", "parent2@schoolsystem.com", "David Parent"),
                    createUser("staff1", "staff123", "STAFF", "staff1@schoolsystem.com", "Edward Staff")
            );

            // Create users if they don't exist
            for (User user : defaultUsers) {
                if (!userRepository.existsByUsername(user.getUsername())) {
                    userRepository.save(user);
                    log.info("Created user: {} (Role: {})", user.getUsername(), user.getRole());
                }
            }

            log.info("Database initialization completed!");
        };
    }

    private User createUser(String username, String password, String role, String email, String fullName) {
        return User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .fullName(fullName)
                .role(role)
                .phone("+1234567890")
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isAccountNonLocked(true)
                .isCredentialsNonExpired(true)
                .passwordChangedAt(LocalDateTime.now())
                .build();
    }
}