package com.example.albam.domain.store.repository;

import com.example.albam.domain.store.entity.Store;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}
