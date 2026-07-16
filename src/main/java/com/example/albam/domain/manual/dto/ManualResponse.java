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
    public static ManualResponse from(Manual manual) {
        return new ManualResponse(
                manual.getId(),
                manual.getCategory(),
                manual.getTitle(),
                manual.getContent(),
                List.copyOf(manual.getImageUrls()),
                manual.getAuthor().getUser().getName(),
                manual.getDisplayOrder(),
                manual.getCreatedAt(),
                manual.getUpdatedAt()
        );
    }
}
