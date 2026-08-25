package org.edmund.brokeai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.AuthMessageResponse;
import org.edmund.brokeai.dto.LoginRequest;
import org.edmund.brokeai.dto.LoginResponse;
import org.edmund.brokeai.dto.RegisterRequest;
import org.edmund.brokeai.dto.UpgradeGuestRequest;
import org.edmund.brokeai.dto.CurrentUserResponse;
import org.edmund.brokeai.dto.MergeGuestRequest;
import org.edmund.brokeai.dto.MergeGuestResponse;
import org.edmund.brokeai.dto.GuestBootstrapRequest;
import org.edmund.brokeai.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthMessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(new AuthMessageResponse("User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/guest-login")
    public ResponseEntity<LoginResponse> guestLogin(
        @Valid @RequestBody(required = false) GuestBootstrapRequest request
    ) {
        return ResponseEntity.ok(request == null
            ? authService.guestLogin()
            : authService.guestLogin(request));
    }

    @PostMapping("/upgrade-guest")
    public ResponseEntity<LoginResponse> upgradeGuest(@RequestBody UpgradeGuestRequest request) {
        return ResponseEntity.ok(authService.upgradeGuest(request));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    @PostMapping("/merge-guest")
    public ResponseEntity<MergeGuestResponse> mergeGuest(@Valid @RequestBody MergeGuestRequest request) {
        return ResponseEntity.ok(authService.mergeGuest(request));
    }
}
