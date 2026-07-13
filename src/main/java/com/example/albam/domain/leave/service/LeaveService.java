package com.example.albam.domain.leave.service;

import com.example.albam.domain.leave.dto.LeaveStatusResponse;
import com.example.albam.domain.leave.dto.LeaveUsageResponse;
import com.example.albam.domain.leave.dto.UseLeaveRequest;
import com.example.albam.domain.leave.entity.LeaveUsage;
import com.example.albam.domain.leave.repository.LeaveUsageRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.ConflictException;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차(유급휴가) 발생·사용 관리.
 *
 * <p>발생 기준(근로기준법 제60조를 단순화): 입사 1년 미만은 1개월당 1일(최대 11일), 1년 이상은 매년 15일을
 * 추가 부여한다. 개근·80% 출근율 요건과 미사용 연차의 소멸(사용촉진), 3년차부터의 가산(2년마다 +1일)은
 * 아직 반영하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveService {

    private static final int FIRST_YEAR_MAX_DAYS = 11;
    private static final int ANNUAL_DAYS = 15;

    private final LeaveUsageRepository leaveUsageRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    public LeaveStatusResponse getLeaveStatus(Long storeId, Long memberId, Long userId) {
        StoreMember requester = storeAuthorizationService.requireMember(storeId, userId);
        StoreMember target = getMemberInStore(storeId, memberId);
        if (!requester.getId().equals(target.getId()) && !requester.isOwnerOrManager()) {
            throw new ForbiddenException("본인 또는 매장 관리자만 조회할 수 있습니다.");
        }
        int entitled = entitledDays(target.getJoinedAt().toLocalDate(), LocalDate.now());
        List<LeaveUsageResponse> usages = leaveUsageRepository
                .findAllByStoreMemberIdOrderByLeaveDateDesc(target.getId()).stream()
                .map(LeaveUsageResponse::from)
                .toList();
        int used = usages.size();
        return new LeaveStatusResponse(entitled, used, entitled - used, usages);
    }

    @Transactional
    public LeaveUsageResponse useLeave(Long storeId, Long memberId, Long userId, UseLeaveRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = getMemberInStore(storeId, memberId);
        if (leaveUsageRepository.existsByStoreMemberIdAndLeaveDate(target.getId(), request.leaveDate())) {
            throw new ConflictException("해당 날짜에 이미 연차가 사용되었습니다.");
        }
        int entitled = entitledDays(target.getJoinedAt().toLocalDate(), LocalDate.now());
        long used = leaveUsageRepository.countByStoreMemberId(target.getId());
        if (used >= entitled) {
            throw new InvalidRequestException("남은 연차가 없습니다. (발생 " + entitled + "일, 사용 " + used + "일)");
        }
        return LeaveUsageResponse.from(leaveUsageRepository.save(new LeaveUsage(target, request.leaveDate())));
    }

    @Transactional
    public void cancelLeave(Long storeId, Long leaveId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        LeaveUsage leaveUsage = leaveUsageRepository.findByIdAndStoreMemberStoreId(leaveId, storeId)
                .orElseThrow(() -> new NotFoundException("연차 사용 기록을 찾을 수 없습니다."));
        leaveUsageRepository.delete(leaveUsage);
    }

    private StoreMember getMemberInStore(Long storeId, Long memberId) {
        StoreMember member = storeMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (!member.getStore().getId().equals(storeId)) {
            throw new NotFoundException("멤버를 찾을 수 없습니다.");
        }
        return member;
    }

    private int entitledDays(LocalDate joinedDate, LocalDate today) {
        long months = ChronoUnit.MONTHS.between(joinedDate, today);
        if (months < 12) {
            return (int) Math.min(months, FIRST_YEAR_MAX_DAYS);
        }
        long years = months / 12;
        return (int) (FIRST_YEAR_MAX_DAYS + ANNUAL_DAYS * years);
    }
}
