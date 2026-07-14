package com.example.albam.domain.payroll.service;

import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.leave.repository.LeaveUsageRepository;
import com.example.albam.domain.payroll.dto.PayrollResponse;
import com.example.albam.domain.payroll.entity.Payroll;
import com.example.albam.domain.payroll.repository.PayrollRepository;
import com.example.albam.domain.payroll.service.PayrollCalculator.PayrollResult;
import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.entity.ShiftStatus;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.entity.TaxMode;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import com.example.albam.global.labor.LaborStandards;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollService {

    private final PayrollRepository payrollRepository;
    private final AttendanceRepository attendanceRepository;
    private final ShiftRepository shiftRepository;
    private final LeaveUsageRepository leaveUsageRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public PayrollResponse getPayroll(Long storeId, Long memberId, Long userId, int year, int month) {
        if (month < 1 || month > 12) {
            throw new InvalidRequestException("월은 1~12 사이여야 합니다.");
        }
        StoreMember requester = storeAuthorizationService.requireMember(storeId, userId);
        StoreMember target = storeMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (!target.getStore().getId().equals(storeId)) {
            throw new NotFoundException("멤버를 찾을 수 없습니다.");
        }
        if (!requester.getId().equals(target.getId()) && !requester.isOwnerOrManager()) {
            throw new InvalidRequestException("본인 또는 매장 관리자만 조회할 수 있습니다.");
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        // 주 단위 판정(연장·주휴)을 위해 월과 겹치는 ISO 주 전체를 조회한다
        LocalDate fetchFrom = monthStart.with(DayOfWeek.MONDAY);
        LocalDate fetchTo = monthEnd.with(DayOfWeek.SUNDAY);

        List<Attendance> attendances = attendanceRepository.findAllByStoreMemberIdAndStatusAndWorkDateBetween(
                target.getId(), AttendanceStatus.DONE, fetchFrom, fetchTo);
        List<Shift> shifts = shiftRepository
                .findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                        target.getId(), fetchFrom, fetchTo).stream()
                .filter(shift -> shift.getStatus() != ShiftStatus.CANCELED)
                .toList();
        // 주휴수당 개근 판정용: 비취소 스케줄 날짜
        Set<LocalDate> scheduledDates = shifts.stream()
                .map(Shift::getWorkDate)
                .collect(Collectors.toSet());

        PayrollResult result = PayrollCalculator.calculate(attendances, target.getHourlyWage(),
                target.getStore().isSmallBusiness(), target.getWeeklyHolidayDay(), scheduledDates, yearMonth);
        long leavePay = calculateLeavePay(target, shifts, monthStart, monthEnd);
        long grossPay = result.regularPay() + result.overtimePay() + result.nightPay()
                + result.holidayWorkPay() + result.weeklyHolidayPay() + leavePay;
        long deduction = calculateDeduction(target.getTaxMode(), grossPay);

        Payroll payroll = payrollRepository
                .findByStoreMemberIdAndTargetYearAndTargetMonth(target.getId(), year, month)
                .orElseGet(() -> new Payroll(target, year, month));
        payroll.applyResult(result.regularPay(), result.overtimePay(), result.nightPay(),
                result.holidayWorkPay(), result.weeklyHolidayPay(), leavePay, deduction);
        payrollRepository.save(payroll);

        return PayrollResponse.from(payroll);
    }

    /**
     * 연차수당: 해당 월의 연차 사용일 x 1일 평균 스케줄 근무시간 x 시급.
     * 평균은 그 달의 비취소 스케줄로 계산하고, 스케줄이 없으면 법정 1일 기준(8시간)을 쓴다.
     */
    private long calculateLeavePay(StoreMember member, List<Shift> shifts, LocalDate monthStart,
            LocalDate monthEnd) {
        long leaveDays = leaveUsageRepository.countByStoreMemberIdAndLeaveDateBetween(
                member.getId(), monthStart, monthEnd);
        if (leaveDays == 0) {
            return 0;
        }
        List<Shift> monthShifts = shifts.stream()
                .filter(shift -> !shift.getWorkDate().isBefore(monthStart)
                        && !shift.getWorkDate().isAfter(monthEnd))
                .toList();
        long scheduledDayCount = monthShifts.stream().map(Shift::getWorkDate).distinct().count();
        double dailyHours = scheduledDayCount == 0
                ? LaborStandards.STANDARD_DAILY_HOURS
                : monthShifts.stream().mapToLong(Shift::workMinutes).sum() / 60.0 / scheduledDayCount;
        return Math.round(leaveDays * dailyHours * member.getHourlyWage());
    }

    private long calculateDeduction(TaxMode taxMode, long grossPay) {
        return switch (taxMode) {
            case NONE -> 0;
            case WITHHOLDING_3_3 -> Math.round(grossPay * LaborStandards.WITHHOLDING_TAX_RATE);
            case FOUR_INSURANCES -> Math.round(grossPay * LaborStandards.FOUR_INSURANCES_EMPLOYEE_RATE);
        };
    }
}
