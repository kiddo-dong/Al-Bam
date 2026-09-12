package com.example.albam.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.repository.StoreRepository;
import com.example.albam.domain.storemember.dto.StoreMemberResponse;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Set;
import org.hibernate.collection.spi.PersistentCollection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * 응답 DTO에 Hibernate의 지연 컬렉션이 실려 나가지 않는지 확인한다.
 *
 * <p>OSIV를 끈 뒤로 JSON 변환은 트랜잭션이 끝난 다음에 일어난다. 그때 응답 안에 지연 컬렉션이 남아
 * 있으면 초기화하려다 LazyInitializationException이 나고 500이 된다. 멤버 목록이 실제로 그렇게
 * 깨졌었다. 테스트 안에서는 세션이 살아 있어 예외가 나지 않으므로, 대신 담긴 것이 지연 컬렉션인지
 * 아닌지를 본다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ResponseDetachmentTest {

    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreMemberRepository storeMemberRepository;

    @Test
    void memberResponseCarriesAPlainCopyOfTheAvailableDays() {
        User user = userRepository.save(new User("detach@albam.dev", "알바", AuthProvider.LOCAL, "pid-d"));
        Store store = storeRepository.save(new Store("가게", null, null, null, new HashMap<>(), "DETACH",
                BreakPolicy.STATUTORY, false, null));
        StoreMember member = storeMemberRepository.save(new StoreMember(store, user, MemberRole.STAFF, 10030));
        member.changeAvailableDays(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        entityManager.flush();
        entityManager.clear();

        StoreMember reloaded = storeMemberRepository.findById(member.getId()).orElseThrow();
        StoreMemberResponse response = StoreMemberResponse.from(reloaded, null);

        assertThat(response.availableDays())
                .isNotInstanceOf(PersistentCollection.class)
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }
}
