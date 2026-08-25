package com.example.albam.global.exception;

/**
 * 가입은 했지만 이메일 인증을 아직 끝내지 않은 계정으로 로그인을 시도했을 때.
 *
 * <p>일반적인 로그인 실패와 달리 비밀번호는 맞았고, 사용자가 할 일은 메일함을 확인하거나 인증
 * 메일을 다시 받는 것이다. 프론트가 그 안내를 띄울 수 있도록 별도 코드로 구분한다.
 */
public class EmailNotVerifiedException extends BusinessException {

    public EmailNotVerifiedException(String message) {
        super(ErrorCode.EMAIL_NOT_VERIFIED, message);
    }
}
