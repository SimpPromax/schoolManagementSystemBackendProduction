package com.system.SchoolManagementSystem.termmanagement.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.termmanagement.dto.request.*;
import com.system.SchoolManagementSystem.termmanagement.dto.response.*;
import com.system.SchoolManagementSystem.termmanagement.entity.*;
import com.system.SchoolManagementSystem.termmanagement.repository.*;
import com.system.SchoolManagementSystem.transaction.entity.*;
import com.system.SchoolManagementSystem.transaction.enums.FeeStatus;
import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import com.system.SchoolManagementSystem.transaction.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TermFeeService {

    // ========== REPOSITORIES ==========
    private final GradeTermFeeRepository gradeTermFeeRepository;
    private final StudentTermAssignmentRepository studentTermAssignmentRepository;
    private final TermFeeItemRepository termFeeItemRepository;
    private final StudentRepository studentRepository;
    private final StudentFeeAssignmentRepository studentFeeAssignmentRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AcademicTermRepository academicTermRepository;

    // ========== DEPENDENT SERVICES ==========
    private final TermService termService;

    // ========== FEE STRUCTURE MANAGEMENT ==========

    /**
     * Create or update grade fee structure
     */
    @Transactional
    public GradeTermFee saveFeeStructure(FeeStructureRequest request) {
        // Validate term exists
        AcademicTerm term = academicTermRepository.findById(request.getTermId())
                .orElseThrow(() -> new RuntimeException("Academic term not found with id: " + request.getTermId()));

        // Extract numeric grade for comparison
        String numericGrade = extractNumericGrade(request.getGrade());

        Optional<GradeTermFee> existingFee = gradeTermFeeRepository
                .findByAcademicTermIdAndGrade(request.getTermId(), numericGrade != null ? numericGrade : request.getGrade());

        if (existingFee.isPresent()) {
            // Update existing
            GradeTermFee fee = existingFee.get();
            updateFeeStructure(fee, request);
            return gradeTermFeeRepository.save(fee);
        } else {
            // Create new
            GradeTermFee fee = createFeeStructure(request);
            fee.setAcademicTerm(term);
            return gradeTermFeeRepository.save(fee);
        }
    }

    /**
     * Get fee structure for specific grade in term
     */
    public Optional<GradeTermFee> getFeeStructure(Long termId, String grade) {
        if (grade == null) return Optional.empty();

        String numericGrade = extractNumericGrade(grade);

        // First try exact match
        Optional<GradeTermFee> exactMatch = gradeTermFeeRepository
                .findByAcademicTermIdAndGrade(termId, grade);

        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        // If not found and we have a numeric grade, try that
        if (numericGrade != null && !numericGrade.equals(grade)) {
            Optional<GradeTermFee> numericMatch = gradeTermFeeRepository
                    .findByAcademicTermIdAndGrade(termId, numericGrade);

            if (numericMatch.isPresent()) {
                return numericMatch;
            }
        }

        // If still not found, check all structures for matching grade
        List<GradeTermFee> termFees = gradeTermFeeRepository.findByAcademicTermId(termId);
        return termFees.stream()
                .filter(fee -> gradesMatch(grade, fee.getGrade()))
                .findFirst();
    }

    /**
     * Get all fee structures for a term
     */
    public List<GradeTermFee> getFeeStructuresForTerm(Long termId) {
        return gradeTermFeeRepository.findByAcademicTermId(termId);
    }

    /**
     * Get active fee structures
     */
    public List<GradeTermFee> getActiveFeeStructures() {
        return gradeTermFeeRepository.findByIsActiveTrue();
    }

    /**
     * Update existing fee structure
     */
    @Transactional
    public GradeTermFee updateFeeStructure(Long structureId, FeeStructureRequest request) {
        GradeTermFee structure = gradeTermFeeRepository.findById(structureId)
                .orElseThrow(() -> new RuntimeException("Fee structure not found with id: " + structureId));

        // Validate term exists
        AcademicTerm term = academicTermRepository.findById(request.getTermId())
                .orElseThrow(() -> new RuntimeException("Academic term not found with id: " + request.getTermId()));

        // Extract numeric grades for comparison
        String existingNumericGrade = extractNumericGrade(structure.getGrade());
        String newNumericGrade = extractNumericGrade(request.getGrade());

        // Check if numeric grades changed
        if (existingNumericGrade != null && newNumericGrade != null &&
                !existingNumericGrade.equals(newNumericGrade)) {

            Optional<GradeTermFee> existingForNewGrade = gradeTermFeeRepository
                    .findByAcademicTermIdAndGrade(request.getTermId(), newNumericGrade);

            if (existingForNewGrade.isPresent() &&
                    !existingForNewGrade.get().getId().equals(structureId)) {
                throw new RuntimeException("Fee structure already exists for grade " +
                        newNumericGrade + " in this term");
            }
        }

        // Update structure
        structure.setAcademicTerm(term);
        structure.setGrade(request.getGrade());
        structure.setTuitionFee(request.getTuitionFee());
        structure.setBasicFee(request.getBasicFee());
        structure.setExaminationFee(request.getExaminationFee());
        structure.setTransportFee(request.getTransportFee());
        structure.setLibraryFee(request.getLibraryFee());
        structure.setSportsFee(request.getSportsFee());
        structure.setActivityFee(request.getActivityFee());
        structure.setHostelFee(request.getHostelFee());
        structure.setUniformFee(request.getUniformFee());
        structure.setBookFee(request.getBookFee());
        structure.setOtherFees(request.getOtherFees());
        structure.setIsActive(request.getIsActive());

        return gradeTermFeeRepository.save(structure);
    }

    /**
     * Delete fee structure
     */
    @Transactional
    public boolean deleteFeeStructure(Long structureId) {
        GradeTermFee structure = gradeTermFeeRepository.findById(structureId)
                .orElseThrow(() -> new RuntimeException("Fee structure not found with id: " + structureId));

        // Check if structure has active assignments
        List<StudentTermAssignment> assignments = studentTermAssignmentRepository
                .findByAcademicTermId(structure.getAcademicTerm().getId());

        boolean hasAssignments = assignments.stream()
                .anyMatch(assignment -> assignment.getStudent() != null &&
                        assignment.getStudent().getGrade() != null &&
                        gradesMatch(assignment.getStudent().getGrade(), structure.getGrade()));

        if (hasAssignments) {
            throw new RuntimeException("Cannot delete fee structure with active student assignments");
        }

        gradeTermFeeRepository.delete(structure);
        return true;
    }

    /**
     * Check if fee structure exists for grade in term
     */
    public boolean feeStructureExists(Long termId, String grade) {
        if (grade == null) return false;

        String numericGrade = extractNumericGrade(grade);

        // Try exact match first
        boolean exists = gradeTermFeeRepository.existsByAcademicTermIdAndGrade(termId, grade);

        // If not found and we have a numeric grade, try that
        if (!exists && numericGrade != null && !numericGrade.equals(grade)) {
            exists = gradeTermFeeRepository.existsByAcademicTermIdAndGrade(termId, numericGrade);
        }

        // If still not found, check all structures for matching grade
        if (!exists) {
            List<GradeTermFee> termFees = gradeTermFeeRepository.findByAcademicTermId(termId);
            exists = termFees.stream()
                    .anyMatch(fee -> gradesMatch(grade, fee.getGrade()));
        }

        return exists;
    }

    /**
     * Update fee structure status
     */
    @Transactional
    public boolean updateFeeStructureStatus(Long structureId, Boolean isActive) {
        return gradeTermFeeRepository.findById(structureId)
                .map(structure -> {
                    structure.setIsActive(isActive);
                    gradeTermFeeRepository.save(structure);
                    return true;
                })
                .orElse(false);
    }

    // ========== AUTO-BILLING ==========

    /**
     * Auto-bill all active students for current term
     */
    @Transactional
    public AutoBillingResult autoBillCurrentTerm() {
        log.info("🔄 ========== STARTING AUTO-BILLING PROCESS ==========");

        try {
            // Get current term with null check
            Optional<AcademicTerm> currentTermOpt = termService.getCurrentTerm();
            if (currentTermOpt.isEmpty()) {
                log.error("❌ No current term found. Auto-billing cannot proceed.");
                return AutoBillingResult.builder()
                        .success(false)
                        .message("No active term found")
                        .billedCount(0)
                        .skippedCount(0)
                        .errors(List.of("No current term available"))
                        .build();
            }

            AcademicTerm term = currentTermOpt.get();
            log.info("📊 Processing term: {} (ID: {}) - Academic Year: {}",
                    term.getTermName(), term.getId(), term.getAcademicYear());
            log.info("📅 Term Dates: {} to {} | Fee Due: {}",
                    term.getStartDate(), term.getEndDate(), term.getFeeDueDate());

            // ========== ONLY CHANGE THIS LINE ==========
            // OLD: List<Student> activeStudents = studentRepository.findByStatus(Student.StudentStatus.ACTIVE);
            // NEW: Use the new method that excludes deleted students
            List<Student> activeStudents = studentRepository.findActiveAndNotDeleted();
            // ===========================================

            if (activeStudents == null || activeStudents.isEmpty()) {
                log.warn("⚠️ No active students found to bill");
                return AutoBillingResult.builder()
                        .success(true)
                        .message("No active students to bill")
                        .billedCount(0)
                        .skippedCount(0)
                        .errors(new ArrayList<>())
                        .build();
            }

            log.info("👥 Found {} active and not deleted students", activeStudents.size());

            // Count students with missing grades
            long studentsWithoutGrade = activeStudents.stream()
                    .filter(s -> s.getGrade() == null || s.getGrade().trim().isEmpty())
                    .count();

            if (studentsWithoutGrade > 0) {
                log.warn("⚠️ {} students have no grade assigned and will be skipped", studentsWithoutGrade);
            }

            // Process billing (rest of your existing code remains the same)
            AutoBillingResult result = processAutoBilling(term, activeStudents);

            log.info("✅ ========== AUTO-BILLING COMPLETED ==========");
            log.info("📈 Summary: {} billed | {} skipped | {} errors",
                    result.getBilledCount(), result.getSkippedCount(), result.getErrors().size());

            if (!result.getErrors().isEmpty()) {
                log.warn("⚠️ Encountered {} errors during auto-billing:", result.getErrors().size());
                result.getErrors().forEach(error -> log.warn("   • {}", error));
            }

            return result;

        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR in auto-billing process: {}", e.getMessage(), e);
            return AutoBillingResult.builder()
                    .success(false)
                    .message("Auto-billing failed due to system error")
                    .billedCount(0)
                    .skippedCount(0)
                    .errors(List.of("System error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Auto-bill specific student for term
     */
    @Transactional
    public boolean autoBillStudentForTerm(Long studentId, Long termId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        AcademicTerm term = termService.getTermById(termId)
                .orElseThrow(() -> new RuntimeException("Term not found: " + termId));

        return autoBillStudent(student, term);
    }

    /**
     * Validate if student can be auto-billed (simplified)
     */
    private boolean canAutoBillStudent(Student student, AcademicTerm term) {
        log.debug("Validating auto-billing for student {} in term {}",
                student.getId(), term.getId());

        // Check student status
        if (student.getStatus() != Student.StudentStatus.ACTIVE) {
            log.warn("Student {} is not active (status: {})",
                    student.getId(), student.getStatus());
            return false;
        }

        // Check grade
        if (student.getGrade() == null || student.getGrade().trim().isEmpty()) {
            log.warn("Student {} has no grade assigned", student.getId());
            return false;
        }

        // Check if fee structure exists
        String studentGrade = student.getGrade();
        String numericGrade = extractNumericGrade(studentGrade);

        boolean feeStructureExists = false;

        if (numericGrade != null) {
            feeStructureExists = gradeTermFeeRepository
                    .existsByAcademicTermIdAndGrade(term.getId(), numericGrade);

            if (!feeStructureExists) {
                List<GradeTermFee> termFees = gradeTermFeeRepository.findByAcademicTermId(term.getId());
                feeStructureExists = termFees.stream()
                        .anyMatch(fee -> gradesMatch(studentGrade, fee.getGrade()));
            }
        }

        if (!feeStructureExists) {
            log.warn("No fee structure for grade {} (numeric: {}) in term {}",
                    studentGrade, numericGrade, term.getId());
            return false;
        }

        return true;
    }

    /**
     * Process auto-billing for multiple students with better transaction management
     */
    private AutoBillingResult processAutoBilling(AcademicTerm term, List<Student> students) {
        log.debug("Processing auto-billing for {} students in term {}", students.size(), term.getId());

        int billedCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();
        List<String> successfulBills = new ArrayList<>();

        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);

            // Clear Hibernate session periodically to avoid memory issues
            if (i % 20 == 0 && i > 0) {
                log.debug("Clearing Hibernate session after processing {} students", i);
            }

            try {
                log.debug("Processing student {}/{}: {} (ID: {})",
                        i + 1, students.size(), student.getFullName(), student.getId());

                // Check if already billed (fresh check each iteration)
                boolean alreadyBilled = studentTermAssignmentRepository
                        .existsByStudentIdAndAcademicTermId(student.getId(), term.getId());

                if (alreadyBilled) {
                    log.debug("↪ Student {} already billed, skipping", student.getId());
                    skippedCount++;
                    continue;
                }

                if (autoBillStudent(student, term)) {
                    billedCount++;
                    successfulBills.add(String.format("%s (%s)", student.getFullName(), student.getGrade()));
                    log.debug("✓ Successfully billed student {} ({})", student.getId(), student.getGrade());
                } else {
                    skippedCount++;
                    log.debug("↪ Skipped billing for student {} ({})", student.getId(), student.getGrade());
                }

            } catch (Exception e) {
                skippedCount++;
                String errorMsg = String.format("Student %s (%s): %s",
                        student.getFullName(), student.getGrade(), e.getMessage());
                errors.add(errorMsg);
                log.error("❌ Failed to bill student {}: {}", student.getId(), e.getMessage(), e);
            }

            // Progress logging
            if ((i + 1) % 10 == 0 || (i + 1) == students.size()) {
                log.info("📊 Progress: {}/{} students processed ({} billed, {} skipped, {} errors)",
                        i + 1, students.size(), billedCount, skippedCount, errors.size());
            }
        }

        return AutoBillingResult.builder()
                .success(true)
                .message(String.format("Auto-billing completed: %d billed, %d skipped", billedCount, skippedCount))
                .billedCount(billedCount)
                .skippedCount(skippedCount)
                .errors(errors)
                .successfulBills(successfulBills)
                .termName(term.getTermName())
                .academicYear(term.getAcademicYear())
                .build();
    }

    /**
     * Auto-bill individual student
     */
    private boolean autoBillStudent(Student student, AcademicTerm term) {
        log.debug("=== Auto-billing Student ===");
        log.debug("Student: {} (ID: {})", student.getFullName(), student.getId());
        log.debug("Student Grade: {}", student.getGrade());
        log.debug("Term: {} (ID: {})", term.getTermName(), term.getId());

        // Check if already billed using EXISTS query (avoids transient issues)
        boolean alreadyBilled = studentTermAssignmentRepository
                .existsByStudentIdAndAcademicTermId(student.getId(), term.getId());

        if (alreadyBilled) {
            log.debug("Student {} already billed for term {}", student.getId(), term.getId());
            return false;
        }

        // Validate student can be billed
        if (!canAutoBillStudent(student, term)) {
            log.debug("Student {} failed validation for auto-billing in term {}",
                    student.getId(), term.getId());
            return false;
        }

        try {
            // Create term assignment in a separate transaction
            return createTermAssignmentForStudent(student, term);

        } catch (Exception e) {
            log.error("Failed to auto-bill student {}: {}", student.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Create term assignment - SIMPLIFIED: Use term due date for student
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createTermAssignmentForStudent(Student student, AcademicTerm term) {
        log.debug("Creating term assignment for student {} in term {}",
                student.getFullName(), term.getTermName());

        String studentGrade = student.getGrade();
        String numericGrade = extractNumericGrade(studentGrade);

        // Get fee structure
        GradeTermFee gradeFee = getFeeStructureForGrade(term.getId(), studentGrade, numericGrade)
                .orElseThrow(() -> new RuntimeException(
                        "No fee structure found for grade " + studentGrade + " in term " + term.getTermName()));

        log.info("Using fee structure for grade {} to bill student with grade {}",
                gradeFee.getGrade(), studentGrade);

        // ========== KEY CHANGE: Use term due date for student ==========
        LocalDate termDueDate = term.getFeeDueDate();
        if (termDueDate == null) {
            // Fallback: 30 days from term start
            termDueDate = term.getStartDate().plusDays(30);
            log.debug("Term has no due date, using fallback: {}", termDueDate);
        }

        // Create base assignment WITH TERM DUE DATE
        StudentTermAssignment assignment = createBaseAssignment(student, term, termDueDate);

        // Save the assignment first (without fee items)
        StudentTermAssignment savedAssignment = studentTermAssignmentRepository.save(assignment);

        // Now create and save fee items in a separate step
        createAndAssignFeeItems(savedAssignment, gradeFee, termDueDate);

        // Save the assignment with fee items
        studentTermAssignmentRepository.save(savedAssignment);

        // Create or update fee assignment
        updateFeeAssignment(student, term, savedAssignment);

        // ========== UPDATE STUDENT WITH TERM DUE DATE ==========
        updateStudentWithTermDueDate(student, termDueDate);

        log.info("✅ Auto-billed student {} (grade: {}) for term {}: ₹{}, Term Due Date: {}",
                student.getFullName(), studentGrade, term.getTermName(),
                savedAssignment.getTotalTermFee(), termDueDate);

        return true;
    }

    /**
     * Create base assignment with term due date
     */
    private StudentTermAssignment createBaseAssignment(Student student, AcademicTerm term, LocalDate termDueDate) {
        return StudentTermAssignment.builder()
                .student(student)
                .academicTerm(term)
                .dueDate(termDueDate)
                .billingDate(LocalDate.now())
                .isBilled(true)
                .termFeeStatus(StudentTermAssignment.FeeStatus.PENDING)
                .feeItems(new ArrayList<>()) // Initialize empty list
                .build();
    }

    /**
     * Create and assign fee items with term due date
     */
    private void createAndAssignFeeItems(StudentTermAssignment assignment, GradeTermFee gradeFee, LocalDate termDueDate) {
        List<TermFeeItem> feeItems = new ArrayList<>();
        LocalDate dueDate = termDueDate; // Use term due date for all items
        int sequence = 1;

        log.debug("Creating fee items for student {} with term due date {}",
                assignment.getStudent().getFullName(), dueDate);

        // Create fee items (but don't save yet)
        createFeeItemIfPositive(feeItems, assignment, "Tuition Fee", TermFeeItem.FeeType.TUITION,
                gradeFee.getTuitionFee(), dueDate, sequence++);
        createFeeItemIfPositive(feeItems, assignment, "Basic Fee", TermFeeItem.FeeType.BASIC,
                gradeFee.getBasicFee(), dueDate, sequence++);
        createFeeItemIfPositive(feeItems, assignment, "Examination Fee", TermFeeItem.FeeType.EXAMINATION,
                gradeFee.getExaminationFee(), dueDate, sequence++);

        // Add transport fee only if applicable
        Student student = assignment.getStudent();
        if (student.getTransportMode() != null &&
                student.getTransportMode() != Student.TransportMode.WALKING) {
            createFeeItemIfPositive(feeItems, assignment, "Transport Fee", TermFeeItem.FeeType.TRANSPORT,
                    gradeFee.getTransportFee(), dueDate, sequence++);
            log.debug("Added transport fee for student (mode: {})", student.getTransportMode());
        }

        // Add optional fees
        createFeeItemIfPositive(feeItems, assignment, "Library Fee", TermFeeItem.FeeType.LIBRARY,
                gradeFee.getLibraryFee(), dueDate, sequence++);
        createFeeItemIfPositive(feeItems, assignment, "Sports Fee", TermFeeItem.FeeType.SPORTS,
                gradeFee.getSportsFee(), dueDate, sequence++);
        createFeeItemIfPositive(feeItems, assignment, "Activity Fee", TermFeeItem.FeeType.ACTIVITY,
                gradeFee.getActivityFee(), dueDate, sequence++);
        createFeeItemIfPositive(feeItems, assignment, "Hostel Fee", TermFeeItem.FeeType.HOSTEL,
                gradeFee.getHostelFee(), dueDate, sequence++);
        createFeeItemIfPositive(feeItems, assignment, "Uniform Fee", TermFeeItem.FeeType.UNIFORM,
                gradeFee.getUniformFee(), dueDate, sequence++);
        createFeeItemIfPositive(feeItems, assignment, "Book Fee", TermFeeItem.FeeType.BOOKS,
                gradeFee.getBookFee(), dueDate, sequence++);
        createFeeItemIfPositive(feeItems, assignment, "Other Fees", TermFeeItem.FeeType.OTHER,
                gradeFee.getOtherFees(), dueDate, sequence++);

        // Save fee items first
        if (!feeItems.isEmpty()) {
            List<TermFeeItem> savedItems = termFeeItemRepository.saveAll(feeItems);
            // Add saved items to assignment
            assignment.addFeeItems(savedItems);
        }

        // Recalculate amounts
        assignment.calculateAmounts();

        log.info("📋 Created {} fee items totaling ₹{} with due date {}",
                feeItems.size(),
                feeItems.stream().mapToDouble(TermFeeItem::getAmount).sum(),
                dueDate);
    }

    /**
     * Helper method to create fee items
     */
    private void createFeeItemIfPositive(List<TermFeeItem> feeItems, StudentTermAssignment assignment,
                                         String itemName, TermFeeItem.FeeType feeType, Double amount,
                                         LocalDate dueDate, int sequence) {
        if (amount != null && amount > 0) {
            TermFeeItem item = TermFeeItem.builder()
                    .studentTermAssignment(assignment)
                    .itemName(itemName)
                    .feeType(feeType)
                    .itemType("BASIC")
                    .amount(amount)
                    .originalAmount(amount)
                    .dueDate(dueDate)
                    .isAutoGenerated(true)
                    .isMandatory(true)
                    .sequenceOrder(sequence)
                    .status(getInitialFeeStatus(dueDate))
                    .build();

            feeItems.add(item);
        }
    }

    /**
     * Update student with term due date - USE MANUAL METHOD
     */
    private void updateStudentWithTermDueDate(Student student, LocalDate termDueDate) {
        try {
            // USE MANUAL METHOD to bypass automatic calculation
            student.setFeeDueDateManually(termDueDate);

            // Update student fee totals based on term assignments
            updateStudentFeeTotals(student);

            // Save will trigger status update via entity logic
            studentRepository.save(student);

            log.debug("📅 Manually updated student {} due date to: {}", student.getId(), termDueDate);

        } catch (Exception e) {
            log.error("❌ Error updating student due date for {}: {}",
                    student.getId(), e.getMessage());
        }
    }

    /**
     * Update student fee totals from term assignments
     */
    private void updateStudentFeeTotals(Student student) {
        try {
            // Get all term assignments for student
            List<StudentTermAssignment> assignments = studentTermAssignmentRepository
                    .findByStudentId(student.getId());

            if (assignments.isEmpty()) {
                return;
            }

            // Calculate totals
            double totalTermFee = assignments.stream()
                    .mapToDouble(StudentTermAssignment::getTotalTermFee)
                    .sum();

            double totalPaid = assignments.stream()
                    .mapToDouble(StudentTermAssignment::getPaidAmount)
                    .sum();

            double totalPending = Math.max(0, totalTermFee - totalPaid);

            // Update student fields
            student.setTotalFee(totalTermFee);
            student.setPaidAmount(totalPaid);
            student.setPendingAmount(totalPending);

            log.debug("💰 Updated student {} fee totals: Total ₹{}, Paid ₹{}, Pending ₹{}",
                    student.getId(), totalTermFee, totalPaid, totalPending);

        } catch (Exception e) {
            log.error("Error updating student fee totals for {}: {}", student.getId(), e.getMessage());
        }
    }

    /**
     * Update student fee status based on due date
     */
    private void updateStudentFeeStatus(Student student) {
        if (student.getPendingAmount() != null && student.getPendingAmount() <= 0) {
            student.setFeeStatus(Student.FeeStatus.PAID);
            return;
        }

        if (student.getFeeDueDate() == null) {
            if (student.getPaidAmount() != null && student.getPaidAmount() > 0) {
                student.setFeeStatus(Student.FeeStatus.PARTIAL);
            } else {
                student.setFeeStatus(Student.FeeStatus.PENDING);
            }
            return;
        }

        LocalDate today = LocalDate.now();

        if (today.isAfter(student.getFeeDueDate())) {
            student.setFeeStatus(Student.FeeStatus.OVERDUE);
        } else if (student.getPaidAmount() != null && student.getPaidAmount() > 0) {
            student.setFeeStatus(Student.FeeStatus.PARTIAL);
        } else {
            student.setFeeStatus(Student.FeeStatus.PENDING);
        }
    }

    /**
     * Get fee structure for grade
     */
    private Optional<GradeTermFee> getFeeStructureForGrade(Long termId, String studentGrade, String numericGrade) {
        if (numericGrade != null) {
            Optional<GradeTermFee> exactMatch = gradeTermFeeRepository
                    .findByAcademicTermIdAndGrade(termId, numericGrade);

            if (exactMatch.isPresent()) {
                return exactMatch;
            }
        }

        // Fallback to checking all structures
        List<GradeTermFee> termFees = gradeTermFeeRepository.findByAcademicTermId(termId);
        return termFees.stream()
                .filter(fee -> gradesMatch(studentGrade, fee.getGrade()))
                .findFirst();
    }

    // ========== PAYMENT PROCESSING ==========

    /**
     * Apply payment to student's fee items (FIFO logic)
     */
    @Transactional
    public PaymentApplicationResponse applyPaymentToStudent(PaymentApplicationRequest request) {
        long startTime = System.currentTimeMillis();

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found: " + request.getStudentId()));

        log.info("💰 Applying payment of ₹{} to student {}", request.getAmount(), student.getFullName());

        PaymentApplicationResponse response = PaymentApplicationResponse.fromStudent(student, request.getAmount());
        double remainingPayment = request.getAmount();

        // Get unpaid items - fresh query every time
        List<TermFeeItem> unpaidItems = termFeeItemRepository
                .findUnpaidItemsByStudentOrdered(request.getStudentId());

        if (unpaidItems.isEmpty()) {
            log.warn("⚠️ No unpaid fee items found for student {}", student.getFullName());
            response.setAppliedPayment(0.0);
            response.setRemainingPayment(request.getAmount());
            response.setAllPaid(true);
            return response;
        }

        List<PaymentApplicationResponse.AppliedItem> appliedItems = new ArrayList<>();
        List<TermFeeItem> itemsToUpdate = new ArrayList<>();

        // Apply payment to items (FIFO)
        for (TermFeeItem item : unpaidItems) {
            if (remainingPayment <= 0) break;

            double pendingAmount = item.getPendingAmount() != null ?
                    item.getPendingAmount() :
                    item.getAmount();

            double amountToApply = Math.min(remainingPayment, pendingAmount);

            if (amountToApply > 0) {
                PaymentApplicationResponse.AppliedItem appliedItem =
                        new PaymentApplicationResponse.AppliedItem();
                appliedItem.setItemId(item.getId());
                appliedItem.setItemName(item.getItemName());
                appliedItem.setFeeType(item.getFeeType().name());
                appliedItem.setAmountApplied(amountToApply);

                // Apply payment
                double newPaidAmount = (item.getPaidAmount() != null ? item.getPaidAmount() : 0.0) + amountToApply;
                item.setPaidAmount(newPaidAmount);

                // Update pending amount
                double newPendingAmount = Math.max(0, item.getAmount() - newPaidAmount);
                item.setPendingAmount(newPendingAmount);

                // Update status
                if (newPaidAmount >= item.getAmount()) {
                    item.setStatus(TermFeeItem.FeeStatus.PAID);
                    item.setPaidDate(LocalDate.now());
                } else if (newPaidAmount > 0) {
                    item.setStatus(TermFeeItem.FeeStatus.PARTIAL);
                }

                // Check if overdue
                if (item.getDueDate() != null &&
                        LocalDate.now().isAfter(item.getDueDate()) &&
                        newPaidAmount < item.getAmount()) {
                    item.setStatus(TermFeeItem.FeeStatus.OVERDUE);
                }

                appliedItem.setNewStatus(item.getStatus().name());
                appliedItem.setRemainingBalance(remainingPayment - amountToApply);
                appliedItems.add(appliedItem);

                remainingPayment -= amountToApply;
                itemsToUpdate.add(item);

                log.debug("   Applied ₹{} to {} (ID: {}, Remaining: ₹{})",
                        amountToApply, item.getItemName(), item.getId(), item.getPendingAmount());
            }
        }

        // Save all updated items
        if (!itemsToUpdate.isEmpty()) {
            termFeeItemRepository.saveAll(itemsToUpdate);
        }

        response.setAppliedItems(appliedItems);

        // Update term assignments
        updateTermAssignmentsAfterPayment(request.getStudentId());

        // Handle any overpayment
        if (remainingPayment > 0) {
            handleOverpayment(student, remainingPayment, request);
        }

        response.calculateAppliedTotal();
        response.setRemainingPayment(remainingPayment);

        // Check if all items are paid (fresh query)
        boolean allPaid = termFeeItemRepository.findUnpaidItemsByStudentOrdered(request.getStudentId()).isEmpty();
        response.setAllPaid(allPaid);

        // ========== UPDATE STUDENT AFTER PAYMENT ==========
        updateStudentAfterPayment(student);

        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ Payment applied in {}ms: ₹{} used, ₹{} remaining, All paid: {}",
                duration, request.getAmount() - remainingPayment,
                remainingPayment, response.getAllPaid());

        return response;
    }

    /**
     * Update student after payment
     */
    private void updateStudentAfterPayment(Student student) {
        try {
            // Recalculate student totals
            updateStudentFeeTotals(student);

            // Clear due date if all paid - USE MANUAL METHOD
            if (student.getPendingAmount() != null && student.getPendingAmount() <= 0) {
                student.clearFeeDueDateManually();
            }

            studentRepository.save(student);

            log.debug("📊 Updated student {} after payment: Status = {}, Due Date = {}",
                    student.getId(), student.getFeeStatus(), student.getFeeDueDate());

        } catch (Exception e) {
            log.error("Error updating student after payment for {}: {}", student.getId(), e.getMessage());
        }
    }

    /**
     * Update term assignments after payment
     */
    private void updateTermAssignmentsAfterPayment(Long studentId) {
        // Get all term assignments for this student
        List<StudentTermAssignment> assignments = studentTermAssignmentRepository
                .findByStudentId(studentId);

        // Update each assignment
        for (StudentTermAssignment assignment : assignments) {
            assignment.calculateAmounts();
        }

        // Bulk save
        if (!assignments.isEmpty()) {
            studentTermAssignmentRepository.saveAll(assignments);
        }

        // Update student fee assignment
        updateStudentFeeAssignment(studentId);
    }

    /**
     * Update student fee assignment
     */
    private void updateStudentFeeAssignment(Long studentId) {
        // Get current academic year
        Optional<AcademicTerm> currentTerm = termService.getCurrentTerm();
        if (currentTerm.isEmpty()) return;

        String academicYear = currentTerm.get().getAcademicYear();

        studentFeeAssignmentRepository
                .findByStudentIdAndAcademicYear(studentId, academicYear)
                .ifPresent(this::recalculateFeeAssignment);
    }

    /**
     * Recalculate fee assignment
     */
    private void recalculateFeeAssignment(StudentFeeAssignment assignment) {
        // Get term assignments for academic year
        List<StudentTermAssignment> termAssignments = getTermAssignmentsForAcademicYear(
                assignment.getStudent().getId(), assignment.getAcademicYear());

        double total = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getTotalTermFee)
                .sum();
        double paid = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPaidAmount)
                .sum();
        double pending = total - paid;

        assignment.setTotalAmount(total);
        assignment.setPaidAmount(paid);
        assignment.setPendingAmount(pending);

        // Update status
        if (pending <= 0) {
            assignment.setFeeStatus(FeeStatus.PAID);
        } else if (paid > 0) {
            assignment.setFeeStatus(FeeStatus.PARTIAL);
        } else if (assignment.getDueDate() != null &&
                LocalDate.now().isAfter(assignment.getDueDate())) {
            assignment.setFeeStatus(FeeStatus.OVERDUE);
        } else {
            assignment.setFeeStatus(FeeStatus.PENDING);
        }

        assignment.setUpdatedAt(LocalDateTime.now());
        studentFeeAssignmentRepository.save(assignment);
    }

    /**
     * Handle overpayment
     */
    private void handleOverpayment(Student student, Double amount, PaymentApplicationRequest request) {
        if (Boolean.TRUE.equals(request.getApplyToFutureTerms())) {
            applyOverpaymentToFutureTerms(student, amount);
        } else {
            createPaymentCredit(student, amount, request.getReference());
        }
        log.info("💸 Overpayment of ₹{} for student {}", amount, student.getFullName());
    }

    /**
     * Apply overpayment to future terms
     */
    private void applyOverpaymentToFutureTerms(Student student, Double amount) {
        List<AcademicTerm> upcomingTerms = termService.getUpcomingAndCurrentTerms();
        double remainingAmount = amount;

        for (AcademicTerm term : upcomingTerms) {
            if (remainingAmount <= 0) break;

            Optional<StudentTermAssignment> assignmentOpt = studentTermAssignmentRepository
                    .findByStudentIdAndAcademicTermId(student.getId(), term.getId());

            if (assignmentOpt.isPresent()) {
                remainingAmount = applyPaymentToTermAssignment(assignmentOpt.get(), remainingAmount);
            }
        }

        if (remainingAmount > 0) {
            createPaymentCredit(student, remainingAmount, "Overpayment Credit");
        }
    }

    /**
     * Apply payment to term assignment
     */
    private double applyPaymentToTermAssignment(StudentTermAssignment assignment, double amount) {
        List<TermFeeItem> unpaidItems = termFeeItemRepository
                .findByStudentTermAssignmentId(assignment.getId());

        double remainingAmount = amount;

        for (TermFeeItem item : unpaidItems) {
            if (remainingAmount <= 0) break;

            double pending = item.getPendingAmount();
            double toApply = Math.min(remainingAmount, pending);

            if (toApply > 0) {
                item.setPaidAmount(item.getPaidAmount() + toApply);
                item.setPendingAmount(pending - toApply);
                remainingAmount -= toApply;
                termFeeItemRepository.save(item);
            }
        }

        assignment.calculateAmounts();
        studentTermAssignmentRepository.save(assignment);
        return remainingAmount;
    }

    /**
     * Create payment credit
     */
    private void createPaymentCredit(Student student, Double amount, String reference) {
        termService.getCurrentTerm().ifPresent(currentTerm -> {
            StudentTermAssignment assignment = studentTermAssignmentRepository
                    .findByStudentIdAndAcademicTermId(student.getId(), currentTerm.getId())
                    .orElseGet(() -> {
                        LocalDate termDueDate = currentTerm.getFeeDueDate() != null ?
                                currentTerm.getFeeDueDate() : currentTerm.getStartDate().plusDays(30);
                        StudentTermAssignment newAssignment = createBaseAssignment(student, currentTerm, termDueDate);
                        return studentTermAssignmentRepository.save(newAssignment);
                    });

            TermFeeItem creditItem = TermFeeItem.builder()
                    .studentTermAssignment(assignment)
                    .itemName("Payment Credit")
                    .feeType(TermFeeItem.FeeType.DISCOUNT)
                    .itemType("DISCOUNT")
                    .amount(-amount)
                    .originalAmount(-amount)
                    .dueDate(assignment.getDueDate())
                    .isAutoGenerated(false)
                    .isMandatory(false)
                    .sequenceOrder(999)
                    .notes(String.format("Credit from payment %s", reference))
                    .build();

            termFeeItemRepository.save(creditItem);
            assignment.addFeeItem(creditItem);
            assignment.calculateAmounts();
            studentTermAssignmentRepository.save(assignment);

            // Update student using manual method if needed
            updateStudentFeeTotals(student);

            // Check if we need to clear due date
            if (student.getPendingAmount() != null && student.getPendingAmount() <= 0) {
                student.clearFeeDueDateManually();
            }

            studentRepository.save(student);
        });
    }

    // ========== ADDITIONAL FEES ==========

    /**
     * Add additional fee item for student
     */
    @Transactional
    public TermFeeItem addAdditionalFeeItem(AdditionalFeeRequestDto request) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found: " + request.getStudentId()));

        AcademicTerm term = termService.getTermById(request.getTermId())
                .orElseThrow(() -> new RuntimeException("Term not found: " + request.getTermId()));

        // Get or create term assignment
        StudentTermAssignment termAssignment = studentTermAssignmentRepository
                .findByStudentIdAndAcademicTermId(student.getId(), term.getId())
                .orElseGet(() -> {
                    LocalDate termDueDate = term.getFeeDueDate() != null ?
                            term.getFeeDueDate() : term.getStartDate().plusDays(30);
                    return createBaseAssignment(student, term, termDueDate);
                });

        TermFeeItem additionalItem = createAdditionalFeeItem(termAssignment, request);
        TermFeeItem savedItem = termFeeItemRepository.save(additionalItem);

        // Update term assignment
        termAssignment.addFeeItem(savedItem);
        studentTermAssignmentRepository.save(termAssignment);

        // Update student
        updateStudentFeeTotals(student);
        studentRepository.save(student);

        log.info("➕ Added additional fee item '{}' (₹{}) for student {}",
                request.getItemName(), request.getAmount(), student.getFullName());

        return savedItem;
    }

    /**
     * Bulk add additional fees to multiple students
     */
    @Transactional
    public BulkFeeResult bulkAddAdditionalFees(BulkFeeRequest request) {
        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();
        List<String> successes = new ArrayList<>();

        for (Long studentId : request.getStudentIds()) {
            try {
                AdditionalFeeRequestDto dto = mapToAdditionalFeeRequest(request, studentId);
                addAdditionalFeeItem(dto);
                successCount++;
                successes.add(String.format("Student %d: Success", studentId));
            } catch (Exception e) {
                failedCount++;
                errors.add(String.format("Student %d: %s", studentId, e.getMessage()));
                log.error("Failed to add fee to student {}: {}", studentId, e.getMessage());
            }
        }

        return BulkFeeResult.builder()
                .totalStudents(request.getStudentIds().size())
                .successCount(successCount)
                .failedCount(failedCount)
                .errors(errors)
                .successes(successes)
                .totalAmount(request.getAmount() * successCount)
                .build();
    }

    /**
     * Get additional fees for student
     */
    public List<TermFeeItem> getStudentAdditionalFees(Long studentId) {
        return termFeeItemRepository.findAdditionalFeesForStudent(studentId);
    }

    /**
     * Delete additional fee item
     */
    @Transactional
    public boolean deleteAdditionalFee(Long feeItemId) {
        return termFeeItemRepository.findById(feeItemId)
                .map(item -> {
                    if (!item.getIsAutoGenerated()) {
                        StudentTermAssignment assignment = item.getStudentTermAssignment();
                        termFeeItemRepository.delete(item);
                        assignment.calculateAmounts();
                        studentTermAssignmentRepository.save(assignment);

                        // Update student
                        Student student = assignment.getStudent();
                        updateStudentFeeTotals(student);
                        studentRepository.save(student);

                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    // ========== DASHBOARD & REPORTING ==========

    /**
     * Get grade fee dashboard data
     */
    public GradeFeeDashboardResponse getGradeFeeDashboard(String grade, Long termId) {
        // Validate term exists
        AcademicTerm term = termService.getTermById(termId)
                .orElseThrow(() -> new RuntimeException("Term not found: " + termId));

        List<Student> gradeStudents = studentRepository.findByGrade(grade);
        List<StudentTermAssignment> termAssignments = studentTermAssignmentRepository
                .findByAcademicTermIdAndStudentGrade(termId, grade);

        GradeFeeDashboardResponse dashboard = new GradeFeeDashboardResponse();
        dashboard.setGrade(grade);
        dashboard.setTermName(term.getTermName());
        dashboard.setAcademicYear(term.getAcademicYear());

        // Calculate statistics
        calculateDashboardStatistics(dashboard, gradeStudents, termAssignments);

        // Payment status breakdown
        dashboard.setPaymentStatus(calculatePaymentStatus(termAssignments));

        // Fee type breakdown
        dashboard.setFeeBreakdown(calculateFeeBreakdown(termAssignments));

        // Top defaulters
        dashboard.setTopDefaulters(getTopDefaulters(termAssignments));

        // Fee structure
        getFeeStructure(termId, grade)
                .ifPresent(feeStructure ->
                        dashboard.setFeeStructure(mapToFeeStructureResponse(feeStructure)));

        return dashboard;
    }

    /**
     * Get school-wide fee summary
     */
    @Transactional(readOnly = true)
    public SchoolFeeSummary getSchoolFeeSummary() {
        SchoolFeeSummary summary = new SchoolFeeSummary();
        summary.setTimestamp(LocalDateTime.now());

        try {
            // Get current term info
            Optional<AcademicTerm> currentTermOpt = termService.getCurrentTerm();
            if (currentTermOpt.isPresent()) {
                AcademicTerm term = currentTermOpt.get();
                summary.setCurrentTerm(term.getTermName());
                summary.setAcademicYear(term.getAcademicYear());
                summary.setCurrentTermId(term.getId());
            }

            // Get all active students
            List<Student> activeStudents = studentRepository.findByStatus(Student.StudentStatus.ACTIVE);

            if (activeStudents == null || activeStudents.isEmpty()) {
                return summary; // Return empty summary
            }

            summary.setTotalStudents((long) activeStudents.size());
            summary.setActiveStudents((long) activeStudents.size());

            // Calculate fee statistics from student data
            double totalExpected = 0.0;
            double totalCollected = 0.0;
            double totalPending = 0.0;
            long paidStudents = 0L;
            long pendingStudents = 0L;
            long overdueStudents = 0L;

            for (Student student : activeStudents) {
                totalExpected += student.getTotalFee() != null ? student.getTotalFee() : 0.0;
                totalCollected += student.getPaidAmount() != null ? student.getPaidAmount() : 0.0;
                totalPending += student.getPendingAmount() != null ? student.getPendingAmount() : 0.0;

                // Count students by fee status
                Student.FeeStatus feeStatus = student.getFeeStatus();
                if (feeStatus != null) {
                    switch (feeStatus) {
                        case PAID:
                            paidStudents++;
                            break;
                        case PENDING:
                            pendingStudents++;
                            break;
                        case OVERDUE:
                            overdueStudents++;
                            break;
                        case PARTIAL:
                            pendingStudents++; // Count partial as pending
                            break;
                    }
                }
            }

            summary.setTotalExpectedFee(totalExpected);
            summary.setTotalCollected(totalCollected);
            summary.setTotalPending(totalPending);
            summary.setPaidStudents(paidStudents);
            summary.setPendingStudents(pendingStudents);
            summary.setOverdueStudents(overdueStudents);

            // Calculate collection rate
            if (totalExpected > 0) {
                double collectionRate = (totalCollected / totalExpected) * 100;
                summary.setCollectionRate(Math.round(collectionRate * 100.0) / 100.0);
            } else {
                summary.setCollectionRate(0.0);
            }

            // Calculate grade-wise summaries
            Map<String, SchoolFeeSummary.GradeSummary> gradeSummaries = new HashMap<>();

            // Group students by grade
            Map<String, List<Student>> studentsByGrade = activeStudents.stream()
                    .filter(student -> student.getGrade() != null)
                    .collect(Collectors.groupingBy(Student::getGrade));

            for (Map.Entry<String, List<Student>> entry : studentsByGrade.entrySet()) {
                String grade = entry.getKey();
                List<Student> gradeStudents = entry.getValue();

                SchoolFeeSummary.GradeSummary gradeSummary = new SchoolFeeSummary.GradeSummary();
                gradeSummary.setTotalStudents((long) gradeStudents.size());

                double gradeTotal = 0.0;
                double gradePaid = 0.0;
                double gradePending = 0.0;
                long gradePaidCount = 0L;
                long gradePendingCount = 0L;
                long gradeOverdueCount = 0L;

                for (Student student : gradeStudents) {
                    gradeTotal += student.getTotalFee() != null ? student.getTotalFee() : 0.0;
                    gradePaid += student.getPaidAmount() != null ? student.getPaidAmount() : 0.0;
                    gradePending += student.getPendingAmount() != null ? student.getPendingAmount() : 0.0;

                    Student.FeeStatus feeStatus = student.getFeeStatus();
                    if (feeStatus != null) {
                        switch (feeStatus) {
                            case PAID:
                                gradePaidCount++;
                                break;
                            case PENDING:
                                gradePendingCount++;
                                break;
                            case OVERDUE:
                                gradeOverdueCount++;
                                break;
                            case PARTIAL:
                                gradePendingCount++;
                                break;
                        }
                    }
                }

                gradeSummary.setTotalFee(gradeTotal);
                gradeSummary.setTotalCollected(gradePaid);
                gradeSummary.setTotalPending(gradePending);
                gradeSummary.setPaidStudents(gradePaidCount);
                gradeSummary.setPendingStudents(gradePendingCount);
                gradeSummary.setOverdueStudents(gradeOverdueCount);

                if (gradeTotal > 0) {
                    double gradeCollectionRate = (gradePaid / gradeTotal) * 100;
                    gradeSummary.setCollectionRate(Math.round(gradeCollectionRate * 100.0) / 100.0);
                } else {
                    gradeSummary.setCollectionRate(0.0);
                }

                gradeSummaries.put(grade, gradeSummary);
            }

            summary.setGradeSummaries(gradeSummaries);

            log.info("Generated school fee summary: {} students, ₹{} collected, {}% rate",
                    summary.getTotalStudents(), summary.getTotalCollected(), summary.getCollectionRate());

        } catch (Exception e) {
            log.error("Error generating school fee summary: {}", e.getMessage(), e);
        }

        return summary;
    }

    // ========== STUDENT PAYMENT HISTORY ==========

    /**
     * Get student's payment history
     */
    @Transactional(readOnly = true)
    public PaymentHistoryResponse getStudentPaymentHistory(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        // Get payment transactions for student
        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByStudentIdOrderByPaymentDateDesc(studentId);

        // Get term assignments for context
        List<StudentTermAssignment> termAssignments = studentTermAssignmentRepository
                .findByStudentIdOrderByTermDateDesc(studentId);

        PaymentHistoryResponse response = new PaymentHistoryResponse();
        response.setStudentId(studentId);
        response.setStudentName(student.getFullName());
        response.setStudentCode(student.getStudentId());
        response.setGrade(student.getGrade());
        response.setTotalPayments(transactions.size());

        double totalAmountPaid = transactions.stream()
                .mapToDouble(PaymentTransaction::getAmount)
                .sum();
        response.setTotalAmountPaid(totalAmountPaid);

        // Set last payment date if available
        if (!transactions.isEmpty()) {
            response.setLastPaymentDate(transactions.get(0).getPaymentDate());
        }

        // Map transactions to DTOs
        List<PaymentHistoryResponse.PaymentDetail> paymentDetails = transactions.stream()
                .map(this::mapToPaymentDetail)
                .collect(Collectors.toList());
        response.setPaymentHistory(paymentDetails);

        // Map term context
        Map<Long, String> termMap = new HashMap<>();
        for (StudentTermAssignment assignment : termAssignments) {
            if (assignment.getAcademicTerm() != null) {
                termMap.put(assignment.getId(),
                        String.format("%s - %s",
                                assignment.getAcademicTerm().getTermName(),
                                assignment.getAcademicTerm().getAcademicYear()));
            }
        }
        response.setTermContext(termMap);

        return response;
    }

    // ========== OVERDUE FEES REPORT ==========

    /**
     * Get overdue fees report
     */
    @Transactional(readOnly = true)
    public OverdueFeesReport getOverdueFeesReport() {
        LocalDate today = LocalDate.now();

        // Find all overdue fee items
        List<TermFeeItem> overdueItems = termFeeItemRepository
                .findByDueDateBeforeAndStatus(today, TermFeeItem.FeeStatus.PENDING);

        // Group by student
        Map<Long, List<TermFeeItem>> itemsByStudent = overdueItems.stream()
                .collect(Collectors.groupingBy(item ->
                        item.getStudentTermAssignment().getStudent().getId()));

        // Calculate overall statistics
        double totalOverdueAmount = overdueItems.stream()
                .mapToDouble(TermFeeItem::getPendingAmount)
                .sum();

        // Prepare response
        OverdueFeesReport report = new OverdueFeesReport();
        report.setGeneratedDate(LocalDateTime.now());
        report.setReportPeriod("Current");
        report.setTotalOverdueAmount(totalOverdueAmount);
        report.setTotalStudents(itemsByStudent.size());
        report.setTotalOverdueItems(overdueItems.size());
        report.setOverdueThresholdDays(30);

        // Create overdue student list
        List<OverdueFeesReport.OverdueStudent> overdueStudents = new ArrayList<>();

        for (Map.Entry<Long, List<TermFeeItem>> entry : itemsByStudent.entrySet()) {
            Student student = studentRepository.findById(entry.getKey()).orElse(null);
            if (student != null) {
                List<TermFeeItem> studentOverdueItems = entry.getValue();

                double studentOverdue = studentOverdueItems.stream()
                        .mapToDouble(TermFeeItem::getPendingAmount)
                        .sum();

                OverdueFeesReport.OverdueStudent overdueStudent = new OverdueFeesReport.OverdueStudent();
                overdueStudent.setStudentId(student.getId());
                overdueStudent.setStudentName(student.getFullName());
                overdueStudent.setStudentCode(student.getStudentId());
                overdueStudent.setGrade(student.getGrade());
                overdueStudent.setClassName(student.getGrade());
                overdueStudent.setTotalOverdueAmount(studentOverdue);
                overdueStudent.setOverdueItemsCount(studentOverdueItems.size());

                // Calculate earliest and latest due dates
                Optional<LocalDate> earliestDate = studentOverdueItems.stream()
                        .map(TermFeeItem::getDueDate)
                        .min(LocalDate::compareTo);
                Optional<LocalDate> latestDate = studentOverdueItems.stream()
                        .map(TermFeeItem::getDueDate)
                        .max(LocalDate::compareTo);

                earliestDate.ifPresent(overdueStudent::setEarliestDueDate);
                latestDate.ifPresent(overdueStudent::setLatestDueDate);

                // Calculate days overdue
                if (earliestDate.isPresent()) {
                    long daysOverdue = ChronoUnit.DAYS.between(earliestDate.get(), today);
                    overdueStudent.setDaysOverdue((int) Math.max(0, daysOverdue));
                }

                // Get parent/guardian info
                overdueStudent.setParentName(student.getEmergencyContactName());
                overdueStudent.setParentPhone(student.getEmergencyContactPhone());
                overdueStudent.setParentEmail(student.getEmail());

                // Get reminder info from term assignment
                studentTermAssignmentRepository.findCurrentTermAssignmentForStudent(student.getId())
                        .ifPresent(assignment -> {
                            overdueStudent.setRemindersSent(assignment.getRemindersSent());
                            overdueStudent.setLastReminderDate(assignment.getLastReminderDate());
                        });

                overdueStudents.add(overdueStudent);
            }
        }

        // Sort by overdue amount (descending)
        overdueStudents.sort((a, b) -> Double.compare(b.getTotalOverdueAmount(), a.getTotalOverdueAmount()));
        report.setOverdueItems(overdueStudents);

        // Calculate statistics
        if (!overdueStudents.isEmpty()) {
            report.setAverageOverduePerStudent(totalOverdueAmount / overdueStudents.size());

            // Count students by overdue amount categories
            int high = 0, medium = 0, low = 0;
            for (OverdueFeesReport.OverdueStudent student : overdueStudents) {
                if (student.getTotalOverdueAmount() > 100000) {
                    high++;
                } else if (student.getTotalOverdueAmount() > 50000) {
                    medium++;
                } else {
                    low++;
                }
            }
            report.setStudentsWithHighOverdue(high);
            report.setStudentsWithMediumOverdue(medium);
            report.setStudentsWithLowOverdue(low);
        }

        // Calculate grade-wise breakdown
        Map<String, List<OverdueFeesReport.OverdueStudent>> studentsByGrade = overdueStudents.stream()
                .collect(Collectors.groupingBy(OverdueFeesReport.OverdueStudent::getGrade));

        List<OverdueFeesReport.GradeOverdueSummary> gradeBreakdown = new ArrayList<>();
        for (Map.Entry<String, List<OverdueFeesReport.OverdueStudent>> entry : studentsByGrade.entrySet()) {
            String grade = entry.getKey();
            List<OverdueFeesReport.OverdueStudent> gradeStudents = entry.getValue();

            double gradeTotal = gradeStudents.stream()
                    .mapToDouble(OverdueFeesReport.OverdueStudent::getTotalOverdueAmount)
                    .sum();

            OverdueFeesReport.GradeOverdueSummary summary = new OverdueFeesReport.GradeOverdueSummary();
            summary.setGrade(grade);
            summary.setStudentCount(gradeStudents.size());
            summary.setTotalOverdueAmount(gradeTotal);
            summary.setAverageOverdue(gradeStudents.isEmpty() ? 0 : gradeTotal / gradeStudents.size());

            gradeBreakdown.add(summary);
        }
        report.setGradeBreakdown(gradeBreakdown);

        return report;
    }

    // ========== COLLECTION SUMMARY ==========

    /**
     * Get collection summary for date range
     */
    @Transactional(readOnly = true)
    public CollectionSummaryResponse getCollectionSummary(LocalDate startDate, LocalDate endDate) {
        // Validate date range
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        if (startDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Start date cannot be in the future");
        }

        // Get payments in date range
        List<PaymentTransaction> payments = paymentTransactionRepository
                .findByPaymentDateBetween(startDate.atStartOfDay(),
                        endDate.plusDays(1).atStartOfDay());

        // Calculate overall statistics
        double totalCollections = payments.stream()
                .mapToDouble(PaymentTransaction::getAmount)
                .sum();

        double highestTransaction = payments.stream()
                .mapToDouble(PaymentTransaction::getAmount)
                .max()
                .orElse(0.0);

        double lowestTransaction = payments.stream()
                .mapToDouble(PaymentTransaction::getAmount)
                .filter(amount -> amount > 0)
                .min()
                .orElse(0.0);

        // Create response
        CollectionSummaryResponse response = new CollectionSummaryResponse();
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setGeneratedAt(LocalDateTime.now());
        response.setTotalCollections(totalCollections);
        response.setTransactionsCount(payments.size());
        response.setAverageTransactionAmount(payments.isEmpty() ? 0 : totalCollections / payments.size());
        response.setHighestTransactionAmount(highestTransaction);
        response.setLowestTransactionAmount(lowestTransaction);

        // Daily collections breakdown
        Map<LocalDate, CollectionSummaryResponse.DailyCollection> dailyCollections = new LinkedHashMap<>();
        Map<LocalDate, List<PaymentTransaction>> paymentsByDay = payments.stream()
                .collect(Collectors.groupingBy(tx -> tx.getPaymentDate().toLocalDate()));

        // Initialize all dates in range
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            CollectionSummaryResponse.DailyCollection daily = new CollectionSummaryResponse.DailyCollection();
            daily.setDate(date);
            daily.setAmount(0.0);
            daily.setTransactionCount(0);
            daily.setStudentCount(0);
            dailyCollections.put(date, daily);
        }

        // Fill with actual data
        for (Map.Entry<LocalDate, List<PaymentTransaction>> entry : paymentsByDay.entrySet()) {
            LocalDate date = entry.getKey();
            if (dailyCollections.containsKey(date)) {
                List<PaymentTransaction> dayPayments = entry.getValue();

                double dayAmount = dayPayments.stream()
                        .mapToDouble(PaymentTransaction::getAmount)
                        .sum();

                long uniqueStudents = dayPayments.stream()
                        .map(tx -> tx.getStudent() != null ? tx.getStudent().getId() : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();

                CollectionSummaryResponse.DailyCollection daily = dailyCollections.get(date);
                daily.setAmount(dayAmount);
                daily.setTransactionCount(dayPayments.size());
                daily.setStudentCount((int) uniqueStudents);
            }
        }
        response.setDailyCollections(dailyCollections);

        // Payment method breakdown
        Map<String, CollectionSummaryResponse.PaymentMethodSummary> methodBreakdown = new HashMap<>();
        Map<PaymentMethod, List<PaymentTransaction>> paymentsByMethod = payments.stream()
                .collect(Collectors.groupingBy(PaymentTransaction::getPaymentMethod));

        for (Map.Entry<PaymentMethod, List<PaymentTransaction>> entry : paymentsByMethod.entrySet()) {
            PaymentMethod method = entry.getKey();
            List<PaymentTransaction> methodPayments = entry.getValue();

            double methodAmount = methodPayments.stream()
                    .mapToDouble(PaymentTransaction::getAmount)
                    .sum();

            CollectionSummaryResponse.PaymentMethodSummary summary =
                    new CollectionSummaryResponse.PaymentMethodSummary();
            summary.setMethod(method != null ? method.name() : "UNKNOWN");
            summary.setAmount(methodAmount);
            summary.setTransactionCount(methodPayments.size());
            summary.setPercentage(totalCollections > 0 ? (methodAmount / totalCollections) * 100 : 0);

            methodBreakdown.put(method != null ? method.name() : "UNKNOWN", summary);
        }
        response.setPaymentMethodBreakdown(methodBreakdown);

        // Grade-wise breakdown
        Map<String, CollectionSummaryResponse.GradeCollectionSummary> gradeBreakdown = new HashMap<>();
        Map<String, List<PaymentTransaction>> paymentsByGrade = new HashMap<>();

        for (PaymentTransaction payment : payments) {
            if (payment.getStudent() != null && payment.getStudent().getGrade() != null) {
                String grade = payment.getStudent().getGrade();
                paymentsByGrade.computeIfAbsent(grade, k -> new ArrayList<>()).add(payment);
            }
        }

        for (Map.Entry<String, List<PaymentTransaction>> entry : paymentsByGrade.entrySet()) {
            String grade = entry.getKey();
            List<PaymentTransaction> gradePayments = entry.getValue();

            double gradeAmount = gradePayments.stream()
                    .mapToDouble(PaymentTransaction::getAmount)
                    .sum();

            long uniqueStudents = gradePayments.stream()
                    .map(PaymentTransaction::getStudent)
                    .filter(Objects::nonNull)
                    .map(Student::getId)
                    .distinct()
                    .count();

            CollectionSummaryResponse.GradeCollectionSummary summary =
                    new CollectionSummaryResponse.GradeCollectionSummary();
            summary.setGrade(grade);
            summary.setAmount(gradeAmount);
            summary.setTransactionCount(gradePayments.size());
            summary.setStudentCount((int) uniqueStudents);
            summary.setAveragePerStudent(uniqueStudents > 0 ? gradeAmount / uniqueStudents : 0);

            gradeBreakdown.put(grade, summary);
        }
        response.setGradeBreakdown(gradeBreakdown);

        return response;
    }

    // ========== BULK OPERATIONS ==========

    /**
     * Bulk update fee status for multiple students
     */
    @Transactional
    public BulkUpdateResult bulkUpdateFeeStatus(BulkUpdateRequest request) {
        BulkUpdateResult result = new BulkUpdateResult();
        result.setTimestamp(LocalDateTime.now());
        result.setOperationType("FEE_STATUS_UPDATE");
        result.setPerformedBy(request.getUpdatedBy());
        result.setTotalStudents(request.getStudentIds().size());

        List<String> errors = new ArrayList<>();
        List<String> successes = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<BulkUpdateResult.StudentUpdateDetail> studentDetails = new ArrayList<>();

        for (Long studentId : request.getStudentIds()) {
            try {
                Student student = studentRepository.findById(studentId)
                        .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

                // Get current fee assignment
                StudentFeeAssignment feeAssignment = studentFeeAssignmentRepository
                        .findByStudentIdAndAcademicYear(studentId, request.getAcademicYear())
                        .orElseThrow(() -> new RuntimeException(
                                "Fee assignment not found for student: " + studentId));

                // Store previous status
                String previousStatus = feeAssignment.getFeeStatus().name();

                // Update status
                feeAssignment.setFeeStatus(request.getNewStatus());
                feeAssignment.setUpdatedAt(LocalDateTime.now());

                // Update related term assignments
                List<StudentTermAssignment> termAssignments = studentTermAssignmentRepository
                        .findByStudentId(studentId).stream()
                        .filter(ta -> ta.getAcademicTerm() != null &&
                                ta.getAcademicTerm().getAcademicYear().equals(request.getAcademicYear()))
                        .collect(Collectors.toList());

                for (StudentTermAssignment assignment : termAssignments) {
                    assignment.setTermFeeStatus(mapToTermFeeStatus(request.getNewStatus()));
                    studentTermAssignmentRepository.save(assignment);
                }

                studentFeeAssignmentRepository.save(feeAssignment);

                // Record success
                successes.add(String.format("Student %d: Status updated to %s", studentId, request.getNewStatus()));
                result.setSuccessCount(result.getSuccessCount() + 1);

                // Add student detail
                BulkUpdateResult.StudentUpdateDetail detail = new BulkUpdateResult.StudentUpdateDetail();
                detail.setStudentId(studentId);
                detail.setStudentName(student.getFullName());
                detail.setStatus("SUCCESS");
                detail.setMessage("Fee status updated successfully");
                detail.setPreviousStatus(previousStatus);
                detail.setNewStatus(request.getNewStatus().name());
                detail.setUpdatedAt(LocalDateTime.now());
                studentDetails.add(detail);

            } catch (Exception e) {
                errors.add(String.format("Student %d: %s", studentId, e.getMessage()));
                result.setFailedCount(result.getFailedCount() + 1);
                log.error("Failed to update fee status for student {}: {}", studentId, e.getMessage());

                // Add failed student detail
                BulkUpdateResult.StudentUpdateDetail detail = new BulkUpdateResult.StudentUpdateDetail();
                detail.setStudentId(studentId);
                detail.setStatus("FAILED");
                detail.setMessage(e.getMessage());
                detail.setUpdatedAt(LocalDateTime.now());
                studentDetails.add(detail);
            }
        }

        result.setErrors(errors);
        result.setSuccesses(successes);
        result.setSkipped(skipped);
        result.setStudentDetails(studentDetails);
        result.setSummaryMessage(String.format("Updated %d of %d students",
                result.getSuccessCount(), result.getTotalStudents()));

        return result;
    }

    /**
     * Send fee reminders to multiple students
     */
    @Transactional
    public ReminderResult bulkSendReminders(ReminderRequest request) {
        ReminderResult result = new ReminderResult();
        result.setTimestamp(LocalDateTime.now());
        result.setReminderType(request.getReminderType());
        result.setSenderName(request.getSenderName());
        result.setTotalStudents(request.getStudentIds().size());

        List<String> sentReminders = new ArrayList<>();
        List<String> failedReminders = new ArrayList<>();
        List<String> skippedReminders = new ArrayList<>();
        List<ReminderResult.DeliveryStatus> deliveryStatuses = new ArrayList<>();
        List<ReminderResult.ReminderResponse> responses = new ArrayList<>();

        int smsCount = 0;
        int emailCount = 0;
        double estimatedCost = 0.0;

        for (Long studentId : request.getStudentIds()) {
            try {
                Student student = studentRepository.findById(studentId)
                        .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

                // Get pending fees
                List<TermFeeItem> pendingItems = termFeeItemRepository
                        .findPendingItemsOrderedBySequence(studentId);

                if (!pendingItems.isEmpty()) {
                    double totalPending = pendingItems.stream()
                            .mapToDouble(TermFeeItem::getPendingAmount)
                            .sum();

                    // Determine contact methods based on reminder type
                    boolean sendSms = request.getReminderType().contains("SMS") ||
                            request.getReminderType().equals("BOTH");
                    boolean sendEmail = request.getReminderType().contains("EMAIL") ||
                            request.getReminderType().equals("BOTH");

                    boolean sentSuccessfully = false;
                    String messageId = "MSG-" + System.currentTimeMillis() + "-" + studentId;

                    // Simulate sending reminders
                    if (sendSms && student.getEmergencyContactPhone() != null) {

                        ReminderResult.DeliveryStatus smsStatus = new ReminderResult.DeliveryStatus();
                        smsStatus.setStudentId(studentId);
                        smsStatus.setContactMethod("SMS");
                        smsStatus.setStatus("SENT");
                        smsStatus.setMessageId(messageId + "-SMS");
                        smsStatus.setSentAt(LocalDateTime.now());
                        deliveryStatuses.add(smsStatus);

                        smsCount++;
                        estimatedCost += 1.5; // Assume ₹1.5 per SMS
                        sentSuccessfully = true;
                    }

                    if (sendEmail && student.getEmail() != null) {
                        ReminderResult.DeliveryStatus emailStatus = new ReminderResult.DeliveryStatus();
                        emailStatus.setStudentId(studentId);
                        emailStatus.setContactMethod("EMAIL");
                        emailStatus.setStatus("SENT");
                        emailStatus.setMessageId(messageId + "-EMAIL");
                        emailStatus.setSentAt(LocalDateTime.now());
                        deliveryStatuses.add(emailStatus);

                        emailCount++;
                        sentSuccessfully = true;
                    }

                    if (sentSuccessfully) {
                        // Update reminder count in term assignment
                        StudentTermAssignment assignment = pendingItems.get(0).getStudentTermAssignment();
                        assignment.setRemindersSent(assignment.getRemindersSent() + 1);
                        assignment.setLastReminderDate(LocalDate.now());
                        studentTermAssignmentRepository.save(assignment);

                        sentReminders.add(String.format("Student %d: Reminder sent", studentId));
                        result.setSentCount(result.getSentCount() + 1);
                    } else {
                        failedReminders.add(String.format("Student %d: No valid contact method", studentId));
                        result.setFailedCount(result.getFailedCount() + 1);
                    }
                } else {
                    skippedReminders.add(String.format("Student %d: No pending fees", studentId));
                    result.setSkippedCount(result.getSkippedCount() + 1);
                }

            } catch (Exception e) {
                failedReminders.add(String.format("Student %d: %s", studentId, e.getMessage()));
                result.setFailedCount(result.getFailedCount() + 1);
                log.error("Failed to send reminder to student {}: {}", studentId, e.getMessage());
            }
        }

        result.setSentReminders(sentReminders);
        result.setFailedReminders(failedReminders);
        result.setSkippedReminders(skippedReminders);
        result.setDeliveryStatuses(deliveryStatuses);
        result.setResponses(responses);
        result.setEstimatedCost(estimatedCost);
        result.setSmsCount(smsCount);
        result.setEmailCount(emailCount);

        return result;
    }

    // ========== FEE STATISTICS ==========

    /**
     * Get fee statistics for term
     */
    @Transactional(readOnly = true)
    public TermFeeStatistics getTermFeeStatistics(Long termId) {
        AcademicTerm term = academicTermRepository.findById(termId)
                .orElseThrow(() -> new RuntimeException("Term not found: " + termId));

        // Calculate basic statistics
        Long totalStudents = studentTermAssignmentRepository.countByAcademicTermId(termId);
        Long billedStudents = studentTermAssignmentRepository.countBilledStudentsByTermId(termId);
        Long unbilledStudents = totalStudents - billedStudents;

        Double totalExpectedFee = studentTermAssignmentRepository.getTotalExpectedRevenueForTerm(termId);
        Double totalCollected = studentTermAssignmentRepository.getTotalCollectedForTerm(termId);
        Double totalPending = studentTermAssignmentRepository.getTotalOutstandingForTerm(termId);
        Double collectionRate = totalExpectedFee > 0 ? (totalCollected / totalExpectedFee) * 100 : 0;

        // Create response
        TermFeeStatistics statistics = new TermFeeStatistics();
        statistics.setTermId(termId);
        statistics.setTermName(term.getTermName());
        statistics.setAcademicYear(term.getAcademicYear());
        statistics.setGeneratedAt(LocalDateTime.now());
        statistics.setTotalStudents(totalStudents);
        statistics.setBilledStudents(billedStudents);
        statistics.setUnbilledStudents(unbilledStudents);
        statistics.setTotalExpectedFee(totalExpectedFee);
        statistics.setTotalCollected(totalCollected);
        statistics.setTotalPending(totalPending);
        statistics.setCollectionRate(round(collectionRate, 2));
        statistics.setAverageFeePerStudent(totalStudents > 0 ? totalExpectedFee / totalStudents : 0);

        // Status distribution
        Map<String, Long> statusDistribution = new HashMap<>();
        for (StudentTermAssignment.FeeStatus status : StudentTermAssignment.FeeStatus.values()) {
            Long count = studentTermAssignmentRepository.countByAcademicTermIdAndStatus(termId, status);
            statusDistribution.put(status.name(), count != null ? count : 0L);
        }
        statistics.setFeeStatusDistribution(statusDistribution);

        // Grade-wise statistics
        Map<String, TermFeeStatistics.GradeStatistics> gradeStatistics = new HashMap<>();

        // Get all grades with assignments
        List<String> grades = studentTermAssignmentRepository.findByAcademicTermId(termId).stream()
                .filter(assignment -> assignment.getStudent() != null && assignment.getStudent().getGrade() != null)
                .map(assignment -> assignment.getStudent().getGrade())
                .distinct()
                .collect(Collectors.toList());

        for (String grade : grades) {
            List<StudentTermAssignment> gradeAssignments = studentTermAssignmentRepository
                    .findByAcademicTermIdAndStudentGrade(termId, grade);

            if (!gradeAssignments.isEmpty()) {
                double gradeExpected = gradeAssignments.stream()
                        .mapToDouble(StudentTermAssignment::getTotalTermFee)
                        .sum();

                double gradeCollected = gradeAssignments.stream()
                        .mapToDouble(StudentTermAssignment::getPaidAmount)
                        .sum();

                double gradeCollectionRate = gradeExpected > 0 ? (gradeCollected / gradeExpected) * 100 : 0;

                TermFeeStatistics.GradeStatistics gradeStats = new TermFeeStatistics.GradeStatistics();
                gradeStats.setGrade(grade);
                gradeStats.setStudentCount((long) gradeAssignments.size());
                gradeStats.setExpectedFee(gradeExpected);
                gradeStats.setCollected(gradeCollected);
                gradeStats.setPending(gradeExpected - gradeCollected);
                gradeStats.setCollectionRate(round(gradeCollectionRate, 2));
                gradeStats.setAverageFee(gradeAssignments.isEmpty() ? 0 : gradeExpected / gradeAssignments.size());

                gradeStatistics.put(grade, gradeStats);
            }
        }
        statistics.setGradeStatistics(gradeStatistics);

        // Fee type breakdown
        List<Object[]> feeBreakdown = termFeeItemRepository.getFeeBreakdownByTypeForTerm(termId);
        Map<String, Double> feeTypeDistribution = new HashMap<>();
        if (feeBreakdown != null) {
            feeTypeDistribution = feeBreakdown.stream()
                    .collect(Collectors.toMap(
                            arr -> ((TermFeeItem.FeeType) arr[0]).name(),
                            arr -> (Double) arr[1]
                    ));
        }
        statistics.setFeeTypeDistribution(feeTypeDistribution);

        // Daily collection trend (last 7 days)
        Map<String, TermFeeStatistics.DailyCollection> dailyCollectionTrend = new LinkedHashMap<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(6); // Last 7 days inclusive

        // Get payments for the term in date range
        List<PaymentTransaction> recentPayments = paymentTransactionRepository
                .findByPaymentDateBetween(startDate.atStartOfDay(),
                        endDate.plusDays(1).atStartOfDay());

        // Group by day
        Map<LocalDate, List<PaymentTransaction>> paymentsByDay = recentPayments.stream()
                .collect(Collectors.groupingBy(tx -> tx.getPaymentDate().toLocalDate()));

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<PaymentTransaction> dayPayments = paymentsByDay.getOrDefault(date, new ArrayList<>());

            double dayAmount = dayPayments.stream()
                    .mapToDouble(PaymentTransaction::getAmount)
                    .sum();

            TermFeeStatistics.DailyCollection daily = new TermFeeStatistics.DailyCollection();
            daily.setDate(date.toString());
            daily.setAmount(dayAmount);
            daily.setTransactionCount(dayPayments.size());
            dailyCollectionTrend.put(date.toString(), daily);
        }
        statistics.setDailyCollectionTrend(dailyCollectionTrend);

        // Top defaulters
        List<StudentTermAssignment> pendingAssignments = studentTermAssignmentRepository
                .findPendingAssignmentsForTerm(termId);

        List<TermFeeStatistics.StudentDefault> highestDefaulters = pendingAssignments.stream()
                .filter(a -> a.getPendingAmount() > 0)
                .sorted((a, b) -> Double.compare(b.getPendingAmount(), a.getPendingAmount()))
                .limit(10)
                .map(assignment -> {
                    TermFeeStatistics.StudentDefault defaulter = new TermFeeStatistics.StudentDefault();
                    defaulter.setStudentId(assignment.getStudent().getId());
                    defaulter.setStudentName(assignment.getStudent().getFullName());
                    defaulter.setGrade(assignment.getStudent().getGrade());
                    defaulter.setPendingAmount(assignment.getPendingAmount());
                    defaulter.setDaysOverdue(assignment.getDueDate() != null ?
                            (int) ChronoUnit.DAYS.between(assignment.getDueDate(), LocalDate.now()) : 0);
                    defaulter.setRemindersSent(assignment.getRemindersSent());
                    return defaulter;
                })
                .collect(Collectors.toList());

        TermFeeStatistics.TopDefaulters topDefaulters = new TermFeeStatistics.TopDefaulters();
        topDefaulters.setHighestDefaulters(highestDefaulters);

        // Calculate worst performing grades
        Map<String, Double> gradePendingAmounts = new HashMap<>();
        for (StudentTermAssignment assignment : pendingAssignments) {
            if (assignment.getStudent() != null && assignment.getStudent().getGrade() != null) {
                String grade = assignment.getStudent().getGrade();
                gradePendingAmounts.put(grade,
                        gradePendingAmounts.getOrDefault(grade, 0.0) + assignment.getPendingAmount());
            }
        }

        List<TermFeeStatistics.GradeDefault> worstPerformingGrades = gradePendingAmounts.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> {
                    TermFeeStatistics.GradeDefault gradeDefault = new TermFeeStatistics.GradeDefault();
                    gradeDefault.setGrade(entry.getKey());
                    gradeDefault.setPendingAmount(entry.getValue());

                    long defaultingStudents = pendingAssignments.stream()
                            .filter(a -> a.getStudent() != null &&
                                    a.getStudent().getGrade() != null &&
                                    a.getStudent().getGrade().equals(entry.getKey()))
                            .count();
                    gradeDefault.setDefaultingStudents((int) defaultingStudents);

                    // Calculate collection rate for this grade
                    TermFeeStatistics.GradeStatistics gradeStats = gradeStatistics.get(entry.getKey());
                    if (gradeStats != null) {
                        gradeDefault.setCollectionRate(gradeStats.getCollectionRate());
                    }

                    return gradeDefault;
                })
                .collect(Collectors.toList());

        topDefaulters.setWorstPerformingGrades(worstPerformingGrades);
        statistics.setTopDefaulters(topDefaulters);

        return statistics;
    }

    // ========== BILL REGENERATION ==========

    /**
     * Regenerate bill for student in term
     */
    @Transactional
    public RegenerateBillResponse regenerateStudentBill(Long studentId, Long termId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        AcademicTerm term = academicTermRepository.findById(termId)
                .orElseThrow(() -> new RuntimeException("Term not found: " + termId));

        // Get previous assignment details
        Optional<StudentTermAssignment> previousAssignmentOpt = studentTermAssignmentRepository
                .findByStudentIdAndAcademicTermId(studentId, termId);

        RegenerateBillResponse response = new RegenerateBillResponse();
        response.setStudentId(studentId);
        response.setStudentName(student.getFullName());
        response.setStudentCode(student.getStudentId());
        response.setTermId(termId);
        response.setTermName(term.getTermName());
        response.setAcademicYear(term.getAcademicYear());
        response.setRegeneratedAt(LocalDateTime.now());
        response.setRegeneratedBy("SYSTEM");
        response.setReasonForRegeneration("Manual regeneration request");
        response.setReferenceNumber("REGEN-" + System.currentTimeMillis());

        if (previousAssignmentOpt.isPresent()) {
            StudentTermAssignment previousAssignment = previousAssignmentOpt.get();

            // Store previous details
            response.setPreviousTotalFee(previousAssignment.getTotalTermFee());
            response.setPreviousItemsCount(previousAssignment.getFeeItems().size());

            RegenerateBillResponse.AssignmentDetails previousDetails =
                    new RegenerateBillResponse.AssignmentDetails();
            previousDetails.setAssignmentId(previousAssignment.getId());
            previousDetails.setTotalFee(previousAssignment.getTotalTermFee());
            previousDetails.setPaidAmount(previousAssignment.getPaidAmount());
            previousDetails.setPendingAmount(previousAssignment.getPendingAmount());
            previousDetails.setFeeStatus(previousAssignment.getTermFeeStatus().name());
            previousDetails.setDueDate(previousAssignment.getDueDate());
            previousDetails.setBillingDate(previousAssignment.getBillingDate());
            previousDetails.setFeeItemsCount(previousAssignment.getFeeItems().size());
            previousDetails.setIsBilled(previousAssignment.getIsBilled());
            response.setPreviousAssignment(previousDetails);

            // Delete existing assignment and fee items
            termFeeItemRepository.deleteAll(previousAssignment.getFeeItems());
            studentTermAssignmentRepository.delete(previousAssignment);
        }

        // Re-bill student
        boolean success = autoBillStudentForTerm(studentId, termId);

        // Get the new assignment
        StudentTermAssignment newAssignment = studentTermAssignmentRepository
                .findByStudentIdAndAcademicTermId(studentId, termId)
                .orElseThrow(() -> new RuntimeException("Failed to regenerate bill"));

        response.setRegenerated(success);
        response.setNewTotalFee(newAssignment.getTotalTermFee());
        response.setNewItemsCount(newAssignment.getFeeItems().size());
        response.setDifference(response.getNewTotalFee() - (response.getPreviousTotalFee() != null ?
                response.getPreviousTotalFee() : 0));
        response.setDifferenceReason("Fee structure update");
        response.setRegenerationType("FULL");

        // Store new details
        RegenerateBillResponse.AssignmentDetails newDetails =
                new RegenerateBillResponse.AssignmentDetails();
        newDetails.setAssignmentId(newAssignment.getId());
        newDetails.setTotalFee(newAssignment.getTotalTermFee());
        newDetails.setPaidAmount(newAssignment.getPaidAmount());
        newDetails.setPendingAmount(newAssignment.getPendingAmount());
        newDetails.setFeeStatus(newAssignment.getTermFeeStatus().name());
        newDetails.setDueDate(newAssignment.getDueDate());
        newDetails.setBillingDate(newAssignment.getBillingDate());
        newDetails.setFeeItemsCount(newAssignment.getFeeItems().size());
        newDetails.setIsBilled(newAssignment.getIsBilled());
        response.setNewAssignment(newDetails);

        // Compare fee items
        List<RegenerateBillResponse.FeeItemComparison> itemComparison = new ArrayList<>();
        if (previousAssignmentOpt.isPresent()) {
            RegenerateBillResponse.FeeItemComparison comparison =
                    new RegenerateBillResponse.FeeItemComparison();
            comparison.setItemName("All Items");
            comparison.setFeeType("SUMMARY");
            comparison.setPreviousAmount(response.getPreviousTotalFee());
            comparison.setNewAmount(response.getNewTotalFee());
            comparison.setDifference(response.getDifference());
            comparison.setChangeType("MODIFIED");
            comparison.setChangeReason("Complete regeneration");
            itemComparison.add(comparison);
        }
        response.setItemComparison(itemComparison);

        return response;
    }

    // ========== STUDENT TERM ASSIGNMENTS ==========

    /**
     * Get student's term assignments
     */
    public List<StudentTermAssignment> getStudentTermAssignments(Long studentId) {
        return studentTermAssignmentRepository.findByStudentId(studentId);
    }

    // ========== SIMPLE DUE DATE MANAGEMENT METHODS ==========

    /**
     * Update student's due date from term
     */
    @Transactional
    public void updateStudentDueDateFromTerm(Long studentId, Long termId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        AcademicTerm term = academicTermRepository.findById(termId)
                .orElseThrow(() -> new RuntimeException("Term not found: " + termId));

        // Get term due date
        LocalDate termDueDate = term.getFeeDueDate();
        if (termDueDate == null) {
            termDueDate = term.getStartDate().plusDays(30); // Fallback
        }

        // USE MANUAL METHOD
        student.setFeeDueDateManually(termDueDate);
        studentRepository.save(student);

        log.info("Manually updated student {} due date to term due date: {}", studentId, termDueDate);
    }

    /**
     * Update all students' due dates for a term
     */
    @Transactional
    public int updateAllStudentsDueDateForTerm(Long termId) {
        AcademicTerm term = academicTermRepository.findById(termId)
                .orElseThrow(() -> new RuntimeException("Term not found: " + termId));

        LocalDate termDueDate = term.getFeeDueDate();
        if (termDueDate == null) {
            termDueDate = term.getStartDate().plusDays(30);
        }

        // Get all students with term assignments
        List<StudentTermAssignment> assignments = studentTermAssignmentRepository
                .findByAcademicTermId(termId);

        Set<Long> studentIds = assignments.stream()
                .map(assignment -> assignment.getStudent().getId())
                .collect(Collectors.toSet());

        int updated = 0;
        for (Long studentId : studentIds) {
            try {
                // Get student without lambda
                Optional<Student> studentOpt = studentRepository.findById(studentId);
                if (studentOpt.isPresent()) {
                    Student student = studentOpt.get();
                    // USE MANUAL METHOD
                    student.setFeeDueDateManually(termDueDate);
                    studentRepository.save(student);
                    updated++;
                }
            } catch (Exception e) {
                log.error("Failed to update due date for student {}: {}", studentId, e.getMessage());
            }
        }

        log.info("Manually updated due dates for {} students for term {}", updated, term.getTermName());
        return updated;
    }

    /**
     * Clear student due date (when all paid)
     */
    @Transactional
    public void clearStudentDueDate(Long studentId) {
        studentRepository.findById(studentId).ifPresent(student -> {
            if (student.getPendingAmount() != null && student.getPendingAmount() <= 0) {
                // USE MANUAL METHOD
                student.clearFeeDueDateManually();
                studentRepository.save(student);
                log.info("Cleared due date for student {} (all fees paid)", studentId);
            }
        });
    }

    // ========== HELPER METHODS ==========

    private GradeTermFee createFeeStructure(FeeStructureRequest request) {
        return GradeTermFee.builder()
                .grade(request.getGrade())
                .tuitionFee(request.getTuitionFee())
                .basicFee(request.getBasicFee())
                .examinationFee(request.getExaminationFee())
                .transportFee(request.getTransportFee())
                .libraryFee(request.getLibraryFee())
                .sportsFee(request.getSportsFee())
                .activityFee(request.getActivityFee())
                .hostelFee(request.getHostelFee())
                .uniformFee(request.getUniformFee())
                .bookFee(request.getBookFee())
                .otherFees(request.getOtherFees())
                .isActive(request.getIsActive())
                .build();
    }

    private void updateFeeStructure(GradeTermFee fee, FeeStructureRequest request) {
        fee.setTuitionFee(request.getTuitionFee());
        fee.setBasicFee(request.getBasicFee());
        fee.setExaminationFee(request.getExaminationFee());
        fee.setTransportFee(request.getTransportFee());
        fee.setLibraryFee(request.getLibraryFee());
        fee.setSportsFee(request.getSportsFee());
        fee.setActivityFee(request.getActivityFee());
        fee.setHostelFee(request.getHostelFee());
        fee.setUniformFee(request.getUniformFee());
        fee.setBookFee(request.getBookFee());
        fee.setOtherFees(request.getOtherFees());
        fee.setIsActive(request.getIsActive());
    }

    private void updateFeeAssignment(Student student, AcademicTerm term, StudentTermAssignment termAssignment) {
        studentFeeAssignmentRepository
                .findByStudentIdAndAcademicYear(student.getId(), term.getAcademicYear())
                .ifPresentOrElse(
                        assignment -> updateExistingFeeAssignment(assignment, termAssignment),
                        () -> createNewFeeAssignment(student, term, termAssignment)
                );
    }

    private void updateExistingFeeAssignment(StudentFeeAssignment assignment, StudentTermAssignment termAssignment) {
        double newTotal = assignment.getTotalAmount() + termAssignment.getTotalTermFee();
        double newPaid = assignment.getPaidAmount();
        double newPending = newTotal - newPaid;

        assignment.setTotalAmount(newTotal);
        assignment.setPendingAmount(newPending);

        // Update due date if earlier than current
        if (termAssignment.getDueDate() != null &&
                (assignment.getDueDate() == null ||
                        termAssignment.getDueDate().isBefore(assignment.getDueDate()))) {
            assignment.setDueDate(termAssignment.getDueDate());
        }

        // Update status
        if (newPending <= 0) {
            assignment.setFeeStatus(FeeStatus.PAID);
        } else if (newPaid > 0) {
            assignment.setFeeStatus(FeeStatus.PARTIAL);
        } else if (assignment.getDueDate() != null &&
                LocalDate.now().isAfter(assignment.getDueDate())) {
            assignment.setFeeStatus(FeeStatus.OVERDUE);
        } else {
            assignment.setFeeStatus(FeeStatus.PENDING);
        }

        assignment.setUpdatedAt(LocalDateTime.now());
        studentFeeAssignmentRepository.save(assignment);
    }

    private void createNewFeeAssignment(Student student, AcademicTerm term, StudentTermAssignment termAssignment) {
        FeeStructure feeStructure = FeeStructure.builder()
                .structureName(String.format("%s - %s", student.getFullName(), term.getAcademicYear()))
                .grade(student.getGrade())
                .academicYear(term.getAcademicYear())
                .totalAmount(termAssignment.getTotalTermFee())
                .isActive(true)
                .build();

        FeeStructure savedStructure = feeStructureRepository.save(feeStructure);

        StudentFeeAssignment assignment = StudentFeeAssignment.builder()
                .student(student)
                .feeStructure(savedStructure)
                .academicYear(term.getAcademicYear())
                .assignedDate(LocalDate.now())
                .totalAmount(termAssignment.getTotalTermFee())
                .paidAmount(0.0)
                .pendingAmount(termAssignment.getTotalTermFee())
                .feeStatus(FeeStatus.PENDING)
                .dueDate(termAssignment.getDueDate())
                .isActive(true)
                .build();

        studentFeeAssignmentRepository.save(assignment);
    }

    private TermFeeItem createAdditionalFeeItem(StudentTermAssignment assignment, AdditionalFeeRequestDto request) {
        // Get next sequence order
        int nextSequence = assignment.getFeeItems().stream()
                .mapToInt(TermFeeItem::getSequenceOrder)
                .max().orElse(0) + 1;

        return TermFeeItem.builder()
                .studentTermAssignment(assignment)
                .itemName(request.getItemName())
                .feeType(request.getFeeType())
                .itemType(request.getItemType())
                .amount(request.getAmount())
                .originalAmount(request.getAmount())
                .dueDate(request.getDueDate())
                .isAutoGenerated(false)
                .isMandatory(request.getIsMandatory())
                .sequenceOrder(nextSequence)
                .status(getInitialFeeStatus(request.getDueDate()))
                .notes(request.getNotes())
                .build();
    }

    private AdditionalFeeRequestDto mapToAdditionalFeeRequest(BulkFeeRequest request, Long studentId) {
        AdditionalFeeRequestDto dto = new AdditionalFeeRequestDto();
        dto.setStudentId(studentId);
        dto.setTermId(request.getTermId());
        dto.setItemName(request.getItemName());
        dto.setFeeType(request.getFeeType());
        dto.setAmount(request.getAmount());
        dto.setDueDate(request.getDueDate());
        dto.setIsMandatory(request.getIsMandatory());
        dto.setNotes(request.getNotes());
        dto.setItemType("ADDITIONAL");
        return dto;
    }

    private void calculateDashboardStatistics(GradeFeeDashboardResponse dashboard,
                                              List<Student> gradeStudents,
                                              List<StudentTermAssignment> termAssignments) {
        dashboard.setStudentsEnrolled(gradeStudents.size());
        dashboard.setBilledStudents(termAssignments.size());

        double expectedRevenue = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getTotalTermFee)
                .sum();
        dashboard.setExpectedRevenue(expectedRevenue);

        double collected = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPaidAmount)
                .sum();
        dashboard.setCollected(collected);

        double outstanding = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum();
        dashboard.setOutstanding(outstanding);

        double collectionRate = expectedRevenue > 0 ? (collected / expectedRevenue) * 100 : 0;
        dashboard.setCollectionRate(round(collectionRate, 2));
    }

    private Map<String, Integer> calculatePaymentStatus(List<StudentTermAssignment> assignments) {
        Map<String, Integer> paymentStatus = new HashMap<>();
        assignments.forEach(a ->
                paymentStatus.merge(a.getTermFeeStatus().name(), 1, Integer::sum));
        return paymentStatus;
    }

    private Map<String, Double> calculateFeeBreakdown(List<StudentTermAssignment> assignments) {
        Map<String, Double> feeBreakdown = new HashMap<>();
        assignments.forEach(assignment ->
                assignment.getFeeItems().forEach(item ->
                        feeBreakdown.merge(item.getFeeType().name(), item.getAmount(), Double::sum)));
        return feeBreakdown;
    }

    private List<GradeFeeDashboardResponse.TopDefaulter> getTopDefaulters(
            List<StudentTermAssignment> assignments) {
        return assignments.stream()
                .filter(a -> a.getPendingAmount() > 0)
                .sorted((a, b) -> Double.compare(b.getPendingAmount(), a.getPendingAmount()))
                .limit(10)
                .map(this::mapToTopDefaulter)
                .collect(Collectors.toList());
    }

    private GradeFeeDashboardResponse.TopDefaulter mapToTopDefaulter(StudentTermAssignment assignment) {
        GradeFeeDashboardResponse.TopDefaulter defaulter = new GradeFeeDashboardResponse.TopDefaulter();
        defaulter.setStudentId(assignment.getStudent().getId());
        defaulter.setStudentName(assignment.getStudent().getFullName());
        defaulter.setStudentCode(assignment.getStudent().getStudentId());
        defaulter.setPendingAmount(assignment.getPendingAmount());
        return defaulter;
    }

    private GradeFeeDashboardResponse.FeeStructureResponse mapToFeeStructureResponse(GradeTermFee feeStructure) {
        GradeFeeDashboardResponse.FeeStructureResponse response =
                new GradeFeeDashboardResponse.FeeStructureResponse();
        response.setTuitionFee(feeStructure.getTuitionFee());
        response.setBasicFee(feeStructure.getBasicFee());
        response.setExaminationFee(feeStructure.getExaminationFee());
        response.setTransportFee(feeStructure.getTransportFee());
        response.setLibraryFee(feeStructure.getLibraryFee());
        response.setSportsFee(feeStructure.getSportsFee());
        response.setActivityFee(feeStructure.getActivityFee());
        response.setHostelFee(feeStructure.getHostelFee());
        response.setUniformFee(feeStructure.getUniformFee());
        response.setBookFee(feeStructure.getBookFee());
        response.setOtherFees(feeStructure.getOtherFees());
        response.setTotalFee(feeStructure.getTotalFee());
        return response;
    }

    private List<StudentTermAssignment> getTermAssignmentsForAcademicYear(Long studentId, String academicYear) {
        return studentTermAssignmentRepository.findByStudentId(studentId).stream()
                .filter(ta -> ta.getAcademicTerm() != null &&
                        ta.getAcademicTerm().getAcademicYear().equals(academicYear))
                .collect(Collectors.toList());
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private PaymentHistoryResponse.PaymentDetail mapToPaymentDetail(PaymentTransaction transaction) {
        PaymentHistoryResponse.PaymentDetail detail = new PaymentHistoryResponse.PaymentDetail();
        detail.setTransactionId(transaction.getId());
        detail.setTransactionCode(transaction.getReceiptNumber());
        detail.setAmount(transaction.getAmount());
        detail.setPaymentDate(transaction.getPaymentDate());
        detail.setPaymentMethod(transaction.getPaymentMethod() != null ? transaction.getPaymentMethod().name() : "UNKNOWN");
        detail.setReferenceNumber(transaction.getBankReference());
        detail.setCollectedBy("ADMIN");
        detail.setStatus(transaction.getIsVerified() ? "VERIFIED" : "PENDING");
        detail.setReceiptNumber(transaction.getReceiptNumber());

        // Bank transaction details
        if (transaction.getBankTransaction() != null) {
            detail.setBankName(transaction.getBankTransaction().getBankName());
            detail.setBranchName(transaction.getBankTransaction().getBankBranch());
            detail.setChequeNumber(transaction.getBankTransaction().getChequeNumber());
        }

        detail.setPaidItems(new ArrayList<>());

        return detail;
    }

    private StudentTermAssignment.FeeStatus mapToTermFeeStatus(FeeStatus feeStatus) {
        switch (feeStatus) {
            case PAID: return StudentTermAssignment.FeeStatus.PAID;
            case PARTIAL: return StudentTermAssignment.FeeStatus.PARTIAL;
            case OVERDUE: return StudentTermAssignment.FeeStatus.OVERDUE;
            case PENDING: return StudentTermAssignment.FeeStatus.PENDING;
            default: return StudentTermAssignment.FeeStatus.PENDING;
        }
    }

    // ========== GRADE EXTRACTION HELPER METHODS ==========

    /**
     * Extract numeric part from grade string
     */
    private String extractNumericGrade(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return null;
        }

        // Remove any whitespace
        String cleanGrade = grade.trim();

        // If grade contains "-", take the part before it
        int dashIndex = cleanGrade.indexOf('-');
        if (dashIndex > 0) {
            return cleanGrade.substring(0, dashIndex).trim();
        }

        // If grade contains space, take the numeric part after "Grade"
        if (cleanGrade.toLowerCase().startsWith("grade")) {
            String[] parts = cleanGrade.split("\\s+");
            for (String part : parts) {
                if (part.matches("\\d+")) {
                    return part;
                }
            }
        }

        // Extract only digits from the string
        StringBuilder numericPart = new StringBuilder();
        for (char c : cleanGrade.toCharArray()) {
            if (Character.isDigit(c)) {
                numericPart.append(c);
            } else if (numericPart.length() > 0) {
                // Stop at first non-digit after digits
                break;
            }
        }

        return numericPart.length() > 0 ? numericPart.toString() : cleanGrade;
    }

    /**
     * Check if two grades match (handles sectioned grades)
     */
    private boolean gradesMatch(String grade1, String grade2) {
        if (grade1 == null || grade2 == null) {
            return false;
        }

        // Direct match
        if (grade1.equalsIgnoreCase(grade2)) {
            return true;
        }

        // Extract numeric parts and compare
        String numeric1 = extractNumericGrade(grade1);
        String numeric2 = extractNumericGrade(grade2);

        return numeric1 != null && numeric2 != null && numeric1.equals(numeric2);
    }

    private TermFeeItem.FeeStatus getInitialFeeStatus(LocalDate dueDate) {
        if (dueDate != null && LocalDate.now().isAfter(dueDate)) {
            return TermFeeItem.FeeStatus.OVERDUE;
        }
        return TermFeeItem.FeeStatus.PENDING;
    }
}