package com.example.albam.global.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 엔드포인트를 사용자 1명이 부를 수 있는 횟수를 제한한다.
 * 주 용도는 OpenAI를 호출하는 엔드포인트의 비용 보호 — 무한루프 버그나 스크립트 반복 호출로
 * 과금이 폭증하는 것을 막는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** windowMinutes 동안 허용할 최대 호출 횟수. */
    int limit();

    /** 제한 기간(분). */
    int windowMinutes();
}
