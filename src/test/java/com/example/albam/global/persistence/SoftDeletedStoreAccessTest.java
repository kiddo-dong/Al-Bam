package com.example.albam.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.repository.StoreRepository;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * 소프트 삭제된 매장이 조회에서 실제로 빠지는지 확인한다.
 *
 * <p>Store에 걸린 @SQLRestriction은 Store를 조회할 때만 적용된다. StoreMember는 그 제한을 받지
 * 않아서, store_id 컬럼만 보는 쿼리는 지워진 매장의 멤버도 그대로 찾아낸다. 권한 검사가 그 쿼리를
 * 쓰고 있었기 때문에 매장을 지워도 공지·체크리스트 같은 것이 계속 등록됐다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SoftDeletedStoreAccessTest {

    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreMemberRepository storeMemberRepository;

    private User owner;
    private Store store;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("owner@albam.dev", "사장", AuthProvider.LOCAL, "pid-1"));
        store = storeRepository.save(new Store("지울 매장", null, null, null, new HashMap<>(),
                "DELME1", BreakPolicy.STATUTORY, false, null));
        storeMemberRepository.save(new StoreMember(store, owner, MemberRole.OWNER, 0));
    }

    private void softDeleteAndReload() {
        store.softDelete();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void theStoreItselfIsHiddenOnceDeleted() {
        softDeleteAndReload();

        assertThat(storeRepository.findById(store.getId())).isEmpty();
    }

    /** 권한 검사가 쓰는 조회. 여기가 뚫려 있으면 그 뒤의 모든 매장 기능이 함께 열린다. */
    @Test
    void membershipIsNoLongerFoundOnceTheStoreIsDeleted() {
        softDeleteAndReload();

        assertThat(storeMemberRepository.findByStoreIdAndUserId(store.getId(), owner.getId())).isEmpty();
    }

    @Test
    void membershipIsFoundWhileTheStoreIsAlive() {
        assertThat(storeMemberRepository.findByStoreIdAndUserId(store.getId(), owner.getId())).isPresent();
    }

    /** 내 매장 목록에서도 빠져야 한다. */
    @Test
    void theStoreDropsOutOfMyStoreList() {
        softDeleteAndReload();

        assertThat(storeMemberRepository.findAllByUserIdAndStatus(owner.getId(), MemberStatus.ACTIVE))
                .isEmpty();
    }

    /** 초대 코드로 다시 들어오는 경로도 닫혀 있어야 한다. */
    @Test
    void theInviteCodeStopsResolving() {
        softDeleteAndReload();

        assertThat(storeRepository.findByInviteCode("DELME1")).isEmpty();
    }
}
