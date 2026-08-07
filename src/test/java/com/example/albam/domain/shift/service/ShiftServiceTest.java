package com.example.albam.domain.shift.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.albam.domain.shift.dto.CreateShiftRequest;
import com.example.albam.domain.shift.dto.ShiftResponse;
import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.BusinessHour;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.domain.user.entity.User;
import com.example.albam.global.exception.InvalidRequestException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private StoreMemberRepository storeMemberRepository;
    @Mock
    private StoreAuthorizationService storeAuthorizationService;

    @InjectMocks
    private ShiftService shiftService;

    private static final Long STORE_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);

    private StoreMember adultMember;

    @BeforeEach
    void setUp() {
        Store store = store(Map.of(), BreakPolicy.STATUTORY, false);
        User adult = new User("staff@albam.dev", "pw", "직원", "010-0000-0000",
                LocalDate.of(1990, 1, 1), null);
        adultMember = new StoreMember(store, adult, MemberRole.STAFF, 10_000);
        ReflectionTestUtils.setField(adultMember, "id", MEMBER_ID);

        lenient().when(storeAuthorizationService.requireOwnerOrManager(anyLong(), anyLong()))
                .thenReturn(adultMember);
        lenient().when(storeMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(adultMember));
        lenient().when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                        anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void createShift_savesWhenNoConflicts() {
        CreateShiftRequest request = new CreateShiftRequest(MEMBER_ID, MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), null);

        ShiftResponse response = shiftService.createShift(STORE_ID, USER_ID, request);

        assertThat(response.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.breakMinutes()).isEqualTo(60); // 9시간 체류, 법정 강제 정책 -> 자동 1시간
    }

    @Test
    void createShift_rejectsOverlappingSchedule() {
        Shift existing = new Shift(adultMember, MONDAY, LocalTime.of(8, 0), LocalTime.of(15, 0), 60);
        ReflectionTestUtils.setField(existing, "id", 99L);
        when(shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                MEMBER_ID, MONDAY.minusDays(1), MONDAY.plusDays(1)))
                .thenReturn(List.of(existing));

        CreateShiftRequest request = new CreateShiftRequest(MEMBER_ID, MONDAY,
                LocalTime.of(14, 0), LocalTime.of(20, 0), 60);

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("겹치는 스케줄");
    }

    @Test
    void createShift_rejectsWhenWeeklyLimitExceeded() {
        // 이번 주에 이미 50시간이 잡혀 있는 상태에서 6시간을 더 배정하면 주 52시간을 넘긴다.
        Shift existing = new Shift(adultMember, MONDAY, LocalTime.of(0, 0), LocalTime.of(0, 0).plusMinutes(1), 0);
        ReflectionTestUtils.setField(existing, "id", 99L);
        // workMinutes()는 startTime~endTime 기준이라 별도 스텁으로 50시간을 흉내내기 어려우므로,
        // 화~일 6개 스케줄(각 8시간20분)로 주간 합계를 50시간에 맞춘다.
        List<Shift> weekShifts = new java.util.ArrayList<>();
        LocalDate day = MONDAY;
        for (int i = 0; i < 6; i++) {
            Shift s = new Shift(adultMember, day, LocalTime.of(0, 0), LocalTime.of(8, 20), 0);
            ReflectionTestUtils.setField(s, "id", 100L + i);
            weekShifts.add(s);
            day = day.plusDays(1);
        }
        when(shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                MEMBER_ID, MONDAY, MONDAY.plusDays(6)))
                .thenReturn(weekShifts);

        CreateShiftRequest request = new CreateShiftRequest(MEMBER_ID, MONDAY.plusDays(6),
                LocalTime.of(9, 0), LocalTime.of(15, 0), 30); // 6시간 추가 -> 주 상한 초과

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("주");
    }

    @Test
    void createShift_rejectsMinorWorkingLateAtNight() {
        Store store = store(Map.of(), BreakPolicy.STATUTORY, false);
        User minor = new User("minor@albam.dev", "pw", "미성년", "010-1111-1111",
                LocalDate.of(2010, 1, 1), null); // 2026년 기준 16세
        StoreMember minorMember = new StoreMember(store, minor, MemberRole.STAFF, 10_000);
        ReflectionTestUtils.setField(minorMember, "id", MEMBER_ID);
        when(storeMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(minorMember));

        CreateShiftRequest request = new CreateShiftRequest(MEMBER_ID, MONDAY,
                LocalTime.of(18, 0), LocalTime.of(23, 0), 30); // 22시 이후까지 근무

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("연소근로자");
    }

    @Test
    void createShift_rejectsMinorWorkingOverSevenHoursADay() {
        Store store = store(Map.of(), BreakPolicy.STATUTORY, false);
        User minor = new User("minor@albam.dev", "pw", "미성년", "010-1111-1111",
                LocalDate.of(2010, 1, 1), null);
        StoreMember minorMember = new StoreMember(store, minor, MemberRole.STAFF, 10_000);
        ReflectionTestUtils.setField(minorMember, "id", MEMBER_ID);
        when(storeMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(minorMember));

        CreateShiftRequest request = new CreateShiftRequest(MEMBER_ID, MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0), 60); // 실근무 8시간, 연소자 상한(7h) 초과

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("7시간");
    }

    @Test
    void createShift_rejectsWhenMemberNotAvailableOnThatDay() {
        adultMember.changeAvailableDays(Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY));

        CreateShiftRequest request = new CreateShiftRequest(MEMBER_ID, MONDAY, // 월요일은 근무 가능 요일이 아님
                LocalTime.of(9, 0), LocalTime.of(18, 0), 60);

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("근무 가능");
    }

    @Test
    void createShift_rejectsWhenStoreClosedOnThatDay() {
        Store store = store(Map.of(DayOfWeek.MONDAY, new BusinessHour(null, null, true)),
                BreakPolicy.STATUTORY, false);
        User adult = new User("staff2@albam.dev", "pw", "직원2", "010-2222-2222",
                LocalDate.of(1990, 1, 1), null);
        StoreMember member = new StoreMember(store, adult, MemberRole.STAFF, 10_000);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(storeMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        CreateShiftRequest request = new CreateShiftRequest(MEMBER_ID, MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), 60);

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("휴무");
    }

    @Test
    void createShift_rejectsWhenOutsideBusinessHours() {
        Store store = store(Map.of(DayOfWeek.MONDAY, new BusinessHour(LocalTime.of(9, 0), LocalTime.of(18, 0), false)),
                BreakPolicy.STATUTORY, false);
        User adult = new User("staff3@albam.dev", "pw", "직원3", "010-3333-3333",
                LocalDate.of(1990, 1, 1), null);
        StoreMember member = new StoreMember(store, adult, MemberRole.STAFF, 10_000);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(storeMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        CreateShiftRequest request = new CreateShiftRequest(MEMBER_ID, MONDAY,
                LocalTime.of(7, 0), LocalTime.of(12, 0), 30); // 영업 시작(9시) 전부터 근무

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("영업시간");
    }

    @Test
    void createShift_smallBusinessSkipsWeeklyLimitForAdults() {
        Store store = store(Map.of(), BreakPolicy.STATUTORY, true); // 5인 미만 사업장
        User adult = new User("staff4@albam.dev", "pw", "직원4", "010-4444-4444",
                LocalDate.of(1990, 1, 1), null);
        StoreMember member = new StoreMember(store, adult, MemberRole.STAFF, 10_000);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(storeMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        CreateShiftRequest request = new CreateShiftRequest(MEMBER_ID, MONDAY.plusDays(6),
                LocalTime.of(9, 0), LocalTime.of(15, 0), 30);

        ShiftResponse response = shiftService.createShift(STORE_ID, USER_ID, request);

        // 5인 미만 사업장은 주간 상한 검증 자체를 건너뛰므로(레포지토리 조회조차 없음), 그냥 저장된다.
        assertThat(response).isNotNull();
        verify(shiftRepository, never()).findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                MEMBER_ID, MONDAY, MONDAY.plusDays(6));
    }

    private Store store(Map<DayOfWeek, BusinessHour> businessHours, BreakPolicy breakPolicy, boolean smallBusiness) {
        Store store = new Store("테스트 매장", "서울", null, null,
                new HashMap<>(businessHours), "ABC123", breakPolicy, smallBusiness);
        ReflectionTestUtils.setField(store, "id", STORE_ID);
        return store;
    }
}
