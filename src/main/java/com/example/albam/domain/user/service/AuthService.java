package com.example.albam.domain.user.service;

import com.example.albam.domain.user.dto.LoginRequest;
import com.example.albam.domain.user.dto.PasswordResetConfirmRequest;
import com.example.albam.domain.user.dto.SignupRequest;
import com.example.albam.domain.user.dto.TokenResponse;
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
import org.springframework.transaction.annotation.Transactional;

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

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public Long signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new InvalidRequestException("비밀번호가 일치하지 않습니다.");
        }
        validatePasswordComplexity(request.password());
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("이미 가입된 이메일입니다.");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new ConflictException("이미 가입된 전화번호입니다.");
        }
        User user = new User(request.email(), passwordEncoder.encode(request.password()),
                request.name(), request.phone(), request.birthDate(), LocalDateTime.now());
        userRepository.save(user);
        sendVerificationMail(user);
        return user.getId();
    }

    private void sendVerificationMail(User user) {
        EmailToken token = emailTokenRepository.save(new EmailToken(user, UUID.randomUUID().toString(),
                EmailTokenType.VERIFY_EMAIL, LocalDateTime.now().plusHours(VERIFY_EMAIL_EXPIRATION_HOURS)));
        String link = baseUrl + "/api/v1/auth/verify-email?token=" + token.getToken();
        mailService.send(user.getEmail(), "[알밤] 이메일 인증을 완료해 주세요",
                user.getName() + "님, 알밤 가입을 환영합니다!\n\n"
                        + "아래 링크를 클릭해 이메일 인증을 완료해 주세요. (24시간 이내)\n" + link);
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

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidRequestException("가입되지 않은 이메일입니다."));
        if (user.getProvider() != AuthProvider.LOCAL || user.isEmailVerified()) {
            throw new InvalidRequestException("이메일 인증이 필요한 계정이 아닙니다.");
        }
        sendVerificationMail(user);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        // 계정 존재 여부가 노출되지 않도록, 없는 이메일이거나 소셜 계정이어도 조용히 성공 처리한다
        userRepository.findByEmail(email)
                .filter(user -> user.getProvider() == AuthProvider.LOCAL)
                .ifPresent(user -> {
                    EmailToken token = emailTokenRepository.save(new EmailToken(user,
                            UUID.randomUUID().toString(), EmailTokenType.PASSWORD_RESET,
                            LocalDateTime.now().plusMinutes(PASSWORD_RESET_EXPIRATION_MINUTES)));
                    mailService.send(user.getEmail(), "[알밤] 비밀번호 재설정 안내",
                            user.getName() + "님, 비밀번호 재설정 요청이 접수되었습니다.\n\n"
                                    + "아래 토큰으로 30분 이내에 새 비밀번호를 설정해 주세요.\n"
                                    + "토큰: " + token.getToken() + "\n\n"
                                    + "재설정 주소: " + baseUrl + "/api/v1/auth/password-reset/confirm\n"
                                    + "본인이 요청하지 않았다면 이 메일을 무시하세요.");
                });
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

    public TokenResponse login(LoginRequest request) {
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
    public TokenResponse oauthLogin(AuthProvider provider, String accessToken) {
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

    public TokenResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidRequestException("유효하지 않은 리프레시 토큰입니다.");
        }
        User user = userRepository.findById(jwtTokenProvider.getUserId(refreshToken))
                .orElseThrow(() -> new InvalidRequestException("유효하지 않은 리프레시 토큰입니다."));
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());
        return new TokenResponse(accessToken, refreshToken, user.isProfileCompleted());
    }
}
