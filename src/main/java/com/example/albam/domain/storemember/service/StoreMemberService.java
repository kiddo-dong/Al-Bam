package com.example.albam.domain.storemember.service;

import com.example.albam.domain.storemember.dto.StoreMemberResponse;
import com.example.albam.domain.storemember.dto.UpdateAvailableDaysRequest;
import com.example.albam.domain.storemember.dto.UpdateStoreMemberRequest;
import com.example.albam.domain.storemember.entity.MemberRole;
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

    public List<StoreMemberResponse> getMembers(Long storeId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return storeMemberRepository.findAllByStoreId(storeId).stream()
                .map(StoreMemberResponse::from)
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
        return StoreMemberResponse.from(target);
    }

    @Transactional
    public StoreMemberResponse updateMyAvailableDays(Long storeId, Long userId, UpdateAvailableDaysRequest request) {
        StoreMember member = storeAuthorizationService.requireMember(storeId, userId);
        member.changeAvailableDays(request.availableDays());
        return StoreMemberResponse.from(member);
    }

    @Transactional
    public void removeMember(Long storeId, Long memberId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = getStoreMember(storeId, memberId);
        if (target.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자는 삭제할 수 없습니다.");
        }
        storeMemberRepository.delete(target);
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
