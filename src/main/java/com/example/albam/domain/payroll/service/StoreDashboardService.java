package com.example.albam.domain.payroll.service;

import com.example.albam.domain.attendance.dto.AttendanceReportEntry;
import com.example.albam.domain.attendance.dto.WorkComplianceStatus;
import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.attendance.service.AttendanceReportService;
import com.example.albam.domain.payroll.dto.DashboardResponse;
import com.example.albam.domain.payroll.dto.DashboardResponse.MemberCostRow;
import com.example.albam.domain.payroll.service.PayrollCalculator.PayrollResult;
import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.entity.ShiftStatus;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사장님용 월간 대시보드 집계. 멤버별로 급여 엔진을 돌려 인건비를 합산한다. */
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
        // 인건비(시급·급여)는 민감정보 정책상 OWNER 전용 (멤버 상세목록과 동일 기준)
        storeAuthorizationService.requireOwner(storeId, userId);

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
}
