package com.example.albam.domain.handover.dto;

import com.example.albam.domain.handover.entity.HandoverNote;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record HandoverNoteResponse(
        Long id,
        Long authorMemberId,
        String authorName,
        String content,
        LocalDate workDate,
        LocalDateTime createdAt
) {
    public static HandoverNoteResponse from(HandoverNote note) {
        return new HandoverNoteResponse(note.getId(), note.getAuthor().getId(),
                note.getAuthor().getUser().getName(), note.getContent(), note.getWorkDate(),
                note.getCreatedAt());
    }
}
