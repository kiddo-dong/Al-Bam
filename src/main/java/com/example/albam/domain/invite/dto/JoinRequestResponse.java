package com.example.albam.domain.invite.dto;

import com.example.albam.domain.invite.entity.JoinRequest;
import com.example.albam.domain.invite.entity.JoinRequestStatus;
import com.example.albam.domain.storemember.entity.MemberRole;
import java.time.LocalDateTime;

public record JoinRequestResponse(
        Long id,
        Long storeId,
        String storeName,
        Long userId,
        String userName,
        String userEmail,
        /** 신청자 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        JoinRequestStatus status,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        MemberRole decidedRole
) {
    public static JoinRequestResponse from(JoinRequest joinRequest, String userProfileImageUrl) {
        return new JoinRequestResponse(
                joinRequest.getId(),
                joinRequest.getStore().getId(),
                joinRequest.getStore().getName(),
                joinRequest.getUser().getId(),
                joinRequest.getUser().getName(),
                joinRequest.getUser().getEmail(),
                userProfileImageUrl,
                joinRequest.getStatus(),
                joinRequest.getRequestedAt(),
                joinRequest.getDecidedAt(),
                joinRequest.getDecidedRole()
        );
    }
}
