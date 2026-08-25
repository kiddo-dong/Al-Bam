package com.example.albam.domain.user.repository;

import com.example.albam.domain.user.entity.EmailToken;
import com.example.albam.domain.user.entity.EmailTokenType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenAndType(String token, EmailTokenType type);

    void deleteByUserId(Long userId);

    /** 계정 자동 정리에서 여러 사용자의 토큰을 한 번에 지운다. */
    void deleteByUserIdIn(Collection<Long> userIds);
}
