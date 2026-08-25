package com.example.albam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.EmailTokenRepository;
import com.example.albam.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 인증하지 않고 방치된 계정을 정리하는 동작. 잘못 지우면 멀쩡한 사용자의 계정이 사라지므로,
 * 대상 조건과 삭제 순서를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class UnverifiedAccountCleanupServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailTokenRepository emailTokenRepository;

    @InjectMocks
    private UnverifiedAccountCleanupService cleanupService;

    private static final int RETENTION_DAYS = 7;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cleanupService, "retentionDays", RETENTION_DAYS);
    }

    private User abandonedUser(long id) {
        User user = new User("old@albam.dev", "encoded", "테스트", "010-1111-2222",
                LocalDate.of(1990, 1, 1), LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private void givenCandidates(List<User> users) {
        when(userRepository.findByProviderAndEmailVerifiedFalseAndCreatedAtBefore(
                eq(AuthProvider.LOCAL), any(LocalDateTime.class))).thenReturn(users);
    }

    @Test
    void deletesAccountsLeftUnverifiedPastTheRetentionPeriod() {
        List<User> abandoned = List.of(abandonedUser(1L), abandonedUser(2L));
        givenCandidates(abandoned);

        cleanupService.deleteExpiredUnverifiedAccounts();

        verify(userRepository).deleteAll(abandoned);
    }

    /** 인증 토큰이 계정을 참조하므로 먼저 지워야 삭제가 통과한다. */
    @Test
    void removesTheVerificationTokensOfTheDeletedAccounts() {
        givenCandidates(List.of(abandonedUser(1L), abandonedUser(2L)));

        cleanupService.deleteExpiredUnverifiedAccounts();

        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(emailTokenRepository).deleteByUserIdIn(ids.capture());
        assertThat(ids.getValue()).containsExactly(1L, 2L);
    }

    /** 지울 게 없을 때 굳이 삭제 쿼리를 보내지 않는다. */
    @Test
    void doesNothingWhenNoAccountQualifies() {
        givenCandidates(List.of());

        cleanupService.deleteExpiredUnverifiedAccounts();

        verify(userRepository, never()).deleteAll(any());
        verify(emailTokenRepository, never()).deleteByUserIdIn(anyCollection());
    }

    /** 기간은 실행 시점 기준으로 계산되어야 한다 — 고정 날짜를 쓰면 시간이 지나며 어긋난다. */
    @Test
    void looksBackByTheConfiguredRetentionPeriod() {
        givenCandidates(List.of());
        LocalDateTime before = LocalDateTime.now().minusDays(RETENTION_DAYS);

        cleanupService.deleteExpiredUnverifiedAccounts();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).findByProviderAndEmailVerifiedFalseAndCreatedAtBefore(
                eq(AuthProvider.LOCAL), threshold.capture());
        assertThat(threshold.getValue())
                .isBetween(before.minusMinutes(1), LocalDateTime.now().minusDays(RETENTION_DAYS).plusMinutes(1));
    }
}
