package com.example.albam.domain.invite.repository;

import com.example.albam.domain.invite.entity.JoinRequest;
import com.example.albam.domain.invite.entity.JoinRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    Optional<JoinRequest> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndUserIdAndStatus(Long storeId, Long userId, JoinRequestStatus status);

    /** 사장이 보는 대기 목록 — 신청자 이름·이메일을 함께 보여준다. */
    @EntityGraph(attributePaths = {"user", "store"})
    List<JoinRequest> findAllByStoreIdAndStatusOrderByRequestedAtAsc(Long storeId, JoinRequestStatus status);

    /** 알바생이 보는 내 신청 목록 — 어느 매장에 넣었는지 매장 이름을 함께 보여준다. */
    @EntityGraph(attributePaths = {"user", "store"})
    List<JoinRequest> findAllByUserIdOrderByRequestedAtDesc(Long userId);

    long countByStoreIdAndStatus(Long storeId, JoinRequestStatus status);

    void deleteByUserId(Long userId);
}
