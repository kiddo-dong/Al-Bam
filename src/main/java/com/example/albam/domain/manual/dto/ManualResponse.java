package com.example.albam.domain.manual.dto;

import com.example.albam.domain.manual.entity.Manual;
import java.time.LocalDateTime;
import java.util.List;

public record ManualResponse(
        Long id,
        String category,
        String title,
        String content,
        List<String> imageUrls,
        String authorName,
        int displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** imageUrls는 저장된 key들로부터 조립해 넘겨받는다 (엔티티에는 key만 있다). */
    public static ManualResponse from(Manual manual, List<String> imageUrls) {
        return new ManualResponse(
                manual.getId(),
                manual.getCategory(),
                manual.getTitle(),
                manual.getContent(),
                List.copyOf(imageUrls),
                manual.getAuthor().getUser().getName(),
                manual.getDisplayOrder(),
                manual.getCreatedAt(),
                manual.getUpdatedAt()
        );
    }
}
