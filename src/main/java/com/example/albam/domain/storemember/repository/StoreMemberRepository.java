package com.example.albam.domain.storemember.repository;

import com.example.albam.domain.storemember.entity.StoreMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMemberRepository extends JpaRepository<StoreMember, Long> {

    Optional<StoreMember> findByStoreIdAndUserId(Long storeId, Long userId);

    List<StoreMember> findAllByStoreId(Long storeId);

    List<StoreMember> findAllByUserId(Long userId);

    boolean existsByStoreIdAndUserId(Long storeId, Long userId);
}
