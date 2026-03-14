package com.system.SchoolManagementSystem.auth.controller;

import com.system.SchoolManagementSystem.auth.dto.*;
import com.system.SchoolManagementSystem.auth.entity.RegistrationStatus;
import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.service.AdminAuthService;
import com.system.SchoolManagementSystem.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @GetMapping("/pending-registrations")
    public ResponseEntity<ApiResponse<List<PendingRegistrationDTO>>> getPendingRegistrations() {
        log.info("Fetching pending registrations");
        List<PendingRegistrationDTO> pendingUsers = adminAuthService.getPendingRegistrations();
        return ResponseEntity.ok(ApiResponse.success(pendingUsers, "Pending registrations retrieved successfully"));
    }

    @GetMapping("/registrations/stats")
    public ResponseEntity<ApiResponse<RegistrationStatsDTO>> getRegistrationStats() {
        log.info("Fetching registration statistics");
        RegistrationStatsDTO stats = adminAuthService.getRegistrationStats();
        return ResponseEntity.ok(ApiResponse.success(stats, "Registration statistics retrieved successfully"));
    }

    @GetMapping("/registrations/search")
    public ResponseEntity<ApiResponse<List<PendingRegistrationDTO>>> searchRegistrations(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role) {
        log.info("Searching registrations with search: {}, role: {}", search, role);
        List<PendingRegistrationDTO> registrations = adminAuthService.searchRegistrations(search, role);
        return ResponseEntity.ok(ApiResponse.success(registrations, "Registrations retrieved successfully"));
    }

    @GetMapping("/registrations/{userId}")
    public ResponseEntity<ApiResponse<PendingRegistrationDTO>> getRegistrationDetails(
            @PathVariable String userId) {
        log.info("Fetching registration details for user: {}", userId);
        PendingRegistrationDTO registration = adminAuthService.getRegistrationDetails(userId);
        return ResponseEntity.ok(ApiResponse.success(registration, "Registration details retrieved successfully"));
    }

    @PostMapping("/process-registration/{userId}")
    public ResponseEntity<ApiResponse<Void>> processRegistration(
            @PathVariable String userId,
            @Valid @RequestBody ProcessRegistrationRequest request,
            @AuthenticationPrincipal User admin) {
        log.info("Processing registration for user: {} with status: {}", userId, request.getStatus());
        adminAuthService.processRegistration(userId, request, admin.getId());

        String message = request.getStatus() == RegistrationStatus.APPROVED
                ? "User approved successfully"
                : "User rejected successfully";

        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/bulk-approve")
    public ResponseEntity<ApiResponse<Void>> bulkApprove(
            @RequestBody List<String> userIds,
            @AuthenticationPrincipal User admin) {
        log.info("Bulk approving {} users", userIds.size());
        adminAuthService.bulkApprove(userIds, admin.getId());
        return ResponseEntity.ok(ApiResponse.success("Users approved successfully"));
    }

    @PostMapping("/bulk-reject")
    public ResponseEntity<ApiResponse<Void>> bulkReject(
            @RequestBody BulkRejectRequest request,
            @AuthenticationPrincipal User admin) {
        log.info("Bulk rejecting {} users", request.getUserIds().size());
        adminAuthService.bulkReject(request.getUserIds(), request.getReason(), admin.getId());
        return ResponseEntity.ok(ApiResponse.success("Users rejected successfully"));
    }
}