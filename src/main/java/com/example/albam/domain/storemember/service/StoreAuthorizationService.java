package com.example.albam.domain.storemember.service;

import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.store.repository.StoreRepository;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매장 단위 권한 체크를 담당한다. 서비스 메서드 진입부에서 호출하여 사용한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreAuthorizationService {

    private final StoreMemberRepository storeMemberRepository;
    private final StoreRepository storeRepository;

    public StoreMember requireMember(Long storeId, Long userId) {
        StoreMember member = storeMemberRepository.findByStoreIdAndUserId(storeId, userId)
                .orElseThrow(() -> notAMemberOf(storeId));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ForbiddenException("퇴사 처리된 멤버는 매장 기능을 이용할 수 없습니다.");
        }
        return member;
    }

    /**
     * 멤버를 못 찾은 이유를 나눠 안내한다. 삭제된 매장도 멤버가 없는 것으로 나오는데, 자기가 방금
     * 지운 매장을 열어둔 탭에서 다시 눌렀을 때 "멤버가 아니다"라고 하면 무슨 일인지 알 수 없다.
     * 이미 실패한 경로에서만 한 번 더 조회하므로 정상 요청에는 영향이 없다.
     */
    private RuntimeException notAMemberOf(Long storeId) {
        if (!storeRepository.existsById(storeId)) {
            return new NotFoundException("삭제되었거나 존재하지 않는 매장입니다.");
        }
        return new ForbiddenException("해당 매장의 멤버가 아닙니다.");
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
