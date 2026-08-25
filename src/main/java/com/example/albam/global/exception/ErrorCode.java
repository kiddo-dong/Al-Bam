package com.example.albam.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "해당 작업에 대한 권한이 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),

    // 아래 둘은 "가입은 했지만 아직 끝나지 않은" 상태다. 일반적인 거부와 달리 사용자가 할 수 있는
    // 다음 행동이 정해져 있어(인증 메일 재발송 / 추가 정보 입력), 프론트가 그 화면으로 안내할 수
    // 있도록 코드로 구분한다. 메시지 문구로 분기하면 문구를 고칠 때마다 프론트가 깨진다.
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 완료되지 않았습니다."),
    PROFILE_INCOMPLETE(HttpStatus.FORBIDDEN, "추가 정보 입력이 완료되지 않았습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
