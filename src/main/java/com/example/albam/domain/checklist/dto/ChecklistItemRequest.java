package com.example.albam.domain.checklist.dto;

import com.example.albam.domain.checklist.entity.ChecklistType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChecklistItemRequest(
        @NotNull ChecklistType type,
        @NotBlank String content,
        Integer displayOrder
) {
    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
