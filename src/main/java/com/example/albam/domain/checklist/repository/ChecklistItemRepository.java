package com.example.albam.domain.checklist.repository;

import com.example.albam.domain.checklist.entity.ChecklistItem;
import com.example.albam.domain.checklist.entity.ChecklistType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findAllByStoreIdOrderByTypeAscDisplayOrderAscIdAsc(Long storeId);

    Optional<ChecklistItem> findByIdAndStoreId(Long id, Long storeId);

    /**
     * 그 매장·구분에서 지금까지 쓴 가장 큰 순번. 일괄 등록이 0부터 다시 매기면 이미 있는 항목과
     * 순번이 겹쳐 순서가 섞이므로, 뒤에 이어 붙이려고 조회한다. 항목이 없으면 비어 있다.
     */
    @Query("select max(i.displayOrder) from ChecklistItem i where i.store.id = :storeId and i.type = :type")
    Optional<Integer> findMaxDisplayOrder(@Param("storeId") Long storeId, @Param("type") ChecklistType type);
}
