package com.system.SchoolManagementSystem.student.controller;

import com.system.SchoolManagementSystem.student.dto.*;
import com.system.SchoolManagementSystem.student.entity.*;
import com.system.SchoolManagementSystem.student.service.StudentService;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Slf4j
public class StudentProfileController {

    private final StudentService studentService;

    // ========== STUDENT CRUD OPERATIONS ==========

    /**
     * Get all students
     */
    @GetMapping
    public ResponseEntity<List<StudentDTO>> getAllStudents() {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-ALL-STUDENTS] [{}] Started - Request received", requestId);

        List<StudentDTO> students = studentService.getAllStudents();
        log.info("[CONTROLLER] [GET-ALL-STUDENTS] [{}] Completed - Returning {} students",
                requestId, students.size());
        return ResponseEntity.ok(students);
    }

    /**
     * Get student by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<StudentDTO> getStudentById(@PathVariable Long id) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-STUDENT-BY-ID] [{}] Started - ID: {}", requestId, id);

        StudentDTO student = studentService.getStudentById(id);
        log.info("[CONTROLLER] [GET-STUDENT-BY-ID] [{}] Completed - Found student: {} (ID: {})",
                requestId, student.getFullName(), student.getId());
        return ResponseEntity.ok(student);
    }

    /**
     * Get student by student ID
     */
    @GetMapping("/by-student-id/{studentId}")
    public ResponseEntity<StudentDTO> getStudentByStudentId(@PathVariable String studentId) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-STUDENT-BY-STUDENT-ID] [{}] Started - Student ID: {}", requestId, studentId);

        StudentDTO student = studentService.getStudentByStudentId(studentId);
        log.info("[CONTROLLER] [GET-STUDENT-BY-STUDENT-ID] [{}] Completed - Found student: {} (ID: {})",
                requestId, student.getFullName(), student.getId());
        return ResponseEntity.ok(student);
    }

    /**
     * Search students with pagination for dropdowns
     */
    @GetMapping("/search/paginated")
    public ResponseEntity<Map<String, Object>> searchStudentsPaginated(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String requestId = generateRequestId();
        log.info("[CONTROLLER] [SEARCH-STUDENTS-PAGINATED] [{}] Started - query: '{}', page: {}, size: {}",
                requestId, query, page, size);

        try {
            // Create pageable with sorting
            Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());

            Page<Student> studentPage;

            if (query == null || query.trim().isEmpty()) {
                // Use service method, not repository directly
                studentPage = studentService.findByDeletedFalse(pageable);
            } else {
                // Use service method, not repository directly
                String searchQuery = "%" + query.toLowerCase() + "%";
                studentPage = studentService.searchStudentsPaginated(searchQuery, pageable);
            }

            // Convert to StudentDropdownDTO
            List<StudentDropdownDTO> studentDTOs = studentPage.getContent().stream()
                    .map(student -> new StudentDropdownDTO(
                            student.getId(),
                            student.getStudentId(),
                            student.getFullName(),
                            student.getGrade(),
                            student.getPhone(),
                            student.getEmail()
                    ))
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("content", studentDTOs);
            response.put("currentPage", studentPage.getNumber());
            response.put("totalItems", studentPage.getTotalElements());
            response.put("totalPages", studentPage.getTotalPages());
            response.put("hasNext", studentPage.hasNext());
            response.put("hasPrevious", studentPage.hasPrevious());

            log.info("[CONTROLLER] [SEARCH-STUDENTS-PAGINATED] [{}] Completed - Found {} students, total pages: {}",
                    requestId, studentDTOs.size(), studentPage.getTotalPages());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[CONTROLLER] [SEARCH-STUDENTS-PAGINATED] [{}] ERROR: {}", requestId, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to search students");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("status", 500);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get student by email
     */
    @GetMapping("/by-email/{email}")
    public ResponseEntity<StudentDTO> getStudentByEmail(@PathVariable String email) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-STUDENT-BY-EMAIL] [{}] Started - Email: {}", requestId, email);

        StudentDTO student = studentService.getStudentByEmail(email);
        log.info("[CONTROLLER] [GET-STUDENT-BY-EMAIL] [{}] Completed - Found student: {} (ID: {})",
                requestId, student.getFullName(), student.getId());
        return ResponseEntity.ok(student);
    }

    /**
     * Create new student
     */
    @PostMapping
    public ResponseEntity<StudentDTO> createStudent(@Valid @RequestBody StudentCreateDTO createDTO) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [CREATE-STUDENT] [{}] Started - Creating student: {}",
                requestId, createDTO.getFullName());
        log.debug("[CONTROLLER] [CREATE-STUDENT] [{}] Request body: {}", requestId, createDTO);

        StudentDTO createdStudent = studentService.createStudent(createDTO);
        log.info("[CONTROLLER] [CREATE-STUDENT] [{}] Completed - Created student: {} (ID: {})",
                requestId, createdStudent.getFullName(), createdStudent.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdStudent);
    }

    /**
     * Update student
     */
    @PutMapping("/{id}")
    public ResponseEntity<StudentDTO> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody StudentUpdateDTO updateDTO) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [UPDATE-STUDENT] [{}] Started - Updating student ID: {}", requestId, id);
        log.debug("[CONTROLLER] [UPDATE-STUDENT] [{}] Update DTO: {}", requestId, updateDTO);

        StudentDTO updatedStudent = studentService.updateStudent(id, updateDTO);
        log.info("[CONTROLLER] [UPDATE-STUDENT] [{}] Completed - Updated student: {} (ID: {})",
                requestId, updatedStudent.getFullName(), updatedStudent.getId());
        return ResponseEntity.ok(updatedStudent);
    }

    /**
     * Delete student
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [DELETE-STUDENT] [{}] Started - Deleting student ID: {}", requestId, id);

        studentService.deleteStudent(id);
        log.info("[CONTROLLER] [DELETE-STUDENT] [{}] Completed - Student ID {} deleted", requestId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Search students by name
     */
    @GetMapping("/search")
    public ResponseEntity<List<StudentDTO>> searchStudents(@RequestParam String name) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [SEARCH-STUDENTS] [{}] Started - Search term: '{}'", requestId, name);

        List<StudentDTO> students = studentService.searchStudentsByName(name);
        log.info("[CONTROLLER] [SEARCH-STUDENTS] [{}] Completed - Found {} students for term '{}'",
                requestId, students.size(), name);
        return ResponseEntity.ok(students);
    }

    /**
     * Get students by grade
     */
    @GetMapping("/by-grade/{grade}")
    public ResponseEntity<List<StudentDTO>> getStudentsByGrade(@PathVariable String grade) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-STUDENTS-BY-GRADE] [{}] Started - Grade: {}", requestId, grade);

        List<StudentDTO> students = studentService.getStudentsByGrade(grade);
        log.info("[CONTROLLER] [GET-STUDENTS-BY-GRADE] [{}] Completed - Found {} students in grade {}",
                requestId, students.size(), grade);
        return ResponseEntity.ok(students);
    }

    /**
     * Get students by academic year
     */
    @GetMapping("/by-academic-year/{academicYear}")
    public ResponseEntity<List<StudentDTO>> getStudentsByAcademicYear(@PathVariable String academicYear) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-STUDENTS-BY-ACADEMIC-YEAR] [{}] Started - Academic Year: {}",
                requestId, academicYear);

        List<StudentDTO> students = studentService.getStudentsByAcademicYear(academicYear);
        log.info("[CONTROLLER] [GET-STUDENTS-BY-ACADEMIC-YEAR] [{}] Completed - Found {} students for academic year {}",
                requestId, students.size(), academicYear);
        return ResponseEntity.ok(students);
    }

    /**
     * Get student age
     */
    @GetMapping("/{studentId}/age")
    public ResponseEntity<Map<String, Integer>> getStudentAge(@PathVariable String studentId) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-STUDENT-AGE] [{}] Started - Student ID: {}", requestId, studentId);

        Integer age = studentService.calculateStudentAge(studentId);
        Map<String, Integer> response = new HashMap<>();
        response.put("age", age);

        log.info("[CONTROLLER] [GET-STUDENT-AGE] [{}] Completed - Age: {}", requestId, age);
        return ResponseEntity.ok(response);
    }

    // ========== FAMILY MEMBER OPERATIONS ==========

    /**
     * Get family members for student
     */
    @GetMapping("/{studentId}/family-members")
    public ResponseEntity<List<FamilyMemberDTO>> getFamilyMembers(@PathVariable Long studentId) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-FAMILY-MEMBERS] [{}] Started - Student ID: {}", requestId, studentId);

        List<FamilyMemberDTO> familyMembers = studentService.getFamilyMembers(studentId);
        log.info("[CONTROLLER] [GET-FAMILY-MEMBERS] [{}] Completed - Found {} family members",
                requestId, familyMembers.size());
        return ResponseEntity.ok(familyMembers);
    }

    /**
     * Add family member to student
     */
    @PostMapping("/{studentId}/family-members")
    public ResponseEntity<FamilyMemberDTO> addFamilyMember(
            @PathVariable Long studentId,
            @Valid @RequestBody FamilyMemberCreateDTO createDTO) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [ADD-FAMILY-MEMBER] [{}] Started - Student ID: {}", requestId, studentId);
        log.debug("[CONTROLLER] [ADD-FAMILY-MEMBER] [{}] Family member DTO: {}", requestId, createDTO);

        FamilyMemberDTO familyMember = studentService.addFamilyMember(studentId, createDTO);
        log.info("[CONTROLLER] [ADD-FAMILY-MEMBER] [{}] Completed - Added family member: {} (ID: {})",
                requestId, familyMember.getFullName(), familyMember.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(familyMember);
    }

    /**
     * Update family member
     */
    @PutMapping("/family-members/{familyMemberId}")
    public ResponseEntity<FamilyMemberDTO> updateFamilyMember(
            @PathVariable Long familyMemberId,
            @Valid @RequestBody FamilyMemberCreateDTO updateDTO) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [UPDATE-FAMILY-MEMBER] [{}] Started - Family Member ID: {}", requestId, familyMemberId);
        log.debug("[CONTROLLER] [UPDATE-FAMILY-MEMBER] [{}] Update DTO: {}", requestId, updateDTO);

        FamilyMemberDTO updatedMember = studentService.updateFamilyMember(familyMemberId, updateDTO);
        log.info("[CONTROLLER] [UPDATE-FAMILY-MEMBER] [{}] Completed - Updated family member: {} (ID: {})",
                requestId, updatedMember.getFullName(), updatedMember.getId());
        return ResponseEntity.ok(updatedMember);
    }

    /**
     * Delete family member
     */
    @DeleteMapping("/family-members/{familyMemberId}")
    public ResponseEntity<Void> deleteFamilyMember(@PathVariable Long familyMemberId) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [DELETE-FAMILY-MEMBER] [{}] Started - Family Member ID: {}", requestId, familyMemberId);

        studentService.deleteFamilyMember(familyMemberId);
        log.info("[CONTROLLER] [DELETE-FAMILY-MEMBER] [{}] Completed - Family member ID {} deleted",
                requestId, familyMemberId);
        return ResponseEntity.noContent().build();
    }

    // ========== MEDICAL RECORD OPERATIONS ==========

    /**
     * Get medical records for student
     */
    @GetMapping("/{studentId}/medical-records")
    public ResponseEntity<List<MedicalRecordDTO>> getMedicalRecords(
            @PathVariable Long studentId,
            @RequestParam(required = false) MedicalRecord.RecordType recordType) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-MEDICAL-RECORDS] [{}] Started - Student ID: {}, Record Type: {}",
                requestId, studentId, recordType);

        List<MedicalRecordDTO> medicalRecords = studentService.getMedicalRecords(studentId, recordType);
        log.info("[CONTROLLER] [GET-MEDICAL-RECORDS] [{}] Completed - Found {} medical records",
                requestId, medicalRecords.size());
        return ResponseEntity.ok(medicalRecords);
    }

    /**
     * Add medical record to student
     */
    @PostMapping("/{studentId}/medical-records")
    public ResponseEntity<MedicalRecordDTO> addMedicalRecord(
            @PathVariable Long studentId,
            @Valid @RequestBody MedicalRecordCreateDTO createDTO) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [ADD-MEDICAL-RECORD] [{}] Started - Student ID: {}", requestId, studentId);
        log.debug("[CONTROLLER] [ADD-MEDICAL-RECORD] [{}] Medical record DTO: {}", requestId, createDTO);

        MedicalRecordDTO medicalRecord = studentService.addMedicalRecord(studentId, createDTO);
        log.info("[CONTROLLER] [ADD-MEDICAL-RECORD] [{}] Completed - Added medical record: {} (ID: {})",
                requestId, medicalRecord.getName(), medicalRecord.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(medicalRecord);
    }

    /**
     * Update medical record
     */
    @PutMapping("/medical-records/{medicalRecordId}")
    public ResponseEntity<MedicalRecordDTO> updateMedicalRecord(
            @PathVariable Long medicalRecordId,
            @Valid @RequestBody MedicalRecordCreateDTO updateDTO) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [UPDATE-MEDICAL-RECORD] [{}] Started - Medical Record ID: {}", requestId, medicalRecordId);
        log.debug("[CONTROLLER] [UPDATE-MEDICAL-RECORD] [{}] Update DTO: {}", requestId, updateDTO);

        MedicalRecordDTO updatedRecord = studentService.updateMedicalRecord(medicalRecordId, updateDTO);
        log.info("[CONTROLLER] [UPDATE-MEDICAL-RECORD] [{}] Completed - Updated medical record: {} (ID: {})",
                requestId, updatedRecord.getName(), updatedRecord.getId());
        return ResponseEntity.ok(updatedRecord);
    }

    /**
     * Delete medical record
     */
    @DeleteMapping("/medical-records/{medicalRecordId}")
    public ResponseEntity<Void> deleteMedicalRecord(@PathVariable Long medicalRecordId) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [DELETE-MEDICAL-RECORD] [{}] Started - Medical Record ID: {}", requestId, medicalRecordId);

        studentService.deleteMedicalRecord(medicalRecordId);
        log.info("[CONTROLLER] [DELETE-MEDICAL-RECORD] [{}] Completed - Medical record ID {} deleted",
                requestId, medicalRecordId);
        return ResponseEntity.noContent().build();
    }

    // ========== ACHIEVEMENT OPERATIONS ==========

    /**
     * Get achievements for student
     */
    @GetMapping("/{studentId}/achievements")
    public ResponseEntity<List<AchievementDTO>> getAchievements(
            @PathVariable Long studentId,
            @RequestParam(required = false) Achievement.AchievementType type) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-ACHIEVEMENTS] [{}] Started - Student ID: {}, Achievement Type: {}",
                requestId, studentId, type);

        List<AchievementDTO> achievements = studentService.getAchievements(studentId, type);
        log.info("[CONTROLLER] [GET-ACHIEVEMENTS] [{}] Completed - Found {} achievements",
                requestId, achievements.size());
        return ResponseEntity.ok(achievements);
    }

    /**
     * Add achievement to student
     */
    @PostMapping("/{studentId}/achievements")
    public ResponseEntity<AchievementDTO> addAchievement(
            @PathVariable Long studentId,
            @Valid @RequestBody AchievementCreateDTO createDTO) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [ADD-ACHIEVEMENT] [{}] Started - Student ID: {}", requestId, studentId);
        log.debug("[CONTROLLER] [ADD-ACHIEVEMENT] [{}] Achievement DTO: {}", requestId, createDTO);

        AchievementDTO achievement = studentService.addAchievement(studentId, createDTO);
        log.info("[CONTROLLER] [ADD-ACHIEVEMENT] [{}] Completed - Added achievement: {} (ID: {})",
                requestId, achievement.getTitle(), achievement.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(achievement);
    }

    /**
     * Update achievement
     */
    @PutMapping("/achievements/{achievementId}")
    public ResponseEntity<AchievementDTO> updateAchievement(
            @PathVariable Long achievementId,
            @Valid @RequestBody AchievementCreateDTO updateDTO) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [UPDATE-ACHIEVEMENT] [{}] Started - Achievement ID: {}", requestId, achievementId);
        log.debug("[CONTROLLER] [UPDATE-ACHIEVEMENT] [{}] Update DTO: {}", requestId, updateDTO);

        AchievementDTO updatedAchievement = studentService.updateAchievement(achievementId, updateDTO);
        log.info("[CONTROLLER] [UPDATE-ACHIEVEMENT] [{}] Completed - Updated achievement: {} (ID: {})",
                requestId, updatedAchievement.getTitle(), updatedAchievement.getId());
        return ResponseEntity.ok(updatedAchievement);
    }

    /**
     * Delete achievement
     */
    @DeleteMapping("/achievements/{achievementId}")
    public ResponseEntity<Void> deleteAchievement(@PathVariable Long achievementId) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [DELETE-ACHIEVEMENT] [{}] Started - Achievement ID: {}", requestId, achievementId);

        studentService.deleteAchievement(achievementId);
        log.info("[CONTROLLER] [DELETE-ACHIEVEMENT] [{}] Completed - Achievement ID {} deleted",
                requestId, achievementId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Verify achievement
     */
    @PutMapping("/achievements/{achievementId}/verify")
    public ResponseEntity<AchievementDTO> verifyAchievement(
            @PathVariable Long achievementId,
            @RequestParam String verifiedBy) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [VERIFY-ACHIEVEMENT] [{}] Started - Achievement ID: {}, Verified By: {}",
                requestId, achievementId, verifiedBy);

        AchievementDTO achievement = studentService.verifyAchievement(achievementId, verifiedBy);
        log.info("[CONTROLLER] [VERIFY-ACHIEVEMENT] [{}] Completed - Verified achievement: {} (ID: {})",
                requestId, achievement.getTitle(), achievement.getId());
        return ResponseEntity.ok(achievement);
    }

    // ========== INTEREST OPERATIONS ==========

    /**
     * Get interests for student
     */
    @GetMapping("/{studentId}/interests")
    public ResponseEntity<List<StudentInterestDTO>> getInterests(
            @PathVariable Long studentId,
            @RequestParam(required = false) StudentInterest.InterestType interestType) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-INTERESTS] [{}] Started - Student ID: {}, Interest Type: {}",
                requestId, studentId, interestType);

        List<StudentInterestDTO> interests = studentService.getInterests(studentId, interestType);
        log.info("[CONTROLLER] [GET-INTERESTS] [{}] Completed - Found {} interests",
                requestId, interests.size());
        return ResponseEntity.ok(interests);
    }

    /**
     * Add interest to student
     */
    @PostMapping("/{studentId}/interests")
    public ResponseEntity<StudentInterestDTO> addInterest(
            @PathVariable Long studentId,
            @Valid @RequestBody StudentInterestCreateDTO createDTO) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [ADD-INTEREST] [{}] Started - Student ID: {}", requestId, studentId);
        log.debug("[CONTROLLER] [ADD-INTEREST] [{}] Interest DTO: {}", requestId, createDTO);

        StudentInterestDTO interest = studentService.addInterest(studentId, createDTO);
        log.info("[CONTROLLER] [ADD-INTEREST] [{}] Completed - Added interest: {} (ID: {})",
                requestId, interest.getName(), interest.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(interest);
    }

    /**
     * Update interest
     */
    @PutMapping("/interests/{interestId}")
    public ResponseEntity<StudentInterestDTO> updateInterest(
            @PathVariable Long interestId,
            @Valid @RequestBody StudentInterestCreateDTO updateDTO) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [UPDATE-INTEREST] [{}] Started - Interest ID: {}", requestId, interestId);
        log.debug("[CONTROLLER] [UPDATE-INTEREST] [{}] Update DTO: {}", requestId, updateDTO);

        StudentInterestDTO updatedInterest = studentService.updateInterest(interestId, updateDTO);
        log.info("[CONTROLLER] [UPDATE-INTEREST] [{}] Completed - Updated interest: {} (ID: {})",
                requestId, updatedInterest.getName(), updatedInterest.getId());
        return ResponseEntity.ok(updatedInterest);
    }

    /**
     * Delete interest
     */
    @DeleteMapping("/interests/{interestId}")
    public ResponseEntity<Void> deleteInterest(@PathVariable Long interestId) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [DELETE-INTEREST] [{}] Started - Interest ID: {}", requestId, interestId);

        studentService.deleteInterest(interestId);
        log.info("[CONTROLLER] [DELETE-INTEREST] [{}] Completed - Interest ID {} deleted",
                requestId, interestId);
        return ResponseEntity.noContent().build();
    }

    // ========== FILE UPLOAD OPERATIONS ==========

    /**
     * Upload profile picture for student
     */
    @PostMapping(value = "/{studentId}/upload-profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadProfilePicture(
            @PathVariable Long studentId,
            @RequestParam("file") MultipartFile file) throws IOException {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [UPLOAD-PROFILE-PICTURE] [{}] Started - Student ID: {}", requestId, studentId);
        log.debug("[CONTROLLER] [UPLOAD-PROFILE-PICTURE] [{}] File details - Name: {}, Size: {} bytes, Type: {}",
                requestId, file.getOriginalFilename(), file.getSize(), file.getContentType());

        String fileUrl = studentService.uploadProfilePicture(studentId, file);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Profile picture uploaded successfully");
        response.put("fileUrl", fileUrl);
        response.put("profilePicture", fileUrl);
        response.put("requestId", requestId);

        log.info("[CONTROLLER] [UPLOAD-PROFILE-PICTURE] [{}] Completed - File URL: {}", requestId, fileUrl);
        return ResponseEntity.ok(response);
    }

    /**
     * Upload achievement certificate
     */
    @PostMapping(value = "/achievements/{achievementId}/upload-certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAchievementCertificate(
            @PathVariable Long achievementId,
            @RequestParam("file") MultipartFile file) throws IOException {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [UPLOAD-ACHIEVEMENT-CERTIFICATE] [{}] Started - Achievement ID: {}",
                requestId, achievementId);
        log.debug("[CONTROLLER] [UPLOAD-ACHIEVEMENT-CERTIFICATE] [{}] File details - Name: {}, Size: {} bytes, Type: {}",
                requestId, file.getOriginalFilename(), file.getSize(), file.getContentType());

        String fileUrl = studentService.uploadAchievementCertificate(achievementId, file);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Certificate uploaded successfully");
        response.put("fileUrl", fileUrl);
        response.put("certificatePath", fileUrl);
        response.put("requestId", requestId);

        log.info("[CONTROLLER] [UPLOAD-ACHIEVEMENT-CERTIFICATE] [{}] Completed - File URL: {}", requestId, fileUrl);
        return ResponseEntity.ok(response);
    }

    /**
     * Get student's profile picture
     */
    @GetMapping("/{studentId}/profile-picture")
    public ResponseEntity<Resource> getProfilePicture(@PathVariable Long studentId) throws IOException {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-PROFILE-PICTURE] [{}] Started - Student ID: {}", requestId, studentId);

        Resource resource = studentService.getProfilePictureResource(studentId);

        if (resource.exists() && resource.isReadable()) {
            String contentType = determineContentType(resource);
            log.info("[CONTROLLER] [GET-PROFILE-PICTURE] [{}] Completed - Returning profile picture", requestId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } else {
            log.warn("[CONTROLLER] [GET-PROFILE-PICTURE] [{}] Profile picture not found or not readable", requestId);
            return ResponseEntity.notFound().build();
        }
    }

    private String determineContentType(Resource resource) {
        try {
            String filename = resource.getFilename();
            if (filename != null) {
                if (filename.toLowerCase().endsWith(".png")) {
                    return "image/png";
                } else if (filename.toLowerCase().endsWith(".gif")) {
                    return "image/gif";
                }
            }
            return "image/jpeg"; // Default
        } catch (Exception e) {
            log.warn("[CONTROLLER] [DETERMINE-CONTENT-TYPE] Unable to determine content type, defaulting to JPEG");
            return "image/jpeg";
        }
    }

    // ========== DEMO ENDPOINTS ==========

    /**
     * Demo endpoint to get complete profile
     */
    @GetMapping("/demo/complete-profile")
    public ResponseEntity<StudentDTO> getDemoCompleteProfile() {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [DEMO-COMPLETE-PROFILE] [{}] Started - Getting demo profile", requestId);

        // Create demo student DTO
        StudentDTO demoStudent = new StudentDTO();
        demoStudent.setId(1L);
        demoStudent.setStudentId("STU20240025");
        demoStudent.setFullName("John Smith");
        demoStudent.setDateOfBirth(java.time.LocalDate.of(2009, 6, 15));
        demoStudent.setGender(Student.Gender.MALE);
        demoStudent.setBloodGroup(Student.BloodGroup.O_PLUS);
        demoStudent.setNationality("Indian");
        demoStudent.setReligion("Christian");
        demoStudent.setCategory(Student.Category.GENERAL);
        demoStudent.setGrade("10-A");
        demoStudent.setRollNumber("25");
        demoStudent.setAcademicYear("2024-25");
        demoStudent.setClassTeacher("Mrs. Johnson");
        demoStudent.setHouse("Blue House");
        demoStudent.setAdmissionDate(java.time.LocalDate.of(2020, 6, 1));
        demoStudent.setAddress("123 Maple Street, Springfield, 560001");
        demoStudent.setPhone("+91 9876543210");
        demoStudent.setEmail("john.smith@springfield.edu");
        demoStudent.setEmergencyContactName("Robert Smith");
        demoStudent.setEmergencyContactPhone("+91 9876543211");
        demoStudent.setEmergencyRelation("Father");
        demoStudent.setProfilePicture("http://localhost:8080/uploads/profiles/demo_profile.jpg");

        // Add demo data for frontend testing
        demoStudent.setClubs(List.of("Football Club", "Science Club", "Debate Society"));
        demoStudent.setHobbies(List.of("Reading", "Football", "Painting", "Coding"));

        // Demo family members
        FamilyMemberDTO father = new FamilyMemberDTO();
        father.setId(1L);
        father.setRelation(FamilyMember.Relation.FATHER);
        father.setFullName("Robert Smith");
        father.setOccupation("Engineer");
        father.setPhone("+91 9876543211");
        father.setEmail("robert.smith@email.com");
        father.setIsPrimaryContact(true);
        father.setIsEmergencyContact(true);

        FamilyMemberDTO mother = new FamilyMemberDTO();
        mother.setId(2L);
        mother.setRelation(FamilyMember.Relation.MOTHER);
        mother.setFullName("Mary Smith");
        mother.setOccupation("Teacher");
        mother.setPhone("+91 9876543212");
        mother.setEmail("mary.smith@email.com");
        mother.setIsPrimaryContact(false);
        mother.setIsEmergencyContact(true);

        demoStudent.setFamilyMembers(List.of(father, mother));

        // Demo medical records
        MedicalRecordDTO allergy = new MedicalRecordDTO();
        allergy.setId(1L);
        allergy.setRecordType(MedicalRecord.RecordType.ALLERGY);
        allergy.setName("Peanuts");
        allergy.setSeverity(MedicalRecord.Severity.SEVERE);
        allergy.setNotes("Causes breathing difficulties");

        demoStudent.setMedicalRecords(List.of(allergy));

        // Demo achievements
        AchievementDTO achievement = new AchievementDTO();
        achievement.setId(1L);
        achievement.setTitle("Science Fair Winner");
        achievement.setType(Achievement.AchievementType.ACADEMIC);
        achievement.setLevel(Achievement.AchievementLevel.SCHOOL);
        achievement.setYear(2024);
        achievement.setDescription("Won first prize in school science fair");
        achievement.setAward("Gold Medal");
        achievement.setVerified(true);

        demoStudent.setAchievements(List.of(achievement));

        log.info("[CONTROLLER] [DEMO-COMPLETE-PROFILE] [{}] Completed - Returning demo profile", requestId);
        return ResponseEntity.ok(demoStudent);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [HEALTH-CHECK] [{}] Started", requestId);

        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Student Profile Service");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("requestId", requestId);

        log.info("[CONTROLLER] [HEALTH-CHECK] [{}] Completed - Service is healthy", requestId);
        return ResponseEntity.ok(response);
    }

    // ========== HELPER METHODS ==========

    /**
     * Generate unique request ID for tracking
     */
    private String generateRequestId() {
        return "REQ-" + System.currentTimeMillis() + "-" +
                Thread.currentThread().getId() + "-" +
                ((int) (Math.random() * 1000));
    }

    /**
     * Get all students with fee summaries
     */
    @GetMapping("/fee-summary")
    public ResponseEntity<List<StudentFeeSummaryDTO>> getAllStudentsFeeSummary() {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-ALL-STUDENTS-FEE-SUMMARY] [{}] Started", requestId);

        List<StudentFeeSummaryDTO> summaries = studentService.getAllStudentsFeeSummary();

        log.info("[CONTROLLER] [GET-ALL-STUDENTS-FEE-SUMMARY] [{}] Completed - Returning {} fee summaries",
                requestId, summaries.size());
        return ResponseEntity.ok(summaries);
    }

    /**
     * Get grade-wise fee statistics for dashboard
     */
    @GetMapping("/fee-summary/grade-statistics")
    public ResponseEntity<Map<String, Object>> getGradeWiseFeeStatistics() {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-GRADE-WISE-FEE-STATISTICS] [{}] Started", requestId);

        Map<String, Object> statistics = studentService.getGradeWiseFeeStatistics();

        log.info("[CONTROLLER] [GET-GRADE-WISE-FEE-STATISTICS] [{}] Completed - Statistics for {} grades",
                requestId, statistics.get("totalGrades"));
        return ResponseEntity.ok(statistics);
    }

    /**
     * Get students fee summary by grade
     */
    @GetMapping("/fee-summary/grade/{grade}")
    public ResponseEntity<List<StudentFeeSummaryDTO>> getStudentsFeeSummaryByGrade(@PathVariable String grade) {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-STUDENTS-FEE-SUMMARY-BY-GRADE] [{}] Started - Grade: {}",
                requestId, grade);

        List<StudentFeeSummaryDTO> summaries = studentService.getStudentsFeeSummaryByGrade(grade);

        log.info("[CONTROLLER] [GET-STUDENTS-FEE-SUMMARY-BY-GRADE] [{}] Completed - Found {} students in grade {}",
                requestId, summaries.size(), grade);
        return ResponseEntity.ok(summaries);
    }

// ========== GRADE OPERATIONS ==========

    /**
     * Get all distinct grades from students (for dropdowns)
     */
    @GetMapping("/grades")
    public ResponseEntity<List<String>> getAllGrades() {
        String requestId = generateRequestId();
        log.info("[CONTROLLER] [GET-ALL-GRADES] [{}] Started", requestId);

        try {
            // Call the service method, not repository directly
            List<String> grades = studentService.getAllGrades();

            // Log the grades found
            log.info("[CONTROLLER] [GET-ALL-GRADES] [{}] Found {} unique grades: {}",
                    requestId, grades.size(), grades);

            return ResponseEntity.ok(grades);
        } catch (Exception e) {
            log.error("[CONTROLLER] [GET-ALL-GRADES] [{}] ERROR: {}", requestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch grades: " + e.getMessage(), e);
        }
    }


}