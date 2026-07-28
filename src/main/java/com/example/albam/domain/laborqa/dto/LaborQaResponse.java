package com.example.albam.domain.laborqa.dto;

import java.util.List;

public record LaborQaResponse(
        String answer,
        List<String> sources,
        boolean grounded
) {
}
