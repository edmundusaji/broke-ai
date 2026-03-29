package org.edmund.brokeai.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.AiExpenseResponse;
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

    private final GeminiService geminiService;

    @PostMapping(value = "/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload dan Proses Struk Asli",
            description = "Support File Type: .jpg/.jpeg | .png | .webp | .heic/.heif")
    public ResponseEntity<AiExpenseResponse> processReceipt(@RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            AiExpenseResponse response = geminiService.prosesStruk(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error di Controller: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}