package com.example.albam.domain.handover.dto;

import com.example.albam.domain.handover.entity.HandoverNote;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record HandoverNoteResponse(
        Long id,
        Long authorMemberId,
        String authorName,
        /** 작성자 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String authorProfileImageUrl,
        String content,
        LocalDate workDate,
        LocalDateTime createdAt
) {
    public static HandoverNoteResponse from(HandoverNote note, String authorProfileImageUrl) {
        return new HandoverNoteResponse(note.getId(), note.getAuthor().getId(),
                note.getAuthor().getUser().getName(), authorProfileImageUrl, note.getContent(),
                note.getWorkDate(), note.getCreatedAt());
    }
}
