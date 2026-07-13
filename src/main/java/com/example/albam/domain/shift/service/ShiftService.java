package com.example.albam.domain.shift.service;

import com.example.albam.domain.shift.dto.CreateRecurringShiftRequest;
import com.example.albam.domain.shift.dto.CreateShiftRequest;
import com.example.albam.domain.shift.dto.RecurringShiftResult;
import com.example.albam.domain.shift.dto.ShiftResponse;
import com.example.albam.domain.shift.dto.SkippedShiftDate;
import com.example.albam.domain.shift.dto.UpdateShiftRequest;
import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.entity.ShiftStatus;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.store.entity.BusinessHour;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftService {

    private static final int MAX_RECURRING_PERIOD_DAYS = 92;

    private final ShiftRepository shiftRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public ShiftResponse createShift(Long storeId, Long userId, CreateShiftRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = getStoreMemberInStore(storeId, request.storeMemberId());
        validateAvailability(target, request.workDate(), request.startTime(), request.endTime());
        validateNoOverlap(target, request.workDate(), request.startTime(), request.endTime(), null);
        Shift shift = shiftRepository.save(
                new Shift(target, request.workDate(), request.startTime(), request.endTime()));
        return ShiftResponse.from(shift);
    }

    @Transactional
    public RecurringShiftResult createRecurringShifts(Long storeId, Long userId,
            CreateRecurringShiftRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = getStoreMemberInStore(storeId, request.storeMemberId());
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new InvalidRequestException("종료일은 시작일 이후여야 합니다.");
        }
        long periodDays = ChronoUnit.DAYS.between(request.periodStart(), request.periodEnd()) + 1;
        if (periodDays > MAX_RECURRING_PERIOD_DAYS) {
            throw new InvalidRequestException("반복 스케줄 생성 기간은 최대 " + MAX_RECURRING_PERIOD_DAYS + "일까지 가능합니다.");
        }

        List<ShiftResponse> created = new ArrayList<>();
        List<SkippedShiftDate> skipped = new ArrayList<>();
        for (LocalDate date = request.periodStart(); !date.isAfter(request.periodEnd()); date = date.plusDays(1)) {
            if (!request.daysOfWeek().contains(date.getDayOfWeek())) {
                continue;
            }
            try {
                validateAvailability(target, date, request.startTime(), request.endTime());
                validateNoOverlap(target, date, request.startTime(), request.endTime(), null);
                Shift shift = shiftRepository.save(new Shift(target, date, request.startTime(), request.endTime()));
                created.add(ShiftResponse.from(shift));
            } catch (InvalidRequestException e) {
                skipped.add(new SkippedShiftDate(date, e.getMessage()));
            }
        }
        return new RecurringShiftResult(created, skipped);
    }

    public List<ShiftResponse> getShifts(Long storeId, Long userId, Long storeMemberId, LocalDate from,
            LocalDate to) {
        storeAuthorizationService.requireMember(storeId, userId);
        List<Shift> shifts;
        if (storeMemberId != null) {
            getStoreMemberInStore(storeId, storeMemberId);
            shifts = shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                    storeMemberId, from, to);
        } else {
            shifts = shiftRepository.findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                    storeId, from, to);
        }
        return shifts.stream().map(ShiftResponse::from).toList();
    }

    @Transactional
    public ShiftResponse updateShift(Long storeId, Long shiftId, Long userId, UpdateShiftRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Shift shift = getShiftInStore(storeId, shiftId);
        validateAvailability(shift.getStoreMember(), request.workDate(), request.startTime(), request.endTime());
        if (request.status() != ShiftStatus.CANCELED) {
            validateNoOverlap(shift.getStoreMember(), request.workDate(), request.startTime(),
                    request.endTime(), shiftId);
        }
        shift.update(request.workDate(), request.startTime(), request.endTime(), request.status());
        return ShiftResponse.from(shift);
    }

    @Transactional
    public void deleteShift(Long storeId, Long shiftId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Shift shift = getShiftInStore(storeId, shiftId);
        shiftRepository.delete(shift);
    }

    private StoreMember getStoreMemberInStore(Long storeId, Long storeMemberId) {
        StoreMember member = storeMemberRepository.findById(storeMemberId)
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (!member.getStore().getId().equals(storeId)) {
            throw new InvalidRequestException("해당 매장의 멤버가 아닙니다.");
        }
        return member;
    }

    private Shift getShiftInStore(Long storeId, Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new NotFoundException("스케줄을 찾을 수 없습니다."));
        if (!shift.getStoreMember().getStore().getId().equals(storeId)) {
            throw new NotFoundException("스케줄을 찾을 수 없습니다.");
        }
        return shift;
    }

    private void validateAvailability(StoreMember member, LocalDate workDate, LocalTime startTime,
            LocalTime endTime) {
        DayOfWeek dayOfWeek = workDate.getDayOfWeek();
        validateMemberAvailableDay(member, dayOfWeek);
        validateStoreBusinessHours(member.getStore(), dayOfWeek, startTime, endTime);
    }

    private void validateMemberAvailableDay(StoreMember member, DayOfWeek dayOfWeek) {
        Set<DayOfWeek> availableDays = member.getAvailableDays();
        if (!availableDays.isEmpty() && !availableDays.contains(dayOfWeek)) {
            throw new InvalidRequestException(
                    member.getUser().getName() + "님은 해당 요일에 근무 가능으로 설정되어 있지 않습니다.");
        }
    }

    /**
     * 같은 멤버에게 시간이 겹치는 스케줄이 이미 있으면 거부한다. 자정을 넘는 야간 스케줄까지 비교하기 위해
     * 전날~다음날 스케줄을 함께 조회하고, 날짜+시간(LocalDateTime)으로 정규화하여 구간 겹침을 판정한다.
     * 취소된 스케줄과 자기 자신(수정 시)은 비교 대상에서 제외한다.
     */
    private void validateNoOverlap(StoreMember member, LocalDate workDate, LocalTime startTime,
            LocalTime endTime, Long excludeShiftId) {
        LocalDateTime newStart = workDate.atTime(startTime);
        LocalDate newEndDate = endTime.isBefore(startTime) ? workDate.plusDays(1) : workDate;
        LocalDateTime newEnd = newEndDate.atTime(endTime);

        List<Shift> candidates = shiftRepository
                .findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                        member.getId(), workDate.minusDays(1), workDate.plusDays(1));
        for (Shift existing : candidates) {
            if (existing.getId().equals(excludeShiftId) || existing.getStatus() == ShiftStatus.CANCELED) {
                continue;
            }
            if (newStart.isBefore(existing.endDateTime()) && existing.startDateTime().isBefore(newEnd)) {
                throw new InvalidRequestException(
                        "겹치는 스케줄이 이미 있습니다: " + existing.getWorkDate() + " "
                                + existing.getStartTime() + "~" + existing.getEndTime());
            }
        }
    }

    private void validateStoreBusinessHours(Store store, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalTime endTime) {
        BusinessHour businessHour = store.getBusinessHours().get(dayOfWeek);
        if (businessHour == null) {
            return;
        }
        if (businessHour.isClosed()) {
            throw new InvalidRequestException("매장이 해당 요일은 휴무로 설정되어 있습니다.");
        }
        // 자정을 넘는 스케줄은 다음날 영업시간까지 함께 봐야 정확하므로, 여기서는 범위 검증을 생략한다.
        if (endTime.isBefore(startTime)) {
            return;
        }
        LocalTime openTime = businessHour.getOpenTime();
        LocalTime closeTime = businessHour.getCloseTime();
        if ((openTime != null && startTime.isBefore(openTime)) || (closeTime != null && endTime.isAfter(closeTime))) {
            throw new InvalidRequestException(
                    "매장 영업시간(" + openTime + "~" + closeTime + ")을 벗어난 스케줄입니다.");
        }
    }
}
