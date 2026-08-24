package com.example.albam.domain.user.repository;

import com.example.albam.domain.user.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 비밀번호 변경·재사용 탐지처럼 그 사용자의 모든 세션을 끊어야 할 때 쓴다. 끊은 개수를 돌려준다. */
    long deleteByUserId(Long userId);

    /** 만료된 행 정리. 검증에는 영향이 없고 테이블이 무한히 커지는 것만 막는다. */
    long deleteByExpiresAtBefore(LocalDateTime threshold);
}
