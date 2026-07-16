package com.example.albam.domain.notice.repository;

import com.example.albam.domain.notice.entity.Notice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findAllByStoreIdOrderByCreatedAtDesc(Long storeId);

    Optional<Notice> findByIdAndStoreId(Long id, Long storeId);
}
