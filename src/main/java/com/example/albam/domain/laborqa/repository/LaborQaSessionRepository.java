package com.example.albam.domain.laborqa.repository;

import com.example.albam.domain.laborqa.entity.LaborQaSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaborQaSessionRepository extends JpaRepository<LaborQaSession, Long> {

    List<LaborQaSession> findAllByUserIdOrderByIdDesc(Long userId);

    List<LaborQaSession> findAllByUserId(Long userId);
}
