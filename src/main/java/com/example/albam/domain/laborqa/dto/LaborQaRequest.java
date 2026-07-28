package com.example.albam.domain.laborqa.dto;

import jakarta.validation.constraints.NotBlank;

public record LaborQaRequest(
        @NotBlank String question
) {
}
