// ImportResult.java
package com.system.SchoolManagementSystem.transaction.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class ImportResult {
    private int totalTransactions;
    private int savedTransactions;
    private int duplicatesSkipped;
    private List<String> duplicateReferences;
    private String warningMessage;
    private List<BankTransactionResponse> transactions;

    // Helper method
    public boolean hasDuplicates() {
        return duplicatesSkipped > 0;
    }
}