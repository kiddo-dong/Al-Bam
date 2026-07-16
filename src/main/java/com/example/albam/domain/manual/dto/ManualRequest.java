package com.example.albam.domain.manual.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** 매뉴얼 생성·수정 공용 요청. imageUrls는 이미지 업로드 API로 먼저 올린 URL 목록. */
public record ManualRequest(
        @NotBlank String category,
        @NotBlank String title,
        @NotBlank String content,
        Integer displayOrder,
        List<String> imageUrls
) {
    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
