package com.system.SchoolManagementSystem.transaction.entity;

import com.system.SchoolManagementSystem.transaction.enums.FeeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_installments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FeeInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_assignment_id", nullable = false)
    private StudentFeeAssignment feeAssignment;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "installment_name", length = 100)
    private String installmentName;

    @Column(nullable = false)
    private Double amount;

    @Column(name = "paid_amount")
    @Builder.Default
    private Double paidAmount = 0.0;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private FeeStatus status = FeeStatus.PENDING;

    @Column(name = "late_fee_charged")
    @Builder.Default
    private Double lateFeeCharged = 0.0;

    @Column(name = "discount_amount")
    @Builder.Default
    private Double discountAmount = 0.0;

    @Column(name = "net_amount")
    private Double netAmount;

    @Column(name = "payment_deadline")
    private LocalDate paymentDeadline;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void calculateNetAmount() {
        netAmount = amount + lateFeeCharged - discountAmount;
        updateStatus();
    }

    private void updateStatus() {
        if (paidAmount >= netAmount) {
            status = FeeStatus.PAID;
        } else if (dueDate != null && LocalDate.now().isAfter(dueDate)) {
            status = FeeStatus.OVERDUE;
        } else if (paidAmount > 0) {
            status = FeeStatus.PARTIAL;
        } else {
            status = FeeStatus.PENDING;
        }
    }
}