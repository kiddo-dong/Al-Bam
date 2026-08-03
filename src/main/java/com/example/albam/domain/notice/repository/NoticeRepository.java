package com.example.albam.domain.notice.repository;

import com.example.albam.domain.notice.entity.Notice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    @EntityGraph(attributePaths = {"author", "author.user"})
    List<Notice> findAllByStoreIdOrderByCreatedAtDesc(Long storeId);

    Optional<Notice> findByIdAndStoreId(Long id, Long storeId);

    @Query("select n.id from Notice n where n.store.id = :storeId")
    List<Long> findIdsByStoreId(@Param("storeId") Long storeId);
}
