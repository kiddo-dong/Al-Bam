package com.example.albam.domain.notice.dto;

import java.time.LocalDateTime;

/** 공지별 멤버 확인 현황. readAt이 null이면 아직 확인하지 않은 것. */
public record NoticeReadStatusResponse(
        Long storeMemberId,
        String userName,
        LocalDateTime readAt
) {
}
