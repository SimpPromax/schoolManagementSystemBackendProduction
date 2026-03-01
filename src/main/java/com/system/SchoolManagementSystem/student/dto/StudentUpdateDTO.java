package com.system.SchoolManagementSystem.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.entity.StudentInterest;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class StudentUpdateDTO {
    private String fullName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private Student.Gender gender;
    private Student.BloodGroup bloodGroup;
    private String nationality;
    private String religion;
    private Student.Category category;
    private String profilePicture;

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

    // For updating related entities
    private List<FamilyMemberDTO> familyMembers;
    private List<MedicalRecordDTO> medicalRecords;
    private List<AchievementDTO> achievements;
    private List<StudentInterestDTO> interests;

    // Add fee fields
    private Double totalFee;
    private Double tuitionFee;
    private Double admissionFee;
    private Double examinationFee;
    private Double otherFees;
    private Double paidAmount;
    private Double pendingAmount;

    // Setter methods
    // ========== ADD THESE FIELDS FOR BACKWARD COMPATIBILITY ==========
    // These will be populated from the interests list in the controller
    private List<String> clubs;
    private List<String> hobbies;

    // Helper methods to extract clubs and hobbies from interests
    public List<String> getClubs() {
        if (clubs != null) {
            return clubs;
        }
        if (interests != null) {
            return interests.stream()
                    .filter(i -> i.getInterestType() == StudentInterest.InterestType.CLUB)
                    .map(StudentInterestDTO::getName)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    public List<String> getHobbies() {
        if (hobbies != null) {
            return hobbies;
        }
        if (interests != null) {
            return interests.stream()
                    .filter(i -> i.getInterestType() == StudentInterest.InterestType.HOBBY)
                    .map(StudentInterestDTO::getName)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

}