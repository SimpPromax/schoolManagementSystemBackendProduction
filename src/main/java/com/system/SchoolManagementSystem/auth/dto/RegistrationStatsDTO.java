package com.system.SchoolManagementSystem.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationStatsDTO {
    private long pending;
    private long approved;
    private long rejected;
    private long approvedToday;
    private long rejectedToday;
}