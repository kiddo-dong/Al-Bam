package com.example.albam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.albam.domain.user.dto.IssuedTokens;
import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.RefreshToken;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.EmailTokenRepository;
import com.example.albam.domain.user.repository.RefreshTokenRepository;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.mail.MailService;
import com.example.albam.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 리프레시 토큰이 상태를 갖게 된 뒤의 동작을 검증한다. 핵심은 "서명이 유효해도 저장된 기록이 없으면
 * 거부된다"는 것으로, 이게 성립해야 로그아웃·비밀번호 변경이 실제 폐기가 된다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTokenTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailTokenRepository emailTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private MailService mailService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AuthService authService;

    private static final String PRESENTED_TOKEN = "presented.refresh.token";
    private static final String NEW_REFRESH_TOKEN = "new.refresh.token";
    private static final String NEW_ACCESS_TOKEN = "new.access.token";

    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, emailTokenRepository, refreshTokenRepository,
                passwordEncoder, authenticationManager, jwtTokenProvider, List.of(), mailService,
                transactionManager);
        user = new User("test@albam.dev", "테스트", AuthProvider.LOCAL, "provider-id");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    private void givenTokenIsStructurallyValid() {
        when(jwtTokenProvider.validateToken(PRESENTED_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken(PRESENTED_TOKEN)).thenReturn(true);
    }

    private void givenNewTokensAreIssued() {
        when(jwtTokenProvider.createAccessToken(user.getId(), user.getEmail())).thenReturn(NEW_ACCESS_TOKEN);
        when(jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail())).thenReturn(NEW_REFRESH_TOKEN);
        when(jwtTokenProvider.getExpiresAt(NEW_REFRESH_TOKEN)).thenReturn(LocalDateTime.now().plusDays(14));
    }

    @Test
    void refreshIssuesNewTokensWhenTheStoredRecordExists() {
        givenTokenIsStructurallyValid();
        givenNewTokensAreIssued();
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(new RefreshToken(user, "hash", LocalDateTime.now().plusDays(14))));

        IssuedTokens issued = authService.refresh(PRESENTED_TOKEN);

        assertThat(issued.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
        assertThat(issued.refreshToken()).isEqualTo(NEW_REFRESH_TOKEN);
    }

    /** 폐기의 핵심. 서명과 만료가 멀쩡해도 기록이 없으면 통과시키지 않는다. */
    @Test
    void refreshIsRejectedWhenTheTokenWasRevoked() {
        givenTokenIsStructurallyValid();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
                .isInstanceOf(InvalidRequestException.class);
        verify(jwtTokenProvider, never()).createAccessToken(any(), anyString());
    }

    /** 회전: 쓴 토큰은 지워지므로 같은 토큰으로 두 번 재발급받을 수 없다. */
    @Test
    void refreshDeletesTheUsedToken() {
        givenTokenIsStructurallyValid();
        givenNewTokensAreIssued();
        RefreshToken stored = new RefreshToken(user, "hash", LocalDateTime.now().plusDays(14));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        authService.refresh(PRESENTED_TOKEN);

        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    void refreshIsRejectedWhenTheSignatureIsInvalid() {
        when(jwtTokenProvider.validateToken(PRESENTED_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
                .isInstanceOf(InvalidRequestException.class);
        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    /** 액세스 토큰을 리프레시 자리에 넣어도 통과하면 안 된다. */
    @Test
    void refreshIsRejectedWhenTheTokenIsNotARefreshToken() {
        when(jwtTokenProvider.validateToken(PRESENTED_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken(PRESENTED_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
                .isInstanceOf(InvalidRequestException.class);
        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void logoutDeletesTheStoredToken() {
        RefreshToken stored = new RefreshToken(user, "hash", LocalDateTime.now().plusDays(14));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        authService.logout(PRESENTED_TOKEN);

        verify(refreshTokenRepository).delete(stored);
    }

    /** 이미 로그아웃한 뒤 다시 눌러도 실패하면 안 된다. */
    @Test
    void logoutSucceedsWhenTheTokenIsAlreadyGone() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout(PRESENTED_TOKEN);

        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void logoutIsIgnoredWhenNoCookieWasSent() {
        authService.logout(null);

        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    /** 같은 토큰 원문은 항상 같은 해시로 조회돼야 조회가 성립한다. */
    @Test
    void theSameTokenAlwaysResolvesToTheSameStoredHash() {
        givenTokenIsStructurallyValid();
        givenNewTokensAreIssued();
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(new RefreshToken(user, "hash", LocalDateTime.now().plusDays(14))));

        authService.refresh(PRESENTED_TOKEN);
        authService.refresh(PRESENTED_TOKEN);

        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).findByTokenHash(hashes.capture());
        assertThat(hashes.getAllValues().get(0)).isEqualTo(hashes.getAllValues().get(1));
    }

    /** 원문을 그대로 저장하면 DB 유출 시 바로 쓸 수 있는 토큰이 함께 넘어간다. */
    @Test
    void theStoredValueIsAHashRatherThanTheTokenItself() {
        givenTokenIsStructurallyValid();
        givenNewTokensAreIssued();
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(new RefreshToken(user, "hash", LocalDateTime.now().plusDays(14))));

        authService.refresh(PRESENTED_TOKEN);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isNotEqualTo(NEW_REFRESH_TOKEN)
                .hasSize(64);
    }
}
