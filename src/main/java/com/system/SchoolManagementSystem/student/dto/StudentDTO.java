package com.system.SchoolManagementSystem.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.system.SchoolManagementSystem.student.entity.Student;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class StudentDTO {
    private Long id;
    private String studentId;
    private String fullName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private Student.Gender gender;
    private Student.BloodGroup bloodGroup;
    private String nationality;
    private String religion;
    private Student.Category category;
    private String profilePicture;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate admissionDate;

    private String academicYear;
    private String grade;
    private String rollNumber;
    private String classTeacher;
    private String house;

    // Contact
    private String address;
    private String phone;
    private String email;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyRelation;

    // Medical
    private String height;
    private String weight;
    private String bloodPressure;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastMedicalCheckup;

    private String doctorName;
    private String clinicName;

    // Transport
    private Student.TransportMode transportMode;
    private String busRoute;
    private String busStop;
    private String busNumber;
    private String driverName;
    private String driverContact;
    private String pickupTime;
    private String dropTime;
    private Double transportFee;
    private Student.FeeStatus transportFeeStatus;

    // ========== FEE INFORMATION ==========
    private Double totalFee;
    private Double paidAmount;
    private Double pendingAmount;
    private Student.FeeStatus feeStatus;

    // Optional: Fee details breakdown
    private Double tuitionFee;
    private Double admissionFee;
    private Double examinationFee;
    private Double otherFees;
    // ===========================================

    // Nested data
    private List<FamilyMemberDTO> familyMembers;
    private List<MedicalRecordDTO> medicalRecords;
    private List<AchievementDTO> achievements;

    // ========== NEW: Add interests field ==========
    private List<StudentInterestDTO> interests;

    // Clubs & hobbies as arrays (for backward compatibility)
    private List<String> clubs;
    private List<String> hobbies;

    // Helper methods for fee calculations
    public Double getPendingAmount() {
        if (this.pendingAmount != null) {
            return this.pendingAmount;
        }
        if (this.totalFee != null && this.paidAmount != null) {
            return Math.max(0, this.totalFee - this.paidAmount);
        }
        return 0.0;
    }

    public Student.FeeStatus getFeeStatus() {
        if (this.feeStatus != null) {
            return this.feeStatus;
        }
        if (this.totalFee != null && this.paidAmount != null) {
            if (this.paidAmount >= this.totalFee) {
                return Student.FeeStatus.PAID;
            } else if (this.paidAmount > 0) {
                return Student.FeeStatus.PENDING;
            } else {
                return Student.FeeStatus.PENDING;
            }
        }
        return Student.FeeStatus.PENDING;
    }
}