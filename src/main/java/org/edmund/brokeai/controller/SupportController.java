package org.edmund.brokeai.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ApiEnvelope;
import org.edmund.brokeai.dto.PageEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.service.SupportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
@Validated
public class SupportController {
    private final SupportService supportService;

    @GetMapping("/faqs")
    public PageEnvelope<ProfileSettingsApi.FaqArticle> getFaqs(
        @RequestParam(defaultValue = "en") String locale,
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return supportService.getFaqs(locale, category, page, size);
    }

    @PostMapping("/tickets")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.SupportTicket>> createTicket(
        @Valid @RequestBody ProfileSettingsApi.SupportTicketRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(supportService.createTicket(request)));
    }

    @GetMapping("/tickets/{ticketId}")
    public ApiEnvelope<ProfileSettingsApi.SupportTicket> getTicket(@PathVariable UUID ticketId) {
        return ApiEnvelope.of(supportService.getTicket(ticketId));
    }
}
