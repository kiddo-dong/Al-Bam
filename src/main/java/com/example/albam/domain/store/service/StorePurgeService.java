package com.example.albam.domain.store.service;

import com.example.albam.domain.store.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소프트 삭제된 지 유예기간이 지난 매장을 실제로 지운다.
 *
 * <p>즉시 삭제 대신 소프트 삭제를 쓰는 이유(오너의 실수로부터 근태·급여 기록을 지킴)는
 * {@link StoreService#deleteStore}에 적혀 있다. 이 배치는 그 유예기간이 끝난 뒤 뒷정리를 한다 —
 * 자식 테이블은 V6 마이그레이션에서 건 ON DELETE CASCADE가 알아서 정리하므로, 여기서는 stores
 * 행만 지우면 된다.
 *
 * <p>복구 API는 아직 없다. 유예기간 중 되돌려야 하면 지금은 DB에서 deleted_at을 직접 NULL로
 * 바꿔야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorePurgeService {

    private static final int GRACE_PERIOD_DAYS = 90;

    private final StoreRepository storeRepository;

    /** 매일 새벽 4시 30분. 리프레시 토큰 정리(4시)와 겹치지 않게 살짝 늦춘다. */
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purgeExpiredStores() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS);
        List<Long> storeIds = storeRepository.findIdsSoftDeletedBefore(cutoff);
        for (Long storeId : storeIds) {
            storeRepository.purgeById(storeId);
        }
        if (!storeIds.isEmpty()) {
            log.info("유예기간 지난 매장 {}건 완전 삭제: {}", storeIds.size(), storeIds);
        }
    }
}
