package com.example.albam.domain.laborqa.repository;

import com.example.albam.domain.laborqa.entity.LaborQaMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaborQaMessageRepository extends JpaRepository<LaborQaMessage, Long> {

    List<LaborQaMessage> findAllBySessionIdOrderByIdAsc(Long sessionId);

    List<LaborQaMessage> findTop10BySessionIdOrderByIdDesc(Long sessionId);

    void deleteBySessionId(Long sessionId);

    void deleteBySessionUserId(Long userId);
}
