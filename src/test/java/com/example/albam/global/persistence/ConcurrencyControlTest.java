package com.example.albam.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.albam.domain.invite.entity.JoinRequest;
import com.example.albam.domain.invite.entity.JoinRequestStatus;
import com.example.albam.domain.invite.repository.JoinRequestRepository;
import com.example.albam.domain.shift.dto.ConfirmScheduleDraftRequest;
import com.example.albam.domain.shift.dto.ConfirmScheduleDraftResult;
import com.example.albam.domain.shift.dto.CreateShiftRequest;
import com.example.albam.domain.shift.dto.ScheduleDraftItem;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.shift.service.ScheduleAiService;
import com.example.albam.domain.shift.service.ShiftService;
import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.BusinessHour;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.repository.StoreRepository;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.InvalidRequestException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 동시에 들어온 요청을 실제 DB 트랜잭션으로 겨루게 해서, 락이 정말 결과를 한 줄로 세우는지 확인한다.
 *
 * <p>테스트 자체에 트랜잭션을 두지 않는다. 두면 서비스 호출이 모두 그 하나에 합류해 요청마다 따로
 * 커밋되는 실제 상황을 재현하지 못한다. 그래서 데이터를 직접 만들고 끝나면 지운다.
 */
@SpringBootTest
class ConcurrencyControlTest {

    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreMemberRepository storeMemberRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private JoinRequestRepository joinRequestRepository;
    @Autowired private ShiftService shiftService;
    @Autowired private ScheduleAiService scheduleAiService;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<Long> userIds = new ArrayList<>();
    private Long storeId;
    private Long ownerUserId;
    private Long workerMemberId;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = saveUser("owner-" + tag, LocalDate.of(1985, 1, 1));
        User worker = saveUser("worker-" + tag, LocalDate.of(1995, 1, 1));
        ownerUserId = owner.getId();

        Map<DayOfWeek, BusinessHour> hours = new HashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            hours.put(day, new BusinessHour(LocalTime.of(8, 0), LocalTime.of(23, 0), false));
        }
        Store store = storeRepository.save(new Store("동시성 매장", null, null, null, hours,
                "C" + tag.substring(0, 5).toUpperCase(), BreakPolicy.STATUTORY, false, null));
        storeId = store.getId();

        storeMemberRepository.save(new StoreMember(store, owner, MemberRole.OWNER, 0));
        StoreMember workerMember = new StoreMember(store, worker, MemberRole.STAFF, 10030);
        workerMember.changeAvailableDays(EnumSet.allOf(DayOfWeek.class));
        workerMemberId = storeMemberRepository.save(workerMember).getId();
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LocalDate today = LocalDate.now();
            shiftRepository.deleteAll(shiftRepository
                    .findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                            workerMemberId, today, today.plusDays(30)));
            storeRepository.purgeById(storeId);
        });
        userRepository.deleteAllById(userIds);
    }

    private User saveUser(String prefix, LocalDate birthDate) {
        String phone = "010-" + ThreadLocalRandom.current().nextInt(1000, 10000)
                + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
        User user = userRepository.save(new User(prefix + "@concurrency.test", "encoded", prefix, phone,
                birthDate, LocalDateTime.now()));
        userIds.add(user.getId());
        return user;
    }

    /**
     * 같은 멤버의 같은 시간대에 스케줄 여러 건이 동시에 들어오면 하나만 저장돼야 한다.
     *
     * <p>락이 없으면 모든 요청이 "겹치는 스케줄 없음"을 보고 전부 저장한다. 락만 있고 격리 수준이
     * REPEATABLE READ면 줄은 서지만 앞 요청이 넣은 스케줄이 옛 스냅샷에 안 보여서 역시 여러 건이 저장된다.
     */
    @Test
    void onlyOneOfManySimultaneousRequestsForTheSameSlotIsSaved() throws Exception {
        int attempts = 8;
        LocalDate day = LocalDate.now().plusDays(7);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    shiftService.createShift(storeId, ownerUserId, new CreateShiftRequest(
                            workerMemberId, day, LocalTime.of(10, 0), LocalTime.of(14, 0), null));
                    return true;
                } catch (InvalidRequestException rejectedAsOverlap) {
                    return false;
                }
            }));
        }
        start.countDown();

        int saved = 0;
        for (Future<Boolean> result : results) {
            if (result.get(60, TimeUnit.SECONDS)) {
                saved++;
            }
        }
        pool.shutdown();

        assertThat(saved).as("동시에 들어온 %d건 중 저장된 수", attempts).isEqualTo(1);
        assertThat(shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                workerMemberId, day, day)).hasSize(1);
    }

    /**
     * 초안 중 한 건이 규칙에 걸려도 나머지는 저장돼야 한다. 확정 전체가 하나의 트랜잭션이던 때는 거절된
     * 항목의 예외가 트랜잭션을 롤백 전용으로 만들어, 통과한 항목까지 사라지고 500이 났다.
     */
    @Test
    void aRejectedDraftItemDoesNotDiscardTheAcceptedOnes() {
        LocalDate day = LocalDate.now().plusDays(8);
        ScheduleDraftItem accepted = new ScheduleDraftItem(workerMemberId, "알바", day,
                LocalTime.of(10, 0), LocalTime.of(14, 0), null);
        ScheduleDraftItem overlapping = new ScheduleDraftItem(workerMemberId, "알바", day,
                LocalTime.of(12, 0), LocalTime.of(16, 0), null);

        ConfirmScheduleDraftResult result = scheduleAiService.confirmDraft(storeId, ownerUserId,
                new ConfirmScheduleDraftRequest(List.of(accepted, overlapping)));

        assertThat(result.created()).hasSize(1);
        assertThat(result.rejected()).hasSize(1);
        assertThat(shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                workerMemberId, day, day)).hasSize(1);
    }

    /**
     * 매니저 A가 대기 중인 신청을 보고 있는 사이 매니저 B가 먼저 거절하면, A의 승인은 실패해야 한다.
     * 버전이 없으면 A의 저장이 B의 결정을 조용히 덮어써 "승인"으로 남는다.
     */
    @Test
    void approvingARequestSomeoneElseAlreadyDecidedFailsInsteadOfOverwriting() {
        User applicant = saveUser("applicant-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.of(2000, 1, 1));
        Store store = storeRepository.findById(storeId).orElseThrow();
        Long requestId = joinRequestRepository.save(new JoinRequest(store, applicant)).getId();

        TransactionTemplate managerA = new TransactionTemplate(transactionManager);
        TransactionTemplate managerB = new TransactionTemplate(transactionManager);
        managerB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> managerA.executeWithoutResult(status -> {
            JoinRequest seenByA = joinRequestRepository.findById(requestId).orElseThrow();
            managerB.executeWithoutResult(inner ->
                    joinRequestRepository.findById(requestId).orElseThrow().reject());
            seenByA.approve(MemberRole.STAFF);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(joinRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(JoinRequestStatus.REJECTED);
    }
}
