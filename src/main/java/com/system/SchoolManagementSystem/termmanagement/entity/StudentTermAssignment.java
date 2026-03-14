package com.system.SchoolManagementSystem.termmanagement.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.entity.StudentFeeAssignment;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "student_term_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "academic_term_id"}),
        indexes = {
                @Index(name = "idx_student_term", columnList = "student_id, academic_term_id"),
                @Index(name = "idx_term_status", columnList = "academic_term_id, term_fee_status"),
                @Index(name = "idx_due_date", columnList = "due_date"),
                @Index(name = "idx_fee_assignment", columnList = "student_fee_assignment_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"student", "academicTerm", "studentFeeAssignment", "feeItems"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StudentTermAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonBackReference
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id", nullable = false)
    @JsonBackReference
    private AcademicTerm academicTerm;

    @Column(name = "total_term_fee")
    @Builder.Default
    private Double totalTermFee = 0.0;

    @Column(name = "paid_amount")
    @Builder.Default
    private Double paidAmount = 0.0;

    @Column(name = "pending_amount")
    @Builder.Default
    private Double pendingAmount = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "term_fee_status", nullable = false)
    @Builder.Default
    private FeeStatus termFeeStatus = FeeStatus.PENDING;

    @Column(name = "is_billed", nullable = false)
    @Builder.Default
    private Boolean isBilled = false;

    @Column(name = "billing_date")
    private LocalDate billingDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    @Column(name = "reminders_sent")
    @Builder.Default
    private Integer remindersSent = 0;

    @Column(name = "last_reminder_date")
    private LocalDate lastReminderDate;

    // Link to existing StudentFeeAssignment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_fee_assignment_id")
    private StudentFeeAssignment studentFeeAssignment;

    // Detailed fee items for this term - FIXED CASCADE SETTINGS
    @OneToMany(mappedBy = "studentTermAssignment",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            fetch = FetchType.LAZY,
            orphanRemoval = false) // Changed to false to avoid cascade issues
    @JsonManagedReference
    @Builder.Default
    private List<TermFeeItem> feeItems = new ArrayList<>();

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Revised method to avoid circular dependencies and ConcurrentModificationException
    public void calculateAmounts() {
        // Create a defensive copy to avoid ConcurrentModificationException
        List<TermFeeItem> itemsCopy = new ArrayList<>(feeItems);

        double total = itemsCopy.stream()
                .mapToDouble(item -> {
                    Double amount = item.getAmount();
                    return amount != null ? amount : 0.0;
                })
                .sum();

        double paid = itemsCopy.stream()
                .mapToDouble(item -> {
                    Double paidAmount = item.getPaidAmount();
                    return paidAmount != null ? paidAmount : 0.0;
                })
                .sum();

        this.totalTermFee = total;
        this.paidAmount = paid;
        this.pendingAmount = Math.max(0, total - paid);

        // Update status
        updateStatus();
    }

    private void updateStatus() {
        if (paidAmount >= totalTermFee) {
            this.termFeeStatus = FeeStatus.PAID;
        } else if (paidAmount > 0) {
            this.termFeeStatus = FeeStatus.PARTIAL;
        } else if (dueDate != null && LocalDate.now().isAfter(dueDate)) {
            this.termFeeStatus = FeeStatus.OVERDUE;
        } else {
            this.termFeeStatus = FeeStatus.PENDING;
        }
    }

    // FIXED: Safe bidirectional relationship management
    public void addFeeItem(TermFeeItem item) {
        if (feeItems == null) {
            feeItems = new ArrayList<>();
        }

        // Check if item already exists
        if (!feeItems.contains(item)) {
            feeItems.add(item);
            item.setStudentTermAssignment(this);
        }
    }

    // FIXED: Safe bulk add method
    public void addFeeItems(Collection<TermFeeItem> items) {
        if (feeItems == null) {
            feeItems = new ArrayList<>();
        }

        for (TermFeeItem item : items) {
            if (!feeItems.contains(item)) {
                feeItems.add(item);
                item.setStudentTermAssignment(this);
            }
        }
    }

    // FIXED: Safe removal
    public void removeFeeItem(TermFeeItem item) {
        if (feeItems != null) {
            feeItems.remove(item);
            item.setStudentTermAssignment(null);
        }
    }

    // NEW: Clear all items
    public void clearFeeItems() {
        if (feeItems != null) {
            // Clear the bidirectional relationship
            for (TermFeeItem item : feeItems) {
                item.setStudentTermAssignment(null);
            }
            feeItems.clear();
        }
    }

    // NEW: Setter for fee items that ensures bidirectional relationship
    public void setFeeItems(List<TermFeeItem> feeItems) {
        if (this.feeItems != null) {
            // Clear existing relationships
            for (TermFeeItem item : this.feeItems) {
                item.setStudentTermAssignment(null);
            }
        }

        this.feeItems = feeItems != null ? feeItems : new ArrayList<>();

        // Set the bidirectional relationship
        if (feeItems != null) {
            for (TermFeeItem item : feeItems) {
                item.setStudentTermAssignment(this);
            }
        }
    }

    public enum FeeStatus {
        PENDING, PARTIAL, PAID, OVERDUE, CANCELLED, WAIVED
    }

    // NEW: Helper method to check if assignment has pending fees
    public boolean hasPendingFees() {
        return pendingAmount != null && pendingAmount > 0;
    }

    // NEW: Helper method to get days overdue
    public long getDaysOverdue() {
        if (dueDate == null || LocalDate.now().isBefore(dueDate)) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now());
    }
}