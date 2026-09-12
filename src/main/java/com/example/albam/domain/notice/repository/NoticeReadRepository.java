package com.example.albam.domain.notice.repository;

import com.example.albam.domain.notice.entity.NoticeRead;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeReadRepository extends JpaRepository<NoticeRead, Long> {

    boolean existsByNoticeIdAndStoreMemberId(Long noticeId, Long storeMemberId);

    long countByNoticeId(Long noticeId);

    long countByStoreMemberIdAndNoticeIdIn(Long storeMemberId, List<Long> noticeIds);

    List<NoticeRead> findAllByNoticeId(Long noticeId);

    void deleteByNoticeId(Long noticeId);

    /**
     * "확인했습니다"를 문장 하나로 끝낸다. 조회 후 저장하면 연타한 두 번째 요청이 유니크 제약에 걸려
     * 오류로 보였다. 이미 확인했으면 처음 확인한 시각을 그대로 둔다.
     */
    @Modifying
    @Query(value = "INSERT INTO notice_reads (notice_id, store_member_id, read_at) "
            + "VALUES (:noticeId, :storeMemberId, :readAt) "
            + "ON DUPLICATE KEY UPDATE id = id", nativeQuery = true)
    void insertIfAbsent(@Param("noticeId") Long noticeId, @Param("storeMemberId") Long storeMemberId,
            @Param("readAt") LocalDateTime readAt);

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
