package com.example.albam.domain.storemember.service;

import com.example.albam.domain.storemember.dto.StoreMemberResponse;
import com.example.albam.domain.storemember.dto.StoreMemberSummaryResponse;
import com.example.albam.domain.storemember.dto.UpdateAvailableDaysRequest;
import com.example.albam.domain.storemember.dto.UpdateStoreMemberRequest;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import com.example.albam.global.labor.LaborStandards;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreMemberService {

    private final StoreMemberRepository storeMemberRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    /** 멤버 전체 상세 목록 (시급·이메일·공제방식 등 민감정보 포함) — OWNER/MANAGER. */
    public List<StoreMemberResponse> getMembers(Long storeId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return storeMemberRepository.findAllByStoreId(storeId).stream()
                .map(StoreMemberResponse::from)
                .toList();
    }

    /** 멤버 요약 목록 (이름·역할만, 재직자만) — 매장 멤버 누구나 조회 가능. */
    public List<StoreMemberSummaryResponse> getMemberSummaries(Long storeId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return storeMemberRepository.findAllByStoreId(storeId).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .map(StoreMemberSummaryResponse::from)
                .toList();
    }

    @Transactional
    public StoreMemberResponse updateMember(Long storeId, Long memberId, Long userId,
            UpdateStoreMemberRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = getStoreMember(storeId, memberId);

        if (target.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자 정보는 이 API로 변경할 수 없습니다.");
        }
        if (request.role() != null) {
            target.changeRole(request.role());
        }
        if (request.hourlyWage() != null) {
            if (request.hourlyWage() < LaborStandards.MINIMUM_HOURLY_WAGE) {
                throw new InvalidRequestException(
                        "시급은 최저임금(" + LaborStandards.MINIMUM_HOURLY_WAGE + "원) 이상이어야 합니다.");
            }
            target.changeHourlyWage(request.hourlyWage());
        }
        if (request.status() != null) {
            target.changeStatus(request.status());
        }
        if (request.availableDays() != null) {
            target.changeAvailableDays(request.availableDays());
        }
        if (request.weeklyHolidayDay() != null) {
            target.changeWeeklyHolidayDay(request.weeklyHolidayDay());
        }
        if (request.taxMode() != null) {
            target.changeTaxMode(request.taxMode());
        }
        return StoreMemberResponse.from(target);
    }

    @Transactional
    public StoreMemberResponse updateMyAvailableDays(Long storeId, Long userId, UpdateAvailableDaysRequest request) {
        StoreMember member = storeAuthorizationService.requireMember(storeId, userId);
        member.changeAvailableDays(request.availableDays());
        return StoreMemberResponse.from(member);
    }

    /** 본인 퇴사: 근무 이력 보존을 위해 행을 지우지 않고 INACTIVE로 전환한다. */
    @Transactional
    public void leaveStore(Long storeId, Long userId) {
        StoreMember member = storeAuthorizationService.requireMember(storeId, userId);
        if (member.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자는 매장을 나갈 수 없습니다. 매장 삭제를 이용해 주세요.");
        }
        member.resign();
    }

    /** 관리자에 의한 퇴사 처리 (soft delete). */
    @Transactional
    public void removeMember(Long storeId, Long memberId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = getStoreMember(storeId, memberId);
        if (target.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자는 퇴사 처리할 수 없습니다.");
        }
        target.resign();
    }

    private StoreMember getStoreMember(Long storeId, Long memberId) {
        StoreMember target = storeMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (!target.getStore().getId().equals(storeId)) {
            throw new NotFoundException("멤버를 찾을 수 없습니다.");
        }
        return target;
    }
}
