package com.example.albam.domain.notice.repository;

import com.example.albam.domain.notice.entity.NoticeRead;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeReadRepository extends JpaRepository<NoticeRead, Long> {

    boolean existsByNoticeIdAndStoreMemberId(Long noticeId, Long storeMemberId);

    long countByNoticeId(Long noticeId);

    long countByStoreMemberIdAndNoticeIdIn(Long storeMemberId, List<Long> noticeIds);

    List<NoticeRead> findAllByNoticeId(Long noticeId);

    void deleteByNoticeId(Long noticeId);

    @Query("select r.notice.id as noticeId, count(r) as readCount from NoticeRead r "
            + "where r.notice.id in :noticeIds group by r.notice.id")
    List<NoticeReadCount> countByNoticeIdIn(@Param("noticeIds") List<Long> noticeIds);

    @Query("select r.notice.id from NoticeRead r "
            + "where r.storeMember.id = :storeMemberId and r.notice.id in :noticeIds")
    List<Long> findReadNoticeIds(@Param("storeMemberId") Long storeMemberId, @Param("noticeIds") List<Long> noticeIds);

    interface NoticeReadCount {
        Long getNoticeId();

        long getReadCount();
    }
}
