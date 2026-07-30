package com.example.albam.domain.user.controller;

import com.example.albam.domain.user.dto.EmailRequest;
import com.example.albam.domain.user.dto.IssuedTokens;
import com.example.albam.domain.user.dto.LoginRequest;
import com.example.albam.domain.user.dto.OAuthLoginRequest;
import com.example.albam.domain.user.dto.PasswordResetConfirmRequest;
import com.example.albam.domain.user.dto.SignupRequest;
import com.example.albam.domain.user.dto.TokenResponse;
import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.service.AuthService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.exception.InvalidRequestException;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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

    /** 리프레시 토큰 쿠키 이름. HttpOnly라 JS에서 읽을 수 없고, 브라우저가 /api/v1/auth 요청에만 자동 첨부한다. */
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String AUTH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    /** 로컬 개발(http)에서는 false, 배포(https)에서는 true로 설정해야 쿠키가 전송된다. */
    @Value("${app.cookie-secure:false}")
    private boolean cookieSecure;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Long>> signup(@Valid @RequestBody SignupRequest request) {
        Long userId = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(userId));
    }

    /** 액세스 토큰은 본문으로, 리프레시 토큰은 HttpOnly 쿠키로 내려간다 (XSS 탈취 방지). */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponseWithCookie(authService.login(request));
    }

    /** 본문 없이 호출한다. 리프레시 토큰은 브라우저가 쿠키로 자동 첨부하며, 재발급 시 쿠키도 새 토큰으로 교체된다. */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRequestException("리프레시 토큰 쿠키가 없습니다. 다시 로그인해 주세요.");
        }
        return tokenResponseWithCookie(authService.refresh(refreshToken));
    }

    /** 리프레시 토큰 쿠키를 즉시 만료시킨다. 액세스 토큰은 클라이언트가 버리면 된다(짧은 만료로 자연 소멸). */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        ResponseCookie expiredCookie = refreshCookieBuilder("").maxAge(Duration.ZERO).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(ApiResponse.ok());
    }

    @PostMapping("/oauth/{provider}")
    public ResponseEntity<ApiResponse<TokenResponse>> oauthLogin(@PathVariable String provider,
            @Valid @RequestBody OAuthLoginRequest request) {
        AuthProvider authProvider = parseProvider(provider);
        return tokenResponseWithCookie(authService.oauthLogin(authProvider, request.accessToken()));
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

    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponseWithCookie(IssuedTokens tokens) {
        ResponseCookie refreshCookie = refreshCookieBuilder(tokens.refreshToken())
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(tokens.toResponse()));
    }

    private ResponseCookie.ResponseCookieBuilder refreshCookieBuilder(String value) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(AUTH_COOKIE_PATH)
                .sameSite("Lax");
    }

    private AuthProvider parseProvider(String provider) {
        try {
            return AuthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("지원하지 않는 로그인 방식입니다.");
        }
    }
}
