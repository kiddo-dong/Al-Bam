package com.example.albam.domain.payroll.service;

import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.global.labor.LaborStandards;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 근로기준법을 반영한 급여 계산기.
 *
 * <p>포함: 기본급(휴게시간을 제외한 실근무시간 x 시급), 연장근로수당(주 단위로 "1일 8시간 초과 합계"와
 * "주 40시간 초과분" 중 큰 값을 연장으로 보아 1.5배), 야간근로수당(22:00~06:00 근무시간 0.5배 가산),
 * 주휴수당(주 15시간 이상 근무 시 (주 근무시간/40, 최대 1) x 8 x 시급).
 *
 * <p>제외(추후 확장 대상): 5인 미만 사업장 예외, 연차수당, 휴일근로 가산, 주휴수당의 "소정근로일 개근" 요건,
 * 월 경계에 걸친 주의 전체 주간 근무시간 반영(해당 월의 근태 기록만으로 주간 합계를 계산하므로 월 경계 주는
 * 과소 산정될 수 있음). 야간수당은 휴게가 야간 시간대에 있었는지 알 수 없어 휴게 차감 없이 출퇴근 시각
 * 겹침으로만 계산한다.
 */
final class PayrollCalculator {

    private static final double OVERTIME_MULTIPLIER = 1.5;
    private static final double NIGHT_PREMIUM_MULTIPLIER = 0.5;
    private static final double WEEKLY_HOLIDAY_ELIGIBLE_HOURS = 15.0;
    private static final double WEEKLY_HOLIDAY_HOURS = 8.0;
    private static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    private PayrollCalculator() {
    }

    static PayrollResult calculate(List<Attendance> attendances, int hourlyWage) {
        double nightHours = 0;
        // 주별 [0]=총 근무시간, [1]=일 8시간 초과 연장 합계
        Map<String, double[]> weekly = new HashMap<>();

        for (Attendance attendance : attendances) {
            LocalDateTime start = attendance.getClockInAt();
            LocalDateTime end = attendance.getClockOutAt();
            long workedMinutes = Duration.between(start, end).toMinutes() - attendance.getBreakMinutes();
            double workedHours = Math.max(workedMinutes, 0) / 60.0;

            nightHours += nightOverlapMinutes(start, end) / 60.0;

            double[] week = weekly.computeIfAbsent(weekKey(attendance.getWorkDate()), key -> new double[2]);
            week[0] += workedHours;
            week[1] += Math.max(workedHours - LaborStandards.STANDARD_DAILY_HOURS, 0);
        }

        double regularHours = 0;
        double overtimeHours = 0;
        long weeklyHolidayPay = 0;
        for (double[] week : weekly.values()) {
            // 연장근로는 "1일 8시간 초과"와 "주 40시간 초과" 중 큰 쪽 (같은 시간을 중복 가산하지 않음)
            double weekOvertime = Math.max(week[1], week[0] - LaborStandards.STANDARD_WEEKLY_HOURS);
            overtimeHours += weekOvertime;
            regularHours += week[0] - weekOvertime;
            if (week[0] >= WEEKLY_HOLIDAY_ELIGIBLE_HOURS) {
                weeklyHolidayPay += Math.round(
                        Math.min(week[0], LaborStandards.STANDARD_WEEKLY_HOURS)
                                / LaborStandards.STANDARD_WEEKLY_HOURS * WEEKLY_HOLIDAY_HOURS * hourlyWage);
            }
        }

        long regularPay = Math.round(regularHours * hourlyWage);
        long overtimePay = Math.round(overtimeHours * hourlyWage * OVERTIME_MULTIPLIER);
        long nightPay = Math.round(nightHours * hourlyWage * NIGHT_PREMIUM_MULTIPLIER);

        return new PayrollResult(regularPay, overtimePay, nightPay, weeklyHolidayPay);
    }

    private static long nightOverlapMinutes(LocalDateTime start, LocalDateTime end) {
        long totalMinutes = 0;
        LocalDate day = start.toLocalDate().minusDays(1);
        while (!day.isAfter(end.toLocalDate())) {
            LocalDateTime nightStart = LocalDateTime.of(day, NIGHT_START);
            LocalDateTime nightEnd = LocalDateTime.of(day.plusDays(1), NIGHT_END);
            LocalDateTime overlapStart = start.isAfter(nightStart) ? start : nightStart;
            LocalDateTime overlapEnd = end.isBefore(nightEnd) ? end : nightEnd;
            if (overlapStart.isBefore(overlapEnd)) {
                totalMinutes += Duration.between(overlapStart, overlapEnd).toMinutes();
            }
            day = day.plusDays(1);
        }
        return totalMinutes;
    }

    private static String weekKey(LocalDate date) {
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int weekOfYear = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return weekYear + "-" + weekOfYear;
    }

    record PayrollResult(long regularPay, long overtimePay, long nightPay, long weeklyHolidayPay) {
        long totalPay() {
            return regularPay + overtimePay + nightPay + weeklyHolidayPay;
        }
    }
}
