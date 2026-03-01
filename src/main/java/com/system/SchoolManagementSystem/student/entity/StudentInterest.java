package com.system.SchoolManagementSystem.student.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "student_interests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"student"})
public class StudentInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false)
    private InterestType interestType;

    @Column(nullable = false, length = 100)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Custom equals that uses business key (type + name) instead of just id
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StudentInterest that = (StudentInterest) o;
        return interestType == that.interestType &&
                name.equalsIgnoreCase(that.name);  // Case-insensitive!
    }

    @Override
    public int hashCode() {
        return Objects.hash(interestType, name.toLowerCase());  // Use lowercase for hash
    }

    public enum InterestType {
        CLUB, HOBBY
    }
}