package com.example.albam.domain.laborqa.dto;

import com.example.albam.domain.laborqa.entity.LaborQaSession;
import java.time.LocalDateTime;

public record LaborQaSessionResponse(
        Long id,
        String title,
        LocalDateTime createdAt
) {
    public static LaborQaSessionResponse from(LaborQaSession session) {
        return new LaborQaSessionResponse(session.getId(), session.getTitle(), session.getCreatedAt());
    }
}
