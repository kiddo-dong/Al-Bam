package com.example.albam.global.exception;

/**
 * 추가 정보(전화번호·생년월일·약관동의)를 아직 입력하지 않은 계정이 서비스 API를 호출했을 때.
 *
 * <p>소셜 로그인은 이름과 이메일만 받아오므로 가입 직후 이 상태가 된다. 약관에 동의하지 않은 채로
 * 서비스가 쓰이면 안 되기 때문에, 프론트의 화면 이동에만 맡기지 않고 서버에서도 막는다.
 */
public class ProfileIncompleteException extends BusinessException {

    public ProfileIncompleteException(String message) {
        super(ErrorCode.PROFILE_INCOMPLETE, message);
    }
}
