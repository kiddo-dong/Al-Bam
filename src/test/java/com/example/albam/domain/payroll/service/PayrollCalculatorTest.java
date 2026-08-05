package com.example.albam.domain.payroll.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.payroll.service.PayrollCalculator.PayrollResult;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PayrollCalculatorTest {

    private static final int WAGE = 10_000;
    private static final YearMonth JANUARY_2026 = YearMonth.of(2026, 1);

    // 2026-01-05(월) ~ 2026-01-11(일)은 온전히 1월 안에 있는 ISO 주.
    private static final LocalDate MON = LocalDate.of(2026, 1, 5);
    private static final LocalDate TUE = LocalDate.of(2026, 1, 6);
    private static final LocalDate WED = LocalDate.of(2026, 1, 7);
    private static final LocalDate THU = LocalDate.of(2026, 1, 8);
    private static final LocalDate FRI = LocalDate.of(2026, 1, 9);
    private static final LocalDate SAT = LocalDate.of(2026, 1, 10);
    private static final LocalDate SUN = LocalDate.of(2026, 1, 11);

    @Test
    void 하루_8시간_근무는_전부_기본급이다() {
        List<Attendance> attendances = List.of(attendance(MON, LocalTime.of(9, 0), LocalTime.of(18, 0), 60));

        PayrollResult result = PayrollCalculator.calculate(attendances, WAGE, false, null, Set.of(), JANUARY_2026);

        assertThat(result.regularPay()).isEqualTo(80_000);
        assertThat(result.overtimePay()).isZero();
        assertThat(result.nightPay()).isZero();
        assertThat(result.holidayWorkPay()).isZero();
        assertThat(result.weeklyHolidayPay()).isZero();
        assertThat(result.totalWorkedHours()).isEqualTo(8.0);
    }

    @Test
    void 하루_8시간_초과분은_연장근로_1_5배다() {
        List<Attendance> attendances = List.of(attendance(MON, LocalTime.of(9, 0), LocalTime.of(19, 0), 60));

        PayrollResult result = PayrollCalculator.calculate(attendances, WAGE, false, null, Set.of(), JANUARY_2026);

        assertThat(result.regularPay()).isEqualTo(80_000);
        assertThat(result.overtimePay()).isEqualTo(15_000);
        assertThat(result.totalWorkedHours()).isEqualTo(9.0);
        assertThat(result.overtimeHours()).isEqualTo(1.0);
    }

    @Test
    void 하루는_8시간_이하여도_주_40시간_초과분은_연장근로다() {
        List<Attendance> attendances = List.of(
                attendance(MON, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(TUE, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(WED, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(THU, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(FRI, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(SAT, LocalTime.of(9, 0), LocalTime.of(11, 0), 0));

        PayrollResult result = PayrollCalculator.calculate(attendances, WAGE, false, null, Set.of(), JANUARY_2026);

        // 월~금 40시간은 기본급, 토요일 2시간은 주 40시간을 넘긴 몫이라 전부 연장근로로 귀속된다.
        assertThat(result.totalWorkedHours()).isEqualTo(42.0);
        assertThat(result.overtimeHours()).isEqualTo(2.0);
        assertThat(result.regularPay()).isEqualTo(400_000);
        assertThat(result.overtimePay()).isEqualTo(30_000);
    }

    @Test
    void 야간시간대_근무는_가산수당이_붙는다() {
        List<Attendance> attendances = List.of(
                attendance(MON.atTime(20, 0), MON.plusDays(1).atTime(2, 0), 0));

        PayrollResult result = PayrollCalculator.calculate(attendances, WAGE, false, null, Set.of(), JANUARY_2026);

        // 22:00~02:00 4시간만 야간(22~06)과 겹친다.
        assertThat(result.nightHours()).isEqualTo(4.0);
        assertThat(result.nightPay()).isEqualTo(20_000);
        assertThat(result.regularPay()).isEqualTo(60_000);
    }

    @Test
    void 주휴일_근무는_기본급과_별개로_휴일가산이_붙는다() {
        List<Attendance> attendances = List.of(attendance(SUN, LocalTime.of(9, 0), LocalTime.of(19, 0), 60));

        PayrollResult result = PayrollCalculator.calculate(
                attendances, WAGE, false, DayOfWeek.SUNDAY, Set.of(), JANUARY_2026);

        // 9시간 중 8시간까지 0.5배, 초과 1시간은 1.0배 가산. 기본급(9h)은 그대로 지급된다.
        assertThat(result.holidayWorkHours()).isEqualTo(9.0);
        assertThat(result.regularPay()).isEqualTo(90_000);
        assertThat(result.holidayWorkPay()).isEqualTo(50_000);
        assertThat(result.overtimePay()).isZero();
    }

    @Test
    void 개근하고_주15시간_이상이면_주휴수당이_나온다() {
        Set<LocalDate> scheduledDates = Set.of(MON, TUE, WED, THU, FRI);
        List<Attendance> attendances = List.of(
                attendance(MON, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(TUE, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(WED, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(THU, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(FRI, LocalTime.of(9, 0), LocalTime.of(18, 0), 60));

        PayrollResult result = PayrollCalculator.calculate(
                attendances, WAGE, false, null, scheduledDates, JANUARY_2026);

        assertThat(result.weeklyHolidayPay()).isEqualTo(80_000);
        assertThat(result.regularPay()).isEqualTo(400_000);
    }

    @Test
    void 주15시간_미만이면_개근해도_주휴수당이_없다() {
        Set<LocalDate> scheduledDates = Set.of(MON);
        List<Attendance> attendances = List.of(attendance(MON, LocalTime.of(9, 0), LocalTime.of(14, 0), 0));

        PayrollResult result = PayrollCalculator.calculate(
                attendances, WAGE, false, null, scheduledDates, JANUARY_2026);

        assertThat(result.weeklyHolidayPay()).isZero();
    }

    @Test
    void 스케줄된_날_결근하면_시간을_채워도_주휴수당이_없다() {
        Set<LocalDate> scheduledDates = Set.of(MON, TUE, WED); // 수요일 스케줄은 있지만 결근
        List<Attendance> attendances = List.of(
                attendance(MON, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(TUE, LocalTime.of(9, 0), LocalTime.of(18, 0), 60));

        PayrollResult result = PayrollCalculator.calculate(
                attendances, WAGE, false, null, scheduledDates, JANUARY_2026);

        // 주 16시간으로 시간 요건은 충족하지만, 스케줄된 수요일에 결근했으므로 개근이 아니다.
        assertThat(result.regularPay()).isEqualTo(160_000);
        assertThat(result.weeklyHolidayPay()).isZero();
    }

    @Test
    void 오인미만_사업장은_연장야간휴일가산이_없지만_주휴수당은_그대로_나온다() {
        List<Attendance> attendances = List.of(
                attendance(MON, LocalTime.of(8, 0), LocalTime.of(20, 0), 60), // 11시간, 일 연장 3시간
                attendance(TUE, LocalTime.of(9, 0), LocalTime.of(18, 0), 60)); // 8시간

        PayrollResult result = PayrollCalculator.calculate(
                attendances, WAGE, true, null, Set.of(), JANUARY_2026);

        assertThat(result.overtimeHours()).isEqualTo(3.0);
        assertThat(result.regularPay()).isEqualTo(160_000);
        // 5인 미만은 연장 가산율(1.5배) 없이 1배로만 지급된다.
        assertThat(result.overtimePay()).isEqualTo(30_000);
        assertThat(result.nightPay()).isZero();
        // 주휴수당은 5인 미만 사업장에도 적용된다 (주 19시간).
        assertThat(result.weeklyHolidayPay()).isEqualTo(38_000);
    }

    @Test
    void 주휴수당은_그_주_일요일이_속한_달에_귀속된다() {
        // 2026-01-26(월) ~ 2026-02-01(일): 근무는 전부 1월, 주휴수당은 2월로 귀속되어야 한다.
        LocalDate mon = LocalDate.of(2026, 1, 26);
        LocalDate tue = LocalDate.of(2026, 1, 27);
        LocalDate wed = LocalDate.of(2026, 1, 28);
        LocalDate thu = LocalDate.of(2026, 1, 29);
        LocalDate fri = LocalDate.of(2026, 1, 30);
        Set<LocalDate> scheduledDates = Set.of(mon, tue, wed, thu, fri);
        List<Attendance> attendances = List.of(
                attendance(mon, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(tue, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(wed, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(thu, LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
                attendance(fri, LocalTime.of(9, 0), LocalTime.of(18, 0), 60));

        PayrollResult january = PayrollCalculator.calculate(
                attendances, WAGE, false, null, scheduledDates, YearMonth.of(2026, 1));
        PayrollResult february = PayrollCalculator.calculate(
                attendances, WAGE, false, null, scheduledDates, YearMonth.of(2026, 2));

        assertThat(january.regularPay()).isEqualTo(400_000);
        assertThat(january.weeklyHolidayPay()).isZero();

        assertThat(february.regularPay()).isZero();
        assertThat(february.weeklyHolidayPay()).isEqualTo(80_000);
    }

    private static Attendance attendance(LocalDate date, LocalTime clockIn, LocalTime clockOut, int breakMinutes) {
        return attendance(date.atTime(clockIn), date.atTime(clockOut), breakMinutes);
    }

    private static Attendance attendance(LocalDateTime clockIn, LocalDateTime clockOut, int breakMinutes) {
        Attendance attendance = new Attendance(null, clockIn);
        attendance.correctTimes(clockIn, clockOut, breakMinutes);
        return attendance;
    }
}
