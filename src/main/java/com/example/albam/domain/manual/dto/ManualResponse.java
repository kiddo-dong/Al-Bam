package com.example.albam.domain.manual.dto;

import com.example.albam.domain.manual.entity.Manual;
import java.time.LocalDateTime;
import java.util.List;

public record ManualResponse(
        Long id,
        String category,
        String title,
        String content,
        List<ManualImageResponse> images,
        String authorName,
        int displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** images는 저장된 key와 그로부터 조립한 url을 함께 넘겨받는다 (엔티티에는 key만 있다). */
    public static ManualResponse from(Manual manual, List<ManualImageResponse> images) {
        return new ManualResponse(
                manual.getId(),
                manual.getCategory(),
                manual.getTitle(),
                manual.getContent(),
                List.copyOf(images),
                manual.getAuthor().getUser().getName(),
                manual.getDisplayOrder(),
                manual.getCreatedAt(),
                manual.getUpdatedAt()
        );
    }
}
