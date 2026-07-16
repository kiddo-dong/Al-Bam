package com.example.albam.domain.notice.repository;

import com.example.albam.domain.notice.entity.NoticeRead;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeReadRepository extends JpaRepository<NoticeRead, Long> {

    boolean existsByNoticeIdAndStoreMemberId(Long noticeId, Long storeMemberId);

    long countByNoticeId(Long noticeId);

    List<NoticeRead> findAllByNoticeId(Long noticeId);

    void deleteByNoticeId(Long noticeId);
}
