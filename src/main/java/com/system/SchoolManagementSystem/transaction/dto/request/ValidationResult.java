package com.system.SchoolManagementSystem.transaction.dto.request;

import lombok.Data;
import java.util.Map;

@Data
public class ValidationResult {
    private String bankReference;
    private Double amount;
    private String description;
    private String transactionDate;
    private String matchedStudent;
    private Long matchedStudentId;
    private String studentGrade;
    private Boolean valid;
    private String validationMessage;
    private String errorCode;
    private String status;
    private String recommendedAction;
    private Double totalPendingAmount;
    private Boolean hasTermAssignments;
    private Integer termAssignmentCount;
}