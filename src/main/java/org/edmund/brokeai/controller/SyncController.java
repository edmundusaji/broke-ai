package org.edmund.brokeai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.SyncApi;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.service.SyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class SyncController {
    private final SyncService syncService;

    @Value("${app.offline-sync.enabled:false}")
    private boolean syncEnabled;

    @PostMapping("/push")
    @Operation(summary = "Push an ordered batch of idempotent local transaction mutations")
    public ResponseEntity<SyncApi.PushResponse> push(@Valid @RequestBody SyncApi.PushRequest request) {
        requireSyncEnabled();
        return ResponseEntity.ok(syncService.push(request));
    }

    @GetMapping("/pull")
    @Operation(summary = "Pull ordered transaction changes after an opaque cursor")
    public ResponseEntity<SyncApi.PullResponse> pull(
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "100") int limit
    ) {
        requireSyncEnabled();
        return ResponseEntity.ok(syncService.pull(cursor, limit));
    }

    private void requireSyncEnabled() {
        if (!syncEnabled) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SYNC_FEATURE_DISABLED",
                "Transaction synchronization is not enabled on this server."
            );
        }
    }
}
