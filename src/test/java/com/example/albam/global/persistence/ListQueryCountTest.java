package com.example.albam.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.albam.domain.handover.dto.HandoverNoteResponse;
import com.example.albam.domain.handover.entity.HandoverNote;
import com.example.albam.domain.handover.repository.HandoverNoteRepository;
import com.example.albam.domain.store.dto.MyStoreResponse;
import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.BusinessHour;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.repository.StoreRepository;
import com.example.albam.domain.storemember.dto.StoreMemberResponse;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

/**
 * 목록 조회가 건수에 비례해 쿼리를 늘리지 않는지 실제로 세어 본다.
 *
 * <p>N+1은 코드를 읽어서는 잘 드러나지 않고, 데이터가 한두 건인 개발 중에는 체감도 되지 않는다.
 * 그래서 눈으로 확인하는 대신 Hibernate가 실제로 보낸 쿼리 수를 센다. 데이터 건수를 늘려도 수가
 * 그대로여야 통과하므로, 나중에 누가 @EntityGraph를 지우면 여기서 걸린다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ListQueryCountTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private StoreMemberRepository storeMemberRepository;
    @Autowired
    private HandoverNoteRepository handoverNoteRepository;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
    }

    /** 영속성 컨텍스트에 남은 것을 지워야, 이미 읽어둔 것을 다시 안 읽는 착시를 피할 수 있다. */
    private void startCounting() {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
    }

    private long queryCount() {
        return statistics.getPrepareStatementCount();
    }

    private User user(String email) {
        return userRepository.save(new User(email, "이름" + email, AuthProvider.LOCAL, "pid-" + email));
    }

    private Store store(String name, String inviteCode) {
        Map<DayOfWeek, BusinessHour> hours = new HashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            hours.put(day, new BusinessHour(LocalTime.of(9, 0), LocalTime.of(21, 0), false));
        }
        return storeRepository.save(new Store(name, "주소", null, null, hours, inviteCode,
                BreakPolicy.STATUTORY, false, null));
    }

    /**
     * 내 매장 목록. 매장마다 Store와 영업시간을 따로 읽으면 매장 수의 두 배로 늘어난다.
     * 로그인 직후 부르는 화면이라 가장 눈에 띈다.
     */
    @Test
    void listingMyStoresDoesNotGrowWithTheNumberOfStores() {
        User me = user("me@albam.dev");
        for (int i = 0; i < 5; i++) {
            storeMemberRepository.save(
                    new StoreMember(store("매장" + i, "INV00" + i), me, MemberRole.OWNER, 0));
        }
        startCounting();

        List<MyStoreResponse> stores = storeMemberRepository
                .findAllByUserIdAndStatus(me.getId(), MemberStatus.ACTIVE).stream()
                .map(MyStoreResponse::from)
                .toList();

        assertThat(stores).hasSize(5);
        assertThat(stores).allSatisfy(s -> assertThat(s.store().businessHours()).hasSize(7));
        // 멤버+매장 한 번, 영업시간을 IN으로 모아 한 번. 매장 수와 무관해야 한다.
        assertThat(queryCount())
                .as("매장 5개를 읽는 데 보낸 쿼리 수 (매장 수에 비례하면 N+1)")
                .isLessThanOrEqualTo(3);
    }

    /** 멤버 목록은 이름을 함께 보여주므로 user를 매번 따로 읽으면 멤버 수만큼 늘어난다. */
    @Test
    void listingStoreMembersDoesNotGrowWithTheNumberOfMembers() {
        Store store = store("가게", "INVAAA");
        for (int i = 0; i < 5; i++) {
            storeMemberRepository.save(
                    new StoreMember(store, user("member" + i + "@albam.dev"), MemberRole.STAFF, 10000));
        }
        startCounting();

        List<StoreMemberResponse> members = storeMemberRepository.findAllByStoreId(store.getId()).stream()
                .map(StoreMemberResponse::from)
                .toList();

        assertThat(members).hasSize(5);
        assertThat(members).allSatisfy(m -> assertThat(m.userName()).isNotBlank());
        assertThat(queryCount())
                .as("멤버 5명을 읽는 데 보낸 쿼리 수 (멤버 수에 비례하면 N+1)")
                .isLessThanOrEqualTo(2);
    }

    /** 인수인계는 note → author → user로 두 단계를 타서, 안 고치면 노트당 두 번씩 붙는다. */
    @Test
    void listingHandoverNotesDoesNotGrowWithTheNumberOfNotes() {
        Store store = store("가게", "INVBBB");
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 5; i++) {
            StoreMember author = storeMemberRepository.save(
                    new StoreMember(store, user("writer" + i + "@albam.dev"), MemberRole.STAFF, 10000));
            handoverNoteRepository.save(new HandoverNote(store, author, "인수인계 " + i, today));
        }
        startCounting();

        List<HandoverNoteResponse> notes = handoverNoteRepository
                .findAllByStoreIdAndWorkDateBetweenOrderByCreatedAtDesc(store.getId(), today, today).stream()
                .map(HandoverNoteResponse::from)
                .toList();

        assertThat(notes).hasSize(5);
        assertThat(notes).allSatisfy(n -> assertThat(n.authorName()).isNotBlank());
        assertThat(queryCount())
                .as("노트 5건을 읽는 데 보낸 쿼리 수 (노트 수에 비례하면 N+1)")
                .isLessThanOrEqualTo(2);
    }
}
