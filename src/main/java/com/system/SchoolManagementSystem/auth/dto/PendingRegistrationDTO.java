package com.system.SchoolManagementSystem.auth.dto;

import com.system.SchoolManagementSystem.auth.entity.RegistrationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingRegistrationDTO {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String address;
    private String role;
    private RegistrationStatus registrationStatus;
    private LocalDateTime createdAt;
}