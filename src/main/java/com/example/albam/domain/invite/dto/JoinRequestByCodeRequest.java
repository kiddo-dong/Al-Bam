package com.example.albam.domain.invite.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinRequestByCodeRequest(@NotBlank String code) {
}
