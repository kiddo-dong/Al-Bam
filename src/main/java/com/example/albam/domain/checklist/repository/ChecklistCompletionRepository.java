package com.example.albam.domain.checklist.repository;

import com.example.albam.domain.checklist.entity.ChecklistCompletion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface ChecklistCompletionRepository extends JpaRepository<ChecklistCompletion, Long> {

    @EntityGraph(attributePaths = {"checkedBy", "checkedBy.user"})
    List<ChecklistCompletion> findAllByItemStoreIdAndWorkDate(Long storeId, LocalDate workDate);

    Optional<ChecklistCompletion> findByItemIdAndWorkDate(Long itemId, LocalDate workDate);

    void deleteByItemId(Long itemId);

    /**
     * 체크를 문장 하나로 끝낸다. 조회한 뒤 저장하면 연타한 두 요청이 모두 "없음"을 보고 둘 다 INSERT해,
     * 두 번째가 유니크 제약에 걸려 사용자에게 오류로 보였다. 이미 있으면 아무것도 바꾸지 않는다 —
     * 처음 체크한 사람과 시각이 그대로 남는다.
     */
    @Modifying
    @Query(value = "INSERT INTO checklist_completions (item_id, work_date, checked_by, checked_at) "
            + "VALUES (:itemId, :workDate, :checkedBy, :checkedAt) "
            + "ON DUPLICATE KEY UPDATE id = id", nativeQuery = true)
    void insertIfAbsent(@Param("itemId") Long itemId, @Param("workDate") LocalDate workDate,
            @Param("checkedBy") Long checkedBy, @Param("checkedAt") LocalDateTime checkedAt);
}
