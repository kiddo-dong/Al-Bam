package com.example.albam.domain.handover.repository;

import com.example.albam.domain.handover.entity.HandoverNote;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverNoteRepository extends JpaRepository<HandoverNote, Long> {

    List<HandoverNote> findAllByStoreIdAndWorkDateBetweenOrderByCreatedAtDesc(
            Long storeId, LocalDate from, LocalDate to);

    Optional<HandoverNote> findByIdAndStoreId(Long id, Long storeId);
}
