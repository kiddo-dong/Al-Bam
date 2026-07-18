package com.example.albam.domain.payroll.service;

import com.example.albam.domain.attendance.dto.AttendanceReportEntry;
import com.example.albam.domain.attendance.dto.WorkComplianceStatus;
import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.attendance.service.AttendanceReportService;
import com.example.albam.domain.payroll.dto.DailyDashboardResponse;
import com.example.albam.domain.payroll.dto.DailyDashboardResponse.DailyMemberRow;
import com.example.albam.domain.payroll.dto.DashboardResponse;
import com.example.albam.domain.payroll.dto.DashboardResponse.MemberCostRow;
import com.example.albam.domain.payroll.dto.WeeklyDashboardResponse;
import com.example.albam.domain.payroll.dto.WeeklyDashboardResponse.WeeklyMemberRow;
import com.example.albam.domain.payroll.service.PayrollCalculator.PayrollResult;
import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.entity.ShiftStatus;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.labor.LaborStandards;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드 집계. 월간(인건비 포함)·일간·주간(시간/준수 중심)을 담당한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreDashboardService {

    private final StoreMemberRepository storeMemberRepository;
    private final AttendanceRepository attendanceRepository;
    private final ShiftRepository shiftRepository;
    private final StoreAuthorizationService storeAuthorizationService;
    private final AttendanceReportService attendanceReportService;
    private final PayrollService payrollService;

    public DashboardResponse getDashboard(Long storeId, Long userId, int year, int month) {
        if (month < 1 || month > 12) {
            throw new InvalidRequestException("월은 1~12 사이여야 합니다.");
        }
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        LocalDate fetchFrom = monthStart.with(DayOfWeek.MONDAY);
        LocalDate fetchTo = monthEnd.with(DayOfWeek.SUNDAY);

        long totalWorkMinutes = 0;
        long totalLaborCost = 0;
        long totalDeduction = 0;
        int activeMemberCount = 0;
        List<MemberCostRow> rows = new ArrayList<>();

        for (StoreMember member : storeMemberRepository.findAllByStoreId(storeId)) {
            if (member.getStatus() == MemberStatus.ACTIVE) {
                activeMemberCount++;
            }
            List<Attendance> attendances = attendanceRepository
                    .findAllByStoreMemberIdAndStatusAndWorkDateBetween(
                            member.getId(), AttendanceStatus.DONE, fetchFrom, fetchTo);
            List<Shift> shifts = shiftRepository
                    .findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                            member.getId(), fetchFrom, fetchTo).stream()
                    .filter(shift -> shift.getStatus() != ShiftStatus.CANCELED)
                    .toList();
            Set<LocalDate> scheduledDates = shifts.stream()
                    .map(Shift::getWorkDate)
                    .collect(Collectors.toSet());

            PayrollResult result = PayrollCalculator.calculate(attendances, member.getHourlyWage(),
                    member.getStore().isSmallBusiness(), member.getWeeklyHolidayDay(), scheduledDates,
                    yearMonth);
            long leavePay = payrollService.calculateLeavePay(member, shifts, monthStart, monthEnd);
            long totalPay = result.regularPay() + result.overtimePay() + result.nightPay()
                    + result.holidayWorkPay() + result.weeklyHolidayPay() + leavePay;
            long deduction = payrollService.calculateDeduction(member.getTaxMode(), totalPay);
            long workMinutes = Math.round(result.totalWorkedHours() * 60);

            // 이번 달 비용이 없는 퇴사자는 목록에서 제외한다
            if (totalPay == 0 && member.getStatus() != MemberStatus.ACTIVE) {
                continue;
            }
            totalWorkMinutes += workMinutes;
            totalLaborCost += totalPay;
            totalDeduction += deduction;
            rows.add(new MemberCostRow(member.getId(), member.getUser().getName(), member.getRole(),
                    member.getStatus(), member.getHourlyWage(), workMinutes, totalPay,
                    totalPay - deduction));
        }
        rows.sort((a, b) -> Long.compare(b.totalPay(), a.totalPay()));

        Map<WorkComplianceStatus, Long> attendanceSummary =
                attendanceReportService.getReport(storeId, userId, null, monthStart, monthEnd).stream()
                        .collect(Collectors.groupingBy(AttendanceReportEntry::status, Collectors.counting()));

        return new DashboardResponse(year, month, activeMemberCount, totalWorkMinutes, totalLaborCost,
                totalDeduction, totalLaborCost - totalDeduction, attendanceSummary, rows);
    }

    /** 일일 대시보드: 그날의 근무·근태 요약 (금액 없음). */
    public DailyDashboardResponse getDailyDashboard(Long storeId, Long userId, LocalDate date) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        List<AttendanceReportEntry> report =
                attendanceReportService.getReport(storeId, userId, null, date, date);
        Map<Long, Attendance> attendanceById = attendanceRepository
                .findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateDesc(storeId, date, date)
                .stream()
                .collect(Collectors.toMap(Attendance::getId, Function.identity()));

        List<DailyMemberRow> rows = report.stream()
                .map(entry -> {
                    Attendance attendance = entry.attendanceId() == null ? null
                            : attendanceById.get(entry.attendanceId());
                    return new DailyMemberRow(entry.storeMemberId(), entry.userName(), entry.clockInAt(),
                            entry.clockOutAt(), attendance == null ? null : attendance.getBreakMinutes(),
                            attendance == null ? 0 : workMinutesOf(attendance), entry.status(),
                            entry.lateMinutes(), entry.earlyLeaveMinutes());
                })
                .toList();

        long totalWorkMinutes = rows.stream().mapToLong(DailyMemberRow::workMinutes).sum();
        int workedMemberCount = (int) report.stream()
                .filter(entry -> entry.attendanceId() != null)
                .map(AttendanceReportEntry::storeMemberId)
                .distinct()
                .count();
        Map<WorkComplianceStatus, Long> complianceSummary = report.stream()
                .collect(Collectors.groupingBy(AttendanceReportEntry::status, Collectors.counting()));

        return new DailyDashboardResponse(date, totalWorkMinutes, workedMemberCount, complianceSummary, rows);
    }

    /** 주간 대시보드: 주 52시간(연소자 35시간) 상한·주휴 15시간 모니터링 (금액 없음). */
    public WeeklyDashboardResponse getWeeklyDashboard(Long storeId, Long userId, LocalDate date) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate today = LocalDate.now();

        // 실근무: 이번 주의 근태 (근무 중이면 현재까지 누적)
        Map<Long, Long> actualByMemberId = new HashMap<>();
        for (Attendance attendance : attendanceRepository
                .findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateDesc(storeId, weekStart, weekEnd)) {
            actualByMemberId.merge(attendance.getStoreMember().getId(), workMinutesOf(attendance), Long::sum);
        }
        // 남은 스케줄: 오늘 이후의 비취소 스케줄
        Map<Long, Long> remainingByMemberId = new HashMap<>();
        for (Shift shift : shiftRepository
                .findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                        storeId, weekStart, weekEnd)) {
            if (shift.getStatus() != ShiftStatus.CANCELED && shift.getWorkDate().isAfter(today)) {
                remainingByMemberId.merge(shift.getStoreMember().getId(), shift.workMinutes(), Long::sum);
            }
        }

        List<WeeklyMemberRow> rows = new ArrayList<>();
        for (StoreMember member : storeMemberRepository.findAllByStoreId(storeId)) {
            long actual = actualByMemberId.getOrDefault(member.getId(), 0L);
            long remaining = remainingByMemberId.getOrDefault(member.getId(), 0L);
            if (member.getStatus() != MemberStatus.ACTIVE && actual == 0 && remaining == 0) {
                continue;
            }
            long projected = actual + remaining;
            boolean minor = LaborStandards.isMinor(member.getUser().getBirthDate(), weekEnd);
            Long capMinutes;
            if (minor) {
                capMinutes = (long) LaborStandards.MINOR_MAX_WEEKLY_WORK_MINUTES;
            } else if (member.getStore().isSmallBusiness()) {
                capMinutes = null;
            } else {
                capMinutes = (long) LaborStandards.MAX_WEEKLY_WORK_MINUTES;
            }
            rows.add(new WeeklyMemberRow(member.getId(), member.getUser().getName(), actual, remaining,
                    projected, capMinutes, capMinutes != null && projected > capMinutes,
                    projected >= LaborStandards.WEEKLY_HOLIDAY_ELIGIBLE_MINUTES));
        }
        rows.sort(Comparator.comparingLong(WeeklyMemberRow::projectedMinutes).reversed());

        Map<WorkComplianceStatus, Long> complianceSummary = attendanceReportService
                .getReport(storeId, userId, null, weekStart, weekEnd).stream()
                .collect(Collectors.groupingBy(AttendanceReportEntry::status, Collectors.counting()));

        long totalActual = rows.stream().mapToLong(WeeklyMemberRow::actualMinutes).sum();
        long totalProjected = rows.stream().mapToLong(WeeklyMemberRow::projectedMinutes).sum();
        return new WeeklyDashboardResponse(weekStart, weekEnd, totalActual, totalProjected,
                complianceSummary, rows);
    }

    /** 실근무시간(분). 퇴근 전이면 현재 시각까지 누적으로 계산한다. */
    private long workMinutesOf(Attendance attendance) {
        LocalDateTime end = attendance.getClockOutAt() == null ? LocalDateTime.now()
                : attendance.getClockOutAt();
        return Math.max(0,
                Duration.between(attendance.getClockInAt(), end).toMinutes() - attendance.getBreakMinutes());
    }
}
