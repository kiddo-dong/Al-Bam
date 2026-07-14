package com.example.albam.domain.store.dto;

import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;

/** 내 매장 목록 항목: 매장 정보 + 이 매장에서의 내 역할. 프론트가 사장/알바 화면을 구분하는 데 쓴다. */
public record MyStoreResponse(
        StoreResponse store,
        Long myStoreMemberId,
        MemberRole myRole
) {
    public static MyStoreResponse from(StoreMember member) {
        return new MyStoreResponse(StoreResponse.from(member.getStore()), member.getId(), member.getRole());
    }
}
