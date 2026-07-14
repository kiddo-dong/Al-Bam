package com.example.albam.domain.user.dto;

/** profileCompleted가 false면 클라이언트는 추가 정보 입력 화면으로 이동시켜야 한다. */
public record TokenResponse(String accessToken, String refreshToken, boolean profileCompleted) {
}
