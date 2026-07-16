package com.example.albam.domain.checklist.dto;

import com.example.albam.domain.checklist.entity.ChecklistItem;
import com.example.albam.domain.checklist.entity.ChecklistType;

public record ChecklistItemResponse(
        Long id,
        ChecklistType type,
        String content,
        int displayOrder
) {
    public static ChecklistItemResponse from(ChecklistItem item) {
        return new ChecklistItemResponse(item.getId(), item.getType(), item.getContent(),
                item.getDisplayOrder());
    }
}
