package com.example.albam.domain.storemember.service;

import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.global.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매장 단위 권한 체크를 담당한다. 서비스 메서드 진입부에서 호출하여 사용한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreAuthorizationService {

    private final StoreMemberRepository storeMemberRepository;

    public StoreMember requireMember(Long storeId, Long userId) {
        return storeMemberRepository.findByStoreIdAndUserId(storeId, userId)
                .orElseThrow(() -> new ForbiddenException("해당 매장의 멤버가 아닙니다."));
    }

    public StoreMember requireOwnerOrManager(Long storeId, Long userId) {
        StoreMember member = requireMember(storeId, userId);
        if (!member.isOwnerOrManager()) {
            throw new ForbiddenException("매장 관리자만 수행할 수 있는 작업입니다.");
        }
        return member;
    }

    public StoreMember requireOwner(Long storeId, Long userId) {
        StoreMember member = requireMember(storeId, userId);
        if (member.getRole() != MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자만 수행할 수 있는 작업입니다.");
        }
        return member;
    }
}
