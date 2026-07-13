package com.example.albam.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(@NotBlank String accessToken) {
}
