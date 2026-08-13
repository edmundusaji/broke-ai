package org.edmund.brokeai.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ApiEnvelope;
import org.edmund.brokeai.dto.PageEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.service.ProfileSecurityService;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
@Validated
public class ProfileSecurityController {
    private final ProfileSecurityService profileSecurityService;

    @PostMapping("/password/change")
    public ApiEnvelope<ProfileSettingsApi.PasswordChange> changePassword(
        @Valid @RequestBody ProfileSettingsApi.PasswordChangeRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiEnvelope.of(profileSecurityService.changePassword(request, currentSessionId(servletRequest)));
    }

    @GetMapping("/sessions")
    public PageEnvelope<ProfileSettingsApi.Session> sessions(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        HttpServletRequest servletRequest
    ) {
        return profileSecurityService.getSessions(page, size, currentSessionId(servletRequest));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiEnvelope<ApiEnvelope.ActionResult> revokeSession(@PathVariable UUID sessionId) {
        profileSecurityService.revokeSession(sessionId);
        return ApiEnvelope.success();
    }

    @DeleteMapping("/sessions/others")
    public ApiEnvelope<ApiEnvelope.ActionResult> revokeOtherSessions(HttpServletRequest servletRequest) {
        profileSecurityService.revokeOtherSessions(currentSessionId(servletRequest));
        return ApiEnvelope.success();
    }

    private UUID currentSessionId(HttpServletRequest request) {
        Object value = request.getAttribute("broke.sessionId");
        return value instanceof UUID sessionId ? sessionId : null;
    }
}
