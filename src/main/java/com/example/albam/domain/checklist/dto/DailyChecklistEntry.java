package com.example.albam.domain.checklist.dto;

import com.example.albam.domain.checklist.entity.ChecklistType;
import java.time.LocalDateTime;

/** 특정 날짜의 체크리스트 한 항목 상태. */
public record DailyChecklistEntry(
        Long itemId,
        ChecklistType type,
        String content,
        int displayOrder,
        boolean checked,
        String checkedByName,
        LocalDateTime checkedAt
) {
}
