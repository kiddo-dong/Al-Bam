package com.example.albam.domain.store.repository;

import com.example.albam.domain.store.entity.Store;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    /**
     * 유예기간이 지난, 소프트 삭제된 매장의 id 목록. Store에 걸린 {@code @SQLRestriction}은
     * HQL/파생 쿼리에만 적용되고 네이티브 쿼리에는 적용되지 않으므로, 지워진 행을 보려면 이 방식이
     * 필요하다.
     */
    @Query(value = "SELECT id FROM stores WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff", nativeQuery = true)
    List<Long> findIdsSoftDeletedBefore(LocalDateTime cutoff);

    /**
     * id로 실제 행을 지운다. {@code deleteById}는 내부적으로 조회 후 삭제라 {@code @SQLRestriction}에
     * 걸려 소프트 삭제된 행을 못 찾는다. 자식 테이블은 마이그레이션에서 건 ON DELETE CASCADE가 정리한다.
     */
    @Modifying
    @Query(value = "DELETE FROM stores WHERE id = :id", nativeQuery = true)
    void purgeById(Long id);
}
