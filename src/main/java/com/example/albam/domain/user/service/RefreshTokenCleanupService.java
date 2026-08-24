package com.example.albam.domain.user.service;

import com.example.albam.domain.user.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 리프레시 토큰 행을 주기적으로 지운다.
 *
 * <p>만료 검증 자체는 JWT와 조회 시점에서 이미 이뤄지므로 보안상 필요한 작업은 아니다. 로그인할 때마다
 * 행이 하나씩 쌓이는데 지우는 경로가 로그아웃뿐이라, 두지 않으면 테이블이 단조 증가한다.
 *
 * <p>서버가 여러 대가 되면 각 대에서 같은 작업이 겹쳐 돌지만, 삭제는 여러 번 실행해도 결과가 같아
 * 문제되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    /** 매일 새벽 4시. 사용자가 가장 적은 시간대에 돌려 잠금 경합을 피한다. */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        long deleted = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("만료된 리프레시 토큰 {}건 삭제", deleted);
        }
    }
}
