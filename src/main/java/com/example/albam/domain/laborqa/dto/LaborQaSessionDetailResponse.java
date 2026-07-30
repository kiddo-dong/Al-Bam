package com.example.albam.domain.laborqa.dto;

import com.example.albam.domain.laborqa.entity.LaborQaMessage;
import com.example.albam.domain.laborqa.entity.LaborQaSession;
import java.util.List;

public record LaborQaSessionDetailResponse(
        Long id,
        String title,
        List<LaborQaMessageResponse> messages
) {
    public static LaborQaSessionDetailResponse from(LaborQaSession session, List<LaborQaMessage> messages) {
        return new LaborQaSessionDetailResponse(session.getId(), session.getTitle(),
                messages.stream().map(LaborQaMessageResponse::from).toList());
    }
}
