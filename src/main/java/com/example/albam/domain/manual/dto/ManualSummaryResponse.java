package com.example.albam.domain.manual.dto;

import com.example.albam.domain.manual.entity.Manual;
import java.time.LocalDateTime;

/** 목록용 요약 (본문·이미지 제외). */
public record ManualSummaryResponse(
        Long id,
        String category,
        String title,
        int displayOrder,
        LocalDateTime updatedAt
) {
    public static ManualSummaryResponse from(Manual manual) {
        return new ManualSummaryResponse(manual.getId(), manual.getCategory(), manual.getTitle(),
                manual.getDisplayOrder(), manual.getUpdatedAt());
    }
}
