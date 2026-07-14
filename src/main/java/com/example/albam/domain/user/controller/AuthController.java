package com.example.albam.domain.user.controller;

import com.example.albam.domain.user.dto.EmailRequest;
import com.example.albam.domain.user.dto.LoginRequest;
import com.example.albam.domain.user.dto.OAuthLoginRequest;
import com.example.albam.domain.user.dto.PasswordResetConfirmRequest;
import com.example.albam.domain.user.dto.RefreshRequest;
import com.example.albam.domain.user.dto.SignupRequest;
import com.example.albam.domain.user.dto.TokenResponse;
import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.service.AuthService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.exception.InvalidRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Long>> signup(@Valid @RequestBody SignupRequest request) {
        Long userId = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(userId));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request.refreshToken())));
    }

    @PostMapping("/oauth/{provider}")
    public ResponseEntity<ApiResponse<TokenResponse>> oauthLogin(@PathVariable String provider,
            @Valid @RequestBody OAuthLoginRequest request) {
        AuthProvider authProvider = parseProvider(provider);
        return ResponseEntity.ok(ApiResponse.success(authService.oauthLogin(authProvider, request.accessToken())));
    }

    @GetMapping("/verify-email")
    public ApiResponse<String> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ApiResponse.success("이메일 인증이 완료되었습니다. 이제 로그인할 수 있습니다.");
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody EmailRequest request) {
        authService.resendVerification(request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        authService.requestPasswordReset(request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ApiResponse.ok();
    }

    private AuthProvider parseProvider(String provider) {
        try {
            return AuthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("지원하지 않는 로그인 방식입니다.");
        }
    }
}
