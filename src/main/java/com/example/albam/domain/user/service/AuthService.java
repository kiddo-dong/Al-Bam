package com.example.albam.domain.user.service;

import com.example.albam.domain.user.dto.LoginRequest;
import com.example.albam.domain.user.dto.PasswordResetConfirmRequest;
import com.example.albam.domain.user.dto.SignupRequest;
import com.example.albam.domain.user.dto.IssuedTokens;
import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.EmailToken;
import com.example.albam.domain.user.entity.EmailTokenType;
import com.example.albam.domain.user.entity.RefreshToken;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.oauth.OAuthProfilePhotoImporter;
import com.example.albam.domain.user.oauth.OAuthUserInfo;
import com.example.albam.domain.user.oauth.OAuthUserInfoFetcher;
import com.example.albam.domain.user.repository.EmailTokenRepository;
import com.example.albam.domain.user.repository.RefreshTokenRepository;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.ConflictException;
import com.example.albam.global.exception.EmailNotVerifiedException;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.mail.MailService;
import com.example.albam.global.security.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final int VERIFY_EMAIL_EXPIRATION_HOURS = 24;
    private static final int PASSWORD_RESET_EXPIRATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final List<OAuthUserInfoFetcher> oAuthUserInfoFetchers;
    private final MailService mailService;
    private final OAuthProfilePhotoImporter oAuthProfilePhotoImporter;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.base-url}")
    private String baseUrl;

    /** 메일 링크가 향할 사용자용 주소. baseUrl은 API 주소라 사용자가 직접 열 화면이 없다. */
    @Value("${app.frontend-url}")
    private String frontendUrl;

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
            User existing = userRepository.findByEmail(request.email()).orElse(null);
            if (existing != null) {
                return resubmitSignup(existing, request);
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

    /**
     * 이미 그 이메일로 행이 있을 때의 처리.
     *
     * <p>인증을 마치지 않은 로컬 계정이라면 가입이 끝나지 않은 것이므로 새 입력으로 덮어쓰고 인증
     * 메일을 다시 보낸다. 이게 없으면 메일을 열지 않은 채 잊었거나 주소를 잘못 적은 사람에게
     * "이미 가입된 이메일입니다"만 돌아가고, 로그인도 막혀 있어(인증 전) 그 주소로는 영영 가입할 수
     * 없게 된다.
     *
     * <p>인증을 마쳤거나 소셜로 가입한 계정은 실제 소유자가 있으므로 그대로 거절한다.
     */
    private VerificationMail resubmitSignup(User existing, SignupRequest request) {
        if (existing.isEmailVerified() || existing.getProvider() != AuthProvider.LOCAL) {
            throw new ConflictException("이미 가입된 이메일입니다.");
        }
        if (userRepository.existsByPhoneAndIdNot(request.phone(), existing.getId())) {
            throw new ConflictException("이미 가입된 전화번호입니다.");
        }
        existing.resubmitSignup(passwordEncoder.encode(request.password()), request.name(),
                request.phone(), request.birthDate());
        // 앞서 보낸 인증 링크는 더 이상 유효하면 안 된다. 옛 메일이 남아 있어도 새 메일만 통한다.
        emailTokenRepository.deleteByUserId(existing.getId());
        return prepareVerificationMail(existing);
    }

    private VerificationMail prepareVerificationMail(User user) {
        EmailToken token = emailTokenRepository.save(new EmailToken(user, UUID.randomUUID().toString(),
                EmailTokenType.VERIFY_EMAIL, LocalDateTime.now().plusHours(VERIFY_EMAIL_EXPIRATION_HOURS)));
        return new VerificationMail(user.getId(), user.getEmail(), user.getName(), token.getToken());
    }

    /**
     * 인증 링크는 API를 가리킨다 — 토큰 처리는 서버가 하고, 끝나면 사용자를 프론트 화면으로 넘긴다.
     * 토큰이 브라우저 JS까지 갈 이유가 없으므로 이 방향이 더 안전하다.
     */
    private void sendVerificationMail(VerificationMail mail) {
        String link = baseUrl + "/api/v1/auth/verify-email?token=" + mail.token();
        mailService.send(mail.email(), "[ToTheWork] 이메일 인증을 완료해 주세요",
                mail.name() + "님, ToTheWork 가입을 환영합니다!\n\n"
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
        // 재설정 폼은 프론트 화면이다. 예전에는 API 주소를 안내해서, 눌러도 405만 보였다.
        String link = frontendUrl + "/password-reset/confirm?token=" + mail.token();
        mailService.send(mail.email(), "[ToTheWork] 비밀번호 재설정 안내",
                mail.name() + "님, 비밀번호 재설정 요청이 접수되었습니다.\n\n"
                        + "아래 링크에서 30분 이내에 새 비밀번호를 설정해 주세요.\n" + link + "\n\n"
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
        // 비밀번호를 바꾸는 이유 중 하나가 계정 탈취이므로, 기존 로그인 세션을 전부 끊는다.
        refreshTokenRepository.deleteByUserId(emailToken.getUser().getId());
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

    @Transactional
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
            // 비밀번호는 맞았고 남은 것은 인증뿐이라, 프론트가 재발송 안내를 띄울 수 있도록
            // 일반적인 요청 오류와 다른 코드로 구분해 보낸다.
            throw new EmailNotVerifiedException("이메일 인증이 완료되지 않았습니다. 메일함을 확인해 주세요.");
        }
        return issueTokens(user);
    }

    /**
     * 소셜 로그인. 처음 보는 계정이면 가입까지 함께 처리한다.
     *
     * <p>이 메서드 자체는 트랜잭션이 아니다. 제공자 API 호출과 프로필 사진 내려받기가 여기서
     * 일어나는데, 그 동안 DB 커넥션을 붙잡고 있지 않기 위해서다(가입 메일 발송과 같은 이유).
     * DB를 건드리는 부분만 아래에서 따로 트랜잭션으로 묶는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public IssuedTokens oauthLogin(AuthProvider provider, String accessToken) {
        OAuthUserInfo userInfo = resolveFetcher(provider).fetch(accessToken);

        // 사진은 처음 가입할 때만 가져온다. 로그인할 때마다 덮어쓰면 사용자가 직접 올린 사진이
        // 매번 소셜 사진으로 되돌아간다.
        String profileImageKey =
                userRepository.existsByProviderAndProviderId(provider, userInfo.providerId())
                        ? null
                        : oAuthProfilePhotoImporter.importFrom(userInfo.profileImageUrl());

        return new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.findByProviderAndProviderId(provider, userInfo.providerId())
                    .orElseGet(() -> registerOAuthUser(provider, userInfo, profileImageKey));
            return issueTokens(user);
        });
    }

    private User registerOAuthUser(AuthProvider provider, OAuthUserInfo userInfo, String profileImageKey) {
        if (userRepository.existsByEmail(userInfo.email())) {
            throw new ConflictException("이미 다른 방식으로 가입된 이메일입니다.");
        }
        User user = new User(userInfo.email(), userInfo.name(), provider, userInfo.providerId());
        if (profileImageKey != null) {
            user.changeProfileImageKey(profileImageKey);
        }
        return userRepository.save(user);
    }

    private OAuthUserInfoFetcher resolveFetcher(AuthProvider provider) {
        return oAuthUserInfoFetchers.stream()
                .filter(fetcher -> fetcher.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("지원하지 않는 로그인 방식입니다."));
    }

    /**
     * 리프레시 토큰을 새 토큰 쌍으로 교환한다. 서명·만료뿐 아니라 저장된 기록이 있는지도 확인하므로,
     * 로그아웃이나 비밀번호 변경으로 지워진 토큰은 아직 만료 전이어도 거부된다.
     *
     * <p>쓴 토큰은 지우고 새로 발급한다(회전). 한 토큰이 계속 재사용되지 않으므로 유출이 오래 유효하지 않다.
     */
    @Transactional
    public IssuedTokens refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidRequestException("유효하지 않은 리프레시 토큰입니다.");
        }
        Optional<RefreshToken> stored = refreshTokenRepository.findByTokenHash(hashToken(refreshToken));
        if (stored.isEmpty()) {
            revokeAllSessionsAfterReuse(refreshToken);
            throw new InvalidRequestException("만료되었거나 이미 사용된 로그인 정보입니다. 다시 로그인해 주세요.");
        }

        refreshTokenRepository.delete(stored.get());
        return issueTokens(stored.get().getUser());
    }

    /**
     * 서명은 멀쩡한데 기록이 없는 토큰이 들어왔다는 것은, 이미 회전으로 소비됐거나 폐기된 토큰을 누군가
     * 다시 쓰고 있다는 뜻이다. 정상 클라이언트는 새 토큰을 받았으므로 옛 것을 쓸 이유가 없다. 유출됐을
     * 가능성이 있다고 보고 그 사용자의 세션을 전부 끊어, 탈취자가 다른 토큰으로 갈아타지 못하게 한다.
     *
     * <p>탭 두 개가 동시에 갱신하면 늦은 쪽이 여기에 걸려 멀쩡한 사용자도 로그아웃될 수 있다. 그때
     * 이유를 알 수 있도록 경고를 남긴다. 오탐이 잦다면 소비된 토큰에 짧은 유예를 두는 방식이 대안이다.
     */
    private void revokeAllSessionsAfterReuse(String refreshToken) {
        Long userId;
        try {
            userId = jwtTokenProvider.getUserId(refreshToken);
        } catch (RuntimeException e) {
            return;
        }
        long revoked = refreshTokenRepository.deleteByUserId(userId);
        if (revoked > 0) {
            log.warn("이미 사용된 리프레시 토큰이 다시 들어와 userId={}의 세션 {}건을 폐기했다. "
                    + "토큰 유출이거나, 여러 탭이 동시에 갱신을 시도한 경우다.", userId, revoked);
        }
    }

    /**
     * 이 토큰 하나만 폐기한다. 다른 기기의 로그인은 각자의 토큰을 갖고 있으므로 그대로 유지된다.
     * 이미 없는 토큰이어도 조용히 넘어간다 — 로그아웃은 몇 번을 눌러도 성공해야 한다.
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .ifPresent(refreshTokenRepository::delete);
    }

    private IssuedTokens issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());
        refreshTokenRepository.save(new RefreshToken(user, hashToken(refreshToken),
                jwtTokenProvider.getExpiresAt(refreshToken)));
        return new IssuedTokens(accessToken, refreshToken, user.isProfileCompleted());
    }

    /**
     * 토큰 원문 대신 해시를 저장·조회한다. DB가 유출돼도 그대로 쓸 수 있는 토큰이 함께 넘어가지 않는다.
     * 토큰은 서명된 JWT라 추측할 수 없으므로 비밀번호와 달리 솔트·반복 해싱은 필요 없다.
     */
    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
