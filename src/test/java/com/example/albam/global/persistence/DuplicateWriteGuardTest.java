package com.example.albam.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.checklist.entity.ChecklistItem;
import com.example.albam.domain.checklist.entity.ChecklistType;
import com.example.albam.domain.checklist.repository.ChecklistCompletionRepository;
import com.example.albam.domain.checklist.repository.ChecklistItemRepository;
import com.example.albam.domain.notice.entity.Notice;
import com.example.albam.domain.notice.repository.NoticeReadRepository;
import com.example.albam.domain.notice.repository.NoticeRepository;
import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.repository.StoreRepository;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 같은 요청이 거의 동시에 두 번 올 때를 막는 장치들이 실제 DB에서 동작하는지 확인한다.
 *
 * <p>조회 후 저장하는 코드는 두 요청이 모두 "없음"을 보는 순간을 막지 못한다. 그래서 막는 일은 DB가
 * 한다 — 출근은 유니크 제약이, 체크리스트·공지 확인은 INSERT ... ON DUPLICATE KEY가. 이 테스트는
 * 조회를 건너뛰고 곧바로 두 번째 쓰기를 보내, 경합에서 조회가 뚫린 상황을 그대로 재현한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DuplicateWriteGuardTest {

    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreMemberRepository storeMemberRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private ChecklistItemRepository checklistItemRepository;
    @Autowired private ChecklistCompletionRepository checklistCompletionRepository;
    @Autowired private NoticeRepository noticeRepository;
    @Autowired private NoticeReadRepository noticeReadRepository;

    private Store store;
    private StoreMember member;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("guard@albam.dev", "알바", AuthProvider.LOCAL, "pid-g"));
        store = storeRepository.save(new Store("가게", null, null, null, new HashMap<>(), "GUARD1",
                BreakPolicy.STATUTORY, false, null));
        member = storeMemberRepository.save(new StoreMember(store, user, MemberRole.STAFF, 10030));
        entityManager.flush();
    }

    /** 경합에서 조회가 뚫려 두 번째 출근이 INSERT까지 가도, DB가 받아주지 않아야 한다. */
    @Test
    void aSecondOpenShiftForTheSameMemberIsRefusedByTheDatabase() {
        attendanceRepository.saveAndFlush(new Attendance(member, LocalDateTime.now()));

        assertThatThrownBy(() -> attendanceRepository.saveAndFlush(new Attendance(member, LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("uk_attendances_one_working"));
    }

    /** 퇴근한 기록은 몇 개든 쌓여야 한다 — 제약은 "출근 중"인 기록에만 걸린다. */
    @Test
    void finishedShiftsDoNotCountAgainstTheLimit() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        for (int i = 0; i < 3; i++) {
            Attendance done = new Attendance(member, start.plusDays(i));
            done.correctTimes(start.plusDays(i), start.plusDays(i).plusHours(4), 0);
            attendanceRepository.saveAndFlush(done);
        }

        attendanceRepository.saveAndFlush(new Attendance(member, LocalDateTime.now()));

        assertThat(attendanceRepository.count()).isGreaterThanOrEqualTo(4);
    }

    /** 연타한 두 번째 체크가 오류가 되지 않고, 행도 하나만 남아야 한다. */
    @Test
    void checkingTheSameItemTwiceLeavesOneRowAndNoError() {
        ChecklistItem item = checklistItemRepository.save(
                new ChecklistItem(store, ChecklistType.OPEN, "포스 시재 확인", 0));
        LocalDate today = LocalDate.now();

        checklistCompletionRepository.insertIfAbsent(item.getId(), today, member.getId(), LocalDateTime.now());
        checklistCompletionRepository.insertIfAbsent(item.getId(), today, member.getId(), LocalDateTime.now());
        entityManager.clear();

        assertThat(checklistCompletionRepository.findByItemIdAndWorkDate(item.getId(), today)).isPresent();
        assertThat(checklistCompletionRepository.findAllByItemStoreIdAndWorkDate(store.getId(), today))
                .hasSize(1);
    }

    @Test
    void markingTheSameNoticeReadTwiceLeavesOneRowAndNoError() {
        Notice notice = noticeRepository.save(new Notice(store, member, "공지", "내용"));

        noticeReadRepository.insertIfAbsent(notice.getId(), member.getId(), LocalDateTime.now());
        noticeReadRepository.insertIfAbsent(notice.getId(), member.getId(), LocalDateTime.now());

        assertThat(noticeReadRepository.countByNoticeId(notice.getId())).isEqualTo(1);
    }
}
