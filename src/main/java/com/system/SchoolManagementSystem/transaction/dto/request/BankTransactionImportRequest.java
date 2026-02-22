package com.system.SchoolManagementSystem.transaction.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class BankTransactionImportRequest {
    private MultipartFile file;
    private String importType; // CSV, EXCEL, PDF
    private String bankAccount;
    private String importBatchId;
    private String validationResults; // NEW: JSON string of validation results from frontend
}