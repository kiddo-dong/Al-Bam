package com.example.albam.domain.notice.dto;

import com.example.albam.domain.notice.entity.Notice;
import java.time.LocalDateTime;

public record NoticeResponse(
        Long id,
        String title,
        String content,
        String authorName,
        LocalDateTime createdAt,
        long readCount,
        boolean readByMe
) {
    public static NoticeResponse from(Notice notice, long readCount, boolean readByMe) {
        return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(),
                notice.getAuthor().getUser().getName(), notice.getCreatedAt(), readCount, readByMe);
    }
}
