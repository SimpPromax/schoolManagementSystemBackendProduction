package com.system.SchoolManagementSystem.auth.dto;

import com.system.SchoolManagementSystem.auth.entity.RegistrationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessRegistrationRequest {
    @NotNull(message = "Status is required")
    private RegistrationStatus status;

    private String rejectionReason;
    private String notes;
}