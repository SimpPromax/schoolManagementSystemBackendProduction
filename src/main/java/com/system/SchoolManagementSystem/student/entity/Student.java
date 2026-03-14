package com.system.SchoolManagementSystem.student.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "students")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"familyMembers", "medicalRecords", "achievements", "interests", "termAssignments"})
@SQLDelete(sql = "UPDATE students SET deleted = true WHERE id = ?")
@Where(clause = "deleted = false")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "student_id", unique = true, nullable = false, length = 20)
    @EqualsAndHashCode.Include
    private String studentId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group")
    private BloodGroup bloodGroup;

    private String nationality;
    private String religion;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(name = "profile_picture")
    private String profilePicture;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(name = "academic_year", nullable = false, length = 10)
    private String academicYear;

    // Academic details
    @Column(nullable = false, length = 20)
    private String grade;

    @Column(name = "roll_number", length = 20)
    private String rollNumber;

    @Column(name = "class_teacher", length = 100)
    private String classTeacher;

    private String house;

    // Contact details
    private String address;
    private String phone;
    private String email;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "emergency_relation", length = 50)
    private String emergencyRelation;

    // Medical
    private String height;
    private String weight;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    @Column(name = "last_medical_checkup")
    private LocalDate lastMedicalCheckup;

    @Column(name = "doctor_name", length = 100)
    private String doctorName;

    @Column(name = "clinic_name", length = 100)
    private String clinicName;

    // Transport
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode")
    private TransportMode transportMode;

    @Column(name = "bus_route", length = 50)
    private String busRoute;

    @Column(name = "bus_stop", length = 100)
    private String busStop;

    @Column(name = "bus_number", length = 20)
    private String busNumber;

    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(name = "driver_contact", length = 20)
    private String driverContact;

    @Column(name = "pickup_time")
    private String pickupTime;

    @Column(name = "drop_time")
    private String dropTime;

    @Column(name = "transport_fee")
    private Double transportFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_fee_status")
    @Builder.Default
    private FeeStatus transportFeeStatus = FeeStatus.PENDING;

    // Fee Information
    @Column(name = "total_fee")
    private Double totalFee;

    @Column(name = "paid_amount")
    @Builder.Default
    private Double paidAmount = 0.0;

    @Column(name = "pending_amount")
    private Double pendingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_status")
    @Builder.Default
    private FeeStatus feeStatus = FeeStatus.PENDING;

    @Column(name = "tuition_fee")
    private Double tuitionFee;

    @Column(name = "admission_fee")
    private Double admissionFee;

    @Column(name = "examination_fee")
    private Double examinationFee;

    @Column(name = "other_fees")
    private Double otherFees;

    @Column(name = "fee_due_date")
    private LocalDate feeDueDate;

    @Column(name = "last_fee_update")
    private LocalDateTime lastFeeUpdate;

    // Status
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StudentStatus status = StudentStatus.ACTIVE;

    // Soft delete field
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<FamilyMember> familyMembers = new HashSet<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<MedicalRecord> medicalRecords = new HashSet<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<Achievement> achievements = new HashSet<>();

    // UPDATED: Interests relationship with proper cascade
    @OneToMany(mappedBy = "student",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<StudentInterest> interests = new HashSet<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<StudentTermAssignment> termAssignments = new HashSet<>();

    @Transient
    @Builder.Default
    private boolean manualDueDateUpdate = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastFeeUpdate = LocalDateTime.now();
        calculateFeeDetails();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastFeeUpdate = LocalDateTime.now();

        if (!manualDueDateUpdate) {
            calculateFeeDetails();
        } else {
            manualDueDateUpdate = false;
            calculateFeeDetailsWithoutDueDate();
        }
    }

    private void calculateFeeDetailsWithoutDueDate() {
        try {
            LocalDate preservedDueDate = this.feeDueDate;

            if (Hibernate.isInitialized(this.termAssignments) && this.termAssignments != null) {
                double totalTermFee = getTotalFeeAmount();
                double totalPaid = getTotalPaidAmount();
                double totalPending = getTotalPendingAmount();

                double tuitionTotal = getTuitionFeeTotal();
                double admissionTotal = getAdmissionFeeTotal();
                double examinationTotal = getExaminationFeeTotal();
                double otherTotal = getOtherFeesTotal();

                this.totalFee = totalTermFee;
                this.paidAmount = totalPaid;
                this.pendingAmount = totalPending;
                this.tuitionFee = tuitionTotal;
                this.admissionFee = admissionTotal;
                this.examinationFee = examinationTotal;
                this.otherFees = otherTotal;

                updateFeeStatusBasedOnCurrentDueDate();
            } else {
                this.pendingAmount = this.totalFee != null ?
                        Math.max(0, this.totalFee - (this.paidAmount != null ? this.paidAmount : 0.0)) : 0.0;
                updateFeeStatusBasedOnCurrentDueDate();
            }

            this.feeDueDate = preservedDueDate;

        } catch (Exception e) {
            System.err.println("Error calculating fee details without due date: " + e.getMessage());
        }
    }

    private void calculateFeeDetails() {
        try {
            if (Hibernate.isInitialized(this.termAssignments) && this.termAssignments != null) {
                double totalTermFee = getTotalFeeAmount();
                double totalPaid = getTotalPaidAmount();
                double totalPending = getTotalPendingAmount();

                double tuitionTotal = getTuitionFeeTotal();
                double admissionTotal = getAdmissionFeeTotal();
                double examinationTotal = getExaminationFeeTotal();
                double otherTotal = getOtherFeesTotal();

                this.totalFee = totalTermFee;
                this.paidAmount = totalPaid;
                this.pendingAmount = totalPending;
                this.tuitionFee = tuitionTotal;
                this.admissionFee = admissionTotal;
                this.examinationFee = examinationTotal;
                this.otherFees = otherTotal;

                Optional<LocalDate> earliestDueDate = getEarliestPendingDueDate();
                this.feeDueDate = earliestDueDate.orElse(null);

                updateFeeStatusBasedOnDueDate();
            } else {
                this.pendingAmount = this.totalFee != null ?
                        Math.max(0, this.totalFee - (this.paidAmount != null ? this.paidAmount : 0.0)) : 0.0;
                updateFeeStatusBasedOnDueDate();
            }
        } catch (Exception e) {
            System.err.println("Error calculating fee details: " + e.getMessage());
        }
    }

    private void updateFeeStatusBasedOnDueDate() {
        if (this.pendingAmount != null && this.pendingAmount <= 0) {
            this.feeStatus = FeeStatus.PAID;
            return;
        }

        if (this.totalFee == null || this.totalFee <= 0) {
            this.feeStatus = FeeStatus.PAID;
            return;
        }

        if (this.feeDueDate == null) {
            if (this.paidAmount != null && this.paidAmount > 0) {
                this.feeStatus = FeeStatus.PARTIAL;
            } else {
                this.feeStatus = FeeStatus.PENDING;
            }
            return;
        }

        LocalDate today = LocalDate.now();

        if (today.isAfter(this.feeDueDate)) {
            if (this.pendingAmount != null && this.pendingAmount > 0) {
                this.feeStatus = FeeStatus.OVERDUE;
            } else {
                this.feeStatus = FeeStatus.PAID;
            }
        } else {
            if (this.paidAmount != null && this.paidAmount > 0) {
                this.feeStatus = FeeStatus.PARTIAL;
            } else {
                this.feeStatus = FeeStatus.PENDING;
            }
        }
    }

    private void updateFeeStatusBasedOnCurrentDueDate() {
        if (this.pendingAmount != null && this.pendingAmount <= 0) {
            this.feeStatus = FeeStatus.PAID;
            return;
        }

        if (this.totalFee == null || this.totalFee <= 0) {
            this.feeStatus = FeeStatus.PAID;
            return;
        }

        if (this.feeDueDate == null) {
            if (this.paidAmount != null && this.paidAmount > 0) {
                this.feeStatus = FeeStatus.PARTIAL;
            } else {
                this.feeStatus = FeeStatus.PENDING;
            }
            return;
        }

        LocalDate today = LocalDate.now();

        if (today.isAfter(this.feeDueDate)) {
            if (this.pendingAmount != null && this.pendingAmount > 0) {
                this.feeStatus = FeeStatus.OVERDUE;
            } else {
                this.feeStatus = FeeStatus.PAID;
            }
        } else {
            if (this.paidAmount != null && this.paidAmount > 0) {
                this.feeStatus = FeeStatus.PARTIAL;
            } else {
                this.feeStatus = FeeStatus.PENDING;
            }
        }
    }

    // Manual due date methods
    public void setFeeDueDateManually(LocalDate dueDate) {
        this.feeDueDate = dueDate;
        this.manualDueDateUpdate = true;
        updateFeeStatusBasedOnCurrentDueDate();
    }

    public void clearFeeDueDateManually() {
        this.feeDueDate = null;
        this.manualDueDateUpdate = true;
        if (this.pendingAmount != null && this.pendingAmount <= 0) {
            this.feeStatus = FeeStatus.PAID;
        } else if (this.paidAmount != null && this.paidAmount > 0) {
            this.feeStatus = FeeStatus.PARTIAL;
        } else {
            this.feeStatus = FeeStatus.PENDING;
        }
    }

    public void updateDueDateFromTerm(LocalDate termDueDate) {
        setFeeDueDateManually(termDueDate);
    }

    // Fee calculation helper methods
    public Double getTotalFeeAmount() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getTotalTermFee)
                .sum();
    }

    public Double getTotalPaidAmount() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPaidAmount)
                .sum();
    }

    public Double getTotalPendingAmount() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum();
    }

    public Double getTuitionFeeTotal() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .flatMap(ta -> {
                    if (Hibernate.isInitialized(ta.getFeeItems())) {
                        return ta.getFeeItems().stream();
                    }
                    return Stream.empty();
                })
                .filter(item -> item.getFeeType() != null &&
                        item.getFeeType().name().equals("TUITION"))
                .mapToDouble(item -> item.getAmount())
                .sum();
    }

    public Double getAdmissionFeeTotal() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .flatMap(ta -> {
                    if (Hibernate.isInitialized(ta.getFeeItems())) {
                        return ta.getFeeItems().stream();
                    }
                    return Stream.empty();
                })
                .filter(item -> item.getFeeType() != null &&
                        item.getFeeType().name().equals("ADMISSION"))
                .mapToDouble(item -> item.getAmount())
                .sum();
    }

    public Double getExaminationFeeTotal() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .flatMap(ta -> {
                    if (Hibernate.isInitialized(ta.getFeeItems())) {
                        return ta.getFeeItems().stream();
                    }
                    return Stream.empty();
                })
                .filter(item -> item.getFeeType() != null &&
                        item.getFeeType().name().equals("EXAMINATION"))
                .mapToDouble(item -> item.getAmount())
                .sum();
    }

    public Double getOtherFeesTotal() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .flatMap(ta -> {
                    if (Hibernate.isInitialized(ta.getFeeItems())) {
                        return ta.getFeeItems().stream();
                    }
                    return Stream.empty();
                })
                .filter(item -> item.getFeeType() != null &&
                        (item.getFeeType().name().equals("OTHER") ||
                                item.getFeeType().name().equals("LIBRARY") ||
                                item.getFeeType().name().equals("SPORTS") ||
                                item.getFeeType().name().equals("ACTIVITY") ||
                                item.getFeeType().name().equals("HOSTEL") ||
                                item.getFeeType().name().equals("UNIFORM") ||
                                item.getFeeType().name().equals("BOOKS")))
                .mapToDouble(item -> item.getAmount())
                .sum();
    }

    public Optional<LocalDate> getEarliestPendingDueDate() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return Optional.empty();
        }

        LocalDate earliestDate = null;

        for (StudentTermAssignment assignment : this.termAssignments) {
            if (assignment.getPendingAmount() > 0 && assignment.getDueDate() != null) {
                if (earliestDate == null || assignment.getDueDate().isBefore(earliestDate)) {
                    earliestDate = assignment.getDueDate();
                }
            }

            if (Hibernate.isInitialized(assignment.getFeeItems())) {
                for (var feeItem : assignment.getFeeItems()) {
                    if (feeItem.getPendingAmount() != null &&
                            feeItem.getPendingAmount() > 0 &&
                            feeItem.getDueDate() != null) {

                        if (earliestDate == null || feeItem.getDueDate().isBefore(earliestDate)) {
                            earliestDate = feeItem.getDueDate();
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(earliestDate);
    }

    public Optional<StudentTermAssignment> getCurrentTermAssignment() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return Optional.empty();
        }
        return termAssignments.stream()
                .filter(ta -> ta.getAcademicTerm() != null &&
                        ta.getAcademicTerm().getIsCurrent())
                .findFirst();
    }

    public boolean hasOverdueFees() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return false;
        }
        return termAssignments.stream()
                .anyMatch(ta -> ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE);
    }

    public Double getOverdueAmount() {
        if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
            return 0.0;
        }
        return termAssignments.stream()
                .filter(ta -> ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum();
    }

    public Double getPaymentPercentage() {
        if (totalFee == null || totalFee <= 0) {
            return 0.0;
        }
        return (paidAmount != null ? paidAmount : 0.0) / totalFee * 100;
    }

    public void addTermAssignment(StudentTermAssignment assignment) {
        if (Hibernate.isInitialized(this.termAssignments) && this.termAssignments != null) {
            termAssignments.add(assignment);
            assignment.setStudent(this);
            calculateFeeDetails();
        }
    }

    public void removeTermAssignment(StudentTermAssignment assignment) {
        if (Hibernate.isInitialized(this.termAssignments) && this.termAssignments != null) {
            termAssignments.remove(assignment);
            assignment.setStudent(null);
            calculateFeeDetails();
        }
    }

    public void updateFeeSummary() {
        calculateFeeDetails();
    }

    // Due date helper methods
    @Transient
    public boolean isDueDateOverdue() {
        if (this.feeDueDate == null) return false;
        return LocalDate.now().isAfter(this.feeDueDate) &&
                this.pendingAmount != null &&
                this.pendingAmount > 0;
    }

    @Transient
    public Long getDaysUntilDueDate() {
        if (this.feeDueDate == null) return null;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), this.feeDueDate);
    }

    @Transient
    public Long getOverdueDays() {
        if (!isDueDateOverdue()) return 0L;
        return Math.abs(getDaysUntilDueDate());
    }

    public void clearDueDateIfPaid() {
        if (this.pendingAmount != null && this.pendingAmount <= 0) {
            this.feeDueDate = null;
            this.feeStatus = FeeStatus.PAID;
        }
    }

    public void refreshFeeStatusAndDueDate() {
        calculateFeeDetails();
    }

    @Transient
    public boolean needsDueDateUpdate() {
        if (this.feeDueDate == null && (this.pendingAmount == null || this.pendingAmount <= 0)) {
            return false;
        }

        Optional<LocalDate> calculatedDueDate = getEarliestPendingDueDate();
        LocalDate currentDueDate = this.feeDueDate;

        if (calculatedDueDate.isPresent() && currentDueDate == null) {
            return true;
        }

        if (!calculatedDueDate.isPresent() && currentDueDate != null) {
            return true;
        }

        if (calculatedDueDate.isPresent() && currentDueDate != null) {
            return !calculatedDueDate.get().equals(currentDueDate);
        }

        return false;
    }

    @Transient
    public boolean isDueDateManuallySet() {
        return this.manualDueDateUpdate;
    }

    // Term assignment helper methods
    @Transient
    public Boolean getHasTermAssignments() {
        try {
            if (!Hibernate.isInitialized(this.termAssignments)) {
                return null;
            }
            return this.termAssignments != null && !this.termAssignments.isEmpty();
        } catch (Exception e) {
            System.err.println("Error in getHasTermAssignments: " + e.getMessage());
            return null;
        }
    }

    @Transient
    public Integer getTermAssignmentCount() {
        try {
            if (!Hibernate.isInitialized(this.termAssignments)) {
                return null;
            }
            return this.termAssignments != null ? this.termAssignments.size() : 0;
        } catch (Exception e) {
            System.err.println("Error in getTermAssignmentCount: " + e.getMessage());
            return 0;
        }
    }

    @Transient
    public Integer getActiveTermAssignmentCount() {
        try {
            if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
                return 0;
            }

            return (int) this.termAssignments.stream()
                    .filter(ta -> ta.getTermFeeStatus() != StudentTermAssignment.FeeStatus.CANCELLED &&
                            ta.getTermFeeStatus() != StudentTermAssignment.FeeStatus.WAIVED)
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    @Transient
    public Integer getPendingTermAssignmentCount() {
        try {
            if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
                return 0;
            }

            return (int) this.termAssignments.stream()
                    .filter(ta -> ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.PENDING ||
                            ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.PARTIAL ||
                            ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    @Transient
    public Boolean getHasCurrentTermAssignment() {
        return getCurrentTermAssignment().isPresent();
    }

    @Transient
    public Double getTotalPendingFromAssignments() {
        try {
            if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
                return 0.0;
            }

            return this.termAssignments.stream()
                    .filter(ta -> ta.getTermFeeStatus() != StudentTermAssignment.FeeStatus.CANCELLED &&
                            ta.getTermFeeStatus() != StudentTermAssignment.FeeStatus.WAIVED)
                    .mapToDouble(StudentTermAssignment::getPendingAmount)
                    .sum();
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Transient
    public List<StudentTermAssignment> getOverdueTermAssignments() {
        try {
            if (!Hibernate.isInitialized(this.termAssignments) || this.termAssignments == null) {
                return Collections.emptyList();
            }

            return this.termAssignments.stream()
                    .filter(ta -> ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Transient
    public Boolean getHasOverdueTermAssignments() {
        return !getOverdueTermAssignments().isEmpty();
    }

    @Transient
    public String getDueDateStatus() {
        if (this.feeDueDate == null) {
            return "NO_DUE_DATE";
        }

        LocalDate today = LocalDate.now();

        if (today.isAfter(this.feeDueDate)) {
            return "OVERDUE";
        } else if (today.isEqual(this.feeDueDate)) {
            return "DUE_TODAY";
        } else if (today.plusDays(3).isAfter(this.feeDueDate)) {
            return "DUE_SOON";
        } else {
            return "UPCOMING";
        }
    }

    @Transient
    public boolean isPaymentUrgent() {
        if (this.feeDueDate == null) return false;

        LocalDate today = LocalDate.now();
        return today.isAfter(this.feeDueDate) ||
                !today.plusDays(3).isBefore(this.feeDueDate);
    }

    // Enums
    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum BloodGroup {
        A_PLUS, A_MINUS, B_PLUS, B_MINUS, O_PLUS, O_MINUS, AB_PLUS, AB_MINUS, UNKNOWN
    }

    public enum Category {
        GENERAL, OBC, SC, ST, OTHER
    }

    public enum TransportMode {
        SCHOOL_BUS, PRIVATE_VEHICLE, PUBLIC_TRANSPORT, WALKING, OTHER
    }

    public enum FeeStatus {
        PAID, PENDING, OVERDUE, PARTIAL
    }

    public enum StudentStatus {
        ACTIVE, GRADUATED, TRANSFERRED, INACTIVE, SUSPENDED
    }
}