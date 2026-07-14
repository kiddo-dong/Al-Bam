package com.example.albam.domain.storemember.dto;

import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;

/** 멤버 요약 (이름·역할만). 시급·연락처 등 개인정보 없이 동료 멤버에게 공개해도 되는 최소 정보. */
public record StoreMemberSummaryResponse(
        Long id,
        String userName,
        MemberRole role
) {
    public static StoreMemberSummaryResponse from(StoreMember member) {
        return new StoreMemberSummaryResponse(member.getId(), member.getUser().getName(), member.getRole());
    }
}
