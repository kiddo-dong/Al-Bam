package com.example.albam.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.albam.domain.attendance.dto.AttendanceReportEntry;
import com.example.albam.domain.attendance.dto.WorkComplianceStatus;
import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.leave.entity.LeaveUsage;
import com.example.albam.domain.leave.repository.LeaveUsageRepository;
import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.domain.user.entity.User;
import com.example.albam.global.exception.ForbiddenException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttendanceReportServiceTest {

    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private LeaveUsageRepository leaveUsageRepository;
    @Mock
    private StoreAuthorizationService storeAuthorizationService;

    @InjectMocks
    private AttendanceReportService attendanceReportService;

    private static final Long STORE_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    // 실행 시점과 무관하게 항상 "과거"인 고정 날짜 -> 지각/조퇴/결근 판정이 결정적으로 나온다.
    private static final LocalDate PAST_DATE = LocalDate.of(2026, 1, 5);

    private StoreMember member;
    private StoreMember managerRequester;

    @BeforeEach
    void setUp() {
        Store store = new Store("테스트 매장", "서울", null, null, new HashMap<>(), "ABC123",
                BreakPolicy.STATUTORY, false);
        ReflectionTestUtils.setField(store, "id", STORE_ID);

        User user = new User("staff@albam.dev", "pw", "직원", "010-0000-0000",
                LocalDate.of(1990, 1, 1), null);
        member = new StoreMember(store, user, MemberRole.STAFF, 10_000);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);

        User managerUser = new User("manager@albam.dev", "pw", "매니저", "010-1111-1111",
                LocalDate.of(1985, 1, 1), null);
        managerRequester = new StoreMember(store, managerUser, MemberRole.MANAGER, 12_000);
        ReflectionTestUtils.setField(managerRequester, "id", 1L);

        lenient().when(storeAuthorizationService.requireMember(STORE_ID, USER_ID)).thenReturn(managerRequester);
        lenient().when(leaveUsageRepository.findAllByStoreMemberIdAndLeaveDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void exactMatch_isJudgedNormal() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance attendance = attendance(1L, PAST_DATE.atTime(9, 0), PAST_DATE.atTime(18, 0));
        stub(List.of(shift), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report).hasSize(1);
        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.NORMAL);
        assertThat(report.get(0).lateMinutes()).isZero();
        assertThat(report.get(0).earlyLeaveMinutes()).isZero();
    }

    @Test
    void lateClockIn_isJudgedLate() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance attendance = attendance(1L, PAST_DATE.atTime(9, 15), PAST_DATE.atTime(18, 0));
        stub(List.of(shift), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.LATE);
        assertThat(report.get(0).lateMinutes()).isEqualTo(15);
    }

    @Test
    void earlyClockOut_isJudgedEarlyLeave() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance attendance = attendance(1L, PAST_DATE.atTime(9, 0), PAST_DATE.atTime(17, 30));
        stub(List.of(shift), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.EARLY_LEAVE);
        assertThat(report.get(0).earlyLeaveMinutes()).isEqualTo(30);
    }

    @Test
    void lateAndEarlyLeave_areBothJudged() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance attendance = attendance(1L, PAST_DATE.atTime(9, 10), PAST_DATE.atTime(17, 40));
        stub(List.of(shift), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.LATE_AND_EARLY_LEAVE);
    }

    @Test
    void stillClockedIn_isJudgedWorking() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance attendance = attendance(1L, PAST_DATE.atTime(9, 0), null); // 아직 퇴근 안 함
        stub(List.of(shift), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.WORKING);
    }

    @Test
    void noAttendance_pastShift_isJudgedAbsent() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        stub(List.of(shift), List.of());

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report).hasSize(1);
        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.ABSENT);
    }

    @Test
    void noAttendance_butOnApprovedLeave_isJudgedOnLeaveInsteadOfAbsent() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        when(shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                MEMBER_ID, PAST_DATE, PAST_DATE)).thenReturn(List.of(shift));
        when(attendanceRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateDesc(
                MEMBER_ID, PAST_DATE, PAST_DATE)).thenReturn(List.of());
        LeaveUsage leave = new LeaveUsage(member, PAST_DATE);
        when(leaveUsageRepository.findAllByStoreMemberIdAndLeaveDateBetween(MEMBER_ID, PAST_DATE, PAST_DATE))
                .thenReturn(List.of(leave));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.ON_LEAVE);
    }

    @Test
    void futureShift_isSkippedEntirely() {
        LocalDate future = LocalDate.now().plusYears(1);
        Shift shift = shift(future, LocalTime.of(9, 0), LocalTime.of(18, 0));
        when(shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                MEMBER_ID, future, future)).thenReturn(List.of(shift));
        when(attendanceRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateDesc(
                MEMBER_ID, future, future)).thenReturn(List.of());
        when(leaveUsageRepository.findAllByStoreMemberIdAndLeaveDateBetween(MEMBER_ID, future, future))
                .thenReturn(List.of());

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, future, future);

        assertThat(report).isEmpty(); // 아직 시작 안 한 스케줄은 결근으로도, 그 무엇으로도 판정하지 않는다
    }

    @Test
    void attendanceWithoutMatchingShift_isJudgedExtra() {
        Attendance attendance = attendance(1L, PAST_DATE.atTime(10, 0), PAST_DATE.atTime(14, 0));
        stub(List.of(), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report).hasSize(1);
        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.EXTRA);
        assertThat(report.get(0).shiftId()).isNull();
    }

    @Test
    void whenMultipleAttendancesSameDay_matchesTheOneWithLargestOverlap() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance weakOverlap = attendance(1L, PAST_DATE.atTime(7, 0), PAST_DATE.atTime(9, 30)); // 30분 겹침
        Attendance strongOverlap = attendance(2L, PAST_DATE.atTime(9, 0), PAST_DATE.atTime(18, 0)); // 완전 겹침
        stub(List.of(shift), List.of(weakOverlap, strongOverlap));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        AttendanceReportEntry matchedEntry = report.stream()
                .filter(entry -> entry.shiftId() != null)
                .findFirst().orElseThrow();
        assertThat(matchedEntry.attendanceId()).isEqualTo(2L);
        assertThat(matchedEntry.status()).isEqualTo(WorkComplianceStatus.NORMAL);

        // 매칭에서 밀린 나머지 하나는 EXTRA로 남는다.
        assertThat(report).anySatisfy(entry ->
                assertThat(entry.status()).isEqualTo(WorkComplianceStatus.EXTRA));
    }

    @Test
    void nonManagerCannotViewOtherMembersReport() {
        User otherUser = new User("other@albam.dev", "pw", "동료", "010-9999-9999",
                LocalDate.of(1992, 1, 1), null);
        StoreMember requestingStaff = new StoreMember(member.getStore(), otherUser, MemberRole.STAFF, 10_000);
        ReflectionTestUtils.setField(requestingStaff, "id", 30L); // MEMBER_ID(10)와 다른 본인 id
        when(storeAuthorizationService.requireMember(STORE_ID, USER_ID)).thenReturn(requestingStaff);

        assertThatThrownBy(() -> attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void staffCanViewTheirOwnReport() {
        when(storeAuthorizationService.requireMember(STORE_ID, USER_ID)).thenReturn(member);
        stub(List.of(), List.of());

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE); // storeMemberId == 본인 id

        assertThat(report).isEmpty(); // 예외 없이 빈 리포트가 반환되면 통과
    }

    private void stub(List<Shift> shifts, List<Attendance> attendances) {
        lenient().when(shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                MEMBER_ID, PAST_DATE, PAST_DATE)).thenReturn(shifts);
        lenient().when(attendanceRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateDesc(
                MEMBER_ID, PAST_DATE, PAST_DATE)).thenReturn(attendances);
        lenient().when(leaveUsageRepository.findAllByStoreMemberIdAndLeaveDateBetween(
                MEMBER_ID, PAST_DATE, PAST_DATE)).thenReturn(List.of());
    }

    private static long nextShiftId = 1;

    private Shift shift(LocalDate date, LocalTime start, LocalTime end) {
        Shift shift = new Shift(member, date, start, end, 0);
        ReflectionTestUtils.setField(shift, "id", nextShiftId++);
        return shift;
    }

    private Attendance attendance(long id, LocalDateTime clockIn, LocalDateTime clockOut) {
        Attendance attendance = new Attendance(member, clockIn);
        ReflectionTestUtils.setField(attendance, "id", id);
        if (clockOut != null) {
            attendance.correctTimes(clockIn, clockOut, 0);
        }
        return attendance;
    }
}
