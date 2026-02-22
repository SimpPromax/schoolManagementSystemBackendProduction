package com.system.SchoolManagementSystem.transaction.repository;

import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    Optional<BankTransaction> findByBankReference(String bankReference);

    List<BankTransaction> findByStatus(TransactionStatus status);

    Page<BankTransaction> findByStatus(TransactionStatus status, Pageable pageable);

    List<BankTransaction> findByStudentId(Long studentId);

    // ========== NEW METHOD ADDED ==========
    List<BankTransaction> findByStudentIdAndStatus(Long studentId, TransactionStatus status);

    List<BankTransaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT COUNT(bt) FROM BankTransaction bt WHERE bt.status = :status")
    Long countByStatus(@Param("status") TransactionStatus status);

    @Query("SELECT SUM(bt.amount) FROM BankTransaction bt WHERE bt.status = :status AND bt.transactionDate >= :startDate")
    Double sumAmountByStatusAndDate(@Param("status") TransactionStatus status, @Param("startDate") LocalDate startDate);

    List<BankTransaction> findByImportBatchId(String importBatchId);

    @Query("SELECT bt FROM BankTransaction bt WHERE " +
            "(:search IS NULL OR " +
            "LOWER(bt.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "bt.bankReference LIKE CONCAT('%', :search, '%') OR " +
            "(bt.student IS NOT NULL AND LOWER(bt.student.fullName) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<BankTransaction> searchTransactions(@Param("search") String search, Pageable pageable);

    // New methods for statistics
    @Query("SELECT COUNT(bt) FROM BankTransaction bt WHERE bt.status = :status AND bt.transactionDate >= :startDate")
    Long countByStatusAndDateAfter(@Param("status") TransactionStatus status,
                                   @Param("startDate") LocalDate startDate);

    @Query("SELECT SUM(bt.amount) FROM BankTransaction bt WHERE bt.status = :status")
    Double sumAmountByStatus(@Param("status") TransactionStatus status);

    @Query("SELECT COUNT(bt) FROM BankTransaction bt WHERE bt.transactionDate BETWEEN :startDate AND :endDate")
    Long countByDateRange(@Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate);

    @Query("SELECT bt.status, COUNT(bt) FROM BankTransaction bt WHERE bt.transactionDate BETWEEN :startDate AND :endDate GROUP BY bt.status")
    List<Object[]> countByStatusAndDateRange(@Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    // ========== ADDITIONAL METHODS NEEDED ==========

    @Query("SELECT bt FROM BankTransaction bt WHERE bt.student.id = :studentId AND bt.status = 'MATCHED'")
    List<BankTransaction> findByStudentIdAndMatched(@Param("studentId") Long studentId);

    @Query("SELECT bt FROM BankTransaction bt WHERE bt.student.id = :studentId AND bt.status = 'VERIFIED'")
    List<BankTransaction> findByStudentIdAndVerified(@Param("studentId") Long studentId);

    @Query("SELECT COALESCE(SUM(bt.amount), 0) FROM BankTransaction bt WHERE bt.student.id = :studentId AND bt.status = 'VERIFIED'")
    Double sumVerifiedAmountByStudent(@Param("studentId") Long studentId);

    @Query("SELECT COALESCE(SUM(bt.amount), 0) FROM BankTransaction bt WHERE bt.student.id = :studentId AND bt.status = 'MATCHED'")
    Double sumMatchedAmountByStudent(@Param("studentId") Long studentId);

    @Query("SELECT COALESCE(SUM(bt.amount), 0) FROM BankTransaction bt WHERE bt.student.id = :studentId")
    Double sumTotalAmountByStudent(@Param("studentId") Long studentId);

    @Query("SELECT bt FROM BankTransaction bt WHERE bt.status IN ('MATCHED', 'VERIFIED') AND bt.student.id = :studentId")
    List<BankTransaction> findProcessedTransactionsByStudent(@Param("studentId") Long studentId);

    @Query("SELECT COALESCE(SUM(bt.amount), 0) FROM BankTransaction bt WHERE bt.status IN ('MATCHED', 'VERIFIED')")
    Double sumProcessedAmount();

    @Query("SELECT COALESCE(SUM(bt.amount), 0) FROM BankTransaction bt WHERE bt.status IN ('MATCHED', 'VERIFIED') AND bt.transactionDate = :date")
    Double sumProcessedAmountByDate(@Param("date") LocalDate date);

    // Method to get bank transactions for a specific student with any status
    @Query("SELECT bt FROM BankTransaction bt WHERE bt.student.id = :studentId")
    List<BankTransaction> findAllByStudentId(@Param("studentId") Long studentId);

    // In BankTransactionRepository.java, add these methods:

    // Single reference check
    boolean existsByBankReference(String bankReference);

    // Batch reference check - OPTIMIZED
    @Query("SELECT b.bankReference FROM BankTransaction b WHERE b.bankReference IN :references")
    Set<String> findExistingReferences(@Param("references") List<String> references);
}