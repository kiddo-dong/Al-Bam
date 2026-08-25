package com.example.albam.global.common;

/**
 * 모든 응답의 공통 형식.
 *
 * <p>{@code code}는 실패했을 때만 채워지며, 프론트가 문구가 아니라 값으로 분기할 수 있게 한다.
 * 메시지는 사용자에게 보여주기 위한 것이라 언제든 바뀔 수 있어서, 그걸로 조건을 걸면 문구를 다듬는
 * 순간 클라이언트가 깨진다.
 */
public record ApiResponse<T>(boolean success, T data, String message, String code) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static ApiResponse<Void> error(String message, String code) {
        return new ApiResponse<>(false, null, message, code);
    }
}
