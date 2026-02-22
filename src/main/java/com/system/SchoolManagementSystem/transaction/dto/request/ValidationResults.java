package com.system.SchoolManagementSystem.transaction.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class ValidationResults {
    private List<ValidationResult> validationResults;
    private Integer totalTransactions;
    private Integer validCount;
    private Integer invalidCount;
    private String warning;
}