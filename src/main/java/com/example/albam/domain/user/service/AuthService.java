package com.example.albam.domain.user.service;

import com.example.albam.domain.user.dto.LoginRequest;
import com.example.albam.domain.user.dto.PasswordResetConfirmRequest;
import com.example.albam.domain.user.dto.SignupRequest;
import com.example.albam.domain.user.dto.IssuedTokens;
import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.EmailToken;
import com.example.albam.domain.user.entity.EmailTokenType;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.oauth.OAuthUserInfo;
import com.example.albam.domain.user.oauth.OAuthUserInfoFetcher;
import com.example.albam.domain.user.repository.EmailTokenRepository;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.ConflictException;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.mail.MailService;
import com.example.albam.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final int VERIFY_EMAIL_EXPIRATION_HOURS = 24;
    private static final int PASSWORD_RESET_EXPIRATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final List<OAuthUserInfoFetcher> oAuthUserInfoFetchers;
    private final MailService mailService;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * 메일 발송(SMTP)은 트랜잭션 밖에서 한다 — DB 커밋까지 커넥션을 붙잡지 않기 위함이자,
     * 메일 서버 장애로 이미 커밋된 가입을 롤백시키지 않기 위함. 인증 메일은 재발송 API로 복구 가능하다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new InvalidRequestException("비밀번호가 일치하지 않습니다.");
        }
        validatePasswordComplexity(request.password());
        VerificationMail mail = new TransactionTemplate(transactionManager).execute(status -> {
            if (userRepository.existsByEmail(request.email())) {
                throw new ConflictException("이미 가입된 이메일입니다.");
            }
            if (userRepository.existsByPhone(request.phone())) {
                throw new ConflictException("이미 가입된 전화번호입니다.");
            }
            User user = new User(request.email(), passwordEncoder.encode(request.password()),
                    request.name(), request.phone(), request.birthDate(), LocalDateTime.now());
            userRepository.save(user);
            return prepareVerificationMail(user);
        });
        sendVerificationMail(mail);
        return mail.userId();
    }

    private VerificationMail prepareVerificationMail(User user) {
        EmailToken token = emailTokenRepository.save(new EmailToken(user, UUID.randomUUID().toString(),
                EmailTokenType.VERIFY_EMAIL, LocalDateTime.now().plusHours(VERIFY_EMAIL_EXPIRATION_HOURS)));
        return new VerificationMail(user.getId(), user.getEmail(), user.getName(), token.getToken());
    }

    private void sendVerificationMail(VerificationMail mail) {
        String link = baseUrl + "/api/v1/auth/verify-email?token=" + mail.token();
        mailService.send(mail.email(), "[알밤] 이메일 인증을 완료해 주세요",
                mail.name() + "님, 알밤 가입을 환영합니다!\n\n"
                        + "아래 링크를 클릭해 이메일 인증을 완료해 주세요. (24시간 이내)\n" + link);
    }

    private record VerificationMail(Long userId, String email, String name, String token) {
    }

    private record PasswordResetMail(String email, String name, String token) {
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailToken emailToken = emailTokenRepository.findByTokenAndType(token, EmailTokenType.VERIFY_EMAIL)
                .orElseThrow(() -> new InvalidRequestException("유효하지 않은 인증 링크입니다."));
        if (!emailToken.isUsable()) {
            throw new InvalidRequestException("만료되었거나 이미 사용된 인증 링크입니다.");
        }
        emailToken.getUser().markEmailVerified();
        emailToken.markUsed();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void resendVerification(String email) {
        VerificationMail mail = new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new InvalidRequestException("가입되지 않은 이메일입니다."));
            if (user.getProvider() != AuthProvider.LOCAL || user.isEmailVerified()) {
                throw new InvalidRequestException("이메일 인증이 필요한 계정이 아닙니다.");
            }
            return prepareVerificationMail(user);
        });
        sendVerificationMail(mail);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void requestPasswordReset(String email) {
        // 계정 존재 여부가 노출되지 않도록, 없는 이메일이거나 소셜 계정이어도 조용히 성공 처리한다
        PasswordResetMail mail = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.findByEmail(email)
                        .filter(user -> user.getProvider() == AuthProvider.LOCAL)
                        .map(user -> {
                            EmailToken token = emailTokenRepository.save(new EmailToken(user,
                                    UUID.randomUUID().toString(), EmailTokenType.PASSWORD_RESET,
                                    LocalDateTime.now().plusMinutes(PASSWORD_RESET_EXPIRATION_MINUTES)));
                            return new PasswordResetMail(user.getEmail(), user.getName(), token.getToken());
                        })
                        .orElse(null));
        if (mail == null) {
            return;
        }
        mailService.send(mail.email(), "[알밤] 비밀번호 재설정 안내",
                mail.name() + "님, 비밀번호 재설정 요청이 접수되었습니다.\n\n"
                        + "아래 토큰으로 30분 이내에 새 비밀번호를 설정해 주세요.\n"
                        + "토큰: " + mail.token() + "\n\n"
                        + "재설정 주소: " + baseUrl + "/api/v1/auth/password-reset/confirm\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시하세요.");
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new InvalidRequestException("비밀번호가 일치하지 않습니다.");
        }
        validatePasswordComplexity(request.newPassword());
        EmailToken emailToken = emailTokenRepository
                .findByTokenAndType(request.token(), EmailTokenType.PASSWORD_RESET)
                .orElseThrow(() -> new InvalidRequestException("유효하지 않은 재설정 토큰입니다."));
        if (!emailToken.isUsable()) {
            throw new InvalidRequestException("만료되었거나 이미 사용된 재설정 토큰입니다.");
        }
        emailToken.getUser().changePassword(passwordEncoder.encode(request.newPassword()));
        emailToken.markUsed();
    }

    private void validatePasswordComplexity(String password) {
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        int satisfied = (hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        if (satisfied < 2) {
            throw new InvalidRequestException("비밀번호는 영문/숫자/특수문자 중 2가지 이상을 조합해야 합니다.");
        }
    }

    public IssuedTokens login(LoginRequest request) {
        // 비밀번호 오류와 동일한 401 응답을 내려 계정 존재 여부를 노출하지 않는다
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new InvalidRequestException(
                    user.getProvider() + " 소셜 로그인으로 가입된 계정입니다. 소셜 로그인을 이용해 주세요.");
        }
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        if (!user.isEmailVerified()) {
            throw new InvalidRequestException("이메일 인증이 완료되지 않았습니다. 메일함을 확인해 주세요.");
        }
        return issueTokens(user);
    }

    @Transactional
    public IssuedTokens oauthLogin(AuthProvider provider, String accessToken) {
        OAuthUserInfo userInfo = resolveFetcher(provider).fetch(accessToken);
        User user = userRepository.findByProviderAndProviderId(provider, userInfo.providerId())
                .orElseGet(() -> registerOAuthUser(provider, userInfo));
        return issueTokens(user);
    }

    private User registerOAuthUser(AuthProvider provider, OAuthUserInfo userInfo) {
        if (userRepository.existsByEmail(userInfo.email())) {
            throw new ConflictException("이미 다른 방식으로 가입된 이메일입니다.");
        }
        return userRepository.save(new User(userInfo.email(), userInfo.name(), provider, userInfo.providerId()));
    }

    private OAuthUserInfoFetcher resolveFetcher(AuthProvider provider) {
        return oAuthUserInfoFetchers.stream()
                .filter(fetcher -> fetcher.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("지원하지 않는 로그인 방식입니다."));
    }

    public IssuedTokens refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidRequestException("유효하지 않은 리프레시 토큰입니다.");
        }
        User user = userRepository.findById(jwtTokenProvider.getUserId(refreshToken))
                .orElseThrow(() -> new InvalidRequestException("유효하지 않은 리프레시 토큰입니다."));
        return issueTokens(user);
    }

    private IssuedTokens issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());
        return new IssuedTokens(accessToken, refreshToken, user.isProfileCompleted());
    }
}
