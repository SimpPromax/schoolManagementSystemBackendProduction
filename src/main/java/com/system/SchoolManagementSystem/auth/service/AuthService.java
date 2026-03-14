package com.system.SchoolManagementSystem.auth.service;

import com.system.SchoolManagementSystem.auth.dto.*;
import com.system.SchoolManagementSystem.auth.enums.RegistrationStatus;
import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import com.system.SchoolManagementSystem.config.JwtTokenUtil;
import com.system.SchoolManagementSystem.config.RefreshTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final RefreshTokenUtil refreshTokenUtil;
    private final CustomUserDetailsService userDetailsService;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_TIME_MINUTES = 30;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());

        User user = userRepository.findByUsernameOrEmail(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found: {}", request.getUsername());
                    throw new RuntimeException("Invalid username or password");
                });

        // Check registration status
        if (user.getRegistrationStatus() == RegistrationStatus.PENDING) {
            log.warn("Login failed - registration pending for user: {}", request.getUsername());
            throw new RuntimeException("Your registration is pending approval. Please check back later.");
        }

        if (user.getRegistrationStatus() == RegistrationStatus.REJECTED) {
            log.warn("Login failed - registration rejected for user: {}", request.getUsername());
            throw new RuntimeException("Your registration was rejected. Please contact support for more information.");
        }

        // Check if account is locked
        if (!user.isAccountNonLocked()) {
            log.warn("Login failed - account locked for user: {}", request.getUsername());
            throw new RuntimeException("Account is locked. Please try again later.");
        }

        // Check if account is enabled
        if (!user.getIsEnabled()) {
            log.warn("Login failed - account disabled for user: {}", request.getUsername());
            throw new RuntimeException("Account is disabled");
        }

        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Reset failed attempts on successful login
            userRepository.resetFailedAttempts(user.getId());

            // Update last login time
            LocalDateTime now = LocalDateTime.now();
            userRepository.updateLastLogin(user.getId(), now);

            // Generate tokens
            String accessToken = jwtTokenUtil.generateToken(userDetailsService.loadUserByUsername(request.getUsername()));
            String refreshToken = refreshTokenUtil.generateRefreshToken(user.getUsername());

            log.info("Login successful for user: {}", request.getUsername());

            return LoginResponse.builder()
                    .token(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(TimeUnit.MILLISECONDS.toSeconds(jwtTokenUtil.getJwtExpiration()))
                    .expiresAt(now.plusSeconds(TimeUnit.MILLISECONDS.toSeconds(jwtTokenUtil.getJwtExpiration())))
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .phone(user.getPhone())
                    .role(user.getRole())
                    .profilePicture(user.getProfilePicture())
                    .lastLoginAt(now)
                    .build();

        } catch (BadCredentialsException e) {
            // Increment failed attempts
            userRepository.incrementFailedAttempts(user.getId());

            // Check if we should lock the account
            User updatedUser = userRepository.findById(user.getId()).orElse(user);
            if (updatedUser.getFailedLoginAttempts() != null &&
                    updatedUser.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                LocalDateTime lockTime = LocalDateTime.now().plusMinutes(LOCK_TIME_MINUTES);
                userRepository.lockUser(user.getId(), lockTime);
                log.warn("Account locked for user: {} after {} failed attempts",
                        request.getUsername(), MAX_FAILED_ATTEMPTS);
                throw new RuntimeException("Account locked due to too many failed attempts. Try again in " +
                        LOCK_TIME_MINUTES + " minutes.");
            }

            log.warn("Login failed - invalid password for user: {}", request.getUsername());
            throw new RuntimeException("Invalid username or password");
        }
    }

    @Transactional
    public void register(RegisterRequest request) {
        log.info("Registration attempt for user: {}", request.getUsername());

        // Validate passwords match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        // Check if username exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        // Check if email exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Validate role
        if (!isValidRole(request.getRole())) {
            throw new RuntimeException("Invalid role specified");
        }

        // Create new user with PENDING status
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .address(request.getAddress())
                .role(request.getRole())
                .isEnabled(false) // Set to false initially
                .registrationStatus(RegistrationStatus.PENDING)
                .isAccountNonLocked(true)
                .isAccountNonExpired(true)
                .isCredentialsNonExpired(true)
                .passwordChangedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        log.info("User registered successfully with PENDING status: {}", request.getUsername());
    }

    @Transactional
    public LoginResponse refreshToken(String refreshTokenHeader) {
        if (refreshTokenHeader == null || !refreshTokenHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid refresh token");
        }

        String refreshToken = refreshTokenHeader.substring(7);

        try {
            // Validate refresh token
            String username = refreshTokenUtil.validateRefreshToken(refreshToken);

            // Load user
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Generate new access token
            String newAccessToken = jwtTokenUtil.generateToken(userDetailsService.loadUserByUsername(username));

            // Generate new refresh token (optional - rotate refresh tokens)
            String newRefreshToken = refreshTokenUtil.generateRefreshToken(username);

            log.info("Token refreshed for user: {}", username);

            return LoginResponse.builder()
                    .token(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .expiresIn(TimeUnit.MILLISECONDS.toSeconds(jwtTokenUtil.getJwtExpiration()))
                    .expiresAt(LocalDateTime.now().plusSeconds(TimeUnit.MILLISECONDS.toSeconds(jwtTokenUtil.getJwtExpiration())))
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole())
                    .build();

        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw new RuntimeException("Invalid or expired refresh token");
        }
    }

    @Transactional(readOnly = true)
    public UserProfile getCurrentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return UserProfile.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .profilePicture(user.getProfilePicture())
                .role(user.getRole())
                .isAccountNonLocked(user.isAccountNonLocked())
                .isEnabled(user.getIsEnabled())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Validate new passwords match
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new RuntimeException("New passwords do not match");
        }

        // Check if new password is same as old
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("New password cannot be the same as current password");
        }

        // Update password
        LocalDateTime now = LocalDateTime.now();
        userRepository.updatePassword(userId, passwordEncoder.encode(request.getNewPassword()), now);

        log.info("Password changed for user: {}", user.getUsername());
    }

    @Transactional
    public UserProfile updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update email if provided and changed
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            // Check if new email is already taken
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email already in use");
            }
            user.setEmail(request.getEmail());
        }

        // Update other fields
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }

        if (request.getProfilePicture() != null) {
            user.setProfilePicture(request.getProfilePicture());
        }

        userRepository.save(user);

        log.info("Profile updated for user: {}", user.getUsername());

        return UserProfile.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .profilePicture(user.getProfilePicture())
                .role(user.getRole())
                .isAccountNonLocked(user.isAccountNonLocked())
                .isEnabled(user.getIsEnabled())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Transactional
    public void logout(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("User logged out: {}", user.getUsername());
    }

    @Transactional
    public void enableUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsEnabled(true);
        userRepository.save(user);

        log.info("User enabled: {}", user.getUsername());
    }

    @Transactional
    public void disableUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsEnabled(false);
        userRepository.save(user);

        log.info("User disabled: {}", user.getUsername());
    }

    @Transactional
    public void unlockUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        userRepository.resetFailedAttempts(userId);
        userRepository.lockUser(userId, null);

        log.info("User unlocked: {}", user.getUsername());
    }

    private boolean isValidRole(String role) {
        return role.equals("ADMIN") ||
                role.equals("TEACHER") ||
                role.equals("STUDENT") ||
                role.equals("PARENT") ||
                role.equals("STAFF") ||
                role.equals("ACCOUNTANT");
    }
}