package com.example.albam.domain.notice.dto;

import com.example.albam.domain.notice.entity.Notice;
import java.time.LocalDateTime;

public record NoticeResponse(
        Long id,
        String title,
        String content,
        String authorName,
        /** 작성자 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String authorProfileImageUrl,
        LocalDateTime createdAt,
        long readCount,
        boolean readByMe
) {
    public static NoticeResponse from(Notice notice, String authorProfileImageUrl, long readCount,
            boolean readByMe) {
        return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(),
                notice.getAuthor().getUser().getName(), authorProfileImageUrl, notice.getCreatedAt(),
                readCount, readByMe);
    }
}
