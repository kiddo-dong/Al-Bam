package com.example.albam.global.signup;

import com.example.albam.global.exception.ProfileIncompleteException;
import com.example.albam.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 추가 정보 입력을 끝내지 않은 계정의 서비스 API 호출을 막는다.
 *
 * <p>소셜 로그인은 이름과 이메일만 받아오므로 가입 직후 전화번호·생년월일·약관동의가 비어 있다.
 * 프론트가 응답의 profileCompleted를 보고 입력 화면으로 보내주긴 하지만, 그건 화면 흐름일 뿐이라
 * 주소창에 직접 URL을 치거나 API를 직접 호출하면 그대로 통과한다. 그러면 <b>약관에 동의하지 않은
 * 사용자가 서비스를 쓰게 되므로</b> 서버에서도 같은 규칙을 강제한다.
 *
 * <p>인증 자체는 Spring Security가 판단한다. 여기서는 인증된 요청만 검사하고, 비인증 요청은
 * 그대로 통과시켜 원래대로 401이 나가게 둔다.
 */
public class ProfileCompletionInterceptor implements HandlerInterceptor {

    /**
     * 미완성 상태에서도 허용해야 하는 경로.
     *
     * <p>추가 정보를 입력하려면 자기 정보를 읽고(GET) 제출할(complete-profile) 수 있어야 하고,
     * 입력하지 않고 그만두려면 탈퇴할 수 있어야 한다. 이 셋을 막으면 빠져나갈 방법이 없어진다.
     */
    private static final String ME = "/api/v1/users/me";
    private static final String COMPLETE_PROFILE = ME + "/complete-profile";
    private static final Set<String> ALWAYS_ALLOWED_PREFIXES = Set.of("/api/v1/auth/");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isAllowedWhileIncomplete(request)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return true;
        }
        if (!userDetails.isProfileCompleted()) {
            throw new ProfileIncompleteException(
                    "추가 정보를 입력해야 서비스를 이용할 수 있습니다.");
        }
        return true;
    }

    private boolean isAllowedWhileIncomplete(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : ALWAYS_ALLOWED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        if (COMPLETE_PROFILE.equals(path)) {
            return true;
        }
        // 자기 정보 조회와 탈퇴만 허용한다. 같은 경로의 PATCH(프로필 수정)는 막아야
        // 추가 정보 입력을 건너뛰고 이름만 바꾸는 우회가 생기지 않는다.
        return ME.equals(path)
                && (HttpMethod.GET.matches(request.getMethod())
                        || HttpMethod.DELETE.matches(request.getMethod()));
    }
}
