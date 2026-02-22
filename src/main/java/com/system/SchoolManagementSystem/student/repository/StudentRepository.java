package com.system.SchoolManagementSystem.student.repository;

import com.system.SchoolManagementSystem.student.dto.StudentFeeSummaryDTO;
import com.system.SchoolManagementSystem.student.dto.GradeStatisticsDTO;
import com.system.SchoolManagementSystem.student.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;  // ✅ CORRECT - Change this import
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    // ========== BASIC STUDENT QUERIES ==========

    // Original method - keep for backward compatibility
    Optional<Student> findByStudentId(String studentId);

    // Updated method - REMOVED DISTINCT keyword since we're using Set
    @Query("SELECT s FROM Student s " +
            "LEFT JOIN FETCH s.familyMembers " +
            "LEFT JOIN FETCH s.medicalRecords " +
            "LEFT JOIN FETCH s.achievements " +
            "LEFT JOIN FETCH s.interests " +
            "WHERE s.studentId = :studentId")
    Optional<Student> findByStudentIdWithRelations(@Param("studentId") String studentId);

    // Original method - keep for backward compatibility
    Optional<Student> findByEmail(String email);

    // Updated method - REMOVED DISTINCT keyword since we're using Set
    @Query("SELECT s FROM Student s " +
            "LEFT JOIN FETCH s.familyMembers " +
            "LEFT JOIN FETCH s.medicalRecords " +
            "LEFT JOIN FETCH s.achievements " +
            "LEFT JOIN FETCH s.interests " +
            "WHERE s.email = :email")
    Optional<Student> findByEmailWithRelations(@Param("email") String email);

    // Original method - keep for backward compatibility
    List<Student> findByGrade(String grade);

    // Updated method - REMOVED DISTINCT keyword since we're using Set
    @Query("SELECT s FROM Student s " +
            "LEFT JOIN FETCH s.familyMembers " +
            "LEFT JOIN FETCH s.medicalRecords " +
            "LEFT JOIN FETCH s.achievements " +
            "LEFT JOIN FETCH s.interests " +
            "WHERE s.grade = :grade")
    List<Student> findByGradeWithRelations(@Param("grade") String grade);

    List<Student> findByAcademicYear(String academicYear);
    List<Student> findByStatus(Student.StudentStatus status);

    @Query("SELECT s FROM Student s WHERE LOWER(s.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Student> searchByName(@Param("name") String name);

    @Query("SELECT s FROM Student s WHERE s.grade = :grade AND s.academicYear = :academicYear")
    List<Student> findByGradeAndAcademicYear(@Param("grade") String grade,
                                             @Param("academicYear") String academicYear);

    boolean existsByStudentId(String studentId);
    boolean existsByEmail(String email);

    // ========== TERM MANAGEMENT INTEGRATION QUERIES ==========

    // FIXED: Changed s.isDeleted to s.deleted (boolean field name)
    @Query("SELECT s FROM Student s WHERE s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findActiveStudents();

    // FIXED: Added deleted filter
    List<Student> findByGradeAndStatus(String grade, Student.StudentStatus status);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.pendingAmount > 0 AND s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findStudentsWithPendingFees();

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.feeStatus = 'OVERDUE' AND s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findStudentsWithOverdueFees();

    // FIXED: Added deleted filter
    List<Student> findByTransportMode(Student.TransportMode transportMode);

    // FIXED: Added deleted filter
    List<Student> findByTransportModeAndGrade(Student.TransportMode transportMode, String grade);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.admissionDate BETWEEN :startDate AND :endDate AND s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findStudentsAdmittedBetween(@Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s.grade, COUNT(s) FROM Student s WHERE s.status = 'ACTIVE' AND s.deleted = false GROUP BY s.grade")
    List<Object[]> countStudentsByGrade();

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.id IN (" +
            "SELECT sta.student.id FROM StudentTermAssignment sta WHERE sta.academicTerm.id = :termId) " +
            "AND s.deleted = false")
    List<Student> findStudentsWithTermAssignment(@Param("termId") Long termId);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.status = 'ACTIVE' AND s.deleted = false AND s.id NOT IN (" +
            "SELECT sta.student.id FROM StudentTermAssignment sta WHERE sta.academicTerm.id = :termId)")
    List<Student> findStudentsWithoutTermAssignment(@Param("termId") Long termId);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.grade IN :grades AND s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findByGrades(@Param("grades") List<String> grades);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE " +
            "(:grade IS NULL OR s.grade = :grade) AND " +
            "(:status IS NULL OR s.status = :status) AND " +
            "(:academicYear IS NULL OR s.academicYear = :academicYear) AND " +
            "(:transportMode IS NULL OR s.transportMode = :transportMode) AND " +
            "s.deleted = false")
    List<Student> searchStudents(@Param("grade") String grade,
                                 @Param("status") Student.StudentStatus status,
                                 @Param("academicYear") String academicYear,
                                 @Param("transportMode") Student.TransportMode transportMode);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s.id, s.fullName, s.grade, s.studentId, " +
            "COALESCE(s.totalFee, 0), COALESCE(s.paidAmount, 0), COALESCE(s.pendingAmount, 0), s.feeStatus " +
            "FROM Student s " +
            "WHERE s.status = 'ACTIVE' AND s.deleted = false " +
            "ORDER BY s.grade, s.fullName")
    List<Object[]> getStudentFeeSummary();

    // FIXED: Added deleted filter
    List<Student> findByFeeStatus(Student.FeeStatus feeStatus);

    // FIXED: Added deleted filter
    List<Student> findByGradeAndFeeStatus(String grade, Student.FeeStatus feeStatus);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.pendingAmount >= :minAmount AND s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findStudentsWithPendingAbove(@Param("minAmount") Double minAmount);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE " +
            "s.totalFee > 0 AND (s.paidAmount / s.totalFee * 100) < :percentage AND s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findStudentsWithLowPaymentPercentage(@Param("percentage") Double percentage);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.admissionDate >= :date AND s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findRecentAdmissions(@Param("date") LocalDate date);

    // FIXED: Added deleted filter
    List<Student> findByClassTeacher(String classTeacher);

    // FIXED: Added deleted filter
    List<Student> findByClassTeacherAndGrade(String classTeacher, String grade);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE " +
            "s.feeStatus = 'OVERDUE' AND " +
            "s.phone IS NOT NULL AND " +
            "s.status = 'ACTIVE' AND " +
            "s.deleted = false")
    List<Student> findStudentsForFeeReminders();

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.lastFeeUpdate < :date AND s.pendingAmount > 0 AND s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findStudentsWithNoRecentPayment(@Param("date") LocalDate date);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT " +
            "COUNT(s) as totalStudents, " +
            "COUNT(CASE WHEN s.status = 'ACTIVE' AND s.deleted = false THEN 1 END) as activeStudents, " +
            "COUNT(CASE WHEN s.status = 'GRADUATED' AND s.deleted = false THEN 1 END) as graduatedStudents, " +
            "COUNT(CASE WHEN s.status = 'TRANSFERRED' AND s.deleted = false THEN 1 END) as transferredStudents, " +
            "COUNT(CASE WHEN s.status = 'INACTIVE' AND s.deleted = false THEN 1 END) as inactiveStudents, " +
            "COUNT(CASE WHEN s.feeStatus = 'PAID' AND s.deleted = false THEN 1 END) as paidStudents, " +
            "COUNT(CASE WHEN s.feeStatus = 'PENDING' AND s.deleted = false THEN 1 END) as pendingStudents, " +
            "COUNT(CASE WHEN s.feeStatus = 'OVERDUE' AND s.deleted = false THEN 1 END) as overdueStudents " +
            "FROM Student s")
    Object[] getStudentStatistics();

    // Get grade-wise statistics
    @Query("SELECT new com.system.SchoolManagementSystem.student.dto.GradeStatisticsDTO(" +
            "s.grade, " + // grade
            "COUNT(s), " + // enrolled
            "SUM(CASE WHEN s.feeStatus = 'PAID' THEN 1 ELSE 0 END), " + // paidStudents
            "SUM(CASE WHEN s.feeStatus = 'PARTIAL' THEN 1 ELSE 0 END), " + // partialStudents
            "SUM(CASE WHEN s.feeStatus = 'PENDING' THEN 1 ELSE 0 END), " + // pendingStudents
            "SUM(CASE WHEN s.feeStatus = 'OVERDUE' THEN 1 ELSE 0 END), " + // overdueStudents
            "COALESCE(SUM(s.totalFee), 0.0), " + // totalFee
            "COALESCE(SUM(s.paidAmount), 0.0), " + // paidAmount
            "COALESCE(SUM(s.pendingAmount), 0.0), " + // pendingAmount
            "CASE WHEN COALESCE(SUM(s.totalFee), 0.0) > 0 " +
            "THEN (COALESCE(SUM(s.paidAmount), 0.0) / COALESCE(SUM(s.totalFee), 0.0)) * 100 " +
            "ELSE 0.0 END) " + // collectionRate
            "FROM Student s " +
            "WHERE s.deleted = false " +
            "GROUP BY s.grade " +
            "ORDER BY s.grade")
    List<GradeStatisticsDTO> getGradeWiseStatistics();
    // FIXED: This is the problematic method - changed s.isDeleted to s.deleted
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN TRUE ELSE FALSE END " +
            "FROM Student s WHERE s.id = :id AND s.status = 'ACTIVE' AND s.deleted = false")
    Boolean existsByIdAndActive(@Param("id") Long id);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE MONTH(s.dateOfBirth) = MONTH(CURRENT_DATE) AND s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findStudentsWithBirthdayThisMonth();

    // FIXED: Added deleted filter
    @Query("SELECT s FROM Student s WHERE s.admissionDate BETWEEN :startDate AND :endDate AND s.deleted = false")
    List<Student> findByAdmissionDateBetween(@Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.id IN :ids AND s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findByIds(@Param("ids") List<Long> ids);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s JOIN s.termAssignments ta WHERE ta.academicTerm.id = :termId AND s.deleted = false")
    List<Student> findStudentsByTermId(@Param("termId") Long termId);
    // ========== NEW METHOD FOR TERM ASSIGNMENTS WITH FEE ITEMS ==========

    @Query("SELECT s FROM Student s " +
            "LEFT JOIN FETCH s.termAssignments ta " +
            "LEFT JOIN FETCH ta.feeItems fi " +
            "LEFT JOIN FETCH ta.academicTerm at " +
            "WHERE s.id = :studentId")
    Optional<Student> findByIdWithTermAssignmentsAndFeeItems(@Param("studentId") Long studentId);

    // ========== NEW METHODS FOR SOFT DELETE ==========

    // Find all students including deleted ones
    @Query("SELECT s FROM Student s WHERE s.id = :id")
    Optional<Student> findByIdIncludingDeleted(@Param("id") Long id);

    // Find student by student ID including deleted
    @Query("SELECT s FROM Student s WHERE s.studentId = :studentId")
    Optional<Student> findByStudentIdIncludingDeleted(@Param("studentId") String studentId);

    // Find all deleted students
    @Query("SELECT s FROM Student s WHERE s.deleted = true")
    List<Student> findDeletedStudents();

    // Restore a deleted student
    @Modifying
    @Query("UPDATE Student s SET s.deleted = false WHERE s.id = :id")
    void restoreStudent(@Param("id") Long id);

    // Permanent delete (use with caution)
    @Modifying
    @Query("DELETE FROM Student s WHERE s.id = :id AND s.deleted = true")
    void permanentDelete(@Param("id") Long id);

    // ========== FEE-RELATED QUERIES (EXISTING - KEEP) ==========

    @Query("SELECT COALESCE(SUM(s.totalFee), 0.0) FROM Student s WHERE s.deleted = false")
    Double getTotalFeeSum();

    @Query("SELECT s.feeStatus, COUNT(s) FROM Student s WHERE s.deleted = false GROUP BY s.feeStatus")
    List<Object[]> countStudentsByFeeStatus();

    @Query("SELECT COUNT(s) FROM Student s WHERE s.paidAmount > 0 AND s.paidAmount < s.totalFee AND s.deleted = false")
    Long countPartialPayments();

    @Query("SELECT CASE " +
            "WHEN s.pendingAmount <= 1000 THEN '0-1K' " +
            "WHEN s.pendingAmount <= 5000 THEN '1K-5K' " +
            "WHEN s.pendingAmount <= 10000 THEN '5K-10K' " +
            "WHEN s.pendingAmount <= 20000 THEN '10K-20K' " +
            "ELSE '20K+' " +
            "END as range, " +
            "COUNT(s) as count, " +
            "COALESCE(SUM(s.pendingAmount), 0.0) as amount " +
            "FROM Student s " +
            "WHERE s.feeStatus = 'OVERDUE' AND s.deleted = false " +
            "GROUP BY CASE " +
            "WHEN s.pendingAmount <= 1000 THEN '0-1K' " +
            "WHEN s.pendingAmount <= 5000 THEN '1K-5K' " +
            "WHEN s.pendingAmount <= 10000 THEN '5K-10K' " +
            "WHEN s.pendingAmount <= 20000 THEN '10K-20K' " +
            "ELSE '20K+' " +
            "END")
    List<Object[]> getOverdueDistribution();

    @Query("SELECT s FROM Student s WHERE s.feeStatus = 'OVERDUE' AND s.feeDueDate <= :date AND s.deleted = false")
    List<Student> findOverdueStudents(@Param("date") LocalDate date);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT COALESCE(SUM(s.pendingAmount), 0.0) FROM Student s WHERE s.status = 'ACTIVE' AND s.deleted = false")
    Double getTotalPendingFees();

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT COALESCE(SUM(s.paidAmount), 0.0) FROM Student s WHERE s.deleted = false")
    Double getTotalCollectedFees();

    @Query(value = "SELECT DATE(s.last_fee_update) as date, " +
            "COUNT(s.id) as student_count, " +
            "COALESCE(SUM(s.paid_amount), 0) as collected_amount " +
            "FROM students s " +
            "WHERE s.last_fee_update >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY) " +
            "AND s.deleted = false " +
            "GROUP BY DATE(s.last_fee_update) " +
            "ORDER BY date DESC",
            nativeQuery = true)
    List<Object[]> getFeeCollectionTrend();

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.pendingAmount > 0 AND s.status = 'ACTIVE' AND s.deleted = false ORDER BY s.pendingAmount DESC")
    List<Student> findTopDefaulters(@Param("limit") int limit);

    // FIXED: Changed s.isDeleted to s.deleted
    @Query("SELECT s FROM Student s WHERE s.feeDueDate BETWEEN :startDate AND :endDate AND s.feeStatus != 'PAID' AND s.deleted = false")
    List<Student> findStudentsWithFeeDueSoon(@Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    // Bulk update fee status
    @Modifying
    @Query("UPDATE Student s SET s.feeStatus = :status WHERE s.id IN :studentIds AND s.deleted = false")
    void updateFeeStatusForStudents(@Param("studentIds") List<Long> studentIds,
                                    @Param("status") Student.FeeStatus status);

    // ========== SIMPLER METHODS USING SPRING DATA JPA NAMING ==========

    // These methods will be automatically implemented by Spring Data JPA
    Boolean existsByIdAndStatusAndDeletedFalse(Long id, Student.StudentStatus status);

    @Query("SELECT s FROM Student s WHERE s.deleted = false")
    List<Student> findByDeletedFalse();

    List<Student> findByStatusAndDeletedFalse(Student.StudentStatus status);

    @Query("SELECT s FROM Student s WHERE s.grade = :grade AND s.deleted = false")
    List<Student> findByGradeAndDeletedFalse(@Param("grade") String grade);

    List<Student> findByGradeAndStatusAndDeletedFalse(String grade, Student.StudentStatus status);

    List<Student> findByFeeStatusAndDeletedFalse(Student.FeeStatus feeStatus);

    List<Student> findByGradeAndFeeStatusAndDeletedFalse(String grade, Student.FeeStatus feeStatus);

    // Get all students with fee summary
    @Query("SELECT new com.system.SchoolManagementSystem.student.dto.StudentFeeSummaryDTO(" +
            "s.id, " +                    // studentId
            "s.fullName, " +              // studentName
            "s.grade, " +                 // grade
            "COALESCE(s.totalFee, 0.0), " + // totalFee
            "COALESCE(s.paidAmount, 0.0), " + // paidAmount
            "COALESCE(s.pendingAmount, 0.0), " + // pendingAmount
            "s.feeStatus, " +             // feeStatus
            "(SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.student.id = s.id), " + // transactionCount
            "(SELECT MAX(pt.paymentDate) FROM PaymentTransaction pt WHERE pt.student.id = s.id)) " + // lastPaymentDate
            "FROM Student s " +
            "WHERE s.deleted = false " +
            "ORDER BY s.grade, s.fullName")
    List<StudentFeeSummaryDTO> findAllStudentsWithFeeSummary();

    // ========== AUTO-BILLING SPECIFIC METHOD ==========

    /**
     * Find active students that are not deleted - for auto-billing
     * This excludes soft-deleted students and non-active students
     */
    @Query("SELECT s FROM Student s WHERE s.status = 'ACTIVE' AND s.deleted = false")
    List<Student> findActiveAndNotDeleted();

    // ========== GRADE QUERIES ==========

    @Query("SELECT DISTINCT s.grade FROM Student s " +
            "WHERE s.grade IS NOT NULL " +
            "AND TRIM(s.grade) != '' " +  // Use TRIM to handle whitespace-only grades
            "ORDER BY s.grade")
    List<String> findDistinctGrades();

    // For paginated search
    @Query("SELECT s FROM Student s WHERE " +
            "LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.studentId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.grade) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.phone) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "AND s.deleted = false " +
            "ORDER BY s.fullName")
    Page<Student> searchStudentsPaginated(@Param("query") String query, Pageable pageable);  // ✅ Fixed

    // For paginated list of all active students
    @Query("SELECT s FROM Student s WHERE s.deleted = false ORDER BY s.fullName")
    Page<Student> findByDeletedFalse(Pageable pageable);  // ✅ Fixed

}