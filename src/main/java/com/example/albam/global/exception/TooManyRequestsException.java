package com.example.albam.global.exception;

public class TooManyRequestsException extends BusinessException {

    public TooManyRequestsException(String message) {
        super(ErrorCode.TOO_MANY_REQUESTS, message);
    }
}
