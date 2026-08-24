package com.example.albam.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 활성화. 이게 없으면 스케줄 메서드가 조용히 실행되지 않는다(에러도 나지 않는다).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
