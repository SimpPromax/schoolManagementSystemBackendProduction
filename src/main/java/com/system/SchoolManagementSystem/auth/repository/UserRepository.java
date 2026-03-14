package com.system.SchoolManagementSystem.auth.repository;

import com.system.SchoolManagementSystem.auth.entity.RegistrationStatus;
import com.system.SchoolManagementSystem.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.username = :username OR u.email = :username")
    Optional<User> findByUsernameOrEmail(@Param("username") String username);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :lastLoginAt WHERE u.id = :userId")
    void updateLastLogin(@Param("userId") String userId, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId")
    void incrementFailedAttempts(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = null WHERE u.id = :userId")
    void resetFailedAttempts(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE User u SET u.lockedUntil = :lockedUntil WHERE u.id = :userId")
    void lockUser(@Param("userId") String userId, @Param("lockedUntil") LocalDateTime lockedUntil);

    @Modifying
    @Query("UPDATE User u SET u.password = :password, u.passwordChangedAt = :changedAt WHERE u.id = :userId")
    void updatePassword(@Param("userId") String userId,
                        @Param("password") String password,
                        @Param("changedAt") LocalDateTime changedAt);

    // NEW METHODS FOR REGISTRATION APPROVAL
    List<User> findByRegistrationStatus(RegistrationStatus status);

    long countByRegistrationStatus(RegistrationStatus status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.registrationStatus = :status AND u.approvedAt >= :date")
    long countByRegistrationStatusAndApprovedAtAfter(@Param("status") RegistrationStatus status, @Param("date") LocalDateTime date);

    @Query("SELECT COUNT(u) FROM User u WHERE u.registrationStatus = :status AND u.rejectedAt >= :date")
    long countByRegistrationStatusAndRejectedAtAfter(@Param("status") RegistrationStatus status, @Param("date") LocalDateTime date);

    List<User> findByRoleAndRegistrationStatus(String role, RegistrationStatus status);

    @Query("SELECT u FROM User u WHERE u.registrationStatus = 'PENDING' AND " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchByTerm(@Param("search") String search);

    @Query("SELECT u FROM User u WHERE u.registrationStatus = 'PENDING' AND " +
            "u.role = :role AND " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchByTermAndRole(@Param("search") String search, @Param("role") String role);
}