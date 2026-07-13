package com.example.albam.domain.payroll.service;

import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.global.labor.LaborStandards;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 근로기준법을 반영한 급여 계산기.
 *
 * <p>포함:
 * <ul>
 *   <li>기본급: 휴게시간을 제외한 실근무시간 x 시급</li>
 *   <li>연장근로수당: 주 단위로 "1일 8시간 초과 합계"와 "주 40시간 초과분" 중 큰 값을 연장으로 보아 1.5배.
 *       주휴일 근로시간은 휴일가산과의 중복을 피하기 위해 연장 판정에서 제외</li>
 *   <li>야간근로수당: 22:00~06:00 근무시간 0.5배 가산</li>
 *   <li>휴일근로수당: 주휴일 근무 시 8시간까지 0.5배, 초과분 1.0배 가산 (기본급은 별도 지급)</li>
 *   <li>주휴수당: 주 15시간 이상 근무 + 개근(해당 주의 비취소 스케줄 날짜에 모두 출근 기록 존재) 시
 *       (주 근무시간/40, 최대 1) x 8 x 시급. 스케줄이 없는 주는 개근으로 간주</li>
 *   <li>5인 미만 사업장: 연장·야간·휴일 가산 미적용 (근무시간은 전부 기본급 1배로 지급, 주휴수당은 적용)</li>
 * </ul>
 *
 * <p>제외(추후 확장 대상): 연차수당, 월 경계에 걸친 주의 전체 주간 근무시간 반영(해당 월의 근태 기록만으로
 * 주간 합계를 계산하므로 월 경계 주는 과소 산정될 수 있음). 야간수당은 휴게가 야간 시간대에 있었는지 알 수
 * 없어 휴게 차감 없이 출퇴근 시각 겹침으로만 계산한다.
 */
final class PayrollCalculator {

    private static final double OVERTIME_MULTIPLIER = 1.5;
    private static final double NIGHT_PREMIUM_MULTIPLIER = 0.5;
    private static final double HOLIDAY_PREMIUM_MULTIPLIER = 0.5;
    private static final double HOLIDAY_OVERTIME_PREMIUM_MULTIPLIER = 1.0;
    private static final double WEEKLY_HOLIDAY_ELIGIBLE_HOURS = 15.0;
    private static final double WEEKLY_HOLIDAY_HOURS = 8.0;
    private static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    private PayrollCalculator() {
    }

    static PayrollResult calculate(List<Attendance> attendances, int hourlyWage, boolean smallBusiness,
            DayOfWeek weeklyHolidayDay, Set<LocalDate> scheduledDates) {
        double nightHours = 0;
        // 주별 집계: [0]=총 근무시간, [1]=일 8시간 초과 연장 합계(휴일 제외), [2]=휴일근로 8h 이내, [3]=휴일근로 8h 초과
        Map<String, double[]> weekly = new HashMap<>();
        Set<LocalDate> attendedDates = attendances.stream()
                .map(Attendance::getWorkDate)
                .collect(Collectors.toSet());

        for (Attendance attendance : attendances) {
            LocalDateTime start = attendance.getClockInAt();
            LocalDateTime end = attendance.getClockOutAt();
            long workedMinutes = Duration.between(start, end).toMinutes() - attendance.getBreakMinutes();
            double workedHours = Math.max(workedMinutes, 0) / 60.0;

            nightHours += nightOverlapMinutes(start, end) / 60.0;

            double[] week = weekly.computeIfAbsent(weekKey(attendance.getWorkDate()), key -> new double[4]);
            week[0] += workedHours;
            boolean holidayWork = weeklyHolidayDay != null
                    && attendance.getWorkDate().getDayOfWeek() == weeklyHolidayDay;
            if (holidayWork) {
                week[2] += Math.min(workedHours, LaborStandards.STANDARD_DAILY_HOURS);
                week[3] += Math.max(workedHours - LaborStandards.STANDARD_DAILY_HOURS, 0);
            } else {
                week[1] += Math.max(workedHours - LaborStandards.STANDARD_DAILY_HOURS, 0);
            }
        }

        Map<String, List<LocalDate>> scheduledByWeek = scheduledDates.stream()
                .collect(Collectors.groupingBy(PayrollCalculator::weekKey));

        double regularHours = 0;
        double overtimeHours = 0;
        double holidayPremiumHours = 0;
        long weeklyHolidayPay = 0;
        for (Map.Entry<String, double[]> entry : weekly.entrySet()) {
            double[] week = entry.getValue();
            double nonHolidayHours = week[0] - week[2] - week[3];
            double weekOvertime = Math.max(week[1], nonHolidayHours - LaborStandards.STANDARD_WEEKLY_HOURS);
            overtimeHours += weekOvertime;
            regularHours += week[0] - weekOvertime;
            holidayPremiumHours += week[2] * HOLIDAY_PREMIUM_MULTIPLIER
                    + week[3] * HOLIDAY_OVERTIME_PREMIUM_MULTIPLIER;

            boolean perfectAttendance = scheduledByWeek.getOrDefault(entry.getKey(), List.of()).stream()
                    .allMatch(attendedDates::contains);
            if (week[0] >= WEEKLY_HOLIDAY_ELIGIBLE_HOURS && perfectAttendance) {
                weeklyHolidayPay += Math.round(
                        Math.min(week[0], LaborStandards.STANDARD_WEEKLY_HOURS)
                                / LaborStandards.STANDARD_WEEKLY_HOURS * WEEKLY_HOLIDAY_HOURS * hourlyWage);
            }
        }

        long regularPay = Math.round(regularHours * hourlyWage);
        // 5인 미만 사업장은 가산수당 의무가 없다. 연장분도 기본 1배로만 지급한다.
        long overtimePay = smallBusiness
                ? Math.round(overtimeHours * hourlyWage)
                : Math.round(overtimeHours * hourlyWage * OVERTIME_MULTIPLIER);
        long nightPay = smallBusiness ? 0 : Math.round(nightHours * hourlyWage * NIGHT_PREMIUM_MULTIPLIER);
        long holidayWorkPay = smallBusiness ? 0 : Math.round(holidayPremiumHours * hourlyWage);

        return new PayrollResult(regularPay, overtimePay, nightPay, holidayWorkPay, weeklyHolidayPay);
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

    record PayrollResult(long regularPay, long overtimePay, long nightPay, long holidayWorkPay,
            long weeklyHolidayPay) {
        long totalPay() {
            return regularPay + overtimePay + nightPay + holidayWorkPay + weeklyHolidayPay;
        }
    }
}
