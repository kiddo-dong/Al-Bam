package com.example.albam.domain.handover.repository;

import com.example.albam.domain.handover.entity.HandoverNote;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverNoteRepository extends JpaRepository<HandoverNote, Long> {

    /**
     * 노트마다 작성자 이름을 보여준다. author(StoreMember)와 그 user까지 두 단계를 타므로, 함께 읽지
     * 않으면 노트 한 건당 조회가 두 번씩 따라붙는다.
     */
    @EntityGraph(attributePaths = {"author", "author.user"})
    List<HandoverNote> findAllByStoreIdAndWorkDateBetweenOrderByCreatedAtDesc(
            Long storeId, LocalDate from, LocalDate to);

    Optional<HandoverNote> findByIdAndStoreId(Long id, Long storeId);
}
