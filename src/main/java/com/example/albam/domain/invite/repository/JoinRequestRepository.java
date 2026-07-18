package com.example.albam.domain.invite.repository;

import com.example.albam.domain.invite.entity.JoinRequest;
import com.example.albam.domain.invite.entity.JoinRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    Optional<JoinRequest> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndUserIdAndStatus(Long storeId, Long userId, JoinRequestStatus status);

    List<JoinRequest> findAllByStoreIdAndStatusOrderByRequestedAtAsc(Long storeId, JoinRequestStatus status);

    List<JoinRequest> findAllByUserIdOrderByRequestedAtDesc(Long userId);

    long countByStoreIdAndStatus(Long storeId, JoinRequestStatus status);

    void deleteByUserId(Long userId);
}
