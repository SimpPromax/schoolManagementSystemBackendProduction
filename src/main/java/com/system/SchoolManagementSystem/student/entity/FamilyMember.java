package com.system.SchoolManagementSystem.student.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "family_members")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"student"})
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Relation relation;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    private String occupation;
    private String phone;
    private String email;

    @Column(name = "is_primary_contact")
    @Builder.Default
    private Boolean isPrimaryContact = false;

    @Column(name = "is_emergency_contact")
    @Builder.Default
    private Boolean isEmergencyContact = false;

    private LocalDateTime createdAt;
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

    public enum Relation {
        FATHER, MOTHER, GUARDIAN, SIBLING, OTHER
    }
}