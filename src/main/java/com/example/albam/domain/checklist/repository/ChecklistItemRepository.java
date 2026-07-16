package com.example.albam.domain.checklist.repository;

import com.example.albam.domain.checklist.entity.ChecklistItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findAllByStoreIdOrderByTypeAscDisplayOrderAscIdAsc(Long storeId);

    Optional<ChecklistItem> findByIdAndStoreId(Long id, Long storeId);
}
