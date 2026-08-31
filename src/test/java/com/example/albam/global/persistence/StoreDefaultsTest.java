package com.example.albam.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.entity.StorePlan;
import com.example.albam.domain.store.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * 매장을 만들 때 자동으로 정해지는 값들. 엔티티의 기본값과 실제 컬럼이 어긋나면 저장 후 다시 읽었을
 * 때 드러나므로, 메모리의 객체가 아니라 DB를 거쳐 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StoreDefaultsTest {

    @Autowired private EntityManager entityManager;
    @Autowired private StoreRepository storeRepository;

    /**
     * 요금제는 매장을 만드는 쪽이 정하지 않는다. 생성 요청에도 없고 기본 등급으로만 시작하므로,
     * 결제를 거치지 않고 상위 등급으로 만들어지는 경로가 없어야 한다.
     */
    @Test
    void aNewStoreStartsOnTheBasicPlan() {
        Store saved = storeRepository.save(new Store("새 매장", null, null, null, new HashMap<>(),
                "PLAN01", BreakPolicy.STATUTORY, false, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(storeRepository.findById(saved.getId()))
                .get()
                .extracting(Store::getPlan)
                .isEqualTo(StorePlan.BASIC);
    }
}
