package org.edmund.brokeai.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ApiEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.service.DataControlService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class DataControlController {
    private final DataControlService dataControlService;

    @PostMapping("/data-exports")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.DataExport>> requestExport(
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return ResponseEntity.accepted().body(ApiEnvelope.of(dataControlService.requestExport(idempotencyKey)));
    }

    @GetMapping("/data-exports/{jobId}")
    public ApiEnvelope<ProfileSettingsApi.DataExport> getExport(@PathVariable UUID jobId) {
        return ApiEnvelope.of(dataControlService.getExport(jobId));
    }

    @GetMapping("/data-exports/{jobId}/download")
    public ResponseEntity<byte[]> downloadExport(
        @PathVariable UUID jobId,
        @RequestParam String token
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=broke-ai-data-export-" + jobId + ".zip")
            .body(dataControlService.downloadExport(jobId, token));
    }

    @PostMapping("/transactions/clear")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.ClearTransactions>> clearTransactions(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ProfileSettingsApi.ClearTransactionsRequest request
    ) {
        return ResponseEntity.accepted().body(ApiEnvelope.of(
            dataControlService.clearTransactions(idempotencyKey, request)
        ));
    }

    @PostMapping("/deletion-request")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.AccountDeletion>> requestDeletion(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ProfileSettingsApi.AccountDeletionRequest request
    ) {
        return ResponseEntity.accepted().body(ApiEnvelope.of(
            dataControlService.requestDeletion(idempotencyKey, request)
        ));
    }

    @DeleteMapping("/deletion-request")
    public ApiEnvelope<ApiEnvelope.ActionResult> cancelDeletion() {
        dataControlService.cancelDeletion();
        return ApiEnvelope.success();
    }
}
