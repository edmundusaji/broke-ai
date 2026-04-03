package org.edmund.brokeai.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.service.ExpenseService;
import org.edmund.brokeai.service.GeminiService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/expense")
@RequiredArgsConstructor

/** entrypoint */
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping(value = "/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and Process Receipt",
            description = "Support File Type: .jpg/.jpeg | .png | .webp | .heic/.heif")
    public ResponseEntity<Transaction> processReceipt(@RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Transaction response = expenseService.saveReceipt(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error di Controller: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // DTO only to catch notification
    public record NotificationRequest(String text) {}

    @PostMapping(value = "/notification", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Process Notification",
            description = "Accept Copy&Paste text notifications from: BCA, OVO, Gopay, dll")
    public ResponseEntity<Transaction> processNotification(@RequestBody NotificationRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Transaction savedData = expenseService.prosesDanSimpanNotifikasi(request.text());
            return ResponseEntity.ok(savedData);
        } catch (Exception e) {
            System.err.println("Error di Controller (Notification): " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}