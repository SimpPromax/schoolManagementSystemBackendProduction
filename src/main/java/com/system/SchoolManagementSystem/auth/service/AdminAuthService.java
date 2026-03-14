package com.system.SchoolManagementSystem.auth.service;

import com.system.SchoolManagementSystem.auth.dto.PendingRegistrationDTO;
import com.system.SchoolManagementSystem.auth.dto.ProcessRegistrationRequest;
import com.system.SchoolManagementSystem.auth.dto.RegistrationStatsDTO;
import com.system.SchoolManagementSystem.auth.enums.RegistrationStatus;
import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PendingRegistrationDTO> getPendingRegistrations() {
        List<User> pendingUsers = userRepository.findByRegistrationStatus(RegistrationStatus.PENDING);

        return pendingUsers.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RegistrationStatsDTO getRegistrationStats() {
        LocalDateTime today = LocalDate.now().atStartOfDay();

        long pending = userRepository.countByRegistrationStatus(RegistrationStatus.PENDING);
        long approved = userRepository.countByRegistrationStatus(RegistrationStatus.APPROVED);
        long rejected = userRepository.countByRegistrationStatus(RegistrationStatus.REJECTED);
        long approvedToday = userRepository.countByRegistrationStatusAndApprovedAtAfter(
                RegistrationStatus.APPROVED, today);
        long rejectedToday = userRepository.countByRegistrationStatusAndRejectedAtAfter(
                RegistrationStatus.REJECTED, today);

        return RegistrationStatsDTO.builder()
                .pending(pending)
                .approved(approved)
                .rejected(rejected)
                .approvedToday(approvedToday)
                .rejectedToday(rejectedToday)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PendingRegistrationDTO> searchRegistrations(String search, String role) {
        List<User> users;

        if (search != null && !search.isEmpty() && role != null && !role.isEmpty() && !role.equals("all")) {
            users = userRepository.searchByTermAndRole(search, role);
        } else if (search != null && !search.isEmpty()) {
            users = userRepository.searchByTerm(search);
        } else if (role != null && !role.isEmpty() && !role.equals("all")) {
            users = userRepository.findByRoleAndRegistrationStatus(role, RegistrationStatus.PENDING);
        } else {
            users = userRepository.findByRegistrationStatus(RegistrationStatus.PENDING);
        }

        return users.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PendingRegistrationDTO getRegistrationDetails(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return mapToDTO(user);
    }

    @Transactional
    public void processRegistration(String userId, ProcessRegistrationRequest request, String adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getStatus() == RegistrationStatus.APPROVED) {
            user.setRegistrationStatus(RegistrationStatus.APPROVED);
            user.setIsEnabled(true);
            user.setApprovedBy(adminId);
            user.setApprovedAt(LocalDateTime.now());
            user.setRejectionReason(null);
            user.setRejectedBy(null);
            user.setRejectedAt(null);
            log.info("User {} approved by admin {}", user.getUsername(), adminId);
        } else if (request.getStatus() == RegistrationStatus.REJECTED) {
            user.setRegistrationStatus(RegistrationStatus.REJECTED);
            user.setIsEnabled(false);
            user.setRejectedBy(adminId);
            user.setRejectedAt(LocalDateTime.now());
            user.setRejectionReason(request.getRejectionReason());
            user.setApprovedBy(null);
            user.setApprovedAt(null);
            log.info("User {} rejected by admin {}. Reason: {}",
                    user.getUsername(), adminId, request.getRejectionReason());
        }

        user.setRegistrationNotes(request.getNotes());
        userRepository.save(user);
    }

    @Transactional
    public void bulkApprove(List<String> userIds, String adminId) {
        List<User> users = userRepository.findAllById(userIds);
        LocalDateTime now = LocalDateTime.now();

        for (User user : users) {
            user.setRegistrationStatus(RegistrationStatus.APPROVED);
            user.setIsEnabled(true);
            user.setApprovedBy(adminId);
            user.setApprovedAt(now);
            user.setRejectionReason(null);
            user.setRejectedBy(null);
            user.setRejectedAt(null);
        }

        userRepository.saveAll(users);
        log.info("Bulk approved {} users by admin {}", users.size(), adminId);
    }

    @Transactional
    public void bulkReject(List<String> userIds, String reason, String adminId) {
        List<User> users = userRepository.findAllById(userIds);
        LocalDateTime now = LocalDateTime.now();

        for (User user : users) {
            user.setRegistrationStatus(RegistrationStatus.REJECTED);
            user.setIsEnabled(false);
            user.setRejectedBy(adminId);
            user.setRejectedAt(now);
            user.setRejectionReason(reason);
            user.setApprovedBy(null);
            user.setApprovedAt(null);
        }

        userRepository.saveAll(users);
        log.info("Bulk rejected {} users by admin {}", users.size(), adminId);
    }

    private PendingRegistrationDTO mapToDTO(User user) {
        return PendingRegistrationDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .role(user.getRole())
                .registrationStatus(user.getRegistrationStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}