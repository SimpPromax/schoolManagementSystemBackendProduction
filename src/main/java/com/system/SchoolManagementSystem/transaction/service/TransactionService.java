package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.termmanagement.dto.request.PaymentApplicationRequest;
import com.system.SchoolManagementSystem.termmanagement.dto.response.PaymentApplicationResponse;
import com.system.SchoolManagementSystem.termmanagement.entity.StudentTermAssignment;
import com.system.SchoolManagementSystem.termmanagement.entity.TermFeeItem;
import com.system.SchoolManagementSystem.termmanagement.repository.StudentTermAssignmentRepository;
import com.system.SchoolManagementSystem.termmanagement.repository.TermFeeItemRepository;
import com.system.SchoolManagementSystem.termmanagement.service.TermFeeService;
import com.system.SchoolManagementSystem.transaction.validation.TransactionValidationService;
import com.system.SchoolManagementSystem.transaction.dto.request.*;
import com.system.SchoolManagementSystem.transaction.dto.response.*;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import com.system.SchoolManagementSystem.transaction.entity.*;
import com.system.SchoolManagementSystem.transaction.repository.*;
import com.system.SchoolManagementSystem.transaction.util.*;
import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    // ========== REPOSITORIES ==========
    private final BankTransactionRepository bankTransactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StudentFeeAssignmentRepository feeAssignmentRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;
    private final SmsLogRepository smsLogRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final StudentTermAssignmentRepository studentTermAssignmentRepository;

    // ========== NEW TERM MANAGEMENT INTEGRATION ==========
    private final TermFeeService termFeeService;
    private final TermFeeItemRepository termFeeItemRepository;

    // ========== UTILITIES ==========
    private final ReceiptGenerator receiptGenerator;
    private final BankStatementParser bankStatementParser;

    // ========== NEW OPTIMIZATION COMPONENTS ==========
    private final TransactionMatcher transactionMatcher;
    private final StudentCacheService studentCacheService;
    private final StudentFeeUpdateService studentFeeUpdateService;
    private final PaymentTransactionService paymentTransactionService;

    // ========== PERFORMANCE MONITORING ==========
    private final Map<String, ImportProgress> importProgressMap = new ConcurrentHashMap<>();

    // ========== VALIDATION SERVICE ==========
    private final TransactionValidationService transactionValidationService;

    // ========== HELPER CLASSES ==========

    @Getter
    @AllArgsConstructor
    static class ImportBatchResult {
        private List<BankTransaction> savedTransactions;
        private List<String> duplicateReferences;
        private int duplicateCount;

        public ImportBatchResult(List<BankTransaction> savedTransactions,
                                 List<String> duplicateReferences) {
            this.savedTransactions = savedTransactions;
            this.duplicateReferences = duplicateReferences;
            this.duplicateCount = duplicateReferences.size();
        }
    }

    @Getter
    @AllArgsConstructor
    static class BatchFilterResult {
        private List<BankTransaction> uniqueTransactions;
        private List<String> duplicateReferences;
    }

    // ========== INITIALIZATION ==========

    @PostConstruct
    public void initialize() {
        log.info("Initializing TransactionService with single cache system...");

        // Log cache status after a short delay
        CompletableFuture.runAsync(() -> {
            try {
                // Wait a bit for cache to load
                Thread.sleep(3000);

                log.info("📊 Cache Status:");
                log.info("  StudentCacheService loaded: {}", studentCacheService.isCacheLoaded());

                if (studentCacheService.isCacheLoaded()) {
                    Set<String> names = studentCacheService.getAllNames();
                    log.info("  StudentCacheService names: {}", names.size());

                    if (!names.isEmpty()) {
                        log.info("  Sample cached names:");
                        names.stream().limit(5).forEach(name -> log.info("    - {}", name));
                    }
                }
            } catch (Exception e) {
                log.error("Error checking cache status", e);
            }
        });
    }

    // ========== BANK TRANSACTION IMPORT METHOD ==========

    public BankTransactionImportResponse importBankTransactions(BankTransactionImportRequest request) {
        log.info("📥 Importing bank transactions: {}", request.getFile().getOriginalFilename());

        // ========== PARSE VALIDATION RESULTS FROM FRONTEND ==========
        Map<String, String> validationIssues = new HashMap<>();
        if (request.getValidationResults() != null && !request.getValidationResults().isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                ValidationResults validationData = mapper.readValue(
                        request.getValidationResults(),
                        ValidationResults.class
                );

                log.info("📋 Received validation results: {} total, {} valid, {} invalid",
                        validationData.getTotalTransactions(),
                        validationData.getValidCount(),
                        validationData.getInvalidCount());

                if (validationData.getValidationResults() != null) {
                    for (ValidationResult result : validationData.getValidationResults()) {
                        if ("INVALID".equals(result.getStatus()) || "UNMATCHED".equals(result.getStatus())) {
                            String issueMessage = result.getValidationMessage();
                            validationIssues.put(result.getBankReference(), issueMessage);
                            log.debug("📝 Validation issue for {}: {}", result.getBankReference(), issueMessage);
                        }
                    }
                }

                log.info("✅ Loaded validation issues for {} transactions", validationIssues.size());

            } catch (Exception e) {
                log.warn("⚠️ Failed to parse validation results: {}", e.getMessage());
            }
        }
        // =============================================================

        List<BankTransaction> transactions;
        String fileType = request.getFile().getContentType();

        try {
            if (fileType != null && fileType.contains("csv")) {
                transactions = bankStatementParser.parseCsv(request.getFile(), request.getBankAccount());
            } else if (fileType != null && (fileType.contains("excel") || fileType.contains("spreadsheet"))) {
                transactions = bankStatementParser.parseExcel(request.getFile(), request.getBankAccount());
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + fileType);
            }

            log.info("✅ Parser returned {} transactions", transactions.size());

            // ========== PROCESS TRANSACTIONS WITH VALIDATION ISSUES ==========
            List<BankTransaction> processedTransactions = processTransactionsWithValidation(transactions, validationIssues);

            // ========== SAVE TRANSACTIONS WITH OPTIMIZED DUPLICATE CHECKING ==========
            ImportBatchResult batchResult = saveTransactionsInBatchesOptimized(processedTransactions);
            List<BankTransaction> savedTransactions = batchResult.getSavedTransactions();
            List<String> duplicateReferences = batchResult.getDuplicateReferences();

            // ========== COUNT STATISTICS ==========
            long matchedCount = savedTransactions.stream()
                    .filter(t -> t.getStatus() == TransactionStatus.MATCHED)
                    .count();

            long issueCount = savedTransactions.stream()
                    .filter(t -> t.getStatus() == TransactionStatus.UNVERIFIED &&
                            t.getDescription() != null &&
                            t.getDescription().contains("[VALIDATION ISSUE:"))
                    .count();

            log.info("📊 Processing results: {} total, {} matched, {} with issues",
                    savedTransactions.size(), matchedCount, issueCount);

            // ========== CREATE PAYMENTS FOR MATCHED TRANSACTIONS ==========
            if (matchedCount > 0) {
                processPaymentsForMatchedTransactions(savedTransactions);
            }

            // ========== PREPARE RESPONSE ==========
            ImportResult importResult = createImportResult(
                    processedTransactions,
                    savedTransactions,
                    duplicateReferences,
                    matchedCount,
                    issueCount
            );

            log.info("✅ Import completed: {} saved, {} duplicates skipped, {} matched, {} with issues",
                    savedTransactions.size(), duplicateReferences.size(), matchedCount, issueCount);

            return BankTransactionImportResponse.success(
                    String.format("Successfully imported %d transactions", savedTransactions.size()),
                    importResult
            );

        } catch (Exception e) {
            log.error("❌ Failed to import bank transactions", e);
            return BankTransactionImportResponse.error("Failed to import: " + e.getMessage());
        }
    }

    // ========== OPTIMIZED BATCH SAVING WITH DUPLICATE CHECKING ==========

    private ImportBatchResult saveTransactionsInBatchesOptimized(List<BankTransaction> transactions) {
        if (transactions.isEmpty()) {
            return new ImportBatchResult(Collections.emptyList(), Collections.emptyList(), 0);
        }

        log.info("💾 Optimized batch saving for {} transactions...", transactions.size());

        List<BankTransaction> savedTransactions = new ArrayList<>();
        List<String> duplicateReferences = new ArrayList<>();
        int batchSize = 100;
        int totalBatches = (transactions.size() + batchSize - 1) / batchSize;

        for (int i = 0; i < transactions.size(); i += batchSize) {
            int end = Math.min(i + batchSize, transactions.size());
            List<BankTransaction> batch = transactions.subList(i, end);

            log.info("  Processing batch {}/{} ({} transactions)",
                    (i/batchSize) + 1, totalBatches, batch.size());

            // Step 1: Filter duplicates using optimized batch check
            BatchFilterResult filterResult = filterDuplicatesInBatch(batch);

            List<BankTransaction> uniqueBatch = filterResult.getUniqueTransactions();
            duplicateReferences.addAll(filterResult.getDuplicateReferences());

            log.info("    📊 Batch stats: {} unique, {} duplicates",
                    uniqueBatch.size(), filterResult.getDuplicateReferences().size());

            // Step 2: Save unique transactions
            if (!uniqueBatch.isEmpty()) {
                try {
                    List<BankTransaction> savedBatch = bankTransactionRepository.saveAll(uniqueBatch);
                    savedTransactions.addAll(savedBatch);
                    log.info("    ✅ Saved {} unique transactions", savedBatch.size());

                } catch (Exception e) {
                    log.error("❌ Batch save failed, trying individual saves...", e);
                    // Fallback to individual saves
                    saveTransactionsIndividually(uniqueBatch, savedTransactions, duplicateReferences);
                }
            } else {
                log.warn("    ⚠️ Entire batch contains only duplicate transactions");
            }
        }

        // Summary logging
        int totalDuplicates = duplicateReferences.size();
        log.info("📊 Batch Save Summary: {} saved, {} duplicates skipped",
                savedTransactions.size(), totalDuplicates);

        if (totalDuplicates > 0) {
            log.warn("⚠️ Skipped duplicate references: {}",
                    duplicateReferences.stream().limit(10).collect(Collectors.toList()));
            if (totalDuplicates > 10) {
                log.warn("    ... and {} more duplicates", totalDuplicates - 10);
            }
        }

        return new ImportBatchResult(savedTransactions, duplicateReferences, totalDuplicates);
    }

    // ========== OPTIMIZED BATCH DUPLICATE FILTERING ==========

    private BatchFilterResult filterDuplicatesInBatch(List<BankTransaction> batch) {
        List<BankTransaction> uniqueTransactions = new ArrayList<>();
        List<String> duplicateReferences = new ArrayList<>();

        // Step 1: Generate references for null/empty ones and clean existing ones
        List<BankTransaction> processedBatch = new ArrayList<>();
        Set<String> batchInternalRefs = new HashSet<>();

        for (BankTransaction transaction : batch) {
            String bankRef = transaction.getBankReference();

            if (bankRef == null || bankRef.trim().isEmpty()) {
                // Generate unique reference
                String generatedRef = generateUniqueReference();
                transaction.setBankReference(generatedRef);
                log.debug("✨ Generated reference: {}", generatedRef);
                batchInternalRefs.add(generatedRef);
            } else {
                // Clean and validate reference
                bankRef = bankRef.trim();
                transaction.setBankReference(bankRef);

                // Check for duplicates within same batch
                if (batchInternalRefs.contains(bankRef)) {
                    // Duplicate within same batch - generate new reference
                    String newRef = bankRef + "-DUP-" + UUID.randomUUID().toString().substring(0, 4);
                    transaction.setBankReference(newRef);
                    batchInternalRefs.add(newRef);
                    log.warn("⚠️ Duplicate within same batch, generated new ref: {} -> {}", bankRef, newRef);
                } else {
                    batchInternalRefs.add(bankRef);
                }
            }
            processedBatch.add(transaction);
        }

        // Step 2: Collect all references for batch check
        List<String> allReferences = processedBatch.stream()
                .map(BankTransaction::getBankReference)
                .collect(Collectors.toList());

        // Step 3: OPTIMIZED - Batch check against database
        Set<String> existingReferences = new HashSet<>();
        if (!allReferences.isEmpty()) {
            try {
                existingReferences = new HashSet<>(bankTransactionRepository.findExistingReferences(allReferences));
                log.debug("🔍 Batch checked {} references, found {} existing",
                        allReferences.size(), existingReferences.size());
            } catch (Exception e) {
                log.warn("⚠️ Batch reference check failed, falling back to individual checks", e);
                // Fallback to individual checks
                return filterDuplicatesIndividually(processedBatch);
            }
        }

        // Step 4: Filter using batch results
        Set<String> alreadyAddedInBatch = new HashSet<>();
        for (BankTransaction transaction : processedBatch) {
            String bankRef = transaction.getBankReference();

            if (existingReferences.contains(bankRef)) {
                // Duplicate in database
                duplicateReferences.add(bankRef);

                // Mark as duplicate in notes
                String notes = transaction.getNotes() != null ? transaction.getNotes() : "";
                transaction.setNotes(notes + " [DUPLICATE SKIPPED: Already exists in database]");

                log.debug("📛 Skipping duplicate: {}", bankRef);
            } else if (alreadyAddedInBatch.contains(bankRef)) {
                // Duplicate within this filtered batch (should not happen with our logic)
                duplicateReferences.add(bankRef + " [INTERNAL_DUPLICATE]");
                log.warn("📛 Internal duplicate detected: {}", bankRef);
            } else {
                uniqueTransactions.add(transaction);
                alreadyAddedInBatch.add(bankRef);
            }
        }

        return new BatchFilterResult(uniqueTransactions, duplicateReferences);
    }

    // ========== INDIVIDUAL DUPLICATE FILTERING (FALLBACK) ==========

    private BatchFilterResult filterDuplicatesIndividually(List<BankTransaction> batch) {
        List<BankTransaction> uniqueTransactions = new ArrayList<>();
        List<String> duplicateReferences = new ArrayList<>();

        Set<String> alreadyChecked = new HashSet<>();

        for (BankTransaction transaction : batch) {
            String bankRef = transaction.getBankReference();

            if (alreadyChecked.contains(bankRef)) {
                duplicateReferences.add(bankRef + " [BATCH_DUPLICATE]");
                continue;
            }

            try {
                boolean exists = bankTransactionRepository.existsByBankReference(bankRef);
                if (exists) {
                    duplicateReferences.add(bankRef);

                    String notes = transaction.getNotes() != null ? transaction.getNotes() : "";
                    transaction.setNotes(notes + " [DUPLICATE SKIPPED: Already exists in database]");

                    log.debug("📛 Skipping duplicate (individual check): {}", bankRef);
                } else {
                    uniqueTransactions.add(transaction);
                    alreadyChecked.add(bankRef);
                }
            } catch (Exception e) {
                log.error("❌ Failed to check duplicate for {}: {}", bankRef, e.getMessage());
                // When in doubt, skip to avoid errors
                duplicateReferences.add(bankRef + " [CHECK_FAILED: " + e.getMessage() + "]");
            }
        }

        return new BatchFilterResult(uniqueTransactions, duplicateReferences);
    }

    // ========== INDIVIDUAL SAVE FALLBACK ==========

    private void saveTransactionsIndividually(List<BankTransaction> transactions,
                                              List<BankTransaction> savedTransactions,
                                              List<String> duplicateReferences) {
        log.info("🔄 Attempting individual save for {} transactions", transactions.size());

        int savedCount = 0;
        int duplicateCount = 0;
        int errorCount = 0;

        for (BankTransaction transaction : transactions) {
            try {
                String bankRef = transaction.getBankReference();

                // Double-check before individual save
                boolean exists = bankTransactionRepository.existsByBankReference(bankRef);
                if (exists) {
                    duplicateReferences.add(bankRef);
                    duplicateCount++;

                    // Update transaction notes
                    String notes = transaction.getNotes() != null ? transaction.getNotes() : "";
                    transaction.setNotes(notes + " [DUPLICATE SKIPPED: Already exists in database]");

                    continue;
                }

                BankTransaction saved = bankTransactionRepository.save(transaction);
                savedTransactions.add(saved);
                savedCount++;
                log.debug("✅ Saved individually: {}", saved.getBankReference());

            } catch (DataIntegrityViolationException e) {
                // Constraint violation (duplicate that slipped through)
                duplicateReferences.add(transaction.getBankReference());
                duplicateCount++;
                log.warn("⚠️ Constraint violation for {}: {}",
                        transaction.getBankReference(), e.getMessage());

            } catch (Exception e) {
                errorCount++;
                log.error("❌ Failed to save individual transaction {}: {}",
                        transaction.getBankReference(), e.getMessage());
            }
        }

        log.info("🔄 Individual save complete: {} saved, {} duplicates, {} errors",
                savedCount, duplicateCount, errorCount);
    }

    // ========== CREATE IMPORT RESULT ==========

    private ImportResult createImportResult(List<BankTransaction> allTransactions,
                                            List<BankTransaction> savedTransactions,
                                            List<String> duplicateReferences,
                                            long matchedCount,
                                            long issueCount) {
        ImportResult result = new ImportResult();
        result.setTotalTransactions(allTransactions.size());
        result.setSavedTransactions(savedTransactions.size());
        result.setDuplicatesSkipped(duplicateReferences.size());
        result.setDuplicateReferences(duplicateReferences);

        // Set warning message
        StringBuilder warning = new StringBuilder();
        if (!duplicateReferences.isEmpty()) {
            warning.append(String.format("%d duplicate transaction(s) were skipped. ", duplicateReferences.size()));
        }
        if (issueCount > 0) {
            warning.append(String.format("%d transaction(s) have validation issues. ", issueCount));
        }
        if (warning.length() > 0) {
            result.setWarningMessage(warning.toString().trim());
        }

        // Convert to response DTOs
        List<BankTransactionResponse> transactionResponses = savedTransactions.stream()
                .map(this::convertToBankTransactionResponse)
                .collect(Collectors.toList());
        result.setTransactions(transactionResponses);

        return result;
    }

    // ========== HELPER METHODS ==========

    private String generateUniqueReference() {
        return "GEN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private List<BankTransaction> processTransactionsWithValidation(
            List<BankTransaction> transactions,
            Map<String, String> validationIssues) {

        log.info("🔍 Processing {} transactions with {} validation issues",
                transactions.size(), validationIssues.size());

        List<BankTransaction> processed = new ArrayList<>();

        for (BankTransaction transaction : transactions) {
            // Check if this transaction has validation issues from frontend
            String issueMessage = validationIssues.get(transaction.getBankReference());

            if (issueMessage != null && !issueMessage.isEmpty()) {
                // Mark as UNVERIFIED and append issue to description
                transaction.setStatus(TransactionStatus.UNVERIFIED);

                String originalDescription = transaction.getDescription() != null
                        ? transaction.getDescription()
                        : "";

                // Append validation issue to description
                String enhancedDescription = String.format("%s [VALIDATION ISSUE: %s]",
                        originalDescription.trim(),
                        issueMessage);

                transaction.setDescription(enhancedDescription);
                transaction.setNotes("Imported with validation issue: " + issueMessage);

                log.info("⚠️ Transaction {} marked as UNVERIFIED with issue: {}",
                        transaction.getBankReference(), issueMessage);

            } else {
                // Try to auto-match
                Optional<Student> matchedStudent = transactionMatcher.findMatchingStudent(transaction);

                if (matchedStudent.isPresent()) {
                    Student student = matchedStudent.get();

                    // Validate student before matching
                    TransactionValidationService.ValidationResult validation =
                            transactionValidationService.validateStudentForPayment(
                                    student.getId(),
                                    student.getFullName()
                            );

                    if (validation.isValid()) {
                        transaction.setStudent(student);
                        transaction.setStatus(TransactionStatus.MATCHED);
                        log.info("✅ Transaction {} auto-matched to student: {}",
                                transaction.getBankReference(), student.getFullName());
                    } else {
                        // Student validation failed
                        transaction.setStatus(TransactionStatus.UNVERIFIED);
                        String studentIssue = String.format("Student validation failed: %s", validation.getMessage());

                        String enhancedDescription = String.format("%s [VALIDATION ISSUE: %s]",
                                transaction.getDescription() != null ? transaction.getDescription().trim() : "",
                                studentIssue);

                        transaction.setDescription(enhancedDescription);
                        transaction.setNotes(studentIssue);

                        log.warn("⚠️ Transaction {} could not be matched due to student validation: {}",
                                transaction.getBankReference(), validation.getMessage());
                    }
                } else {
                    // No student match found
                    transaction.setStatus(TransactionStatus.UNVERIFIED);
                    transaction.setNotes("No matching student found during auto-matching");

                    log.info("❓ Transaction {} could not be auto-matched",
                            transaction.getBankReference());
                }
            }

            processed.add(transaction);
        }

        return processed;
    }

    private void processPaymentsForMatchedTransactions(List<BankTransaction> transactions) {
        int paymentCreatedCount = 0;

        for (BankTransaction transaction : transactions) {
            if (transaction.getStudent() != null &&
                    transaction.getStatus() == TransactionStatus.MATCHED &&
                    transaction.getPaymentTransaction() == null) {

                try {
                    PaymentTransaction paymentTransaction =
                            paymentTransactionService.createFromMatchedBankTransaction(transaction);

                    // Apply payment to term fees
                    try {
                        PaymentApplicationRequest feeRequest = new PaymentApplicationRequest();
                        feeRequest.setStudentId(transaction.getStudent().getId());
                        feeRequest.setAmount(transaction.getAmount());
                        feeRequest.setReference(transaction.getBankReference());
                        feeRequest.setNotes("Auto-matched from bank import");

                        PaymentApplicationResponse feeResponse = termFeeService.applyPaymentToStudent(feeRequest);

                        log.info("💰 Payment applied: {} +₹{} (Receipt: {}), Pending: ₹{}",
                                transaction.getStudent().getFullName(),
                                transaction.getAmount(),
                                paymentTransaction.getReceiptNumber(),
                                feeResponse.getRemainingPayment());

                    } catch (Exception feeError) {
                        log.warn("⚠️ Failed to apply payment to term fees: {}", feeError.getMessage());
                    }

                    paymentCreatedCount++;

                    log.info("💰 Created payment for {}: Receipt {} - ₹{}",
                            transaction.getStudent().getFullName(),
                            paymentTransaction.getReceiptNumber(),
                            transaction.getAmount());

                } catch (Exception e) {
                    log.warn("⚠️ Failed to create payment for transaction {}: {}",
                            transaction.getBankReference(), e.getMessage());
                }
            }
        }

        log.info("✅ Created {} payment transactions", paymentCreatedCount);
    }

    // ========== CACHE MANAGEMENT METHODS ==========

    /**
     * Get cache statistics for optimization
     */
    public StudentCacheService.CacheStats getCacheStats() {
        return StudentCacheService.CacheStats.fromService(studentCacheService);
    }

    /**
     * Refresh cache
     */
    public void refreshMatcherCache() {
        log.info("🔄 Refreshing student cache...");
        studentCacheService.refreshCache();
        log.info("✅ Student cache refresh initiated");
    }

    /**
     * Get bank transaction count by status
     */
    public long getBankTransactionCountByStatus(TransactionStatus status) {
        if (status == null) return 0;
        Long count = bankTransactionRepository.countByStatus(status);
        return count != null ? count : 0L;
    }

    public Map<String, Object> verifyCacheStatus() {
        Map<String, Object> status = new HashMap<>();

        // Check StudentCacheService
        boolean isCacheLoaded = studentCacheService.isCacheLoaded();
        status.put("studentCacheLoaded", isCacheLoaded);

        if (isCacheLoaded) {
            Set<String> names = studentCacheService.getAllNames();
            status.put("studentCacheSize", names.size());

            // Get sample names
            List<String> sampleNames = names.stream()
                    .limit(5)
                    .collect(Collectors.toList());
            status.put("sampleNames", sampleNames);
        }

        // Check database
        long studentCount = studentRepository.count();
        status.put("databaseStudentCount", studentCount);

        // Check if students have data
        List<Student> sampleStudents = studentRepository.findAll()
                .stream()
                .limit(3)
                .collect(Collectors.toList());

        status.put("sampleStudents", sampleStudents.stream()
                .map(s -> Map.of(
                        "id", s.getId(),
                        "studentId", s.getStudentId(),
                        "fullName", s.getFullName(),
                        "grade", s.getGrade(),
                        "hasName", s.getFullName() != null && !s.getFullName().isEmpty()
                ))
                .collect(Collectors.toList()));

        // Check if any bank transactions exist
        long bankTxCount = bankTransactionRepository.count();
        status.put("bankTransactionCount", bankTxCount);

        log.info("🔧 Cache verification: loaded={}, students={}, bankTx={}",
                isCacheLoaded, studentCount, bankTxCount);

        return status;
    }

    // ========== BANK TRANSACTION OPERATIONS ==========

    public Page<BankTransactionResponse> getBankTransactions(TransactionStatus status, String search, Pageable pageable) {
        try {
            Page<BankTransaction> transactions;

            if (status != null && search != null && !search.trim().isEmpty()) {
                transactions = bankTransactionRepository.searchTransactions(search, pageable);
                List<BankTransaction> filtered = transactions.getContent().stream()
                        .filter(t -> t.getStatus() == status)
                        .collect(Collectors.toList());

                return new PageImpl<>(filtered.stream()
                        .map(this::convertToBankTransactionResponse)
                        .collect(Collectors.toList()),
                        pageable,
                        filtered.size());

            } else if (status != null) {
                transactions = bankTransactionRepository.findByStatus(status, pageable);
            } else if (search != null && !search.trim().isEmpty()) {
                transactions = bankTransactionRepository.searchTransactions(search, pageable);
            } else {
                transactions = bankTransactionRepository.findAll(pageable);
            }

            return transactions.map(this::convertToBankTransactionResponse);

        } catch (Exception e) {
            log.error("❌ Error getting bank transactions", e);
            return Page.empty(pageable);
        }
    }

    public BankTransactionResponse getBankTransactionById(Long id) {
        BankTransaction transaction = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank transaction not found with id: " + id));
        return convertToBankTransactionResponse(transaction);
    }

    public BankTransactionResponse matchBankTransaction(Long transactionId, Long studentId) {
        BankTransaction transaction = bankTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        transaction.setStudent(student);
        transaction.setStatus(TransactionStatus.MATCHED);
        transaction.setMatchedAt(LocalDateTime.now());

        try {
            PaymentTransaction paymentTransaction =
                    paymentTransactionService.createFromMatchedBankTransaction(transaction);

            try {
                PaymentApplicationRequest feeRequest = new PaymentApplicationRequest();
                feeRequest.setStudentId(student.getId());
                feeRequest.setAmount(transaction.getAmount());
                feeRequest.setReference(transaction.getBankReference());
                feeRequest.setNotes("Manual match from bank transaction");

                PaymentApplicationResponse feeResponse = termFeeService.applyPaymentToStudent(feeRequest);

                log.info("💰 Term fees updated via manual match: {} +₹{} (Receipt: {}), All paid: {}",
                        student.getFullName(), transaction.getAmount(),
                        paymentTransaction.getReceiptNumber(), feeResponse.getAllPaid());

            } catch (Exception feeError) {
                log.error("⚠️ Failed to apply payment to term fees: {}", feeError.getMessage());
                transaction.setStudent(null);
                transaction.setStatus(TransactionStatus.UNVERIFIED);
                transaction.setPaymentTransaction(null);
                throw new RuntimeException("Failed to apply payment to term fees: " + feeError.getMessage());
            }

        } catch (Exception feeError) {
            log.error("⚠️ Failed to create payment: {}", feeError.getMessage());
            transaction.setStudent(null);
            transaction.setStatus(TransactionStatus.UNVERIFIED);
            transaction.setPaymentTransaction(null);
            throw new RuntimeException("Failed to create payment transaction: " + feeError.getMessage());
        }

        BankTransaction savedTransaction = bankTransactionRepository.save(transaction);

        if (savedTransaction.getPaymentTransaction() != null) {
            sendAutoMatchSms(student, savedTransaction, savedTransaction.getPaymentTransaction());
        }

        log.info("✅ Manually matched transaction {} to student {}", transactionId, student.getFullName());
        return convertToBankTransactionResponse(savedTransaction);
    }

    public void deleteBankTransaction(Long id) {
        BankTransaction transaction = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        PaymentTransaction paymentTransaction = transaction.getPaymentTransaction();

        if (transaction.getStudent() != null && transaction.getAmount() != null) {
            log.warn("⚠️ Need to implement term fee reversal for deleted transaction: {}",
                    transaction.getBankReference());
        }

        if (paymentTransaction != null) {
            paymentTransactionRepository.delete(paymentTransaction);
            log.info("🗑️ Deleted linked payment transaction: {}", paymentTransaction.getReceiptNumber());
        }

        bankTransactionRepository.delete(transaction);
        log.info("🗑️ Deleted bank transaction with id: {}", id);
    }

    // ========== PAYMENT TRANSACTION OPERATIONS ==========

    public PaymentTransactionResponse verifyPayment(PaymentVerificationRequest request) {
        log.info("🔐 Verifying payment for bank transaction: {}", request.getBankTransactionId());

        BankTransaction bankTransaction = bankTransactionRepository.findById(request.getBankTransactionId())
                .orElseThrow(() -> new RuntimeException("Bank transaction not found"));

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        PaymentTransaction paymentTransaction =
                paymentTransactionService.getOrCreateForBankTransactionId(request.getBankTransactionId());

        if (!paymentTransaction.getIsVerified()) {
            paymentTransaction.setAmount(request.getAmount());
            paymentTransaction.setPaymentMethod(request.getPaymentMethod());
            paymentTransaction.setPaymentDate(LocalDateTime.now());
            paymentTransaction.setPaymentFor(request.getPaymentFor());
            paymentTransaction.setDiscountApplied(request.getDiscountApplied());
            paymentTransaction.setLateFeePaid(request.getLateFeePaid());
            paymentTransaction.setConvenienceFee(request.getConvenienceFee());
            paymentTransaction.setNotes(request.getNotes());
            paymentTransaction.setIsVerified(true);
            paymentTransaction.setVerifiedAt(LocalDateTime.now());

            bankTransaction.setStudent(student);
            bankTransaction.setStatus(TransactionStatus.VERIFIED);
            bankTransactionRepository.save(bankTransaction);

            paymentTransactionRepository.save(paymentTransaction);
        }

        PaymentTransaction verifiedTransaction =
                paymentTransactionService.verifyPaymentTransaction(paymentTransaction.getId());

        try {
            PaymentApplicationRequest feeRequest = new PaymentApplicationRequest();
            feeRequest.setStudentId(student.getId());
            feeRequest.setAmount(request.getAmount());
            feeRequest.setReference(bankTransaction.getBankReference());
            feeRequest.setNotes("Payment verification: " + request.getNotes());

            PaymentApplicationResponse feeResponse = termFeeService.applyPaymentToStudent(feeRequest);

            log.info("💰 Term fees updated via payment verification: {} +₹{} (Receipt: {}), All paid: {}",
                    student.getFullName(), request.getAmount(),
                    verifiedTransaction.getReceiptNumber(), feeResponse.getAllPaid());

        } catch (Exception feeError) {
            log.error("⚠️ Failed to update term fees during verification: {}", feeError.getMessage());
        }

        log.info("✅ Payment verified: Receipt {} for student {} - ₹{}",
                verifiedTransaction.getReceiptNumber(),
                student.getFullName(),
                verifiedTransaction.getAmount());

        if (request.getSendSms() != null && request.getSendSms()) {
            sendPaymentSmsAsync(student, verifiedTransaction, request.getAmount());
        }

        return convertToPaymentTransactionResponse(verifiedTransaction);
    }

    @Async
    protected void sendPaymentSmsAsync(Student student, PaymentTransaction transaction, Double amount) {
        try {
            SmsRequest smsRequest = new SmsRequest();
            smsRequest.setStudentId(student.getId());
            smsRequest.setPaymentTransactionId(transaction.getId());
            smsRequest.setMessage("Payment of ₹" + amount + " received. Receipt: " + transaction.getReceiptNumber());

            String recipientPhone = getBestContactPhone(student);
            if (recipientPhone != null && !recipientPhone.trim().isEmpty()) {
                smsRequest.setRecipientPhone(recipientPhone);
                sendPaymentSms(smsRequest);
                log.info("📱 SMS sent for payment {}", transaction.getReceiptNumber());
            } else {
                log.warn("📵 No phone number available for student {} to send SMS", student.getFullName());
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to send SMS for payment {}: {}", transaction.getId(), e.getMessage());
        }
    }

    public List<PaymentTransactionResponse> bulkVerifyPayments(BulkVerificationRequest request) {
        log.info("📦 Bulk verifying {} payments", request.getBankTransactionIds().size());

        List<PaymentTransactionResponse> responses = new ArrayList<>();

        for (Long bankTransactionId : request.getBankTransactionIds()) {
            try {
                BankTransaction bankTransaction = bankTransactionRepository.findById(bankTransactionId)
                        .orElseThrow(() -> new RuntimeException("Bank transaction not found: " + bankTransactionId));

                if (bankTransaction.getStudent() == null) {
                    log.warn("⚠️ Skipping transaction {} - not matched to any student", bankTransactionId);
                    continue;
                }

                PaymentTransaction paymentTransaction =
                        paymentTransactionService.getOrCreateForBankTransactionId(bankTransactionId);

                PaymentVerificationRequest singleRequest = new PaymentVerificationRequest();
                singleRequest.setBankTransactionId(bankTransactionId);
                singleRequest.setStudentId(bankTransaction.getStudent().getId());
                singleRequest.setAmount(bankTransaction.getAmount());
                singleRequest.setPaymentMethod(bankTransaction.getPaymentMethod());
                singleRequest.setSendSms(request.getSendSms());
                singleRequest.setNotes(request.getNotes());
                singleRequest.setPaymentFor("SCHOOL_FEE");

                PaymentTransactionResponse verifiedPayment = verifyPayment(singleRequest);
                responses.add(verifiedPayment);

                log.info("✅ Bulk verified transaction {} for student {} (Receipt: {})",
                        bankTransactionId,
                        bankTransaction.getStudent().getFullName(),
                        paymentTransaction.getReceiptNumber());

            } catch (Exception e) {
                log.error("❌ Failed to verify payment for transaction {}: {}", bankTransactionId, e.getMessage());
            }
        }

        log.info("📊 Bulk verification completed: {} successful, {} total",
                responses.size(), request.getBankTransactionIds().size());
        return responses;
    }

    public Page<PaymentTransactionResponse> getVerifiedTransactions(String search, Pageable pageable) {
        try {
            Page<PaymentTransaction> transactions;

            if (search != null && !search.trim().isEmpty()) {
                transactions = paymentTransactionRepository.searchTransactions(search, pageable);

                List<PaymentTransaction> verifiedList = transactions.getContent().stream()
                        .filter(PaymentTransaction::getIsVerified)
                        .collect(Collectors.toList());

                Page<PaymentTransaction> verifiedPage = new PageImpl<>(
                        verifiedList,
                        pageable,
                        verifiedList.size()
                );

                return verifiedPage.map(this::convertToPaymentTransactionResponse);

            } else {
                transactions = paymentTransactionRepository.findByIsVerified(true, pageable);
                return transactions.map(this::convertToPaymentTransactionResponse);
            }

        } catch (Exception e) {
            log.error("❌ Error getting verified transactions", e);
            return Page.empty(pageable);
        }
    }

    public PaymentTransactionResponse getPaymentTransactionById(Long id) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment transaction not found with id: " + id));
        return convertToPaymentTransactionResponse(transaction);
    }

    public List<PaymentTransactionResponse> getStudentTransactions(Long studentId) {
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByStudentId(studentId);
        return transactions.stream()
                .map(this::convertToPaymentTransactionResponse)
                .collect(Collectors.toList());
    }

    // ========== NEW METHODS FOR TERM FEE INTEGRATION ==========

    public Map<String, Object> getStudentFeeBreakdown(Long studentId) {
        Map<String, Object> breakdown = new HashMap<>();

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));

        List<StudentTermAssignment> termAssignments = studentTermAssignmentRepository
                .findByStudentId(studentId);

        List<StudentFeeAssignment> feeAssignments = feeAssignmentRepository
                .findByStudentId(studentId);

        double totalTermFee = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getTotalTermFee)
                .sum();

        double totalPaid = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPaidAmount)
                .sum();

        double totalPending = termAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum();

        Optional<StudentTermAssignment> currentAssignment = termAssignments.stream()
                .filter(ta -> ta.getAcademicTerm() != null && ta.getAcademicTerm().getIsCurrent())
                .findFirst();

        List<StudentTermAssignment> overdueAssignments = termAssignments.stream()
                .filter(ta -> ta.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                .collect(Collectors.toList());

        List<Map<String, Object>> upcomingDueDates = termAssignments.stream()
                .filter(ta -> ta.getDueDate() != null &&
                        ta.getDueDate().isAfter(LocalDate.now()) &&
                        ta.getPendingAmount() > 0)
                .sorted((a, b) -> a.getDueDate().compareTo(b.getDueDate()))
                .limit(5)
                .map(ta -> {
                    Map<String, Object> dueInfo = new HashMap<>();
                    dueInfo.put("termName", ta.getAcademicTerm().getTermName());
                    dueInfo.put("dueDate", ta.getDueDate());
                    dueInfo.put("amount", ta.getPendingAmount());
                    dueInfo.put("status", ta.getTermFeeStatus().name());
                    return dueInfo;
                })
                .collect(Collectors.toList());

        List<PaymentTransaction> paymentHistory = paymentTransactionRepository
                .findByStudentIdOrderByPaymentDateDesc(studentId);

        breakdown.put("studentId", studentId);
        breakdown.put("studentName", student.getFullName());
        breakdown.put("grade", student.getGrade());
        breakdown.put("studentCode", student.getStudentId());

        Map<String, Object> feeSummary = new HashMap<>();
        feeSummary.put("totalFee", totalTermFee);
        feeSummary.put("totalPaid", totalPaid);
        feeSummary.put("totalPending", totalPending);
        feeSummary.put("paymentPercentage", totalTermFee > 0 ? (totalPaid / totalTermFee) * 100 : 0);
        feeSummary.put("overdueCount", overdueAssignments.size());
        feeSummary.put("overdueAmount", overdueAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum());
        breakdown.put("feeSummary", feeSummary);

        if (currentAssignment.isPresent()) {
            Map<String, Object> currentTerm = new HashMap<>();
            StudentTermAssignment assignment = currentAssignment.get();
            currentTerm.put("termName", assignment.getAcademicTerm().getTermName());
            currentTerm.put("totalFee", assignment.getTotalTermFee());
            currentTerm.put("paidAmount", assignment.getPaidAmount());
            currentTerm.put("pendingAmount", assignment.getPendingAmount());
            currentTerm.put("status", assignment.getTermFeeStatus().name());
            currentTerm.put("dueDate", assignment.getDueDate());
            breakdown.put("currentTerm", currentTerm);
        }

        List<Map<String, Object>> termSummaries = termAssignments.stream()
                .map(ta -> {
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("termName", ta.getAcademicTerm().getTermName());
                    summary.put("academicYear", ta.getAcademicTerm().getAcademicYear());
                    summary.put("totalFee", ta.getTotalTermFee());
                    summary.put("paidAmount", ta.getPaidAmount());
                    summary.put("pendingAmount", ta.getPendingAmount());
                    summary.put("status", ta.getTermFeeStatus().name());
                    summary.put("dueDate", ta.getDueDate());
                    summary.put("billingDate", ta.getBillingDate());
                    return summary;
                })
                .collect(Collectors.toList());
        breakdown.put("termSummaries", termSummaries);

        breakdown.put("upcomingDueDates", upcomingDueDates);

        List<Map<String, Object>> recentPayments = paymentHistory.stream()
                .limit(10)
                .map(pt -> {
                    Map<String, Object> payment = new HashMap<>();
                    payment.put("receiptNumber", pt.getReceiptNumber());
                    payment.put("amount", pt.getAmount());
                    payment.put("paymentDate", pt.getPaymentDate());
                    payment.put("paymentMethod", pt.getPaymentMethod().name());
                    payment.put("verified", pt.getIsVerified());
                    return payment;
                })
                .collect(Collectors.toList());
        breakdown.put("recentPayments", recentPayments);

        List<Map<String, Object>> feeAssignmentSummaries = feeAssignments.stream()
                .map(fa -> {
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("academicYear", fa.getAcademicYear());
                    summary.put("totalAmount", fa.getTotalAmount());
                    summary.put("paidAmount", fa.getPaidAmount());
                    summary.put("pendingAmount", fa.getPendingAmount());
                    summary.put("status", fa.getFeeStatus().name());
                    summary.put("dueDate", fa.getDueDate());
                    return summary;
                })
                .collect(Collectors.toList());
        breakdown.put("feeAssignments", feeAssignmentSummaries);

        return breakdown;
    }

    public Map<String, Object> applyManualPayment(Long studentId, Double amount, String reference, String notes) {
        Map<String, Object> response = new HashMap<>();

        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            PaymentApplicationRequest feeRequest = new PaymentApplicationRequest();
            feeRequest.setStudentId(studentId);
            feeRequest.setAmount(amount);
            feeRequest.setReference(reference);
            feeRequest.setNotes(notes != null ? notes : "Manual payment");

            PaymentApplicationResponse paymentResult = termFeeService.applyPaymentToStudent(feeRequest);

            PaymentTransaction manualPayment = PaymentTransaction.builder()
                    .student(student)
                    .amount(amount)
                    .paymentMethod(com.system.SchoolManagementSystem.transaction.enums.PaymentMethod.CASH)
                    .paymentDate(LocalDateTime.now())
                    .receiptNumber("MANUAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .isVerified(true)
                    .verifiedAt(LocalDateTime.now())
                    .notes(notes)
                    .paymentFor("MANUAL_PAYMENT")
                    .build();

            paymentTransactionRepository.save(manualPayment);

            response.put("success", true);
            response.put("message", String.format("Payment of KES %.2f applied to %s", amount, student.getFullName()));
            response.put("receiptNumber", manualPayment.getReceiptNumber());
            response.put("paymentResult", paymentResult);
            response.put("studentName", student.getFullName());
            response.put("appliedAmount", paymentResult.getAppliedPayment());
            response.put("remainingPayment", paymentResult.getRemainingPayment());
            response.put("allPaid", paymentResult.getAllPaid());

            log.info("✅ Manual payment applied: {} +KES {} (Receipt: {}), All paid: {}",
                    student.getFullName(), amount, manualPayment.getReceiptNumber(), paymentResult.getAllPaid());

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to apply manual payment: " + e.getMessage());
            log.error("❌ Failed to apply manual payment: {}", e.getMessage(), e);
        }

        return response;
    }

    public Map<String, Object> getFeeStatisticsByGrade() {
        Map<String, Object> statistics = new HashMap<>();

        Map<String, List<Student>> studentsByGrade = studentRepository.findAll().stream()
                .filter(s -> s.getStatus() == Student.StudentStatus.ACTIVE)
                .collect(Collectors.groupingBy(Student::getGrade));

        List<Map<String, Object>> gradeStats = new ArrayList<>();

        for (Map.Entry<String, List<Student>> entry : studentsByGrade.entrySet()) {
            String grade = entry.getKey();
            List<Student> students = entry.getValue();

            double totalFee = 0.0;
            double totalPaid = 0.0;
            double totalPending = 0.0;
            int overdueCount = 0;

            for (Student student : students) {
                totalFee += student.getTotalFee() != null ? student.getTotalFee() : 0.0;
                totalPaid += student.getPaidAmount() != null ? student.getPaidAmount() : 0.0;
                totalPending += student.getPendingAmount() != null ? student.getPendingAmount() : 0.0;

                if (student.getFeeStatus() == Student.FeeStatus.OVERDUE) {
                    overdueCount++;
                }
            }

            Map<String, Object> gradeStat = new HashMap<>();
            gradeStat.put("grade", grade);
            gradeStat.put("studentCount", students.size());
            gradeStat.put("totalFee", totalFee);
            gradeStat.put("totalPaid", totalPaid);
            gradeStat.put("totalPending", totalPending);
            gradeStat.put("overdueCount", overdueCount);
            gradeStat.put("collectionRate", totalFee > 0 ? (totalPaid / totalFee) * 100 : 0);

            gradeStats.add(gradeStat);
        }

        gradeStats.sort((a, b) -> ((String) a.get("grade")).compareTo((String) b.get("grade")));

        statistics.put("gradeStatistics", gradeStats);
        statistics.put("totalStudents", studentRepository.count());
        statistics.put("totalActiveStudents", studentRepository.findAll().stream()
                .filter(s -> s.getStatus() == Student.StudentStatus.ACTIVE)
                .count());

        double overallTotalFee = gradeStats.stream()
                .mapToDouble(gs -> (Double) gs.get("totalFee"))
                .sum();
        double overallTotalPaid = gradeStats.stream()
                .mapToDouble(gs -> (Double) gs.get("totalPaid"))
                .sum();
        double overallTotalPending = gradeStats.stream()
                .mapToDouble(gs -> (Double) gs.get("totalPending"))
                .sum();
        int overallOverdueCount = gradeStats.stream()
                .mapToInt(gs -> (Integer) gs.get("overdueCount"))
                .sum();

        statistics.put("overallTotalFee", overallTotalFee);
        statistics.put("overallTotalPaid", overallTotalPaid);
        statistics.put("overallTotalPending", overallTotalPending);
        statistics.put("overallOverdueCount", overallOverdueCount);
        statistics.put("overallCollectionRate", overallTotalFee > 0 ?
                (overallTotalPaid / overallTotalFee) * 100 : 0);

        return statistics;
    }

    // ========== STATISTICS OPERATIONS ==========

    public TransactionStatisticsResponse getTransactionStatistics() {
        TransactionStatisticsResponse statistics = new TransactionStatisticsResponse();

        try {
            long totalBank = bankTransactionRepository.count();
            long unverifiedCount = bankTransactionRepository.countByStatus(TransactionStatus.UNVERIFIED);
            long matchedCount = bankTransactionRepository.countByStatus(TransactionStatus.MATCHED);
            long verifiedCount = paymentTransactionRepository.countByIsVerified(true);

            statistics.setUnverifiedCount(unverifiedCount);
            statistics.setMatchedCount(matchedCount);
            statistics.setVerifiedCount(verifiedCount);

            if (totalBank > 0) {
                double matchRate = (matchedCount * 100.0) / totalBank;
                statistics.setMatchRate(String.format("%.1f%%", matchRate));
            } else {
                statistics.setMatchRate("0%");
            }

            Double totalAmount = paymentTransactionRepository.getTotalVerifiedAmount();
            statistics.setTotalAmount(totalAmount != null ? totalAmount : 0.0);

            Double todayAmount = paymentTransactionRepository.getTotalVerifiedAmountToday();
            statistics.setTodayAmount(todayAmount != null ? todayAmount : 0.0);

            Map<String, Object> feeStats = getFeeStatisticsByGrade();
            statistics.setFeeStatistics(feeStats);

            List<StudentTermAssignment> pendingAssignments = studentTermAssignmentRepository
                    .findAll().stream()
                    .filter(a -> a.getTermFeeStatus() == StudentTermAssignment.FeeStatus.PENDING ||
                            a.getTermFeeStatus() == StudentTermAssignment.FeeStatus.PARTIAL ||
                            a.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                    .collect(Collectors.toList());

            long pendingPayments = pendingAssignments.stream()
                    .filter(a -> a.getTermFeeStatus() == StudentTermAssignment.FeeStatus.PENDING)
                    .count();

            long overduePayments = pendingAssignments.stream()
                    .filter(a -> a.getTermFeeStatus() == StudentTermAssignment.FeeStatus.OVERDUE)
                    .count();

            statistics.setPendingPayments(pendingPayments);
            statistics.setOverduePayments(overduePayments);

            double totalPendingAmount = pendingAssignments.stream()
                    .mapToDouble(StudentTermAssignment::getPendingAmount)
                    .sum();
            statistics.setTotalPendingAmount(totalPendingAmount);

            log.info("📊 Statistics calculated: Match Rate={}, Pending Students={}, Total Pending=₹{}",
                    statistics.getMatchRate(), pendingPayments, totalPendingAmount);

        } catch (Exception e) {
            log.error("❌ Error calculating transaction statistics", e);
            statistics.setMatchRate("Error");
        }

        return statistics;
    }

    public TransactionStatisticsResponse getStatisticsByDateRange(LocalDate startDate, LocalDate endDate) {
        TransactionStatisticsResponse statistics = new TransactionStatisticsResponse();

        try {
            List<BankTransaction> unverifiedTransactions = bankTransactionRepository
                    .findByTransactionDateBetween(startDate, endDate)
                    .stream()
                    .filter(t -> t.getStatus() == TransactionStatus.UNVERIFIED)
                    .collect(Collectors.toList());
            statistics.setUnverifiedCount((long) unverifiedTransactions.size());

            List<PaymentTransaction> verifiedPayments = paymentTransactionRepository
                    .findByPaymentDateBetween(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay())
                    .stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .collect(Collectors.toList());
            statistics.setVerifiedCount((long) verifiedPayments.size());

            Double totalAmount = verifiedPayments.stream()
                    .mapToDouble(PaymentTransaction::getAmount)
                    .sum();
            statistics.setTotalAmount(totalAmount);

            if (LocalDate.now().isAfter(startDate.minusDays(1)) && LocalDate.now().isBefore(endDate.plusDays(1))) {
                Double todayAmount = paymentTransactionRepository.getTotalVerifiedAmountToday();
                statistics.setTodayAmount(todayAmount != null ? todayAmount : 0.0);
            } else {
                statistics.setTodayAmount(0.0);
            }

            long totalProcessed = statistics.getVerifiedCount();
            long totalTransactions = statistics.getUnverifiedCount() + totalProcessed;

            if (totalTransactions > 0) {
                double matchRate = (totalProcessed * 100.0) / totalTransactions;
                statistics.setMatchRate(String.format("%.1f%%", matchRate));
            } else {
                statistics.setMatchRate("0%");
            }

        } catch (Exception e) {
            log.error("❌ Error calculating date range statistics", e);
        }

        return statistics;
    }

    // ========== SMS OPERATIONS ==========

    public SmsLogResponse sendPaymentSms(SmsRequest request) {
        log.info("📱 Sending SMS for payment transaction: {}", request.getPaymentTransactionId());

        PaymentTransaction paymentTransaction = paymentTransactionRepository.findById(request.getPaymentTransactionId())
                .orElseThrow(() -> new RuntimeException("Payment transaction not found"));

        Student student = paymentTransaction.getStudent();

        String recipientPhone = request.getRecipientPhone();
        if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
            recipientPhone = getBestContactPhone(student);
        }

        if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
            log.warn("📵 No valid phone number available for student {}", student.getFullName());

            SmsLog smsLog = SmsLog.builder()
                    .student(student)
                    .paymentTransaction(paymentTransaction)
                    .recipientPhone("N/A")
                    .message(request.getMessage())
                    .status(SmsLog.SmsStatus.FAILED)
                    .gatewayResponse("No valid phone number available")
                    .sentAt(LocalDateTime.now())
                    .build();

            SmsLog savedSmsLog = smsLogRepository.save(smsLog);
            return convertToSmsLogResponse(savedSmsLog);
        }

        recipientPhone = cleanPhoneNumber(recipientPhone);
        if (!isValidKenyanPhoneNumber(recipientPhone)) {
            log.warn("📵 Invalid phone number format for student {}: {}", student.getFullName(), recipientPhone);

            SmsLog smsLog = SmsLog.builder()
                    .student(student)
                    .paymentTransaction(paymentTransaction)
                    .recipientPhone(recipientPhone)
                    .message(request.getMessage())
                    .status(SmsLog.SmsStatus.FAILED)
                    .gatewayResponse("Invalid phone number format")
                    .sentAt(LocalDateTime.now())
                    .build();

            SmsLog savedSmsLog = smsLogRepository.save(smsLog);
            return convertToSmsLogResponse(savedSmsLog);
        }

        SmsLog smsLog = SmsLog.builder()
                .student(student)
                .paymentTransaction(paymentTransaction)
                .recipientPhone(recipientPhone)
                .message(request.getMessage())
                .status(SmsLog.SmsStatus.SENT)
                .gatewayMessageId("SMS-" + UUID.randomUUID().toString().substring(0, 8))
                .sentAt(LocalDateTime.now())
                .build();

        log.info("📲 SMS SENT: To {} - Message: {}", recipientPhone, request.getMessage());

        SmsLog savedSmsLog = smsLogRepository.save(smsLog);

        paymentTransaction.setSmsSent(true);
        paymentTransaction.setSmsSentAt(LocalDateTime.now());
        paymentTransaction.setSmsId(savedSmsLog.getGatewayMessageId());
        paymentTransactionRepository.save(paymentTransaction);

        log.info("✅ SMS record saved with ID: {}", savedSmsLog.getId());
        return convertToSmsLogResponse(savedSmsLog);
    }

    public List<SmsLogResponse> getSmsLogs(Long studentId, Long paymentTransactionId) {
        List<SmsLog> smsLogs;

        if (studentId != null && paymentTransactionId != null) {
            smsLogs = smsLogRepository.findByStudentId(studentId).stream()
                    .filter(log -> paymentTransactionId.equals(log.getPaymentTransaction().getId()))
                    .collect(Collectors.toList());
        } else if (studentId != null) {
            smsLogs = smsLogRepository.findByStudentId(studentId);
        } else if (paymentTransactionId != null) {
            smsLogs = smsLogRepository.findByPaymentTransactionId(paymentTransactionId);
        } else {
            smsLogs = smsLogRepository.findAll();
        }

        return smsLogs.stream()
                .map(this::convertToSmsLogResponse)
                .collect(Collectors.toList());
    }

    // ========== EXPORT OPERATIONS ==========

    public byte[] exportTransactionsToCsv(String type, LocalDate startDate, LocalDate endDate) {
        StringBuilder csvContent = new StringBuilder();

        if ("verified".equalsIgnoreCase(type)) {
            csvContent.append("Receipt Number,Payment Date,Amount,Student Name,Grade,Payment Method,Bank Reference,Bank Transaction ID\n");

            List<PaymentTransaction> transactions = paymentTransactionRepository
                    .findByPaymentDateBetween(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay())
                    .stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .limit(100000)
                    .collect(Collectors.toList());

            for (PaymentTransaction transaction : transactions) {
                csvContent.append(String.format("\"%s\",%s,%.2f,\"%s\",\"%s\",\"%s\",\"%s\",%s\n",
                        transaction.getReceiptNumber(),
                        transaction.getPaymentDate().toLocalDate(),
                        transaction.getAmount(),
                        transaction.getStudent().getFullName(),
                        transaction.getStudent().getGrade(),
                        transaction.getPaymentMethod(),
                        transaction.getBankReference() != null ? transaction.getBankReference() : "",
                        transaction.getBankTransaction() != null ? transaction.getBankTransaction().getId() : ""
                ));
            }

            log.info("📤 Exported {} verified transactions to CSV", transactions.size());

        } else if ("bank".equalsIgnoreCase(type)) {
            csvContent.append("Bank Reference,Transaction Date,Description,Amount,Status,Student Name,Bank Account,Payment Transaction ID,Receipt Number\n");

            List<BankTransaction> transactions = bankTransactionRepository
                    .findByTransactionDateBetween(startDate, endDate)
                    .stream()
                    .limit(100000)
                    .collect(Collectors.toList());

            for (BankTransaction transaction : transactions) {
                csvContent.append(String.format("\"%s\",%s,\"%s\",%.2f,\"%s\",\"%s\",\"%s\",%s,\"%s\"\n",
                        transaction.getBankReference(),
                        transaction.getTransactionDate(),
                        transaction.getDescription(),
                        transaction.getAmount(),
                        transaction.getStatus(),
                        transaction.getStudent() != null ? transaction.getStudent().getFullName() : "",
                        transaction.getBankAccount() != null ? transaction.getBankAccount() : "",
                        transaction.getPaymentTransaction() != null ? transaction.getPaymentTransaction().getId() : "",
                        transaction.getPaymentTransaction() != null ? transaction.getPaymentTransaction().getReceiptNumber() : ""
                ));
            }

            log.info("📤 Exported {} bank transactions to CSV", transactions.size());
        } else {
            throw new IllegalArgumentException("Invalid export type: " + type);
        }

        return csvContent.toString().getBytes();
    }

    public byte[] generateReceiptPdf(Long transactionId) {
        log.info("📄 Looking for transaction ID: {}", transactionId);

        Optional<PaymentTransaction> paymentTransactionOpt = paymentTransactionRepository.findById(transactionId);
        if (paymentTransactionOpt.isPresent()) {
            PaymentTransaction paymentTransaction = paymentTransactionOpt.get();
            log.info("✅ Found payment transaction: {}", paymentTransaction.getReceiptNumber());

            if (!paymentTransaction.getIsVerified() && paymentTransaction.getBankTransaction() == null) {
                throw new RuntimeException("Payment transaction must be verified to generate receipt");
            }

            return receiptGenerator.generateReceiptPdf(paymentTransaction);
        }

        Optional<BankTransaction> bankTransactionOpt = bankTransactionRepository.findById(transactionId);
        if (bankTransactionOpt.isPresent()) {
            BankTransaction bankTransaction = bankTransactionOpt.get();
            log.info("✅ Found bank transaction: {}", bankTransaction.getBankReference());

            if (bankTransaction.getStatus() == TransactionStatus.MATCHED ||
                    bankTransaction.getStatus() == TransactionStatus.VERIFIED) {

                if (bankTransaction.getStudent() == null) {
                    throw new RuntimeException("Matched bank transaction must have a student assigned");
                }

                if (bankTransaction.getPaymentTransaction() == null) {
                    log.warn("⚠️ No payment transaction found for matched bank transaction, creating one...");
                    PaymentTransaction paymentTransaction =
                            paymentTransactionService.createFromMatchedBankTransaction(bankTransaction);
                    log.info("📄 Created payment transaction {} for receipt generation",
                            paymentTransaction.getReceiptNumber());
                    return receiptGenerator.generateReceiptPdf(paymentTransaction);
                }

                log.info("📄 Generating receipt for auto-matched bank transaction");
                return receiptGenerator.generateReceiptPdf(bankTransaction.getPaymentTransaction());
            } else {
                throw new RuntimeException("Bank transaction must be MATCHED or VERIFIED to generate receipt");
            }
        }

        log.error("❌ Transaction not found with ID: {}", transactionId);
        throw new RuntimeException("Transaction not found with id: " + transactionId);
    }

    /// ========== HELPER METHODS ==========

    @Async
    private void sendAutoMatchSms(Student student, BankTransaction transaction, PaymentTransaction paymentTransaction) {
        try {
            log.info("📱 Attempting to send auto-match SMS for payment: {}",
                    paymentTransaction.getReceiptNumber());

            String recipientPhone = getBestContactPhone(student);
            if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
                log.warn("📵 No valid phone number available for student {} to send auto-match SMS",
                        student.getFullName());
                return;
            }

            recipientPhone = cleanPhoneNumber(recipientPhone);
            if (!isValidKenyanPhoneNumber(recipientPhone)) {
                log.warn("📵 Invalid phone number format for student {}: {}", student.getFullName(), recipientPhone);
                return;
            }

            String message = String.format(
                    "Dear Parent/Guardian,\n" +
                            "Payment of KES %.2f has been auto-matched to %s (Class: %s).\n" +
                            "Receipt: %s | Bank Ref: %s\n" +
                            "Transaction Date: %s\n" +
                            "Thank you! - School Management System",
                    transaction.getAmount(),
                    student.getFullName(),
                    student.getGrade(),
                    paymentTransaction.getReceiptNumber(),
                    transaction.getBankReference(),
                    transaction.getTransactionDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            );

            SmsLog smsLog = SmsLog.builder()
                    .student(student)
                    .paymentTransaction(paymentTransaction)
                    .recipientPhone(recipientPhone)
                    .message(message)
                    .status(SmsLog.SmsStatus.SENT)
                    .gatewayMessageId("AUTO-" + UUID.randomUUID().toString().substring(0, 8))
                    .gatewayResponse("Auto-match SMS sent successfully")
                    .sentAt(LocalDateTime.now())
                    .build();

            SmsLog savedSmsLog = smsLogRepository.save(smsLog);

            transaction.setSmsSent(true);
            transaction.setSmsSentAt(LocalDateTime.now());
            transaction.setSmsId(savedSmsLog.getGatewayMessageId());
            bankTransactionRepository.save(transaction);

            paymentTransaction.setSmsSent(true);
            paymentTransaction.setSmsSentAt(LocalDateTime.now());
            paymentTransaction.setSmsId(savedSmsLog.getGatewayMessageId());
            paymentTransactionRepository.save(paymentTransaction);

            log.info("✅ Auto-match SMS sent to {} for student {} (Receipt: {})",
                    recipientPhone, student.getFullName(), paymentTransaction.getReceiptNumber());

        } catch (Exception e) {
            log.warn("⚠️ Failed to send auto-match SMS for transaction {}: {}",
                    transaction.getBankReference(), e.getMessage());
        }
    }

    private String getBestContactPhone(Student student) {
        if (student == null) return null;

        // Try student's primary phone first
        if (student.getPhone() != null && !student.getPhone().trim().isEmpty()) {
            String phone = student.getPhone().trim();
            String cleaned = cleanPhoneNumber(phone);
            if (isValidKenyanPhoneNumber(cleaned)) {
                log.debug("📱 Using student primary phone: {} → {}", phone, cleaned);
                return cleaned;
            }
        }

        // Try emergency contact phone second
        if (student.getEmergencyContactPhone() != null &&
                !student.getEmergencyContactPhone().trim().isEmpty()) {
            String phone = student.getEmergencyContactPhone().trim();
            String cleaned = cleanPhoneNumber(phone);
            if (isValidKenyanPhoneNumber(cleaned)) {
                log.debug("📱 Using emergency contact phone: {} → {}", phone, cleaned);
                return cleaned;
            }
        }

        log.warn("📵 No valid Kenyan phone number for student: {} (Primary: {}, Emergency: {})",
                student.getFullName(),
                student.getPhone(),
                student.getEmergencyContactPhone());

        return null;
    }


    private String cleanPhoneNumber(String phone) {
        if (phone == null) return null;

        // Remove all non-digit characters
        String cleaned = phone.replaceAll("[^\\d]", "");

        log.debug("📞 Cleaning phone: {} → {}", phone, cleaned);

        // Handle different Kenyan formats:

        // 07XXXXXXXX → +2547XXXXXXXX
        if (cleaned.matches("^07\\d{8}$")) {
            String formatted = "+254" + cleaned.substring(1);
            log.debug("   Formatted 07... → {}", formatted);
            return formatted;
        }

        // 01XXXXXXXX → +2541XXXXXXXX
        if (cleaned.matches("^01\\d{8}$")) {
            String formatted = "+254" + cleaned.substring(1);
            log.debug("   Formatted 01... → {}", formatted);
            return formatted;
        }

        // 7XXXXXXXX → +2547XXXXXXXX
        if (cleaned.matches("^7\\d{8}$")) {
            String formatted = "+254" + cleaned;
            log.debug("   Formatted 7... → {}", formatted);
            return formatted;
        }

        // 1XXXXXXXX → +2541XXXXXXXX
        if (cleaned.matches("^1\\d{8}$")) {
            String formatted = "+254" + cleaned;
            log.debug("   Formatted 1... → {}", formatted);
            return formatted;
        }

        // 2547XXXXXXXX → +2547XXXXXXXX
        if (cleaned.matches("^2547\\d{8}$")) {
            String formatted = "+" + cleaned;
            log.debug("   Formatted 2547... → {}", formatted);
            return formatted;
        }

        // 2541XXXXXXXX → +2541XXXXXXXX
        if (cleaned.matches("^2541\\d{8}$")) {
            String formatted = "+" + cleaned;
            log.debug("   Formatted 2541... → {}", formatted);
            return formatted;
        }

        // If it's just digits and might be a valid Kenyan number
        if (cleaned.length() == 10 && cleaned.startsWith("0")) {
            String formatted = "+254" + cleaned.substring(1);
            log.debug("   Formatted 0... → {}", formatted);
            return formatted;
        }

        if (cleaned.length() == 9 && (cleaned.startsWith("7") || cleaned.startsWith("1"))) {
            String formatted = "+254" + cleaned;
            log.debug("   Formatted 7/1... → {}", formatted);
            return formatted;
        }

        log.warn("⚠️ Could not format phone number: {} (cleaned: {})", phone, cleaned);
        return cleaned;
    }

    private boolean isValidKenyanPhoneNumber(String phone) {
        if (phone == null) return false;

        // Remove all non-digit characters
        String cleaned = phone.replaceAll("[^\\d]", "");

        // KENYAN MOBILE NUMBER FORMATS:
        // 07XXXXXXXX (Safaricom/Airtel - 10 digits)
        // 01XXXXXXXX (New prefixes: 010, 011, 012, etc. - 10 digits)
        // 7XXXXXXXX  (9 digits - without leading zero)
        // 1XXXXXXXX  (9 digits - new prefixes without zero)
        // 2547XXXXXXX (12 digits - international format)
        // 2541XXXXXXX (12 digits - new prefixes international)

        boolean isValid =
                // 10-digit formats (with leading zero)
                cleaned.matches("^(07|01)\\d{8}$") ||
                        // 9-digit formats (without leading zero)
                        cleaned.matches("^[7,1]\\d{8}$") ||
                        // 12-digit international formats
                        cleaned.matches("^254[7,1]\\d{8}$") ||
                        // Specific network checks
                        cleaned.matches("^(07\\d{8}|011\\d{8}|7\\d{8}|2547\\d{8})$") || // Safaricom
                        cleaned.matches("^(07[8,9]\\d{7}|013\\d{8}|7[8,9]\\d{7}|2547[8,9]\\d{7})$") || // Airtel
                        cleaned.matches("^(077\\d{7}|010\\d{8}|77\\d{7}|25477\\d{7})$"); // Telkom

        if (!isValid) {
            log.debug("❌ Invalid Kenyan phone format: {} (cleaned: {})", phone, cleaned);
        }

        return isValid;
    }

    // ========== CONVERSION METHODS ==========

    private BankTransactionResponse convertToBankTransactionResponseWithTermData(
            BankTransaction transaction,
            Map<Long, Boolean> hasAssignmentsMap,
            Map<Long, Integer> assignmentCountMap) {

        BankTransactionResponse response = new BankTransactionResponse();
        response.setId(transaction.getId());
        response.setBankReference(transaction.getBankReference());
        response.setTransactionDate(transaction.getTransactionDate());
        response.setDescription(transaction.getDescription());
        response.setAmount(transaction.getAmount());
        response.setBankAccount(transaction.getBankAccount());
        response.setStatus(transaction.getStatus());
        response.setPaymentMethod(transaction.getPaymentMethod());
        response.setImportedAt(transaction.getImportedAt());
        response.setMatchedAt(transaction.getMatchedAt());
        response.setFileName(transaction.getFileName());
        response.setImportBatchId(transaction.getImportBatchId());

        response.setNotes(transaction.getNotes());

        response.setSmsSent(transaction.getSmsSent());
        response.setSmsSentAt(transaction.getSmsSentAt());
        response.setSmsId(transaction.getSmsId());

        if (transaction.getPaymentTransaction() != null) {
            PaymentTransaction pt = transaction.getPaymentTransaction();
            response.setPaymentTransactionId(pt.getId());
            response.setReceiptNumber(pt.getReceiptNumber());
            response.setPaymentVerified(pt.getIsVerified());
            response.setPaymentVerifiedAt(pt.getVerifiedAt());
        }

        if (transaction.getStudent() != null) {
            Student student = transaction.getStudent();

            response.setStudentId(student.getId());
            response.setStudentName(student.getFullName());
            response.setStudentGrade(student.getGrade());

            response.setStudentPendingAmount(student.getPendingAmount());
            response.setStudentFeeStatus(student.getFeeStatus());
            response.setStudentTotalFee(student.getTotalFee());
            response.setStudentPaidAmount(student.getPaidAmount());

            // ========== USE PRE-FETCHED TERM ASSIGNMENT DATA ==========
            Long studentId = student.getId();
            Boolean hasAssignments = hasAssignmentsMap.get(studentId);
            Integer assignmentCount = assignmentCountMap.get(studentId);

            response.setHasTermAssignments(hasAssignments != null ? hasAssignments : false);
            response.setTermAssignmentCount(assignmentCount != null ? assignmentCount : 0);
            // ==========================================================

            if (student.getPendingAmount() != null && student.getTotalFee() != null &&
                    student.getTotalFee() > 0) {
                double percentage = (student.getPaidAmount() != null ? student.getPaidAmount() : 0.0) /
                        student.getTotalFee() * 100;
                response.setStudentPaymentPercentage(percentage);
            }
        }

        return response;
    }

    private BankTransactionResponse convertToBankTransactionResponse(BankTransaction transaction) {
        return convertToBankTransactionResponseWithTermData(transaction, new HashMap<>(), new HashMap<>());
    }

    private PaymentTransactionResponse convertToPaymentTransactionResponse(PaymentTransaction transaction) {
        PaymentTransactionResponse response = new PaymentTransactionResponse();
        response.setId(transaction.getId());
        response.setReceiptNumber(transaction.getReceiptNumber());
        response.setAmount(transaction.getAmount());
        response.setPaymentMethod(transaction.getPaymentMethod());
        response.setPaymentDate(transaction.getPaymentDate());
        response.setIsVerified(transaction.getIsVerified());
        response.setVerifiedAt(transaction.getVerifiedAt());
        response.setSmsSent(transaction.getSmsSent());
        response.setSmsSentAt(transaction.getSmsSentAt());
        response.setNotes(transaction.getNotes());
        response.setPaymentFor(transaction.getPaymentFor());
        response.setDiscountApplied(transaction.getDiscountApplied());
        response.setLateFeePaid(transaction.getLateFeePaid());
        response.setConvenienceFee(transaction.getConvenienceFee());
        response.setTotalPaid(transaction.getTotalPaid());
        response.setCreatedAt(transaction.getCreatedAt());

        if (transaction.getStudent() != null) {
            Student student = transaction.getStudent();
            response.setStudentId(student.getId());
            response.setStudentName(student.getFullName());
            response.setStudentGrade(student.getGrade());

            response.setStudentPendingAmount(student.getPendingAmount());
            response.setStudentFeeStatus(student.getFeeStatus());
            response.setStudentTotalFee(student.getTotalFee());
            response.setStudentPaidAmount(student.getPaidAmount());

            // ========== QUERY TERM ASSIGNMENTS FOR PAYMENT TRANSACTIONS ==========
            try {
                boolean hasAssignments = studentTermAssignmentRepository.hasTermAssignments(student.getId());
                Integer assignmentCount = studentTermAssignmentRepository.countTermAssignments(student.getId());

                response.setHasTermAssignments(hasAssignments);
                response.setTermAssignmentCount(assignmentCount != null ? assignmentCount : 0);

            } catch (Exception e) {
                log.warn("Error querying term assignments for student {}: {}",
                        student.getId(), e.getMessage());
                response.setHasTermAssignments(false);
                response.setTermAssignmentCount(0);
            }
            // ==========================================================

            if (student.getPendingAmount() != null && student.getTotalFee() != null &&
                    student.getTotalFee() > 0) {
                double percentage = (student.getPaidAmount() != null ? student.getPaidAmount() : 0.0) /
                        student.getTotalFee() * 100;
                response.setStudentPaymentPercentage(percentage);
            }
        }

        if (transaction.getBankTransaction() != null) {
            response.setBankTransactionId(transaction.getBankTransaction().getId());
            response.setBankReference(transaction.getBankTransaction().getBankReference());
        }

        return response;
    }

    private SmsLogResponse convertToSmsLogResponse(SmsLog smsLog) {
        SmsLogResponse response = new SmsLogResponse();
        response.setId(smsLog.getId());
        response.setRecipientPhone(smsLog.getRecipientPhone());
        response.setMessage(smsLog.getMessage());
        response.setStatus(smsLog.getStatus().toString());
        response.setGatewayMessageId(smsLog.getGatewayMessageId());
        response.setDeliveryStatus(smsLog.getDeliveryStatus());
        response.setSentAt(smsLog.getSentAt());
        response.setDeliveredAt(smsLog.getDeliveredAt());

        if (smsLog.getStudent() != null) {
            response.setStudentId(smsLog.getStudent().getId());
            response.setStudentName(smsLog.getStudent().getFullName());
        }

        if (smsLog.getPaymentTransaction() != null) {
            response.setPaymentTransactionId(smsLog.getPaymentTransaction().getId());
            response.setReceiptNumber(smsLog.getPaymentTransaction().getReceiptNumber());
        }

        return response;
    }

    // ========== INNER CLASSES ==========

    public static class ImportProgress {
        @Getter
        private final String importId;
        @Getter
        private final String fileName;
        @Getter
        private final long fileSize;
        @Getter
        private ImportStatus status;
        @Setter
        @Getter
        private int totalLines;
        @Setter
        @Getter
        private int processedLines;
        @Getter
        @Setter
        private int savedLines;
        @Getter
        private int failedLines;
        @Getter
        private int matchedCount;
        @Getter
        private String currentStatus;
        @Getter
        @Setter
        private String error;
        private long startTime;
        private long endTime;
        @Getter
        @Setter
        private int currentBatch;
        @Getter
        @Setter
        private int totalBatches;

        public ImportProgress(String importId, String fileName, long fileSize) {
            this.importId = importId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.startTime = System.currentTimeMillis();
            this.status = ImportStatus.QUEUED;
            this.currentStatus = "Queued for processing";
        }

        public double getProgressPercentage() {
            if (totalLines == 0) return 0;
            return (processedLines * 100.0) / totalLines;
        }

        public long getElapsedTime() {
            if (endTime > 0) {
                return endTime - startTime;
            }
            return System.currentTimeMillis() - startTime;
        }

        public void incrementFailedLines(int count) {
            this.failedLines += count;
        }

        public void updateStatus(String status) {
            this.currentStatus = status;
        }

        public void setStatus(ImportStatus status) {
            this.status = status;
            if (status == ImportStatus.COMPLETED || status == ImportStatus.FAILED) {
                this.endTime = System.currentTimeMillis();
            }
        }

        public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }
    }

    public enum ImportStatus {
        QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED
    }

    // ========== NEW METHODS FOR CONTROLLER ==========

    public List<BankTransactionResponse> getAllBankTransactions(
            TransactionStatus status, String search, LocalDate fromDate, LocalDate toDate) {

        List<BankTransaction> transactions;

        if (status != null && search != null && !search.trim().isEmpty()) {
            Page<BankTransaction> searchResult = bankTransactionRepository.searchTransactions(
                    search, Pageable.unpaged());
            transactions = searchResult.getContent().stream()
                    .filter(t -> t.getStatus() == status)
                    .collect(Collectors.toList());

        } else if (status != null) {
            transactions = bankTransactionRepository.findByStatus(status);

        } else if (search != null && !search.trim().isEmpty()) {
            Page<BankTransaction> searchResult = bankTransactionRepository.searchTransactions(
                    search, Pageable.unpaged());
            transactions = searchResult.getContent();

        } else {
            transactions = bankTransactionRepository.findAll();
        }

        if (fromDate != null) {
            transactions = transactions.stream()
                    .filter(t -> !t.getTransactionDate().isBefore(fromDate))
                    .collect(Collectors.toList());
        }

        if (toDate != null) {
            transactions = transactions.stream()
                    .filter(t -> !t.getTransactionDate().isAfter(toDate))
                    .collect(Collectors.toList());
        }

        transactions.sort((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()));

        return transactions.stream()
                .map(this::convertToBankTransactionResponse)
                .collect(Collectors.toList());
    }

    public Page<BankTransactionResponse> getBankTransactions(
            TransactionStatus status, String search, LocalDate fromDate, LocalDate toDate, Pageable pageable) {

        Page<BankTransaction> transactions;

        if (status != null && search != null && !search.trim().isEmpty()) {
            Page<BankTransaction> searchResult = bankTransactionRepository.searchTransactions(search, pageable);
            List<BankTransaction> filtered = searchResult.getContent().stream()
                    .filter(t -> t.getStatus() == status)
                    .collect(Collectors.toList());

            transactions = new PageImpl<>(filtered, pageable, filtered.size());

        } else if (status != null) {
            transactions = bankTransactionRepository.findByStatus(status, pageable);

        } else if (search != null && !search.trim().isEmpty()) {
            transactions = bankTransactionRepository.searchTransactions(search, pageable);

        } else {
            transactions = bankTransactionRepository.findAll(pageable);
        }

        if (fromDate != null || toDate != null) {
            List<BankTransaction> filtered = transactions.getContent().stream()
                    .filter(t -> {
                        if (fromDate != null && t.getTransactionDate().isBefore(fromDate)) {
                            return false;
                        }
                        if (toDate != null && t.getTransactionDate().isAfter(toDate)) {
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            transactions = new PageImpl<>(filtered, pageable, filtered.size());
        }

        return transactions.map(this::convertToBankTransactionResponse);
    }

    public List<PaymentTransactionResponse> getAllVerifiedTransactions(String search) {
        List<PaymentTransaction> transactions;

        if (search != null && !search.trim().isEmpty()) {
            Page<PaymentTransaction> searchResult = paymentTransactionRepository.searchTransactions(
                    search, Pageable.unpaged());
            transactions = searchResult.getContent().stream()
                    .filter(PaymentTransaction::getIsVerified)
                    .collect(Collectors.toList());
        } else {
            transactions = paymentTransactionRepository.findByIsVerified(true);
        }

        transactions.sort((a, b) -> b.getPaymentDate().compareTo(a.getPaymentDate()));

        return transactions.stream()
                .map(this::convertToPaymentTransactionResponse)
                .collect(Collectors.toList());
    }

    public long getTotalBankTransactionCount() {
        return bankTransactionRepository.count();
    }

    public long getPaymentTransactionCountVerified() {
        Long count = paymentTransactionRepository.countByIsVerified(true);
        return count != null ? count : 0L;
    }

    public long getBankTransactionCountSince(LocalDate sinceDate) {
        List<BankTransaction> recent = bankTransactionRepository
                .findByTransactionDateBetween(sinceDate, LocalDate.now());
        return recent.size();
    }

    public Map<String, Double> getRecentAmountsByStatus(LocalDate startDate, LocalDate endDate) {
        List<BankTransaction> recentTransactions = bankTransactionRepository
                .findByTransactionDateBetween(startDate, endDate);

        return recentTransactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getStatus().name(),
                        Collectors.summingDouble(BankTransaction::getAmount)
                ));
    }

    public double getTotalPendingFees() {
        List<StudentTermAssignment> allAssignments = studentTermAssignmentRepository.findAll();
        return allAssignments.stream()
                .mapToDouble(StudentTermAssignment::getPendingAmount)
                .sum();
    }

    public Double getTotalVerifiedAmount() {
        try {
            Double amount = paymentTransactionRepository.getTotalVerifiedAmount();
            return amount != null ? amount : 0.0;
        } catch (Exception e) {
            log.error("Error getting total verified amount", e);
            return 0.0;
        }
    }

    public Double getTotalVerifiedAmountToday() {
        try {
            Double amount = paymentTransactionRepository.getTotalVerifiedAmountToday();
            return amount != null ? amount : 0.0;
        } catch (Exception e) {
            log.error("Error getting today's verified amount", e);
            return 0.0;
        }
    }

    // ========== NEW METHODS FOR CONTROLLER ENDPOINTS ==========

    public Map<String, Object> getStudentFeeDetails(Long studentId) {
        return getStudentFeeBreakdown(studentId);
    }

    public Map<String, Object> applyManualPaymentToStudent(Long studentId, Double amount,
                                                           String reference, String notes) {
        return applyManualPayment(studentId, amount, reference, notes);
    }

    public Map<String, Object> getFeeStatistics() {
        return getFeeStatisticsByGrade();
    }

    public Student getStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + studentId));
    }
}