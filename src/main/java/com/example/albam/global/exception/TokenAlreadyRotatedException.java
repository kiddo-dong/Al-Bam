package com.example.albam.global.exception;

/**
 * 같은 리프레시 토큰으로 온 두 요청 중 늦은 쪽. 거의 항상 같은 사람이 연 다른 탭이다.
 *
 * <p>나중에 들어온 재사용(탈취 의심)과 구분하려고 별도 코드를 둔다. 이긴 쪽 응답으로 쿠키가 이미 새
 * 토큰이 되었으므로, 클라이언트는 로그아웃시키지 말고 재발급을 한 번 더 시도하면 된다.
 */
public class TokenAlreadyRotatedException extends BusinessException {

    public TokenAlreadyRotatedException(String message) {
        super(ErrorCode.TOKEN_ALREADY_ROTATED, message);
    }
}
