package com.example.albam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.albam.domain.user.dto.LoginRequest;
import com.example.albam.domain.user.dto.SignupRequest;
import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.EmailToken;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.EmailTokenRepository;
import com.example.albam.domain.user.repository.RefreshTokenRepository;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.ConflictException;
import com.example.albam.global.exception.EmailNotVerifiedException;
import com.example.albam.global.mail.MailService;
import com.example.albam.global.security.JwtTokenProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 가입이 중간에 끊긴 계정을 어떻게 다루는지 검증한다. 인증하지 않은 계정이 이메일을 붙잡고 있으면
 * 사용자는 재가입도 로그인도 못 하는 막다른 상태에 빠진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceSignupTest {

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

    private static final String EMAIL = "test@albam.dev";
    private static final String PHONE = "010-1234-5678";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, emailTokenRepository, refreshTokenRepository,
                passwordEncoder, authenticationManager, jwtTokenProvider, List.of(), mailService,
                transactionManager);
        ReflectionTestUtils.setField(authService, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:5173");

        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(emailTokenRepository.save(any(EmailToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        }).when(userRepository).save(any(User.class));
    }

    private SignupRequest signupRequest() {
        return new SignupRequest(EMAIL, "Test1234!", "Test1234!", "테스트", PHONE,
                LocalDate.of(1990, 1, 1), true);
    }

    private User unverifiedLocalUser() {
        User user = new User(EMAIL, "old-encoded", "예전이름", "010-0000-0000",
                LocalDate.of(1980, 1, 1), LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private User verifiedLocalUser() {
        User user = unverifiedLocalUser();
        user.markEmailVerified();
        return user;
    }

    @Test
    void signupCreatesTheAccountWhenTheEmailIsFree() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.existsByPhone(PHONE)).thenReturn(false);

        assertThatCode(() -> authService.signup(signupRequest())).doesNotThrowAnyException();

        verify(userRepository).save(any(User.class));
    }

    /**
     * 인증 전 계정은 소유자가 증명된 적이 없다. 그대로 거절하면 주소를 잘못 적었거나 메일을 놓친
     * 사람은 그 이메일로 영영 가입할 수 없다.
     */
    @Test
    void signupOverwritesAnUnverifiedAccountWithTheSameEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedLocalUser()));
        when(userRepository.existsByPhoneAndIdNot(PHONE, 1L)).thenReturn(false);

        assertThatCode(() -> authService.signup(signupRequest())).doesNotThrowAnyException();

        // 새 행을 만들지 않는다 — 이메일이 unique라 같은 행을 갱신해야 한다.
        verify(userRepository, never()).save(any(User.class));
    }

    /** 덮어쓴 뒤에는 앞서 보낸 인증 링크가 살아 있으면 안 된다. */
    @Test
    void signupInvalidatesTheEarlierVerificationLink() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedLocalUser()));
        when(userRepository.existsByPhoneAndIdNot(PHONE, 1L)).thenReturn(false);

        authService.signup(signupRequest());

        verify(emailTokenRepository).deleteByUserId(1L);
    }

    /** 인증을 마친 계정에는 실제 소유자가 있으므로 덮어쓰면 안 된다. */
    @Test
    void signupIsRejectedWhenTheAccountIsAlreadyVerified() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedLocalUser()));

        assertThatThrownBy(() -> authService.signup(signupRequest()))
                .isInstanceOf(ConflictException.class);
    }

    /** 소셜 계정도 provider 쪽에서 이미 검증된 것이라 덮어쓰지 않는다. */
    @Test
    void signupIsRejectedWhenTheEmailBelongsToASocialAccount() {
        User social = new User(EMAIL, "소셜", AuthProvider.GOOGLE, "google-123");
        ReflectionTestUtils.setField(social, "id", 1L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(social));

        assertThatThrownBy(() -> authService.signup(signupRequest()))
                .isInstanceOf(ConflictException.class);
    }

    /** 재가입이라도 남이 쓰는 전화번호는 가져올 수 없다. */
    @Test
    void signupIsRejectedWhenThePhoneBelongsToAnotherAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedLocalUser()));
        when(userRepository.existsByPhoneAndIdNot(PHONE, 1L)).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(signupRequest()))
                .isInstanceOf(ConflictException.class);
    }

    /**
     * 비밀번호는 맞았고 남은 것이 인증뿐일 때는, 프론트가 재발송 안내를 띄울 수 있도록 일반적인
     * 요청 오류와 구분되는 코드가 나가야 한다.
     */
    @Test
    void loginReportsAnUnverifiedEmailWithItsOwnCode() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedLocalUser()));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "Test1234!")))
                .isInstanceOf(EmailNotVerifiedException.class);
    }
}
