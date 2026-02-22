package com.system.SchoolManagementSystem.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StudentDropdownDTO {
    private Long id;
    private String studentId;
    private String fullName;
    private String grade;
    private String phone;
    private String email;

    // Constructor for the query
    public StudentDropdownDTO(Long id, String studentId, String fullName, String grade, String phone, String email) {
        this.id = id;
        this.studentId = studentId;
        this.fullName = fullName;
        this.grade = grade;
        this.phone = phone;
        this.email = email;
    }

    // For display in dropdown
    public String getDisplayName() {
        return fullName + " (" + grade + " - " + studentId + ")";
    }
}