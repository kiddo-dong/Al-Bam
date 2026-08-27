package com.example.albam.domain.shift.repository;

import com.example.albam.domain.shift.entity.ShiftTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, Long> {

    List<ShiftTemplate> findAllByStoreIdOrderByDisplayOrderAscIdAsc(Long storeId);

    Optional<ShiftTemplate> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndName(Long storeId, String name);

    boolean existsByStoreIdAndNameAndIdNot(Long storeId, String name, Long id);

    /** 일괄 등록을 기존 목록 뒤에 이어 붙이기 위한 현재 최대 순번. 템플릿이 없으면 비어 있다. */
    @Query("select max(t.displayOrder) from ShiftTemplate t where t.store.id = :storeId")
    Optional<Integer> findMaxDisplayOrder(@Param("storeId") Long storeId);
}
