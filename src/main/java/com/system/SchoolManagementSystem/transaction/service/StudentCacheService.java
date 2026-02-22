package com.system.SchoolManagementSystem.transaction.service;

import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class StudentCacheService {

    private final StudentRepository studentRepository;

    // Cache indices
    private final Map<String, Student> exactNameCache = new ConcurrentHashMap<>();
    private final Map<String, List<Student>> namePartCache = new ConcurrentHashMap<>();
    private final Map<String, List<Student>> amountCache = new ConcurrentHashMap<>();
    private final Set<String> allNames = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean isLoaded = new AtomicBoolean(false);

    // Common school fee amounts for quick matching
    private static final Set<Double> COMMON_SCHOOL_AMOUNTS = Set.of(
            500.0, 1000.0, 1500.0, 2000.0, 2500.0, 3000.0, 3500.0, 4000.0, 4500.0, 5000.0,
            5500.0, 6000.0, 6500.0, 7000.0, 7500.0, 8000.0, 8500.0, 9000.0, 9500.0, 10000.0,
            11000.0, 12000.0, 13000.0, 14000.0, 15000.0, 20000.0, 25000.0, 30000.0
    );

    public StudentCacheService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing StudentCacheService...");
        loadCacheAsync();
    }

    private void loadCacheAsync() {
        new Thread(() -> {
            try {
                log.info("Loading student cache...");
                long startTime = System.currentTimeMillis();

                List<Student> students = studentRepository.findActiveAndNotDeleted();

                for (Student student : students) {
                    indexStudent(student);
                }

                isLoaded.set(true);
                long duration = System.currentTimeMillis() - startTime;

                log.info("✅ Student cache loaded in {}ms: {} students, {} name parts, {} amount entries",
                        duration, students.size(), namePartCache.size(), amountCache.size());

            } catch (Exception e) {
                log.error("❌ Failed to load student cache", e);
            }
        }).start();
    }

    private void indexStudent(Student student) {
        String fullNameLower = student.getFullName().toLowerCase();

        // Index by exact name
        exactNameCache.put(fullNameLower, student);
        allNames.add(fullNameLower);

        // Index by name parts
        String[] nameParts = fullNameLower.split("\\s+");
        for (String part : nameParts) {
            if (part.length() > 2) {
                namePartCache.computeIfAbsent(part, k -> new ArrayList<>())
                        .add(student);
            }
        }

        // Index by pending amount (rounded to nearest 100)
        if (student.getPendingAmount() != null && student.getPendingAmount() > 0) {
            String amountKey = String.valueOf(Math.round(student.getPendingAmount() / 100.0) * 100);
            amountCache.computeIfAbsent(amountKey, k -> new ArrayList<>())
                    .add(student);
        }
    }

    // Public API methods
    public Optional<Student> findByName(String name) {
        if (!isLoaded.get()) {
            return Optional.empty();
        }
        return Optional.ofNullable(exactNameCache.get(name.toLowerCase()));
    }

    public List<Student> findByNamePart(String namePart) {
        if (!isLoaded.get() || namePart.length() < 3) {
            return Collections.emptyList();
        }
        return namePartCache.getOrDefault(namePart.toLowerCase(), Collections.emptyList());
    }

    public List<Student> findByAmount(Double amount) {
        if (!isLoaded.get() || amount == null) {
            return Collections.emptyList();
        }

        // Try exact amount match
        String amountKey = String.valueOf(Math.round(amount / 100.0) * 100);
        List<Student> candidates = amountCache.get(amountKey);

        if (candidates != null && !candidates.isEmpty()) {
            return candidates;
        }

        // Try common school amounts (±10%)
        for (Double commonAmount : COMMON_SCHOOL_AMOUNTS) {
            if (Math.abs(amount - commonAmount) / commonAmount < 0.1) {
                String commonKey = String.valueOf(Math.round(commonAmount));
                candidates = amountCache.get(commonKey);
                if (candidates != null && !candidates.isEmpty()) {
                    return candidates;
                }
            }
        }

        return Collections.emptyList();
    }

    public Set<String> getAllNames() {
        return allNames;
    }

    public boolean isCacheLoaded() {
        return isLoaded.get();
    }

    public void refreshCache() {
        log.info("Refreshing student cache...");
        exactNameCache.clear();
        namePartCache.clear();
        amountCache.clear();
        allNames.clear();
        isLoaded.set(false);
        loadCacheAsync();
    }

    @Data
    public static class CacheStats {
        private int studentCount;
        private int exactNameEntries;
        private int namePartEntries;
        private int amountEntries;
        private boolean isLoaded;

        public static CacheStats fromService(StudentCacheService service) {
            CacheStats stats = new CacheStats();
            stats.studentCount = service.exactNameCache.size();
            stats.exactNameEntries = service.exactNameCache.size();
            stats.namePartEntries = service.namePartCache.size();
            stats.amountEntries = service.amountCache.size();
            stats.isLoaded = service.isLoaded.get();
            return stats;
        }
    }
}