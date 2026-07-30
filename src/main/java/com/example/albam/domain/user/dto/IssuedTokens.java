package com.example.albam.domain.user.dto;

/**
 * 로그인/재발급 시 서비스가 발급한 토큰 묶음 (서비스 내부 결과용).
 * 리프레시 토큰은 응답 본문에 내려가지 않고 컨트롤러가 HttpOnly 쿠키로만 전달한다 — {@link TokenResponse} 참고.
 */
public record IssuedTokens(String accessToken, String refreshToken, boolean profileCompleted) {

    public TokenResponse toResponse() {
        return new TokenResponse(accessToken, profileCompleted);
    }
}
