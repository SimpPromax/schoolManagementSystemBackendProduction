package com.system.SchoolManagementSystem.transaction.entity;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import com.system.SchoolManagementSystem.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "bank_transactions",
        indexes = {
                @Index(name = "idx_bank_transaction_status_date", columnList = "status, transaction_date"),
                @Index(name = "idx_bank_transaction_student_status", columnList = "student_id, status"),
                @Index(name = "idx_bank_transaction_import_batch", columnList = "import_batch_id"),
                @Index(name = "idx_bank_transaction_bank_ref", columnList = "bank_reference", unique = true)
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "bank_reference", nullable = false, unique = true, length = 50)
    private String bankReference;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private Double amount;

    @Column(name = "bank_account", length = 50)
    private String bankAccount;

    // ========== NEW FIELDS ==========
    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_branch", length = 100)
    private String bankBranch;

    @Column(name = "cheque_number", length = 50)
    private String chequeNumber;
    // ================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.UNVERIFIED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @CreationTimestamp
    @Column(name = "imported_at", nullable = false, updatable = false)
    private LocalDateTime importedAt;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_by")
    private User matchedBy;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "import_batch_id")
    private String importBatchId;

    @Column(name = "sms_sent", nullable = false)
    @Builder.Default
    private Boolean smsSent = false;

    @Column(name = "sms_sent_at")
    private LocalDateTime smsSentAt;

    @Column(name = "sms_id")
    private String smsId;

    @Column(length = 1000)
    private String notes;

    @OneToOne(mappedBy = "bankTransaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PaymentTransaction paymentTransaction;

    @PrePersist
    protected void onCreate() {
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.BANK_TRANSFER;
        }
        // smsSent already has @Builder.Default, so no need to set here
    }
}