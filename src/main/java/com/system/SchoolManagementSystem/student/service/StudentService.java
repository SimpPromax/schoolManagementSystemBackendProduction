package com.system.SchoolManagementSystem.student.service;

import com.system.SchoolManagementSystem.student.dto.*;
import com.system.SchoolManagementSystem.student.entity.*;
import com.system.SchoolManagementSystem.student.repository.*;
import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import com.system.SchoolManagementSystem.transaction.repository.PaymentTransactionRepository;
import com.system.SchoolManagementSystem.student.util.FileValidator;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final StudentRepository studentRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final AchievementRepository achievementRepository;
    private final StudentInterestRepository studentInterestRepository;
    private final FileStorageService fileStorageService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    // ========== STUDENT METHODS (WITH FEE INTEGRATION) ==========

    /**
     * Get all students with fee information
     */
    @Transactional(readOnly = true)
    public List<StudentDTO> getAllStudents() {
        log.info("[STUDENT-SERVICE] [GET-ALL-STUDENTS] Started - Fetching all students with fee info");
        try {
            long startTime = System.currentTimeMillis();
            List<Student> students = studentRepository.findAll();
            log.info("[STUDENT-SERVICE] [GET-ALL-STUDENTS] Found {} students in database", students.size());
            List<StudentDTO> result = students.stream()
                    .map(this::convertToDTOWithFeeInfo)
                    .collect(Collectors.toList());
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("[STUDENT-SERVICE] [GET-ALL-STUDENTS] Completed successfully in {} ms. Returned {} students with fee info",
                    executionTime, result.size());
            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-ALL-STUDENTS] ERROR - Failed to fetch students: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch students: " + e.getMessage(), e);
        }
    }

    /**
     * Get student by ID with fee information
     */
    @Transactional(readOnly = true)
    public StudentDTO getStudentById(Long id) {
        log.info("[STUDENT-SERVICE] [GET-STUDENT-BY-ID] Started - ID: {}", id);
        try {
            Student student = studentRepository.findById(id)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [GET-STUDENT-BY-ID] Student not found with ID: {}", id);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with id: " + id);
                    });
            log.info("[STUDENT-SERVICE] [GET-STUDENT-BY-ID] Found student: {} (ID: {})",
                    student.getFullName(), student.getId());
            StudentDTO result = convertToDTOWithFeeInfo(student);
            log.debug("[STUDENT-SERVICE] [GET-STUDENT-BY-ID] Returning DTO with studentId: {}", result.getStudentId());
            log.info("[STUDENT-SERVICE] [GET-STUDENT-BY-ID] Completed successfully with fee info");
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENT-BY-ID] NOT FOUND - Student with ID {} not found", id);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENT-BY-ID] ERROR - Failed to fetch student with ID {}: {}",
                    id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch student: " + e.getMessage(), e);
        }
    }

    /**
     * Get student by student ID with fee information
     */
    @Transactional(readOnly = true)
    public StudentDTO getStudentByStudentId(String studentId) {
        log.info("[STUDENT-SERVICE] [GET-STUDENT-BY-STUDENT-ID] Started - Student ID: {}", studentId);
        try {
            Student student = studentRepository.findByStudentId(studentId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [GET-STUDENT-BY-STUDENT-ID] Student not found with student ID: {}", studentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with student ID: " + studentId);
                    });
            log.info("[STUDENT-SERVICE] [GET-STUDENT-BY-STUDENT-ID] Found student: {} (Database ID: {})",
                    student.getFullName(), student.getId());
            StudentDTO result = convertToDTOWithFeeInfo(student);
            log.info("[STUDENT-SERVICE] [GET-STUDENT-BY-STUDENT-ID] Completed successfully with fee info");
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENT-BY-STUDENT-ID] NOT FOUND - Student with student ID {} not found", studentId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENT-BY-STUDENT-ID] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch student: " + e.getMessage(), e);
        }
    }

    /**
     * Get student by email with fee information
     */
    @Transactional(readOnly = true)
    public StudentDTO getStudentByEmail(String email) {
        log.info("[STUDENT-SERVICE] [GET-STUDENT-BY-EMAIL] Started - Email: {}", email);
        try {
            Student student = studentRepository.findByEmailWithRelations(email)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [GET-STUDENT-BY-EMAIL] Student not found with email: {}", email);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with email: " + email);
                    });
            log.info("[STUDENT-SERVICE] [GET-STUDENT-BY-EMAIL] Found student: {} (ID: {})",
                    student.getFullName(), student.getId());
            StudentDTO result = convertToDTOWithFeeInfo(student);
            log.info("[STUDENT-SERVICE] [GET-STUDENT-BY-EMAIL] Completed successfully with fee info");
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENT-BY-EMAIL] NOT FOUND - Student with email {} not found", email);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENT-BY-EMAIL] ERROR for email {}: {}", email, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch student: " + e.getMessage(), e);
        }
    }

    /**
     * Create new student with default fee structure
     */
    @Transactional
    public StudentDTO createStudent(StudentCreateDTO createDTO) {
        log.info("[STUDENT-SERVICE] [CREATE-STUDENT] Started - Creating student: {}", createDTO.getFullName());
        log.debug("[STUDENT-SERVICE] [CREATE-STUDENT] Request DTO: {}", createDTO);
        try {
            if (studentRepository.existsByStudentId(createDTO.getStudentId())) {
                log.warn("[STUDENT-SERVICE] [CREATE-STUDENT] Student ID already exists: {}", createDTO.getStudentId());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Student ID already exists: " + createDTO.getStudentId());
            }
            if (studentRepository.existsByEmail(createDTO.getEmail())) {
                log.warn("[STUDENT-SERVICE] [CREATE-STUDENT] Email already exists: {}", createDTO.getEmail());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Email already exists: " + createDTO.getEmail());
            }
            log.debug("[STUDENT-SERVICE] [CREATE-STUDENT] Validation passed for student ID: {} and email: {}",
                    createDTO.getStudentId(), createDTO.getEmail());

            Student student = convertToEntity(createDTO);
            setDefaultFeeStructure(student, createDTO.getGrade());

            Student savedStudent = studentRepository.save(student);
            log.info("[STUDENT-SERVICE] [CREATE-STUDENT] Student saved to database with ID: {}", savedStudent.getId());

            StudentDTO result = convertToDTOWithFeeInfo(savedStudent);
            log.info("[STUDENT-SERVICE] [CREATE-STUDENT] Completed successfully. Created student: {} (ID: {})",
                    result.getFullName(), result.getId());
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [CREATE-STUDENT] VALIDATION ERROR: {}", e.getReason());
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [CREATE-STUDENT] ERROR - Failed to create student: {}", e.getMessage(), e);
            log.error("[STUDENT-SERVICE] [CREATE-STUDENT] Error details - Student ID: {}, Email: {}",
                    createDTO.getStudentId(), createDTO.getEmail());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create student: " + e.getMessage(), e);
        }
    }

    /**
     * Update student with optional fee updates and related entities
     */
    @Transactional
    public StudentDTO updateStudent(Long id, StudentUpdateDTO updateDTO) {
        log.info("[STUDENT-SERVICE] [UPDATE-STUDENT] Started - Updating student ID: {}", id);
        log.debug("[STUDENT-SERVICE] [UPDATE-STUDENT] Update DTO: {}", updateDTO);
        try {
            Student student = studentRepository.findById(id)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [UPDATE-STUDENT] Student not found with ID: {}", id);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with id: " + id);
                    });
            log.info("[STUDENT-SERVICE] [UPDATE-STUDENT] Found existing student: {} (ID: {})",
                    student.getFullName(), student.getId());

            logFieldsBeingUpdated(student, updateDTO);
            updateStudentFromDTO(student, updateDTO);

            // Fee field updates
            if (updateDTO.getTotalFee() != null) student.setTotalFee(updateDTO.getTotalFee());
            if (updateDTO.getTuitionFee() != null) student.setTuitionFee(updateDTO.getTuitionFee());
            if (updateDTO.getAdmissionFee() != null) student.setAdmissionFee(updateDTO.getAdmissionFee());
            if (updateDTO.getExaminationFee() != null) student.setExaminationFee(updateDTO.getExaminationFee());
            if (updateDTO.getOtherFees() != null) student.setOtherFees(updateDTO.getOtherFees());
            if (updateDTO.getPaidAmount() != null) student.setPaidAmount(updateDTO.getPaidAmount());

            // Recalculate pending & status
            if (student.getTotalFee() != null && student.getPaidAmount() != null) {
                double pending = Math.max(0, student.getTotalFee() - student.getPaidAmount());
                student.setPendingAmount(pending);
                if (student.getPaidAmount() >= student.getTotalFee()) {
                    student.setFeeStatus(Student.FeeStatus.PAID);
                } else if (student.getPaidAmount() > 0) {
                    student.setFeeStatus(Student.FeeStatus.PENDING);
                } else {
                    student.setFeeStatus(Student.FeeStatus.PENDING);
                }
            }

            // ========== SYNC RELATED ENTITIES ==========
            if (updateDTO.getFamilyMembers() != null) {
                syncFamilyMembers(student, updateDTO.getFamilyMembers());
            }

            if (updateDTO.getMedicalRecords() != null) {
                syncMedicalRecords(student, updateDTO.getMedicalRecords());
            }

            if (updateDTO.getAchievements() != null) {
                syncAchievements(student, updateDTO.getAchievements());
            }

            // CRITICAL: Check both interests and clubs/hobbies for backward compatibility
            List<StudentInterestDTO> interestsToSync = new ArrayList<>();

            // First try to get from interests field
            if (updateDTO.getInterests() != null && !updateDTO.getInterests().isEmpty()) {
                interestsToSync.addAll(updateDTO.getInterests());
                log.info("Using interests from DTO interests field: {} items", updateDTO.getInterests().size());
            }
            // If no interests, try to build from clubs and hobbies (for backward compatibility)
            else {
                if (updateDTO.getClubs() != null && !updateDTO.getClubs().isEmpty()) {
                    for (String club : updateDTO.getClubs()) {
                        StudentInterestDTO interestDTO = new StudentInterestDTO();
                        interestDTO.setInterestType(StudentInterest.InterestType.CLUB);
                        interestDTO.setName(club);
                        interestDTO.setId(null); // New interest
                        interestsToSync.add(interestDTO);
                    }
                    log.info("Added {} clubs from clubs field", updateDTO.getClubs().size());
                }

                if (updateDTO.getHobbies() != null && !updateDTO.getHobbies().isEmpty()) {
                    for (String hobby : updateDTO.getHobbies()) {
                        StudentInterestDTO interestDTO = new StudentInterestDTO();
                        interestDTO.setInterestType(StudentInterest.InterestType.HOBBY);
                        interestDTO.setName(hobby);
                        interestDTO.setId(null); // New interest
                        interestsToSync.add(interestDTO);
                    }
                    log.info("Added {} hobbies from hobbies field", updateDTO.getHobbies().size());
                }
            }

            // Sync interests
            if (!interestsToSync.isEmpty()) {
                syncInterests(student, interestsToSync);
            } else {
                // If no interests at all, clear existing ones
                syncInterests(student, new ArrayList<>());
            }

            Student updatedStudent = studentRepository.save(student);
            log.info("[STUDENT-SERVICE] [UPDATE-STUDENT] Student updated in database");

            StudentDTO result = convertToDTOWithFeeInfo(updatedStudent);
            log.info("[STUDENT-SERVICE] [UPDATE-STUDENT] Completed successfully for student: {} (ID: {})",
                    result.getFullName(), result.getId());
            return result;

        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [UPDATE-STUDENT] NOT FOUND - Student with ID {} not found", id);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [UPDATE-STUDENT] ERROR - Failed to update student ID {}: {}",
                    id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update student: " + e.getMessage(), e);
        }
    }

    /**
     * Delete student
     */
    @Transactional
    public void deleteStudent(Long id) {
        log.info("[STUDENT-SERVICE] [DELETE-STUDENT] Started - Deleting student ID: {}", id);
        try {
            if (!studentRepository.existsById(id)) {
                log.warn("[STUDENT-SERVICE] [DELETE-STUDENT] Student not found with ID: {}", id);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Student not found with id: " + id);
            }
            studentRepository.findById(id).ifPresent(student -> {
                log.info("[STUDENT-SERVICE] [DELETE-STUDENT] Deleting student: {} (Student ID: {})",
                        student.getFullName(), student.getStudentId());
            });
            studentRepository.deleteById(id);
            log.info("[STUDENT-SERVICE] [DELETE-STUDENT] Student with ID {} deleted successfully", id);
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [DELETE-STUDENT] NOT FOUND - Student with ID {} not found", id);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [DELETE-STUDENT] ERROR - Failed to delete student ID {}: {}",
                    id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete student: " + e.getMessage(), e);
        }
    }

    /**
     * Search students by name with fee info
     */
    @Transactional(readOnly = true)
    public List<StudentDTO> searchStudentsByName(String name) {
        log.info("[STUDENT-SERVICE] [SEARCH-STUDENTS-BY-NAME] Started - Search term: '{}'", name);
        try {
            long startTime = System.currentTimeMillis();
            List<Student> students = studentRepository.searchByName(name);
            log.info("[STUDENT-SERVICE] [SEARCH-STUDENTS-BY-NAME] Found {} students matching '{}'",
                    students.size(), name);
            if (students.isEmpty()) {
                log.info("[STUDENT-SERVICE] [SEARCH-STUDENTS-BY-NAME] No students found for search term: '{}'", name);
            }
            List<StudentDTO> result = students.stream()
                    .map(this::convertToDTOWithFeeInfo)
                    .collect(Collectors.toList());
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("[STUDENT-SERVICE] [SEARCH-STUDENTS-BY-NAME] Completed in {} ms. Returning {} results with fee info",
                    executionTime, result.size());
            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [SEARCH-STUDENTS-BY-NAME] ERROR for search term '{}': {}",
                    name, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to search students: " + e.getMessage(), e);
        }
    }

    /**
     * Get students by grade with fee info
     */
    @Transactional(readOnly = true)
    public List<StudentDTO> getStudentsByGrade(String grade) {
        log.info("[STUDENT-SERVICE] [GET-STUDENTS-BY-GRADE] Started - Grade: {}", grade);
        try {
            List<Student> students = studentRepository.findByGrade(grade);
            log.info("[STUDENT-SERVICE] [GET-STUDENTS-BY-GRADE] Found {} students in grade: {}",
                    students.size(), grade);
            List<StudentDTO> result = students.stream()
                    .map(this::convertToDTOWithFeeInfo)
                    .collect(Collectors.toList());
            log.info("[STUDENT-SERVICE] [GET-STUDENTS-BY-GRADE] Completed successfully with fee info");
            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENTS-BY-GRADE] ERROR for grade {}: {}", grade, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch students by grade: " + e.getMessage(), e);
        }
    }

    /**
     * Get students by academic year with fee info
     */
    @Transactional(readOnly = true)
    public List<StudentDTO> getStudentsByAcademicYear(String academicYear) {
        log.info("[STUDENT-SERVICE] [GET-STUDENTS-BY-ACADEMIC-YEAR] Started - Academic Year: {}", academicYear);
        try {
            List<Student> students = studentRepository.findByAcademicYear(academicYear);
            log.info("[STUDENT-SERVICE] [GET-STUDENTS-BY-ACADEMIC-YEAR] Found {} students for academic year: {}",
                    students.size(), academicYear);
            List<StudentDTO> result = students.stream()
                    .map(this::convertToDTOWithFeeInfo)
                    .collect(Collectors.toList());
            log.info("[STUDENT-SERVICE] [GET-STUDENTS-BY-ACADEMIC-YEAR] Completed successfully with fee info");
            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENTS-BY-ACADEMIC-YEAR] ERROR for academic year {}: {}",
                    academicYear, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch students by academic year: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate student age
     */
    public Integer calculateStudentAge(String studentId) {
        log.info("[STUDENT-SERVICE] [CALCULATE-STUDENT-AGE] Started - Student ID: {}", studentId);
        try {
            Student student = studentRepository.findByStudentId(studentId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [CALCULATE-STUDENT-AGE] Student not found with student ID: {}", studentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with student ID: " + studentId);
                    });
            LocalDate dob = student.getDateOfBirth();
            Integer age = Period.between(dob, LocalDate.now()).getYears();
            log.info("[STUDENT-SERVICE] [CALCULATE-STUDENT-AGE] Calculated age: {} for student {} (DOB: {})",
                    age, student.getFullName(), dob);
            return age;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [CALCULATE-STUDENT-AGE] NOT FOUND - Student with student ID {} not found", studentId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [CALCULATE-STUDENT-AGE] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to calculate student age: " + e.getMessage(), e);
        }
    }

    // ========== FAMILY MEMBER METHODS ==========

    @Transactional
    public FamilyMemberDTO addFamilyMember(Long studentId, FamilyMemberCreateDTO createDTO) {
        log.info("[STUDENT-SERVICE] [ADD-FAMILY-MEMBER] Started - Student ID: {}", studentId);
        log.debug("[STUDENT-SERVICE] [ADD-FAMILY-MEMBER] Family member DTO: {}", createDTO);
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [ADD-FAMILY-MEMBER] Student not found with ID: {}", studentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with id: " + studentId);
                    });
            log.info("[STUDENT-SERVICE] [ADD-FAMILY-MEMBER] Adding family member for student: {}", student.getFullName());
            FamilyMember familyMember = FamilyMember.builder()
                    .student(student)
                    .relation(createDTO.getRelation())
                    .fullName(createDTO.getFullName())
                    .occupation(createDTO.getOccupation())
                    .phone(createDTO.getPhone())
                    .email(createDTO.getEmail())
                    .isPrimaryContact(createDTO.getIsPrimaryContact())
                    .isEmergencyContact(createDTO.getIsEmergencyContact())
                    .build();
            FamilyMember savedMember = familyMemberRepository.save(familyMember);
            log.info("[STUDENT-SERVICE] [ADD-FAMILY-MEMBER] Family member saved with ID: {}", savedMember.getId());
            FamilyMemberDTO result = convertToFamilyMemberDTO(savedMember);
            log.info("[STUDENT-SERVICE] [ADD-FAMILY-MEMBER] Completed successfully for student ID: {}", studentId);
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [ADD-FAMILY-MEMBER] NOT FOUND - Student with ID {} not found", studentId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [ADD-FAMILY-MEMBER] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to add family member: " + e.getMessage(), e);
        }
    }

    public List<FamilyMemberDTO> getFamilyMembers(Long studentId) {
        log.info("[STUDENT-SERVICE] [GET-FAMILY-MEMBERS] Started - Student ID: {}", studentId);
        try {
            List<FamilyMember> familyMembers = familyMemberRepository.findByStudentId(studentId);
            log.info("[STUDENT-SERVICE] [GET-FAMILY-MEMBERS] Found {} family members for student ID: {}",
                    familyMembers.size(), studentId);
            List<FamilyMemberDTO> result = familyMembers.stream()
                    .map(this::convertToFamilyMemberDTO)
                    .collect(Collectors.toList());
            log.info("[STUDENT-SERVICE] [GET-FAMILY-MEMBERS] Completed successfully");
            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-FAMILY-MEMBERS] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch family members: " + e.getMessage(), e);
        }
    }

    @Transactional
    public FamilyMemberDTO updateFamilyMember(Long familyMemberId, FamilyMemberCreateDTO updateDTO) {
        log.info("[STUDENT-SERVICE] [UPDATE-FAMILY-MEMBER] Started - Family Member ID: {}", familyMemberId);
        log.debug("[STUDENT-SERVICE] [UPDATE-FAMILY-MEMBER] Update DTO: {}", updateDTO);
        try {
            FamilyMember familyMember = familyMemberRepository.findById(familyMemberId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [UPDATE-FAMILY-MEMBER] Family member not found with ID: {}", familyMemberId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Family member not found with id: " + familyMemberId);
                    });
            log.info("[STUDENT-SERVICE] [UPDATE-FAMILY-MEMBER] Found family member: {} for student: {}",
                    familyMember.getFullName(), familyMember.getStudent().getFullName());
            familyMember.setRelation(updateDTO.getRelation());
            familyMember.setFullName(updateDTO.getFullName());
            familyMember.setOccupation(updateDTO.getOccupation());
            familyMember.setPhone(updateDTO.getPhone());
            familyMember.setEmail(updateDTO.getEmail());
            familyMember.setIsPrimaryContact(updateDTO.getIsPrimaryContact());
            familyMember.setIsEmergencyContact(updateDTO.getIsEmergencyContact());
            FamilyMember updatedMember = familyMemberRepository.save(familyMember);
            log.info("[STUDENT-SERVICE] [UPDATE-FAMILY-MEMBER] Family member updated with ID: {}", updatedMember.getId());
            FamilyMemberDTO result = convertToFamilyMemberDTO(updatedMember);
            log.info("[STUDENT-SERVICE] [UPDATE-FAMILY-MEMBER] Completed successfully");
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [UPDATE-FAMILY-MEMBER] NOT FOUND - Family member with ID {} not found", familyMemberId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [UPDATE-FAMILY-MEMBER] ERROR for family member ID {}: {}",
                    familyMemberId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update family member: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteFamilyMember(Long familyMemberId) {
        log.info("[STUDENT-SERVICE] [DELETE-FAMILY-MEMBER] Started - Family Member ID: {}", familyMemberId);
        try {
            if (!familyMemberRepository.existsById(familyMemberId)) {
                log.warn("[STUDENT-SERVICE] [DELETE-FAMILY-MEMBER] Family member not found with ID: {}", familyMemberId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Family member not found with id: " + familyMemberId);
            }
            familyMemberRepository.findById(familyMemberId).ifPresent(familyMember -> {
                log.info("[STUDENT-SERVICE] [DELETE-FAMILY-MEMBER] Deleting family member: {} for student: {}",
                        familyMember.getFullName(), familyMember.getStudent().getFullName());
            });
            familyMemberRepository.deleteById(familyMemberId);
            log.info("[STUDENT-SERVICE] [DELETE-FAMILY-MEMBER] Family member with ID {} deleted successfully", familyMemberId);
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [DELETE-FAMILY-MEMBER] NOT FOUND - Family member with ID {} not found", familyMemberId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [DELETE-FAMILY-MEMBER] ERROR for family member ID {}: {}",
                    familyMemberId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete family member: " + e.getMessage(), e);
        }
    }

    // ========== MEDICAL RECORD METHODS ==========

    @Transactional
    public MedicalRecordDTO addMedicalRecord(Long studentId, MedicalRecordCreateDTO createDTO) {
        log.info("[STUDENT-SERVICE] [ADD-MEDICAL-RECORD] Started - Student ID: {}", studentId);
        log.debug("[STUDENT-SERVICE] [ADD-MEDICAL-RECORD] Medical record DTO: {}", createDTO);
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [ADD-MEDICAL-RECORD] Student not found with ID: {}", studentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with id: " + studentId);
                    });
            log.info("[STUDENT-SERVICE] [ADD-MEDICAL-RECORD] Adding medical record for student: {}", student.getFullName());
            MedicalRecord medicalRecord = MedicalRecord.builder()
                    .student(student)
                    .recordType(createDTO.getRecordType())
                    .name(createDTO.getName())
                    .severity(createDTO.getSeverity())
                    .notes(createDTO.getNotes())
                    .frequency(createDTO.getFrequency())
                    .prescribedBy(createDTO.getPrescribedBy())
                    .build();
            MedicalRecord savedRecord = medicalRecordRepository.save(medicalRecord);
            log.info("[STUDENT-SERVICE] [ADD-MEDICAL-RECORD] Medical record saved with ID: {}", savedRecord.getId());
            MedicalRecordDTO result = convertToMedicalRecordDTO(savedRecord);
            log.info("[STUDENT-SERVICE] [ADD-MEDICAL-RECORD] Completed successfully for student ID: {}", studentId);
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [ADD-MEDICAL-RECORD] NOT FOUND - Student with ID {} not found", studentId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [ADD-MEDICAL-RECORD] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to add medical record: " + e.getMessage(), e);
        }
    }

    public List<MedicalRecordDTO> getMedicalRecords(Long studentId, MedicalRecord.RecordType recordType) {
        log.info("[STUDENT-SERVICE] [GET-MEDICAL-RECORDS] Started - Student ID: {}, Record Type: {}",
                studentId, recordType);
        try {
            List<MedicalRecord> medicalRecords;
            if (recordType != null) {
                medicalRecords = medicalRecordRepository.findByStudentIdAndRecordType(studentId, recordType);
                log.info("[STUDENT-SERVICE] [GET-MEDICAL-RECORDS] Found {} medical records of type {} for student ID: {}",
                        medicalRecords.size(), recordType, studentId);
            } else {
                medicalRecords = medicalRecordRepository.findByStudentId(studentId);
                log.info("[STUDENT-SERVICE] [GET-MEDICAL-RECORDS] Found {} medical records for student ID: {}",
                        medicalRecords.size(), studentId);
            }
            List<MedicalRecordDTO> result = medicalRecords.stream()
                    .map(this::convertToMedicalRecordDTO)
                    .collect(Collectors.toList());
            log.info("[STUDENT-SERVICE] [GET-MEDICAL-RECORDS] Completed successfully");
            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-MEDICAL-RECORDS] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch medical records: " + e.getMessage(), e);
        }
    }

    @Transactional
    public MedicalRecordDTO updateMedicalRecord(Long medicalRecordId, MedicalRecordCreateDTO updateDTO) {
        log.info("[STUDENT-SERVICE] [UPDATE-MEDICAL-RECORD] Started - Medical Record ID: {}", medicalRecordId);
        log.debug("[STUDENT-SERVICE] [UPDATE-MEDICAL-RECORD] Update DTO: {}", updateDTO);
        try {
            MedicalRecord medicalRecord = medicalRecordRepository.findById(medicalRecordId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [UPDATE-MEDICAL-RECORD] Medical record not found with ID: {}", medicalRecordId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Medical record not found with id: " + medicalRecordId);
                    });
            log.info("[STUDENT-SERVICE] [UPDATE-MEDICAL-RECORD] Found medical record: {} for student: {}",
                    medicalRecord.getName(), medicalRecord.getStudent().getFullName());
            medicalRecord.setRecordType(updateDTO.getRecordType());
            medicalRecord.setName(updateDTO.getName());
            medicalRecord.setSeverity(updateDTO.getSeverity());
            medicalRecord.setNotes(updateDTO.getNotes());
            medicalRecord.setFrequency(updateDTO.getFrequency());
            medicalRecord.setPrescribedBy(updateDTO.getPrescribedBy());
            MedicalRecord updatedRecord = medicalRecordRepository.save(medicalRecord);
            log.info("[STUDENT-SERVICE] [UPDATE-MEDICAL-RECORD] Medical record updated with ID: {}", updatedRecord.getId());
            MedicalRecordDTO result = convertToMedicalRecordDTO(updatedRecord);
            log.info("[STUDENT-SERVICE] [UPDATE-MEDICAL-RECORD] Completed successfully");
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [UPDATE-MEDICAL-RECORD] NOT FOUND - Medical record with ID {} not found", medicalRecordId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [UPDATE-MEDICAL-RECORD] ERROR for medical record ID {}: {}",
                    medicalRecordId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update medical record: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteMedicalRecord(Long medicalRecordId) {
        log.info("[STUDENT-SERVICE] [DELETE-MEDICAL-RECORD] Started - Medical Record ID: {}", medicalRecordId);
        try {
            if (!medicalRecordRepository.existsById(medicalRecordId)) {
                log.warn("[STUDENT-SERVICE] [DELETE-MEDICAL-RECORD] Medical record not found with ID: {}", medicalRecordId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Medical record not found with id: " + medicalRecordId);
            }
            medicalRecordRepository.findById(medicalRecordId).ifPresent(record -> {
                log.info("[STUDENT-SERVICE] [DELETE-MEDICAL-RECORD] Deleting medical record: {} for student: {}",
                        record.getName(), record.getStudent().getFullName());
            });
            medicalRecordRepository.deleteById(medicalRecordId);
            log.info("[STUDENT-SERVICE] [DELETE-MEDICAL-RECORD] Medical record with ID {} deleted successfully", medicalRecordId);
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [DELETE-MEDICAL-RECORD] NOT FOUND - Medical record with ID {} not found", medicalRecordId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [DELETE-MEDICAL-RECORD] ERROR for medical record ID {}: {}",
                    medicalRecordId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete medical record: " + e.getMessage(), e);
        }
    }

    // ========== ACHIEVEMENT METHODS ==========

    @Transactional
    public AchievementDTO addAchievement(Long studentId, AchievementCreateDTO createDTO) {
        log.info("[STUDENT-SERVICE] [ADD-ACHIEVEMENT] Started - Student ID: {}", studentId);
        log.debug("[STUDENT-SERVICE] [ADD-ACHIEVEMENT] Achievement DTO: {}", createDTO);
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [ADD-ACHIEVEMENT] Student not found with ID: {}", studentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with id: " + studentId);
                    });
            log.info("[STUDENT-SERVICE] [ADD-ACHIEVEMENT] Adding achievement for student: {}", student.getFullName());
            Achievement achievement = Achievement.builder()
                    .student(student)
                    .title(createDTO.getTitle())
                    .type(createDTO.getType())
                    .level(createDTO.getLevel())
                    .year(createDTO.getYear())
                    .description(createDTO.getDescription())
                    .award(createDTO.getAward())
                    .certificatePath(createDTO.getCertificatePath())
                    .verifiedBy(createDTO.getVerifiedBy())
                    .verifiedAt(createDTO.getVerifiedAt())
                    .build();
            Achievement savedAchievement = achievementRepository.save(achievement);
            log.info("[STUDENT-SERVICE] [ADD-ACHIEVEMENT] Achievement saved with ID: {}", savedAchievement.getId());
            AchievementDTO result = convertToAchievementDTO(savedAchievement);
            log.info("[STUDENT-SERVICE] [ADD-ACHIEVEMENT] Completed successfully for student ID: {}", studentId);
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [ADD-ACHIEVEMENT] NOT FOUND - Student with ID {} not found", studentId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [ADD-ACHIEVEMENT] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to add achievement: " + e.getMessage(), e);
        }
    }

    public List<AchievementDTO> getAchievements(Long studentId, Achievement.AchievementType type) {
        log.info("[STUDENT-SERVICE] [GET-ACHIEVEMENTS] Started - Student ID: {}, Achievement Type: {}",
                studentId, type);
        try {
            List<Achievement> achievements;
            if (type != null) {
                achievements = achievementRepository.findByStudentIdAndType(studentId, type);
                log.info("[STUDENT-SERVICE] [GET-ACHIEVEMENTS] Found {} achievements of type {} for student ID: {}",
                        achievements.size(), type, studentId);
            } else {
                achievements = achievementRepository.findByStudentId(studentId);
                log.info("[STUDENT-SERVICE] [GET-ACHIEVEMENTS] Found {} achievements for student ID: {}",
                        achievements.size(), studentId);
            }
            List<AchievementDTO> result = achievements.stream()
                    .map(this::convertToAchievementDTO)
                    .collect(Collectors.toList());
            log.info("[STUDENT-SERVICE] [GET-ACHIEVEMENTS] Completed successfully");
            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-ACHIEVEMENTS] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch achievements: " + e.getMessage(), e);
        }
    }

    @Transactional
    public AchievementDTO updateAchievement(Long achievementId, AchievementCreateDTO updateDTO) {
        log.info("[STUDENT-SERVICE] [UPDATE-ACHIEVEMENT] Started - Achievement ID: {}", achievementId);
        log.debug("[STUDENT-SERVICE] [UPDATE-ACHIEVEMENT] Update DTO: {}", updateDTO);
        try {
            Achievement achievement = achievementRepository.findById(achievementId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [UPDATE-ACHIEVEMENT] Achievement not found with ID: {}", achievementId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Achievement not found with id: " + achievementId);
                    });
            log.info("[STUDENT-SERVICE] [UPDATE-ACHIEVEMENT] Found achievement: {} for student: {}",
                    achievement.getTitle(), achievement.getStudent().getFullName());
            achievement.setTitle(updateDTO.getTitle());
            achievement.setType(updateDTO.getType());
            achievement.setLevel(updateDTO.getLevel());
            achievement.setYear(updateDTO.getYear());
            achievement.setDescription(updateDTO.getDescription());
            achievement.setAward(updateDTO.getAward());
            achievement.setCertificatePath(updateDTO.getCertificatePath());
            achievement.setVerifiedBy(updateDTO.getVerifiedBy());
            achievement.setVerifiedAt(updateDTO.getVerifiedAt());
            Achievement updatedAchievement = achievementRepository.save(achievement);
            log.info("[STUDENT-SERVICE] [UPDATE-ACHIEVEMENT] Achievement updated with ID: {}", updatedAchievement.getId());
            AchievementDTO result = convertToAchievementDTO(updatedAchievement);
            log.info("[STUDENT-SERVICE] [UPDATE-ACHIEVEMENT] Completed successfully");
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [UPDATE-ACHIEVEMENT] NOT FOUND - Achievement with ID {} not found", achievementId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [UPDATE-ACHIEVEMENT] ERROR for achievement ID {}: {}",
                    achievementId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update achievement: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteAchievement(Long achievementId) {
        log.info("[STUDENT-SERVICE] [DELETE-ACHIEVEMENT] Started - Achievement ID: {}", achievementId);
        try {
            if (!achievementRepository.existsById(achievementId)) {
                log.warn("[STUDENT-SERVICE] [DELETE-ACHIEVEMENT] Achievement not found with ID: {}", achievementId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Achievement not found with id: " + achievementId);
            }
            achievementRepository.findById(achievementId).ifPresent(achievement -> {
                log.info("[STUDENT-SERVICE] [DELETE-ACHIEVEMENT] Deleting achievement: {} for student: {}",
                        achievement.getTitle(), achievement.getStudent().getFullName());
            });
            achievementRepository.deleteById(achievementId);
            log.info("[STUDENT-SERVICE] [DELETE-ACHIEVEMENT] Achievement with ID {} deleted successfully", achievementId);
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [DELETE-ACHIEVEMENT] NOT FOUND - Achievement with ID {} not found", achievementId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [DELETE-ACHIEVEMENT] ERROR for achievement ID {}: {}",
                    achievementId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete achievement: " + e.getMessage(), e);
        }
    }

    @Transactional
    public AchievementDTO verifyAchievement(Long achievementId, String verifiedBy) {
        log.info("[STUDENT-SERVICE] [VERIFY-ACHIEVEMENT] Started - Achievement ID: {}, Verified By: {}",
                achievementId, verifiedBy);
        try {
            Achievement achievement = achievementRepository.findById(achievementId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [VERIFY-ACHIEVEMENT] Achievement not found with ID: {}", achievementId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Achievement not found with id: " + achievementId);
                    });
            log.info("[STUDENT-SERVICE] [VERIFY-ACHIEVEMENT] Found achievement: {} (Currently verified: {})",
                    achievement.getTitle(), achievement.getVerifiedBy() != null);
            achievement.setVerifiedBy(verifiedBy);
            achievement.setVerifiedAt(LocalDateTime.now());
            Achievement verifiedAchievement = achievementRepository.save(achievement);
            log.info("[STUDENT-SERVICE] [VERIFY-ACHIEVEMENT] Achievement verified by: {}", verifiedBy);
            AchievementDTO result = convertToAchievementDTO(verifiedAchievement);
            log.info("[STUDENT-SERVICE] [VERIFY-ACHIEVEMENT] Completed successfully");
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [VERIFY-ACHIEVEMENT] NOT FOUND - Achievement with ID {} not found", achievementId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [VERIFY-ACHIEVEMENT] ERROR for achievement ID {}: {}",
                    achievementId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to verify achievement: " + e.getMessage(), e);
        }
    }

    // ========== INTEREST METHODS ==========

    @Transactional
    public StudentInterestDTO addInterest(Long studentId, StudentInterestCreateDTO createDTO) {
        log.info("[STUDENT-SERVICE] [ADD-INTEREST] Started - Student ID: {}", studentId);
        log.debug("[STUDENT-SERVICE] [ADD-INTEREST] Interest DTO: {}", createDTO);
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [ADD-INTEREST] Student not found with ID: {}", studentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with id: " + studentId);
                    });
            log.info("[STUDENT-SERVICE] [ADD-INTEREST] Adding interest for student: {}", student.getFullName());
            StudentInterest interest = StudentInterest.builder()
                    .student(student)
                    .interestType(createDTO.getInterestType())
                    .name(createDTO.getName())
                    .description(createDTO.getDescription())
                    .build();
            StudentInterest savedInterest = studentInterestRepository.save(interest);
            log.info("[STUDENT-SERVICE] [ADD-INTEREST] Interest saved with ID: {}", savedInterest.getId());
            StudentInterestDTO result = convertToInterestDTO(savedInterest);
            log.info("[STUDENT-SERVICE] [ADD-INTEREST] Completed successfully for student ID: {}", studentId);
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [ADD-INTEREST] NOT FOUND - Student with ID {} not found", studentId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [ADD-INTEREST] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to add interest: " + e.getMessage(), e);
        }
    }

    public List<StudentInterestDTO> getInterests(Long studentId, StudentInterest.InterestType interestType) {
        log.info("[STUDENT-SERVICE] [GET-INTERESTS] Started - Student ID: {}, Interest Type: {}",
                studentId, interestType);
        try {
            List<StudentInterest> interests;
            if (interestType != null) {
                interests = studentInterestRepository.findByStudentIdAndInterestType(studentId, interestType);
                log.info("[STUDENT-SERVICE] [GET-INTERESTS] Found {} interests of type {} for student ID: {}",
                        interests.size(), interestType, studentId);
            } else {
                interests = studentInterestRepository.findByStudentId(studentId);
                log.info("[STUDENT-SERVICE] [GET-INTERESTS] Found {} interests for student ID: {}",
                        interests.size(), studentId);
            }
            List<StudentInterestDTO> result = interests.stream()
                    .map(this::convertToInterestDTO)
                    .collect(Collectors.toList());
            log.info("[STUDENT-SERVICE] [GET-INTERESTS] Completed successfully");
            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-INTERESTS] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch interests: " + e.getMessage(), e);
        }
    }

    @Transactional
    public StudentInterestDTO updateInterest(Long interestId, StudentInterestCreateDTO updateDTO) {
        log.info("[STUDENT-SERVICE] [UPDATE-INTEREST] Started - Interest ID: {}", interestId);
        log.debug("[STUDENT-SERVICE] [UPDATE-INTEREST] Update DTO: {}", updateDTO);
        try {
            StudentInterest interest = studentInterestRepository.findById(interestId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [UPDATE-INTEREST] Interest not found with ID: {}", interestId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Interest not found with id: " + interestId);
                    });
            log.info("[STUDENT-SERVICE] [UPDATE-INTEREST] Found interest: {} for student: {}",
                    interest.getName(), interest.getStudent().getFullName());
            interest.setInterestType(updateDTO.getInterestType());
            interest.setName(updateDTO.getName());
            interest.setDescription(updateDTO.getDescription());
            StudentInterest updatedInterest = studentInterestRepository.save(interest);
            log.info("[STUDENT-SERVICE] [UPDATE-INTEREST] Interest updated with ID: {}", updatedInterest.getId());
            StudentInterestDTO result = convertToInterestDTO(updatedInterest);
            log.info("[STUDENT-SERVICE] [UPDATE-INTEREST] Completed successfully");
            return result;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [UPDATE-INTEREST] NOT FOUND - Interest with ID {} not found", interestId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [UPDATE-INTEREST] ERROR for interest ID {}: {}",
                    interestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update interest: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteInterest(Long interestId) {
        log.info("[STUDENT-SERVICE] [DELETE-INTEREST] Started - Interest ID: {}", interestId);
        try {
            if (!studentInterestRepository.existsById(interestId)) {
                log.warn("[STUDENT-SERVICE] [DELETE-INTEREST] Interest not found with ID: {}", interestId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Interest not found with id: " + interestId);
            }
            studentInterestRepository.findById(interestId).ifPresent(interest -> {
                log.info("[STUDENT-SERVICE] [DELETE-INTEREST] Deleting interest: {} for student: {}",
                        interest.getName(), interest.getStudent().getFullName());
            });
            studentInterestRepository.deleteById(interestId);
            log.info("[STUDENT-SERVICE] [DELETE-INTEREST] Interest with ID {} deleted successfully", interestId);
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [DELETE-INTEREST] NOT FOUND - Interest with ID {} not found", interestId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [DELETE-INTEREST] ERROR for interest ID {}: {}",
                    interestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete interest: " + e.getMessage(), e);
        }
    }

    // ========== FILE UPLOAD METHODS ==========

    @Transactional
    public String uploadProfilePicture(Long studentId, MultipartFile file) throws IOException {
        log.info("[STUDENT-SERVICE] [UPLOAD-PROFILE-PICTURE] Started - Student ID: {}", studentId);
        log.debug("[STUDENT-SERVICE] [UPLOAD-PROFILE-PICTURE] File details - Name: {}, Size: {} bytes, Content Type: {}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [UPLOAD-PROFILE-PICTURE] Student not found with ID: {}", studentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with id: " + studentId);
                    });
            log.info("[STUDENT-SERVICE] [UPLOAD-PROFILE-PICTURE] Found student: {}", student.getFullName());

            FileValidator.validateImageFile(file);
            String publicUrl = fileStorageService.storeProfilePicture(file, studentId);

            String oldProfilePicture = student.getProfilePicture();
            student.setProfilePicture(publicUrl);
            studentRepository.save(student);
            log.info("[STUDENT-SERVICE] [UPLOAD-PROFILE-PICTURE] Profile picture updated for student {} from {} to {}",
                    studentId, oldProfilePicture != null ? oldProfilePicture : "null", publicUrl);
            log.info("[STUDENT-SERVICE] [UPLOAD-PROFILE-PICTURE] Completed successfully");
            return publicUrl;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [UPLOAD-PROFILE-PICTURE] NOT FOUND - Student with ID {} not found", studentId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [UPLOAD-PROFILE-PICTURE] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            log.error("[STUDENT-SERVICE] [UPLOAD-PROFILE-PICTURE] File details - Name: {}, Size: {}",
                    file.getOriginalFilename(), file.getSize());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload profile picture: " + e.getMessage(), e);
        }
    }

    @Transactional
    public String uploadAchievementCertificate(Long achievementId, MultipartFile file) throws IOException {
        log.info("[STUDENT-SERVICE] [UPLOAD-ACHIEVEMENT-CERTIFICATE] Started - Achievement ID: {}", achievementId);
        log.debug("[STUDENT-SERVICE] [UPLOAD-ACHIEVEMENT-CERTIFICATE] File details - Name: {}, Size: {} bytes, Content Type: {}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
        try {
            Achievement achievement = achievementRepository.findById(achievementId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [UPLOAD-ACHIEVEMENT-CERTIFICATE] Achievement not found with ID: {}", achievementId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Achievement not found with id: " + achievementId);
                    });
            log.info("[STUDENT-SERVICE] [UPLOAD-ACHIEVEMENT-CERTIFICATE] Found achievement: {} for student: {}",
                    achievement.getTitle(), achievement.getStudent().getFullName());

            FileValidator.validateDocumentFile(file);
            String publicUrl = fileStorageService.storeCertificate(file, achievementId);

            String oldCertificatePath = achievement.getCertificatePath();
            achievement.setCertificatePath(publicUrl);
            achievementRepository.save(achievement);
            log.info("[STUDENT-SERVICE] [UPLOAD-ACHIEVEMENT-CERTIFICATE] Certificate updated for achievement {} from {} to {}",
                    achievementId, oldCertificatePath != null ? oldCertificatePath : "null", publicUrl);
            log.info("[STUDENT-SERVICE] [UPLOAD-ACHIEVEMENT-CERTIFICATE] Completed successfully");
            return publicUrl;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [UPLOAD-ACHIEVEMENT-CERTIFICATE] NOT FOUND - Achievement with ID {} not found", achievementId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [UPLOAD-ACHIEVEMENT-CERTIFICATE] ERROR for achievement ID {}: {}",
                    achievementId, e.getMessage(), e);
            log.error("[STUDENT-SERVICE] [UPLOAD-ACHIEVEMENT-CERTIFICATE] File details - Name: {}, Size: {}",
                    file.getOriginalFilename(), file.getSize());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload certificate: " + e.getMessage(), e);
        }
    }

    public Resource getProfilePictureResource(Long studentId) throws IOException {
        log.info("[STUDENT-SERVICE] [GET-PROFILE-PICTURE-RESOURCE] Started - Student ID: {}", studentId);
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [GET-PROFILE-PICTURE-RESOURCE] Student not found with ID: {}", studentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with id: " + studentId);
                    });
            if (student.getProfilePicture() == null || student.getProfilePicture().isEmpty()) {
                log.warn("[STUDENT-SERVICE] [GET-PROFILE-PICTURE-RESOURCE] Profile picture not found for student: {}", studentId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Profile picture not found for student: " + studentId);
            }

            String profilePictureUrl = student.getProfilePicture();
            String filename = profilePictureUrl.substring(profilePictureUrl.lastIndexOf("/") + 1);
            Path filePath = fileStorageService.getUploadPath()
                    .resolve("profiles")
                    .resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                log.error("[STUDENT-SERVICE] [GET-PROFILE-PICTURE-RESOURCE] File not found at path: {}", filePath);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Profile picture file not found: " + filename);
            }
            if (!resource.isReadable()) {
                log.error("[STUDENT-SERVICE] [GET-PROFILE-PICTURE-RESOURCE] File not readable: {}", filePath);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Profile picture file not readable: " + filename);
            }
            log.info("[STUDENT-SERVICE] [GET-PROFILE-PICTURE-RESOURCE] Retrieved profile picture for student: {}", studentId);
            return resource;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [GET-PROFILE-PICTURE-RESOURCE] NOT FOUND - Resource not found for student ID {}: {}",
                    studentId, e.getReason());
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-PROFILE-PICTURE-RESOURCE] ERROR for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve profile picture: " + e.getMessage(), e);
        }
    }

    // ========== SYNC METHODS FOR RELATED ENTITIES ==========

    /**
     * Sync family members from DTO to student entity
     */
    private void syncFamilyMembers(Student student, List<FamilyMemberDTO> familyMemberDTOs) {
        log.debug("[STUDENT-SERVICE] [SYNC-FAMILY-MEMBERS] Syncing family members for student ID: {}", student.getId());

        if (familyMemberDTOs == null) return;

        try {
            Set<FamilyMember> existingMembers = student.getFamilyMembers();
            if (existingMembers == null) {
                existingMembers = new HashSet<>();
                student.setFamilyMembers(existingMembers);
            }

            Map<Long, FamilyMember> existingMap = existingMembers.stream()
                    .filter(m -> m.getId() != null)
                    .collect(Collectors.toMap(FamilyMember::getId, m -> m));

            Set<Long> seenIds = new HashSet<>();

            for (FamilyMemberDTO dto : familyMemberDTOs) {
                if (dto.getId() != null && existingMap.containsKey(dto.getId())) {
                    // Update existing family member
                    FamilyMember member = existingMap.get(dto.getId());
                    member.setRelation(dto.getRelation());
                    member.setFullName(dto.getFullName());
                    member.setOccupation(dto.getOccupation());
                    member.setPhone(dto.getPhone());
                    member.setEmail(dto.getEmail());
                    member.setIsPrimaryContact(dto.getIsPrimaryContact());
                    member.setIsEmergencyContact(dto.getIsEmergencyContact());
                    seenIds.add(dto.getId());
                    log.trace("[STUDENT-SERVICE] [SYNC-FAMILY-MEMBERS] Updated family member ID: {}", dto.getId());
                } else {
                    // Create new family member
                    FamilyMember newMember = FamilyMember.builder()
                            .student(student)
                            .relation(dto.getRelation())
                            .fullName(dto.getFullName())
                            .occupation(dto.getOccupation())
                            .phone(dto.getPhone())
                            .email(dto.getEmail())
                            .isPrimaryContact(dto.getIsPrimaryContact())
                            .isEmergencyContact(dto.getIsEmergencyContact())
                            .build();
                    existingMembers.add(newMember);
                    log.trace("[STUDENT-SERVICE] [SYNC-FAMILY-MEMBERS] Added new family member: {}", dto.getFullName());
                }
            }

            // Remove family members that are no longer present
            existingMembers.removeIf(member -> member.getId() != null && !seenIds.contains(member.getId()));

            log.info("[STUDENT-SERVICE] [SYNC-FAMILY-MEMBERS] Synced {} family members for student ID: {}",
                    existingMembers.size(), student.getId());

        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [SYNC-FAMILY-MEMBERS] Error: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to sync family members: " + e.getMessage(), e);
        }
    }

    /**
     * Sync medical records from DTO to student entity
     */
    private void syncMedicalRecords(Student student, List<MedicalRecordDTO> medicalRecordDTOs) {
        log.debug("[STUDENT-SERVICE] [SYNC-MEDICAL-RECORDS] Syncing medical records for student ID: {}", student.getId());

        if (medicalRecordDTOs == null) return;

        try {
            Set<MedicalRecord> existingRecords = student.getMedicalRecords();
            if (existingRecords == null) {
                existingRecords = new HashSet<>();
                student.setMedicalRecords(existingRecords);
            }

            Map<Long, MedicalRecord> existingMap = existingRecords.stream()
                    .filter(r -> r.getId() != null)
                    .collect(Collectors.toMap(MedicalRecord::getId, r -> r));

            Set<Long> seenIds = new HashSet<>();

            for (MedicalRecordDTO dto : medicalRecordDTOs) {
                if (dto.getId() != null && existingMap.containsKey(dto.getId())) {
                    // Update existing medical record
                    MedicalRecord record = existingMap.get(dto.getId());
                    record.setRecordType(dto.getRecordType());
                    record.setName(dto.getName());
                    record.setSeverity(dto.getSeverity());
                    record.setNotes(dto.getNotes());
                    record.setFrequency(dto.getFrequency());
                    record.setPrescribedBy(dto.getPrescribedBy());
                    seenIds.add(dto.getId());
                    log.trace("[STUDENT-SERVICE] [SYNC-MEDICAL-RECORDS] Updated medical record ID: {}", dto.getId());
                } else {
                    // Create new medical record
                    MedicalRecord newRecord = MedicalRecord.builder()
                            .student(student)
                            .recordType(dto.getRecordType())
                            .name(dto.getName())
                            .severity(dto.getSeverity())
                            .notes(dto.getNotes())
                            .frequency(dto.getFrequency())
                            .prescribedBy(dto.getPrescribedBy())
                            .build();
                    existingRecords.add(newRecord);
                    log.trace("[STUDENT-SERVICE] [SYNC-MEDICAL-RECORDS] Added new medical record: {}", dto.getName());
                }
            }

            // Remove medical records that are no longer present
            existingRecords.removeIf(record -> record.getId() != null && !seenIds.contains(record.getId()));

            log.info("[STUDENT-SERVICE] [SYNC-MEDICAL-RECORDS] Synced {} medical records for student ID: {}",
                    existingRecords.size(), student.getId());

        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [SYNC-MEDICAL-RECORDS] Error: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to sync medical records: " + e.getMessage(), e);
        }
    }

    /**
     * Sync achievements from DTO to student entity
     */
    private void syncAchievements(Student student, List<AchievementDTO> achievementDTOs) {
        log.debug("[STUDENT-SERVICE] [SYNC-ACHIEVEMENTS] Syncing achievements for student ID: {}", student.getId());

        if (achievementDTOs == null) return;

        try {
            Set<Achievement> existingAchievements = student.getAchievements();
            if (existingAchievements == null) {
                existingAchievements = new HashSet<>();
                student.setAchievements(existingAchievements);
            }

            Map<Long, Achievement> existingMap = existingAchievements.stream()
                    .filter(a -> a.getId() != null)
                    .collect(Collectors.toMap(Achievement::getId, a -> a));

            Set<Long> seenIds = new HashSet<>();

            for (AchievementDTO dto : achievementDTOs) {
                if (dto.getId() != null && existingMap.containsKey(dto.getId())) {
                    // Update existing achievement
                    Achievement achievement = existingMap.get(dto.getId());
                    achievement.setTitle(dto.getTitle());
                    achievement.setType(dto.getType());
                    achievement.setLevel(dto.getLevel());
                    achievement.setYear(dto.getYear());
                    achievement.setDescription(dto.getDescription());
                    achievement.setAward(dto.getAward());
                    achievement.setCertificatePath(dto.getCertificatePath());
                    seenIds.add(dto.getId());
                    log.trace("[STUDENT-SERVICE] [SYNC-ACHIEVEMENTS] Updated achievement ID: {}", dto.getId());
                } else {
                    // Create new achievement
                    Achievement newAchievement = Achievement.builder()
                            .student(student)
                            .title(dto.getTitle())
                            .type(dto.getType())
                            .level(dto.getLevel())
                            .year(dto.getYear())
                            .description(dto.getDescription())
                            .award(dto.getAward())
                            .certificatePath(dto.getCertificatePath())
                            .build();
                    existingAchievements.add(newAchievement);
                    log.trace("[STUDENT-SERVICE] [SYNC-ACHIEVEMENTS] Added new achievement: {}", dto.getTitle());
                }
            }

            // Remove achievements that are no longer present
            existingAchievements.removeIf(achievement ->
                    achievement.getId() != null && !seenIds.contains(achievement.getId()));

            log.info("[STUDENT-SERVICE] [SYNC-ACHIEVEMENTS] Synced {} achievements for student ID: {}",
                    existingAchievements.size(), student.getId());

        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [SYNC-ACHIEVEMENTS] Error: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to sync achievements: " + e.getMessage(), e);
        }
    }

    /**
     * Sync interests (clubs/hobbies) from DTO to student entity
     */
    @Transactional
    private void syncInterests(Student student, List<StudentInterestDTO> interestDTOs) {
        log.info("========== SYNC INTERESTS START ==========");
        log.info("Student ID: {}", student.getId());
        log.info("Received {} interests", interestDTOs != null ? interestDTOs.size() : 0);

        if (interestDTOs == null || interestDTOs.isEmpty()) {
            // If no interests in DTO, delete all existing interests
            Set<StudentInterest> existingInterests = student.getInterests();
            if (existingInterests != null && !existingInterests.isEmpty()) {
                studentInterestRepository.deleteAll(existingInterests);
                existingInterests.clear();
                log.info("Deleted all existing interests - no interests in DTO");
            }
            log.info("========== SYNC INTERESTS END (no interests) ==========");
            return;
        }

        // Log each incoming interest
        for (int i = 0; i < interestDTOs.size(); i++) {
            StudentInterestDTO dto = interestDTOs.get(i);
            log.info("INCOMING [{}] - Type: {}, Name: {}, ID: {}",
                    i, dto.getInterestType(), dto.getName(), dto.getId());
        }

        // Get existing interests
        Set<StudentInterest> existingInterests = student.getInterests();
        if (existingInterests == null) {
            existingInterests = new HashSet<>();
            student.setInterests(existingInterests);
        } else {
            // Log existing interests before deletion
            log.info("Existing interests count before sync: {}", existingInterests.size());
            for (StudentInterest interest : existingInterests) {
                log.info("EXISTING - ID: {}, Type: {}, Name: {}",
                        interest.getId(), interest.getInterestType(), interest.getName());
            }

            // Delete all existing interests from database
            if (!existingInterests.isEmpty()) {
                studentInterestRepository.deleteAll(existingInterests);
                existingInterests.clear();
                log.info("Deleted all existing interests");
            }
        }

        // Use a List instead of Set temporarily to preserve all items
        List<StudentInterest> interestsToAdd = new ArrayList<>();

        // Create ALL new interests from the DTOs
        for (StudentInterestDTO dto : interestDTOs) {
            StudentInterest newInterest = StudentInterest.builder()
                    .student(student)
                    .interestType(dto.getInterestType())
                    .name(dto.getName())
                    .description(dto.getDescription() != null ? dto.getDescription() : "")
                    .build();

            // Save to get ID
            StudentInterest savedInterest = studentInterestRepository.save(newInterest);
            interestsToAdd.add(savedInterest);

            log.info("SAVED - Type: {}, Name: {}, New ID: {}",
                    dto.getInterestType(), dto.getName(), savedInterest.getId());
        }

        // Now add all saved interests to the Set
        // With our new equals/hashCode based on type+name, duplicates won't be added
        for (StudentInterest interest : interestsToAdd) {
            boolean added = existingInterests.add(interest);
            if (added) {
                log.info("ADDED to Set - ID: {}, Type: {}, Name: {}",
                        interest.getId(), interest.getInterestType(), interest.getName());
            } else {
                log.warn("DUPLICATE not added to Set - ID: {}, Type: {}, Name: {}",
                        interest.getId(), interest.getInterestType(), interest.getName());
            }
        }

        log.info("Final interests after sync: {}", existingInterests.size());
        for (StudentInterest interest : existingInterests) {
            log.info("FINAL - ID: {}, Type: {}, Name: {}",
                    interest.getId(), interest.getInterestType(), interest.getName());
        }

        log.info("========== SYNC INTERESTS END ==========");
    }

    // ========== FEE-SPECIFIC METHODS ==========

    @Transactional
    public void updateStudentPayment(Long studentId, Double amount) {
        log.info("[STUDENT-SERVICE] [UPDATE-STUDENT-PAYMENT] Updating payment for student ID: {}, amount: {}", studentId, amount);
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [UPDATE-STUDENT-PAYMENT] Student not found with ID: {}", studentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with id: " + studentId);
                    });
            Double currentPaid = student.getPaidAmount() != null ? student.getPaidAmount() : 0.0;
            Double newPaidAmount = currentPaid + amount;
            student.setPaidAmount(newPaidAmount);

            if (student.getTotalFee() != null) {
                student.setPendingAmount(Math.max(0, student.getTotalFee() - newPaidAmount));
                if (newPaidAmount >= student.getTotalFee()) {
                    student.setFeeStatus(Student.FeeStatus.PAID);
                } else if (newPaidAmount > 0) {
                    student.setFeeStatus(Student.FeeStatus.PENDING);
                } else {
                    student.setFeeStatus(Student.FeeStatus.PENDING);
                }
            }
            studentRepository.save(student);
            log.info("[STUDENT-SERVICE] [UPDATE-STUDENT-PAYMENT] Updated payment for student ID: {}. New paid: {}, Status: {}",
                    studentId, newPaidAmount, student.getFeeStatus());
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [UPDATE-STUDENT-PAYMENT] NOT FOUND - Student with ID {} not found", studentId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [UPDATE-STUDENT-PAYMENT] ERROR updating payment for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update student payment: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public StudentFeeSummaryDTO getStudentFeeSummary(Long studentId) {
        log.info("[STUDENT-SERVICE] [GET-STUDENT-FEE-SUMMARY] Getting fee summary for student ID: {}", studentId);
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> {
                        log.warn("[STUDENT-SERVICE] [GET-STUDENT-FEE-SUMMARY] Student not found with ID: {}", studentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Student not found with id: " + studentId);
                    });
            StudentFeeSummaryDTO summary = new StudentFeeSummaryDTO();
            summary.setStudentId(student.getId());
            summary.setStudentName(student.getFullName());
            summary.setGrade(student.getGrade());
            summary.setTotalFee(student.getTotalFee() != null ? student.getTotalFee() : 45000.0);
            summary.setPaidAmount(student.getPaidAmount() != null ? student.getPaidAmount() : 0.0);
            summary.setPendingAmount(summary.getTotalFee() - summary.getPaidAmount());
            summary.setFeeStatus(student.getFeeStatus() != null ? student.getFeeStatus() : Student.FeeStatus.PENDING);

            List<PaymentTransaction> transactions = paymentTransactionRepository
                    .findByStudentIdAndIsVerifiedTrue(studentId);
            summary.setTransactionCount(transactions.size());
            summary.setLastPaymentDate(transactions.isEmpty() ? null : transactions.get(0).getPaymentDate());

            log.info("[STUDENT-SERVICE] [GET-STUDENT-FEE-SUMMARY] Fee summary for student ID {}: Total: {}, Paid: {}, Pending: {}",
                    studentId, summary.getTotalFee(), summary.getPaidAmount(), summary.getPendingAmount());
            return summary;
        } catch (ResponseStatusException e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENT-FEE-SUMMARY] NOT FOUND - Student with ID {} not found", studentId);
            throw e;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENT-FEE-SUMMARY] ERROR getting fee summary for student ID {}: {}",
                    studentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to get fee summary: " + e.getMessage(), e);
        }
    }

    // ========== HELPER METHODS ==========

    private void logFieldsBeingUpdated(Student student, StudentUpdateDTO updateDTO) {
        StringBuilder changes = new StringBuilder();
        changes.append("[STUDENT-SERVICE] [UPDATE-STUDENT] Field changes for student ").append(student.getId()).append(":\n");
        boolean hasChanges = false;
        if (updateDTO.getFullName() != null && !updateDTO.getFullName().equals(student.getFullName())) {
            changes.append("  FullName: ").append(student.getFullName()).append(" -> ").append(updateDTO.getFullName()).append("\n");
            hasChanges = true;
        }
        if (updateDTO.getEmail() != null && !updateDTO.getEmail().equals(student.getEmail())) {
            changes.append("  Email: ").append(student.getEmail()).append(" -> ").append(updateDTO.getEmail()).append("\n");
            hasChanges = true;
        }
        if (updateDTO.getPhone() != null && !updateDTO.getPhone().equals(student.getPhone())) {
            changes.append("  Phone: ").append(student.getPhone()).append(" -> ").append(updateDTO.getPhone()).append("\n");
            hasChanges = true;
        }
        if (updateDTO.getGrade() != null && !updateDTO.getGrade().equals(student.getGrade())) {
            changes.append("  Grade: ").append(student.getGrade()).append(" -> ").append(updateDTO.getGrade()).append("\n");
            hasChanges = true;
        }
        if (updateDTO.getAddress() != null && !updateDTO.getAddress().equals(student.getAddress())) {
            changes.append("  Address: ").append(student.getAddress()).append(" -> ").append(updateDTO.getAddress()).append("\n");
            hasChanges = true;
        }
        if (hasChanges) {
            log.info(changes.toString());
        } else {
            log.debug("[STUDENT-SERVICE] [UPDATE-STUDENT] No significant field changes detected");
        }
    }

    // ========== DTO CONVERSION METHODS ==========

    private StudentDTO convertToDTOWithFeeInfo(Student student) {
        log.debug("[STUDENT-SERVICE] [CONVERT-TO-DTO-WITH-FEE-INFO] Converting student ID: {}", student.getId());
        try {
            StudentDTO dto = convertToDTO(student);
            enrichWithFeeInfo(dto, student.getId());
            log.debug("[STUDENT-SERVICE] [CONVERT-TO-DTO-WITH-FEE-INFO] Completed for student ID: {}", student.getId());
            return dto;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [CONVERT-TO-DTO-WITH-FEE-INFO] ERROR for student ID {}: {}",
                    student.getId(), e.getMessage(), e);
            return convertToDTO(student); // fallback
        }
    }

    private void enrichWithFeeInfo(StudentDTO dto, Long studentId) {
        try {
            List<PaymentTransaction> transactions = paymentTransactionRepository
                    .findByStudentIdAndIsVerifiedTrue(studentId);
            Double totalPaid = transactions.stream()
                    .mapToDouble(t -> t.getTotalPaid() != null ? t.getTotalPaid() : t.getAmount())
                    .sum();
            dto.setPaidAmount(totalPaid);

            Student student = studentRepository.findById(studentId).orElse(null);
            if (student != null) {
                if (student.getTotalFee() != null) {
                    dto.setTotalFee(student.getTotalFee());
                    dto.setTuitionFee(student.getTuitionFee());
                    dto.setAdmissionFee(student.getAdmissionFee());
                    dto.setExaminationFee(student.getExaminationFee());
                    dto.setOtherFees(student.getOtherFees());
                } else {
                    setDefaultFeeStructure(dto, student.getGrade());
                }
                if (dto.getTotalFee() != null) {
                    dto.setPendingAmount(Math.max(0, dto.getTotalFee() - totalPaid));
                    if (totalPaid >= dto.getTotalFee()) {
                        dto.setFeeStatus(Student.FeeStatus.PAID);
                    } else if (totalPaid > 0) {
                        dto.setFeeStatus(Student.FeeStatus.PENDING);
                    } else {
                        dto.setFeeStatus(Student.FeeStatus.PENDING);
                    }
                }
                log.debug("[STUDENT-SERVICE] [ENRICH-WITH-FEE-INFO] Student ID: {}, Total Fee: {}, Paid: {}, Pending: {}, Status: {}",
                        studentId, dto.getTotalFee(), totalPaid, dto.getPendingAmount(), dto.getFeeStatus());
            }
        } catch (Exception e) {
            log.warn("[STUDENT-SERVICE] [ENRICH-WITH-FEE-INFO] Could not fetch fee info for student {}: {}",
                    studentId, e.getMessage());
            setDefaultFeeStructure(dto, dto.getGrade());
            dto.setPaidAmount(0.0);
            dto.setPendingAmount(dto.getTotalFee());
            dto.setFeeStatus(Student.FeeStatus.PENDING);
        }
    }

    private void setDefaultFeeStructure(StudentDTO dto, String grade) {
        if (grade != null) {
            if (grade.contains("10") || grade.contains("11") || grade.contains("12")) {
                dto.setTotalFee(50000.0);
                dto.setTuitionFee(35000.0);
                dto.setExaminationFee(5000.0);
                dto.setAdmissionFee(10000.0);
                dto.setOtherFees(0.0);
            } else if (grade.contains("8") || grade.contains("9")) {
                dto.setTotalFee(40000.0);
                dto.setTuitionFee(28000.0);
                dto.setExaminationFee(4000.0);
                dto.setAdmissionFee(8000.0);
                dto.setOtherFees(0.0);
            } else if (grade.contains("6") || grade.contains("7")) {
                dto.setTotalFee(35000.0);
                dto.setTuitionFee(25000.0);
                dto.setExaminationFee(3000.0);
                dto.setAdmissionFee(7000.0);
                dto.setOtherFees(0.0);
            } else {
                dto.setTotalFee(30000.0);
                dto.setTuitionFee(20000.0);
                dto.setExaminationFee(3000.0);
                dto.setAdmissionFee(7000.0);
                dto.setOtherFees(0.0);
            }
        } else {
            dto.setTotalFee(45000.0);
            dto.setTuitionFee(30000.0);
            dto.setExaminationFee(5000.0);
            dto.setAdmissionFee(10000.0);
            dto.setOtherFees(0.0);
        }
    }

    private void setDefaultFeeStructure(Student student, String grade) {
        if (grade != null) {
            if (grade.contains("10") || grade.contains("11") || grade.contains("12")) {
                student.setTotalFee(50000.0);
                student.setTuitionFee(35000.0);
                student.setExaminationFee(5000.0);
                student.setAdmissionFee(10000.0);
                student.setOtherFees(0.0);
            } else if (grade.contains("8") || grade.contains("9")) {
                student.setTotalFee(40000.0);
                student.setTuitionFee(28000.0);
                student.setExaminationFee(4000.0);
                student.setAdmissionFee(8000.0);
                student.setOtherFees(0.0);
            } else if (grade.contains("6") || grade.contains("7")) {
                student.setTotalFee(35000.0);
                student.setTuitionFee(25000.0);
                student.setExaminationFee(3000.0);
                student.setAdmissionFee(7000.0);
                student.setOtherFees(0.0);
            } else {
                student.setTotalFee(30000.0);
                student.setTuitionFee(20000.0);
                student.setExaminationFee(3000.0);
                student.setAdmissionFee(7000.0);
                student.setOtherFees(0.0);
            }
        } else {
            student.setTotalFee(45000.0);
            student.setTuitionFee(30000.0);
            student.setExaminationFee(5000.0);
            student.setAdmissionFee(10000.0);
            student.setOtherFees(0.0);
        }
        student.setFeeStatus(Student.FeeStatus.PENDING);
        student.setPaidAmount(0.0);
        student.setPendingAmount(student.getTotalFee());
    }

    private StudentDTO convertToDTO(Student student) {
        log.debug("[STUDENT-SERVICE] [CONVERT-TO-DTO] Converting student entity to DTO - ID: {}", student.getId());
        try {
            StudentDTO dto = new StudentDTO();
            dto.setId(student.getId());
            dto.setStudentId(student.getStudentId());
            dto.setFullName(student.getFullName());
            dto.setDateOfBirth(student.getDateOfBirth());
            dto.setGender(student.getGender());
            dto.setBloodGroup(student.getBloodGroup());
            dto.setNationality(student.getNationality());
            dto.setReligion(student.getReligion());
            dto.setCategory(student.getCategory());
            dto.setProfilePicture(student.getProfilePicture());
            dto.setAdmissionDate(student.getAdmissionDate());
            dto.setAcademicYear(student.getAcademicYear());
            dto.setGrade(student.getGrade());
            dto.setRollNumber(student.getRollNumber());
            dto.setClassTeacher(student.getClassTeacher());
            dto.setHouse(student.getHouse());
            dto.setAddress(student.getAddress());
            dto.setPhone(student.getPhone());
            dto.setEmail(student.getEmail());
            dto.setEmergencyContactName(student.getEmergencyContactName());
            dto.setEmergencyContactPhone(student.getEmergencyContactPhone());
            dto.setEmergencyRelation(student.getEmergencyRelation());
            dto.setHeight(student.getHeight());
            dto.setWeight(student.getWeight());
            dto.setBloodPressure(student.getBloodPressure());
            dto.setLastMedicalCheckup(student.getLastMedicalCheckup());
            dto.setDoctorName(student.getDoctorName());
            dto.setClinicName(student.getClinicName());
            dto.setTransportMode(student.getTransportMode());
            dto.setBusRoute(student.getBusRoute());
            dto.setBusStop(student.getBusStop());
            dto.setBusNumber(student.getBusNumber());
            dto.setDriverName(student.getDriverName());
            dto.setDriverContact(student.getDriverContact());
            dto.setPickupTime(student.getPickupTime());
            dto.setDropTime(student.getDropTime());
            dto.setTransportFee(student.getTransportFee());
            dto.setTransportFeeStatus(student.getTransportFeeStatus());
            dto.setTotalFee(student.getTotalFee());
            dto.setTuitionFee(student.getTuitionFee());
            dto.setAdmissionFee(student.getAdmissionFee());
            dto.setExaminationFee(student.getExaminationFee());
            dto.setOtherFees(student.getOtherFees());
            dto.setFeeStatus(student.getFeeStatus());

            // Convert collections
            if (student.getFamilyMembers() != null && !student.getFamilyMembers().isEmpty()) {
                List<FamilyMemberDTO> familyMemberDTOs = student.getFamilyMembers().stream()
                        .map(this::convertToFamilyMemberDTO)
                        .collect(Collectors.toList());
                dto.setFamilyMembers(familyMemberDTOs);
            }

            if (student.getMedicalRecords() != null && !student.getMedicalRecords().isEmpty()) {
                List<MedicalRecordDTO> medicalRecordDTOs = student.getMedicalRecords().stream()
                        .map(this::convertToMedicalRecordDTO)
                        .collect(Collectors.toList());
                dto.setMedicalRecords(medicalRecordDTOs);
            }

            if (student.getAchievements() != null && !student.getAchievements().isEmpty()) {
                List<AchievementDTO> achievementDTOs = student.getAchievements().stream()
                        .map(this::convertToAchievementDTO)
                        .collect(Collectors.toList());
                dto.setAchievements(achievementDTOs);
            }

            if (student.getInterests() != null && !student.getInterests().isEmpty()) {
                List<StudentInterestDTO> interestDTOs = student.getInterests().stream()
                        .map(this::convertToInterestDTO)
                        .collect(Collectors.toList());
                dto.setInterests(interestDTOs);

                // For backward compatibility, also set clubs and hobbies
                dto.setClubs(interestDTOs.stream()
                        .filter(i -> i.getInterestType() == StudentInterest.InterestType.CLUB)
                        .map(StudentInterestDTO::getName)
                        .collect(Collectors.toList()));
                dto.setHobbies(interestDTOs.stream()
                        .filter(i -> i.getInterestType() == StudentInterest.InterestType.HOBBY)
                        .map(StudentInterestDTO::getName)
                        .collect(Collectors.toList()));
            }

            log.debug("[STUDENT-SERVICE] [CONVERT-TO-DTO] Conversion completed for student: {}", student.getFullName());
            return dto;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [CONVERT-TO-DTO] ERROR converting student ID {}: {}",
                    student.getId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to convert student to DTO: " + e.getMessage(), e);
        }
    }

    private FamilyMemberDTO convertToFamilyMemberDTO(FamilyMember familyMember) {
        log.trace("[STUDENT-SERVICE] [CONVERT-FAMILY-MEMBER-TO-DTO] Converting family member ID: {}", familyMember.getId());
        try {
            FamilyMemberDTO dto = new FamilyMemberDTO();
            dto.setId(familyMember.getId());
            dto.setRelation(familyMember.getRelation());
            dto.setFullName(familyMember.getFullName());
            dto.setOccupation(familyMember.getOccupation());
            dto.setPhone(familyMember.getPhone());
            dto.setEmail(familyMember.getEmail());
            dto.setIsPrimaryContact(familyMember.getIsPrimaryContact());
            dto.setIsEmergencyContact(familyMember.getIsEmergencyContact());
            return dto;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [CONVERT-FAMILY-MEMBER-TO-DTO] ERROR converting family member ID {}: {}",
                    familyMember.getId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to convert family member to DTO", e);
        }
    }

    private MedicalRecordDTO convertToMedicalRecordDTO(MedicalRecord medicalRecord) {
        log.trace("[STUDENT-SERVICE] [CONVERT-MEDICAL-RECORD-TO-DTO] Converting medical record ID: {}", medicalRecord.getId());
        try {
            MedicalRecordDTO dto = new MedicalRecordDTO();
            dto.setId(medicalRecord.getId());
            dto.setRecordType(medicalRecord.getRecordType());
            dto.setName(medicalRecord.getName());
            dto.setSeverity(medicalRecord.getSeverity());
            dto.setNotes(medicalRecord.getNotes());
            dto.setFrequency(medicalRecord.getFrequency());
            dto.setPrescribedBy(medicalRecord.getPrescribedBy());
            return dto;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [CONVERT-MEDICAL-RECORD-TO-DTO] ERROR converting medical record ID {}: {}",
                    medicalRecord.getId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to convert medical record to DTO", e);
        }
    }

    private AchievementDTO convertToAchievementDTO(Achievement achievement) {
        log.trace("[STUDENT-SERVICE] [CONVERT-ACHIEVEMENT-TO-DTO] Converting achievement ID: {}", achievement.getId());
        try {
            AchievementDTO dto = new AchievementDTO();
            dto.setId(achievement.getId());
            dto.setTitle(achievement.getTitle());
            dto.setType(achievement.getType());
            dto.setLevel(achievement.getLevel());
            dto.setYear(achievement.getYear());
            dto.setDescription(achievement.getDescription());
            dto.setAward(achievement.getAward());
            dto.setCertificatePath(achievement.getCertificatePath());
            dto.setVerified(achievement.getVerifiedBy() != null);
            return dto;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [CONVERT-ACHIEVEMENT-TO-DTO] ERROR converting achievement ID {}: {}",
                    achievement.getId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to convert achievement to DTO", e);
        }
    }

    private StudentInterestDTO convertToInterestDTO(StudentInterest interest) {
        log.trace("[STUDENT-SERVICE] [CONVERT-INTEREST-TO-DTO] Converting interest ID: {}", interest.getId());
        try {
            StudentInterestDTO dto = new StudentInterestDTO();
            dto.setId(interest.getId());
            dto.setInterestType(interest.getInterestType());
            dto.setName(interest.getName());
            dto.setDescription(interest.getDescription());
            return dto;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [CONVERT-INTEREST-TO-DTO] ERROR converting interest ID {}: {}",
                    interest.getId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to convert interest to DTO", e);
        }
    }

    private Student convertToEntity(StudentCreateDTO createDTO) {
        log.debug("[STUDENT-SERVICE] [CONVERT-TO-ENTITY] Converting create DTO to student entity");
        try {
            return Student.builder()
                    .studentId(createDTO.getStudentId())
                    .fullName(createDTO.getFullName())
                    .dateOfBirth(createDTO.getDateOfBirth())
                    .gender(createDTO.getGender())
                    .bloodGroup(createDTO.getBloodGroup())
                    .nationality(createDTO.getNationality())
                    .religion(createDTO.getReligion())
                    .category(createDTO.getCategory())
                    .admissionDate(createDTO.getAdmissionDate())
                    .academicYear(createDTO.getAcademicYear())
                    .grade(createDTO.getGrade())
                    .rollNumber(createDTO.getRollNumber())
                    .classTeacher(createDTO.getClassTeacher())
                    .house(createDTO.getHouse())
                    .address(createDTO.getAddress())
                    .phone(createDTO.getPhone())
                    .email(createDTO.getEmail())
                    .emergencyContactName(createDTO.getEmergencyContactName())
                    .emergencyContactPhone(createDTO.getEmergencyContactPhone())
                    .emergencyRelation(createDTO.getEmergencyRelation())
                    .build();
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [CONVERT-TO-ENTITY] ERROR converting DTO to entity: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to convert DTO to entity", e);
        }
    }

    private void updateStudentFromDTO(Student student, StudentUpdateDTO updateDTO) {
        log.debug("[STUDENT-SERVICE] [UPDATE-FROM-DTO] Updating student entity from DTO - Student ID: {}", student.getId());
        try {
            if (updateDTO.getFullName() != null) student.setFullName(updateDTO.getFullName());
            if (updateDTO.getDateOfBirth() != null) student.setDateOfBirth(updateDTO.getDateOfBirth());
            if (updateDTO.getGender() != null) student.setGender(updateDTO.getGender());
            if (updateDTO.getBloodGroup() != null) student.setBloodGroup(updateDTO.getBloodGroup());
            if (updateDTO.getNationality() != null) student.setNationality(updateDTO.getNationality());
            if (updateDTO.getReligion() != null) student.setReligion(updateDTO.getReligion());
            if (updateDTO.getCategory() != null) student.setCategory(updateDTO.getCategory());
            if (updateDTO.getProfilePicture() != null) student.setProfilePicture(updateDTO.getProfilePicture());
            if (updateDTO.getGrade() != null) student.setGrade(updateDTO.getGrade());
            if (updateDTO.getRollNumber() != null) student.setRollNumber(updateDTO.getRollNumber());
            if (updateDTO.getClassTeacher() != null) student.setClassTeacher(updateDTO.getClassTeacher());
            if (updateDTO.getHouse() != null) student.setHouse(updateDTO.getHouse());
            if (updateDTO.getAddress() != null) student.setAddress(updateDTO.getAddress());
            if (updateDTO.getPhone() != null) student.setPhone(updateDTO.getPhone());
            if (updateDTO.getEmail() != null) student.setEmail(updateDTO.getEmail());
            if (updateDTO.getEmergencyContactName() != null) student.setEmergencyContactName(updateDTO.getEmergencyContactName());
            if (updateDTO.getEmergencyContactPhone() != null) student.setEmergencyContactPhone(updateDTO.getEmergencyContactPhone());
            if (updateDTO.getEmergencyRelation() != null) student.setEmergencyRelation(updateDTO.getEmergencyRelation());
            if (updateDTO.getHeight() != null) student.setHeight(updateDTO.getHeight());
            if (updateDTO.getWeight() != null) student.setWeight(updateDTO.getWeight());
            if (updateDTO.getBloodPressure() != null) student.setBloodPressure(updateDTO.getBloodPressure());
            if (updateDTO.getLastMedicalCheckup() != null) student.setLastMedicalCheckup(updateDTO.getLastMedicalCheckup());
            if (updateDTO.getDoctorName() != null) student.setDoctorName(updateDTO.getDoctorName());
            if (updateDTO.getClinicName() != null) student.setClinicName(updateDTO.getClinicName());
            if (updateDTO.getTransportMode() != null) student.setTransportMode(updateDTO.getTransportMode());
            if (updateDTO.getBusRoute() != null) student.setBusRoute(updateDTO.getBusRoute());
            if (updateDTO.getBusStop() != null) student.setBusStop(updateDTO.getBusStop());
            if (updateDTO.getBusNumber() != null) student.setBusNumber(updateDTO.getBusNumber());
            if (updateDTO.getDriverName() != null) student.setDriverName(updateDTO.getDriverName());
            if (updateDTO.getDriverContact() != null) student.setDriverContact(updateDTO.getDriverContact());
            if (updateDTO.getPickupTime() != null) student.setPickupTime(updateDTO.getPickupTime());
            if (updateDTO.getDropTime() != null) student.setDropTime(updateDTO.getDropTime());
            log.debug("[STUDENT-SERVICE] [UPDATE-FROM-DTO] Student entity updated successfully");
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [UPDATE-FROM-DTO] ERROR updating student from DTO: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update student from DTO", e);
        }
    }

    /**
     * Get all students with fee summaries for dashboard
     */
    @Transactional(readOnly = true)
    public List<StudentFeeSummaryDTO> getAllStudentsFeeSummary() {
        log.info("[STUDENT-SERVICE] [GET-ALL-STUDENTS-FEE-SUMMARY] Started");
        try {
            long startTime = System.currentTimeMillis();

            List<StudentFeeSummaryDTO> summaries = studentRepository.findAllStudentsWithFeeSummary();

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("[STUDENT-SERVICE] [GET-ALL-STUDENTS-FEE-SUMMARY] Completed in {} ms. Found {} students",
                    executionTime, summaries.size());

            return summaries;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-ALL-STUDENTS-FEE-SUMMARY] ERROR: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch students fee summary: " + e.getMessage(), e);
        }
    }

    /**
     * Get grade-wise fee statistics for dashboard
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getGradeWiseFeeStatistics() {
        log.info("[STUDENT-SERVICE] [GET-GRADE-WISE-FEE-STATISTICS] Started");
        try {
            // Get grade statistics from repository
            List<GradeStatisticsDTO> gradeStats = studentRepository.getGradeWiseStatistics();

            // Calculate overall totals
            double overallTotalFee = gradeStats.stream()
                    .mapToDouble(GradeStatisticsDTO::getTotalFee)
                    .sum();
            double overallPaidAmount = gradeStats.stream()
                    .mapToDouble(GradeStatisticsDTO::getPaidAmount)
                    .sum();
            double overallPendingAmount = gradeStats.stream()
                    .mapToDouble(GradeStatisticsDTO::getPendingAmount)
                    .sum();
            double overallCollectionRate = overallTotalFee > 0 ?
                    (overallPaidAmount / overallTotalFee) * 100 : 0;

            int totalEnrolled = gradeStats.stream()
                    .mapToInt(GradeStatisticsDTO::getEnrolled)
                    .sum();

            Map<String, Object> result = new HashMap<>();
            result.put("grades", gradeStats);
            result.put("totalGrades", gradeStats.size());
            result.put("totalEnrolled", totalEnrolled);
            result.put("overallTotalFee", overallTotalFee);
            result.put("overallPaidAmount", overallPaidAmount);
            result.put("overallPendingAmount", overallPendingAmount);
            result.put("overallCollectionRate", Math.round(overallCollectionRate * 10) / 10.0);

            log.info("[STUDENT-SERVICE] [GET-GRADE-WISE-FEE-STATISTICS] Completed - Found {} grades with {} total students",
                    gradeStats.size(), totalEnrolled);

            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-GRADE-WISE-FEE-STATISTICS] ERROR: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to calculate grade-wise fee statistics: " + e.getMessage(), e);
        }
    }

    /**
     * Get students by grade with fee summary
     */
    @Transactional(readOnly = true)
    public List<StudentFeeSummaryDTO> getStudentsFeeSummaryByGrade(String grade) {
        log.info("[STUDENT-SERVICE] [GET-STUDENTS-FEE-SUMMARY-BY-GRADE] Started - Grade: {}", grade);
        try {
            // Get all students and filter by grade
            List<StudentFeeSummaryDTO> allSummaries = getAllStudentsFeeSummary();
            List<StudentFeeSummaryDTO> gradeSummaries = allSummaries.stream()
                    .filter(summary -> summary.getGrade() != null && summary.getGrade().equals(grade))
                    .collect(Collectors.toList());

            log.info("[STUDENT-SERVICE] [GET-STUDENTS-FEE-SUMMARY-BY-GRADE] Completed - Found {} students in grade {}",
                    gradeSummaries.size(), grade);

            return gradeSummaries;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-STUDENTS-FEE-SUMMARY-BY-GRADE] ERROR for grade {}: {}",
                    grade, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch students fee summary by grade: " + e.getMessage(), e);
        }
    }

    /**
     * Get all distinct grades from active students
     */
    @Transactional(readOnly = true)
    public List<String> getAllGrades() {
        log.info("[STUDENT-SERVICE] [GET-ALL-GRADES] Started");
        try {
            long startTime = System.currentTimeMillis();

            // Call repository method
            List<String> grades = studentRepository.findDistinctGrades();

            if (grades == null) {
                grades = new ArrayList<>();
            }

            // Sort grades numerically if they contain numbers
            List<String> sortedGrades = grades.stream()
                    .filter(grade -> grade != null && !grade.trim().isEmpty())
                    .map(String::trim)
                    .distinct()
                    .sorted((a, b) -> {
                        // Extract numbers from grade strings
                        Integer numA = extractNumberFromGrade(a);
                        Integer numB = extractNumberFromGrade(b);

                        // If both have numbers, compare numerically
                        if (numA != null && numB != null) {
                            return numA.compareTo(numB);
                        }

                        // If only one has number, put it first
                        if (numA != null) return -1;
                        if (numB != null) return 1;

                        // Otherwise, compare alphabetically
                        return a.compareToIgnoreCase(b);
                    })
                    .collect(Collectors.toList());

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("[STUDENT-SERVICE] [GET-ALL-GRADES] Completed in {} ms. Found {} grades: {}",
                    executionTime, sortedGrades.size(), sortedGrades);

            return sortedGrades;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [GET-ALL-GRADES] ERROR: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch grades: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to extract number from grade string
     */
    private Integer extractNumberFromGrade(String grade) {
        if (grade == null || grade.isEmpty()) {
            return null;
        }

        try {
            // Try to extract number from various formats
            String numbers = grade.replaceAll("[^0-9]", "");
            if (!numbers.isEmpty()) {
                return Integer.parseInt(numbers);
            }
        } catch (NumberFormatException e) {
            log.debug("Could not extract number from grade: {}", grade);
        }

        return null;
    }

    /**
     * Find all active students with pagination
     */
    @Transactional(readOnly = true)
    public Page<Student> findByDeletedFalse(Pageable pageable) {
        log.info("[STUDENT-SERVICE] [FIND-BY-DELETED-FALSE] Fetching page {} of students", pageable.getPageNumber());
        try {
            Page<Student> result = studentRepository.findByDeletedFalse(pageable);
            log.info("[STUDENT-SERVICE] [FIND-BY-DELETED-FALSE] Found {} students on page {}",
                    result.getNumberOfElements(), pageable.getPageNumber());
            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [FIND-BY-DELETED-FALSE] ERROR: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch students: " + e.getMessage(), e);
        }
    }

    /**
     * Search students with pagination
     */
    @Transactional(readOnly = true)
    public Page<Student> searchStudentsPaginated(String query, Pageable pageable) {
        log.info("[STUDENT-SERVICE] [SEARCH-STUDENTS-PAGINATED] Searching for: '{}', page: {}",
                query, pageable.getPageNumber());
        try {
            Page<Student> result = studentRepository.searchStudentsPaginated(query, pageable);
            log.info("[STUDENT-SERVICE] [SEARCH-STUDENTS-PAGINATED] Found {} students on page {}",
                    result.getNumberOfElements(), pageable.getPageNumber());
            return result;
        } catch (Exception e) {
            log.error("[STUDENT-SERVICE] [SEARCH-STUDENTS-PAGINATED] ERROR for query '{}': {}",
                    query, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to search students: " + e.getMessage(), e);
        }
    }
}