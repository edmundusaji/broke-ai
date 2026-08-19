package org.edmund.brokeai.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ApiEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.service.GuestAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class GuestAccountController {
    private final GuestAccountService guestAccountService;

    @PostMapping("/guest/transactions/clear")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.ClearTransactions>> clearTransactions(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ProfileSettingsApi.GuestConfirmationRequest request
    ) {
        return ResponseEntity.ok(ApiEnvelope.of(guestAccountService.clearTransactions(idempotencyKey, request)));
    }

    @DeleteMapping("/guest-account")
    public ApiEnvelope<ProfileSettingsApi.GuestDeletion> deleteGuest(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ProfileSettingsApi.GuestConfirmationRequest request
    ) {
        return ApiEnvelope.of(guestAccountService.deleteGuest(idempotencyKey, request));
    }

    @GetMapping("/guest/data-summary")
    public ApiEnvelope<ProfileSettingsApi.GuestDataSummary> getDataSummary() {
        return ApiEnvelope.of(guestAccountService.getDataSummary());
    }
}
