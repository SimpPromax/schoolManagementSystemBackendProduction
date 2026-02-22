
package com.system.SchoolManagementSystem.transaction.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class BankTransactionImportResponse {
    private boolean success;
    private String message;
    private ImportResult result;
    private List<String> warnings;

    public static BankTransactionImportResponse success(String message, ImportResult result) {
        BankTransactionImportResponse response = new BankTransactionImportResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setResult(result);

        if (result != null && result.getDuplicatesSkipped() > 0) {
            response.setWarnings(List.of(result.getWarningMessage()));
        }

        return response;
    }

    public static BankTransactionImportResponse error(String message) {
        BankTransactionImportResponse response = new BankTransactionImportResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}