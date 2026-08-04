package org.edmund.brokeai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ExpenseSummaryResponse;
import org.edmund.brokeai.dto.ExpenseRequest;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.service.ExpenseService;
import org.edmund.brokeai.exception.GuestAiTrialLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/expense")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor

/** entrypoint */
public class ExpenseController {
    private final ExpenseService expenseService;

    @GetMapping("/summary")
    @Operation(summary = "Get monthly expense summary",
        description = "Returns total expenses and category details. Defaults to the current month when month/year are omitted.")
    public ResponseEntity<ExpenseSummaryResponse> getSummary(
        @RequestParam(required = false) Integer month,
        @RequestParam(required = false) Integer year) {

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        try {
            ExpenseSummaryResponse summary = expenseService.getExpenseSummary(month, year);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            System.err.println("Error in expense summary controller: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/history")
    @Operation(summary = "Get monthly transaction history",
        description = "Returns the authenticated user's transactions, ordered by most recent date.")
    public ResponseEntity<List<Transaction>> getHistory(
        @RequestParam(required = false) Integer month,
        @RequestParam(required = false) Integer year) {

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        try {
            List<Transaction> history = expenseService.getExpenseHistory(month, year);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            System.err.println("Error in expense history controller: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and Process Receipt",
            description = "Support File Type: .jpg/.jpeg | .png | .webp | .heic/.heif")
    public ResponseEntity<Transaction> processReceipt(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Transaction response = expenseService.saveReceipt(file);
            return ResponseEntity.ok(response);
        } catch (GuestAiTrialLimitException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error in receipt controller: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // DTO only to catch notification
    public record NotificationRequest(String text) {}

    @PostMapping(value = "/notification", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Process Notification",
            description = "Accepts pasted payment notifications from services such as BCA, OVO, and GoPay.")
    public ResponseEntity<Transaction> processNotification(@RequestBody NotificationRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Transaction savedData = expenseService.saveNotification(request.text());
            return ResponseEntity.ok(savedData);
        } catch (GuestAiTrialLimitException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error in notification controller: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/manual", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a manual transaction")
    public ResponseEntity<Transaction> createManualExpense(@RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(expenseService.createManualExpense(request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a transaction")
    public ResponseEntity<Transaction> updateExpense(
        @PathVariable Long id,
        @RequestBody ExpenseRequest request
    ) {
        return ResponseEntity.ok(expenseService.updateExpense(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }
}
