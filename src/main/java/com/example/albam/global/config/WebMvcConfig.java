package com.example.albam.global.config;

import com.example.albam.global.ratelimit.RateLimitInterceptor;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Bean
    public static RateLimitInterceptor rateLimitInterceptor() {
        return new RateLimitInterceptor(Clock.systemUTC());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 인터셉터 자체가 @RateLimit이 붙은 핸들러만 골라내므로 경로는 전체로 둔다.
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }
}
