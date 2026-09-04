package com.crm.modules.auth.web;

import com.crm.modules.auth.dto.AuthDtos.*;
import com.crm.modules.auth.service.AuthService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request, http);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return Map.of("success", true);
    }

    @GetMapping("/me")
    public MeResponse me() {
        return authService.me(CurrentUser.require().getId());
    }

    @PostMapping("/complete-onboarding")
    public Map<String, Object> completeOnboarding(@Valid @RequestBody CompleteOnboardingRequest request) {
        authService.completeOnboarding(request);
        return Map.of("success", true);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.require(), request);
        return ResponseEntity.noContent().build();
    }
}
