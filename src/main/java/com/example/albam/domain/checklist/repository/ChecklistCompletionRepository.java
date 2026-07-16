package com.example.albam.domain.checklist.repository;

import com.example.albam.domain.checklist.entity.ChecklistCompletion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistCompletionRepository extends JpaRepository<ChecklistCompletion, Long> {

    List<ChecklistCompletion> findAllByItemStoreIdAndWorkDate(Long storeId, LocalDate workDate);

    Optional<ChecklistCompletion> findByItemIdAndWorkDate(Long itemId, LocalDate workDate);

    void deleteByItemId(Long itemId);
}
