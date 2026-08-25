package com.example.albam.domain.user.repository;

import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    /** 인증을 끝내지 않은 채 기간이 지난 로컬 계정. 자동 정리 대상이다. */
    List<User> findByProviderAndEmailVerifiedFalseAndCreatedAtBefore(
            AuthProvider provider, LocalDateTime threshold);
}
