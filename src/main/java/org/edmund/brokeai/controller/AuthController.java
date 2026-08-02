package org.edmund.brokeai.controller;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.AuthMessageResponse;
import org.edmund.brokeai.dto.LoginRequest;
import org.edmund.brokeai.dto.LoginResponse;
import org.edmund.brokeai.dto.RegisterRequest;
import org.edmund.brokeai.dto.UpgradeGuestRequest;
import org.edmund.brokeai.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthMessageResponse> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(new AuthMessageResponse("User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/guest-login")
    public ResponseEntity<LoginResponse> guestLogin() {
        return ResponseEntity.ok(authService.guestLogin());
    }

    @PostMapping("/upgrade-guest")
    public ResponseEntity<LoginResponse> upgradeGuest(@RequestBody UpgradeGuestRequest request) {
        return ResponseEntity.ok(authService.upgradeGuest(request));
    }
}
