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
import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.BusinessHour;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import com.example.albam.global.labor.LaborStandards;
import com.example.albam.global.file.ProfileImageUrls;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftService {

    private static final int MAX_RECURRING_PERIOD_DAYS = 92;

    private final ShiftRepository shiftRepository;
    private final ProfileImageUrls profileImageUrls;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShiftResponse createShift(Long storeId, Long userId, CreateShiftRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = lockStoreMemberInStore(storeId, request.storeMemberId());
        int breakMinutes = validateAndResolveBreak(target, request.workDate(), request.startTime(),
                request.endTime(), request.breakMinutes(), null);
        Shift shift = shiftRepository.save(
                new Shift(target, request.workDate(), request.startTime(), request.endTime(), breakMinutes));
        return ShiftResponse.from(shift, profileImageUrls.of(shift.getStoreMember().getUser()));
    }

    /**
     * AI가 제안한 스케줄 초안이 유효한지 검증만 하고 저장하지 않는다. createShift와 동일한 규칙(근무가능요일·영업시간·
     * 연소자보호·중복·주간상한)을 그대로 재사용하므로, AI 제안이 통과하면 실제 생성도 반드시 통과한다.
     */
    public int validateDraftShift(Long storeId, Long userId, Long storeMemberId, LocalDate workDate,
            LocalTime startTime, LocalTime endTime) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = getStoreMemberInStore(storeId, storeMemberId);
        return validateAndResolveBreak(target, workDate, startTime, endTime, null, null);
    }

    private int validateAndResolveBreak(StoreMember target, LocalDate workDate, LocalTime startTime,
            LocalTime endTime, Integer requestedBreakMinutes, Long excludeShiftId) {
        return validateAndResolveBreak(target, workDate, startTime, endTime, requestedBreakMinutes,
                excludeShiftId, null);
    }

    /**
     * memberShiftsCache가 주어지면 겹침·주간상한 검증에 DB 조회 대신 이 메모리 목록을 사용한다.
     * 반복 생성 시 매 회 DB를 왕복하지 않기 위함 — 호출자가 새로 생성한 스케줄을 계속 추가해줘야 한다.
     */
    private int validateAndResolveBreak(StoreMember target, LocalDate workDate, LocalTime startTime,
            LocalTime endTime, Integer requestedBreakMinutes, Long excludeShiftId,
            List<Shift> memberShiftsCache) {
        int breakMinutes = resolveBreakMinutes(target.getStore(), startTime, endTime, requestedBreakMinutes);
        validateAvailability(target, workDate, startTime, endTime);
        validateMinorProtection(target, workDate, startTime, endTime, breakMinutes);
        validateNoOverlap(target, workDate, startTime, endTime, excludeShiftId, memberShiftsCache);
        validateWeeklyLimit(target, workDate, startTime, endTime, breakMinutes, excludeShiftId, memberShiftsCache);
        return breakMinutes;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RecurringShiftResult createRecurringShifts(Long storeId, Long userId,
            CreateRecurringShiftRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = lockStoreMemberInStore(storeId, request.storeMemberId());
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new InvalidRequestException("종료일은 시작일 이후여야 합니다.");
        }
        long periodDays = ChronoUnit.DAYS.between(request.periodStart(), request.periodEnd()) + 1;
        if (periodDays > MAX_RECURRING_PERIOD_DAYS) {
            throw new InvalidRequestException("반복 스케줄 생성 기간은 최대 " + MAX_RECURRING_PERIOD_DAYS + "일까지 가능합니다.");
        }

        int breakMinutes = resolveBreakMinutes(target.getStore(), request.startTime(), request.endTime(),
                request.breakMinutes());
        List<ShiftResponse> created = new ArrayList<>();
        List<SkippedShiftDate> skipped = new ArrayList<>();
        List<Shift> memberShiftsCache = new ArrayList<>(shiftRepository
                .findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                        target.getId(), request.periodStart().minusDays(1), request.periodEnd().plusDays(1)));
        for (LocalDate date = request.periodStart(); !date.isAfter(request.periodEnd()); date = date.plusDays(1)) {
            if (!request.daysOfWeek().contains(date.getDayOfWeek())) {
                continue;
            }
            try {
                validateAndResolveBreak(target, date, request.startTime(), request.endTime(), breakMinutes, null,
                        memberShiftsCache);
                Shift shift = shiftRepository.save(
                        new Shift(target, date, request.startTime(), request.endTime(), breakMinutes));
                memberShiftsCache.add(shift);
                created.add(ShiftResponse.from(shift, profileImageUrls.of(shift.getStoreMember().getUser())));
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
        return shifts.stream().map(shift -> ShiftResponse.from(shift, profileImageUrls.of(shift.getStoreMember().getUser()))).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShiftResponse updateShift(Long storeId, Long shiftId, Long userId, UpdateShiftRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Shift shift = getShiftInStore(storeId, shiftId);
        // 겹침과 주간 상한을 다시 검사하기 전에 이 멤버의 스케줄 쓰기를 줄 세운다. 퇴사한 멤버의 스케줄도
        // 취소는 할 수 있어야 하므로 재직 여부는 보지 않고 잠그기만 한다.
        storeMemberRepository.findByIdForUpdate(shift.getStoreMember().getId());
        StoreMember member = shift.getStoreMember();
        int breakMinutes;
        if (request.status() == ShiftStatus.CANCELED) {
            breakMinutes = resolveBreakMinutes(member.getStore(), request.startTime(), request.endTime(),
                    request.breakMinutes());
            validateAvailability(member, request.workDate(), request.startTime(), request.endTime());
        } else {
            breakMinutes = validateAndResolveBreak(member, request.workDate(), request.startTime(),
                    request.endTime(), request.breakMinutes(), shiftId);
        }
        shift.update(request.workDate(), request.startTime(), request.endTime(), breakMinutes, request.status());
        return ShiftResponse.from(shift, profileImageUrls.of(shift.getStoreMember().getUser()));
    }

    @Transactional
    public void deleteShift(Long storeId, Long shiftId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Shift shift = getShiftInStore(storeId, shiftId);
        shiftRepository.delete(shift);
    }

    /**
     * 스케줄을 쓰기 전에 대상 멤버 행을 잠근다(SELECT ... FOR UPDATE).
     *
     * <p>겹침·주 52시간·연소자 주 40시간 검사는 모두 "기존 스케줄을 모아 보고 괜찮으면 새로 넣는"
     * 방식이다. 두 요청이 동시에 오면 둘 다 검사를 통과하고 둘 다 넣어, 합치면 상한을 넘는 스케줄이
     * 저장된다. 새 행끼리의 충돌이라 기존 행의 버전이 바뀌지 않으므로 낙관적 락으로는 잡히지 않는다.
     * 그래서 멤버 한 명 단위로 스케줄 쓰기를 한 줄로 세운다.
     *
     * <p>이 락을 쓰는 트랜잭션은 반드시 READ COMMITTED여야 한다. MySQL 기본값인 REPEATABLE READ에서는
     * 트랜잭션의 첫 조회(권한 확인)에서 스냅샷이 정해져, 락을 기다렸다 받은 뒤에도 앞 요청이 방금
     * 커밋한 스케줄이 보이지 않는다. 락은 순서를 세우고, 격리 수준은 그 순서대로 결과가 보이게 한다.
     * 둘 중 하나만 있으면 여전히 둘 다 저장된다 — ConcurrencyControlTest가 이를 확인한다.
     */
    private StoreMember lockStoreMemberInStore(Long storeId, Long storeMemberId) {
        return requireAssignable(storeId, storeMemberRepository.findByIdForUpdate(storeMemberId));
    }

    private StoreMember getStoreMemberInStore(Long storeId, Long storeMemberId) {
        return requireAssignable(storeId, storeMemberRepository.findById(storeMemberId));
    }

    private StoreMember requireAssignable(Long storeId, Optional<StoreMember> found) {
        StoreMember member = found.orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (!member.getStore().getId().equals(storeId)) {
            throw new InvalidRequestException("해당 매장의 멤버가 아닙니다.");
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new InvalidRequestException("퇴사 처리된 멤버에게는 스케줄을 배정할 수 없습니다.");
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

    private int resolveBreakMinutes(Store store, LocalTime startTime, LocalTime endTime, Integer requested) {
        return LaborStandards.resolveBreakMinutes(store.getBreakPolicy() == BreakPolicy.STATUTORY,
                spanMinutes(startTime, endTime), requested);
    }

    /**
     * 해당 주(월~일, workDate 기준)의 스케줄 합산 근무시간 상한을 검증한다.
     * 연소근로자는 주 35시간, 성인은 주 52시간이며, 5인 미만 사업장의 성인은 상한이 적용되지 않는다.
     */
    private void validateWeeklyLimit(StoreMember member, LocalDate workDate, LocalTime startTime,
            LocalTime endTime, int breakMinutes, Long excludeShiftId, List<Shift> memberShiftsCache) {
        boolean minor = LaborStandards.isMinor(member.getUser().getBirthDate(), workDate);
        if (!minor && member.getStore().isSmallBusiness()) {
            return;
        }
        int capMinutes = minor ? LaborStandards.MINOR_MAX_WEEKLY_WORK_MINUTES
                : LaborStandards.MAX_WEEKLY_WORK_MINUTES;
        LocalDate weekStart = LaborStandards.mondayOfWeek(workDate);
        LocalDate weekEnd = LaborStandards.sundayOfWeek(workDate);
        long weeklyMinutes = spanMinutes(startTime, endTime) - breakMinutes;
        List<Shift> candidates = memberShiftsCache != null
                ? filterByDateRange(memberShiftsCache, weekStart, weekEnd)
                : shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                        member.getId(), weekStart, weekEnd);
        for (Shift existing : candidates) {
            if (existing.getId().equals(excludeShiftId) || existing.getStatus() == ShiftStatus.CANCELED) {
                continue;
            }
            weeklyMinutes += existing.workMinutes();
        }
        if (weeklyMinutes > capMinutes) {
            throw new InvalidRequestException(
                    "해당 주의 스케줄 합계가 주 " + capMinutes / 60 + "시간 상한을 초과합니다. (현재 배정 시 "
                            + weeklyMinutes / 60 + "시간 " + weeklyMinutes % 60 + "분)");
        }
    }

    /** 연소근로자(18세 미만) 보호: 1일 7시간 초과, 야간(22:00~06:00)·주휴일 근로 스케줄을 금지한다. */
    private void validateMinorProtection(StoreMember member, LocalDate workDate, LocalTime startTime,
            LocalTime endTime, int breakMinutes) {
        if (!LaborStandards.isMinor(member.getUser().getBirthDate(), workDate)) {
            return;
        }
        long workMinutes = spanMinutes(startTime, endTime) - breakMinutes;
        if (workMinutes > LaborStandards.MINOR_MAX_DAILY_WORK_MINUTES) {
            throw new InvalidRequestException("연소근로자(18세 미만)는 1일 7시간을 초과해 근무할 수 없습니다.");
        }
        boolean touchesNight = endTime.isBefore(startTime)
                || startTime.isBefore(LocalTime.of(6, 0)) || endTime.isAfter(LocalTime.of(22, 0));
        if (touchesNight) {
            throw new InvalidRequestException("연소근로자(18세 미만)는 야간(22:00~06:00)에 근무할 수 없습니다.");
        }
        if (member.getWeeklyHolidayDay() != null && workDate.getDayOfWeek() == member.getWeeklyHolidayDay()) {
            throw new InvalidRequestException("연소근로자(18세 미만)는 주휴일에 근무할 수 없습니다.");
        }
    }

    /** 체류시간(분). 자정을 넘는 근무는 다음날 종료로 계산한다. */
    private long spanMinutes(LocalTime startTime, LocalTime endTime) {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return minutes <= 0 ? minutes + 24 * 60 : minutes;
    }

    /**
     * 같은 멤버에게 시간이 겹치는 스케줄이 이미 있으면 거부한다. 자정을 넘는 야간 스케줄까지 비교하기 위해
     * 전날~다음날 스케줄을 함께 조회하고, 날짜+시간(LocalDateTime)으로 정규화하여 구간 겹침을 판정한다.
     * 취소된 스케줄과 자기 자신(수정 시)은 비교 대상에서 제외한다.
     */
    private void validateNoOverlap(StoreMember member, LocalDate workDate, LocalTime startTime,
            LocalTime endTime, Long excludeShiftId, List<Shift> memberShiftsCache) {
        LocalDateTime newStart = workDate.atTime(startTime);
        LocalDate newEndDate = endTime.isBefore(startTime) ? workDate.plusDays(1) : workDate;
        LocalDateTime newEnd = newEndDate.atTime(endTime);

        List<Shift> candidates = memberShiftsCache != null
                ? filterByDateRange(memberShiftsCache, workDate.minusDays(1), workDate.plusDays(1))
                : shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
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

    private List<Shift> filterByDateRange(List<Shift> shifts, LocalDate from, LocalDate to) {
        return shifts.stream()
                .filter(shift -> !shift.getWorkDate().isBefore(from) && !shift.getWorkDate().isAfter(to))
                .toList();
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
