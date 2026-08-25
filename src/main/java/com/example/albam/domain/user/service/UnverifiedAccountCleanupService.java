package com.example.albam.domain.user.service;

import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.EmailTokenRepository;
import com.example.albam.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 메일을 열지 않은 채 기간이 지난 로컬 계정을 지운다.
 *
 * <p>이런 행은 가입이 끝나지 않았는데도 이메일과 전화번호를 계속 붙잡고 있다. 같은 주소로 다시
 * 가입하면 덮어쓰이므로 사용자가 막히지는 않지만, 아무도 돌아오지 않는 행은 그대로 쌓인다.
 *
 * <p>소셜 계정은 대상이 아니다. provider가 이미 이메일을 검증했으므로 emailVerified가 처음부터
 * true라 애초에 이 상태가 될 수 없다.
 *
 * <p>지워도 안전한 근거는 <b>미인증 로컬 계정은 로그인할 수 없다</b>는 것이다. 로그인을 못 하니
 * 매장·근태·Q&A 같은 데이터를 만들 수 없고, 남는 연관 데이터는 가입 때 발급한 인증 토큰뿐이다.
 * 혹시 그 전제가 깨지면 외래키 제약이 삭제를 막고 아래 로그가 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnverifiedAccountCleanupService {

    private final UserRepository userRepository;
    private final EmailTokenRepository emailTokenRepository;

    /**
     * 인증까지 기다려주는 기간. 인증 링크 자체는 24시간이지만, 그보다 넉넉히 두어 뒤늦게
     * 재발송을 요청하는 사용자가 계정을 잃지 않게 한다.
     */
    @Value("${app.signup.unverified-retention-days:7}")
    private int retentionDays;

    /** 매일 새벽 4시 30분. 리프레시 토큰 정리와 겹치지 않게 시간을 띄운다. */
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void deleteExpiredUnverifiedAccounts() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        List<User> abandoned = userRepository.findByProviderAndEmailVerifiedFalseAndCreatedAtBefore(
                AuthProvider.LOCAL, threshold);
        if (abandoned.isEmpty()) {
            return;
        }
        List<Long> ids = abandoned.stream().map(User::getId).toList();
        // 인증 토큰이 계정을 참조하므로 먼저 지운다.
        emailTokenRepository.deleteByUserIdIn(ids);
        userRepository.deleteAll(abandoned);
        log.info("인증하지 않은 채 {}일이 지난 계정 {}건 삭제", retentionDays, ids.size());
    }
}
