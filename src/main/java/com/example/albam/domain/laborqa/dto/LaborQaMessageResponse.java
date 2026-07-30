package com.example.albam.domain.laborqa.dto;

import com.example.albam.domain.laborqa.entity.LaborQaMessage;
import com.example.albam.domain.laborqa.entity.LaborQaRole;
import java.time.LocalDateTime;
import java.util.List;

public record LaborQaMessageResponse(
        Long id,
        LaborQaRole role,
        String content,
        List<String> sources,
        LocalDateTime createdAt
) {
    public static LaborQaMessageResponse from(LaborQaMessage message) {
        return new LaborQaMessageResponse(message.getId(), message.getRole(), message.getContent(),
                message.getSourceList(), message.getCreatedAt());
    }
}
