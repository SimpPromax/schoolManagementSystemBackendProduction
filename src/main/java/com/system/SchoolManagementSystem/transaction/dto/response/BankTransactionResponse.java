package com.system.SchoolManagementSystem.transaction.dto.response;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BankTransactionResponse {
    private Long id;
    private String bankReference;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transactionDate;

    private String description;
    private Double amount;
    private String bankAccount;
    private TransactionStatus status;
    private PaymentMethod paymentMethod;

    // ✅ ADD THIS: Notes field for validation issues
    private String notes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime importedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime matchedAt;

    private String fileName;
    private String importBatchId;

    // SMS fields for auto-matched transactions
    private Boolean smsSent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime smsSentAt;

    private String smsId;

    // ========== NEW: Payment Transaction Info ==========
    private Long paymentTransactionId;
    private String receiptNumber;
    private Boolean paymentVerified;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paymentVerifiedAt;

    // Student information
    private Long studentId;
    private String studentName;
    private String studentGrade;

    // Student fee information
    private Double studentPendingAmount;
    private Student.FeeStatus studentFeeStatus;

    // Optional: Additional fee details
    private Double studentTotalFee;
    private Double studentPaidAmount;
    private Double studentPaymentPercentage;

    // ========== ADDED: Term Assignment Fields ==========
    private Boolean hasTermAssignments;
    private Integer termAssignmentCount;

    public void setStudentFeeStatus(Student.FeeStatus feeStatus) {
        this.studentFeeStatus = feeStatus;
    }

    public void setStudentFeeStatus(String feeStatus) {
        if (feeStatus != null) {
            try {
                this.studentFeeStatus = Student.FeeStatus.valueOf(feeStatus);
            } catch (IllegalArgumentException e) {
                this.studentFeeStatus = null;
            }
        }
    }
}