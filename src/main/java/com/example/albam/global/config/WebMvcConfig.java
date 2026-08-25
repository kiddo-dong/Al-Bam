package com.example.albam.global.config;

import com.example.albam.global.ratelimit.RateLimitInterceptor;
import com.example.albam.global.signup.ProfileCompletionInterceptor;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final ProfileCompletionInterceptor profileCompletionInterceptor;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor,
            ProfileCompletionInterceptor profileCompletionInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.profileCompletionInterceptor = profileCompletionInterceptor;
    }

    @Bean
    public static RateLimitInterceptor rateLimitInterceptor() {
        return new RateLimitInterceptor(Clock.systemUTC());
    }

    @Bean
    public static ProfileCompletionInterceptor profileCompletionInterceptor() {
        return new ProfileCompletionInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 인터셉터 자체가 @RateLimit이 붙은 핸들러만 골라내므로 경로는 전체로 둔다.
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
        // 가입이 끝나지 않은 계정은 여기서 걸러낸다. 비용이 드는 처리에 들어가기 전에 막도록
        // rate limit 다음에 둔다.
        registry.addInterceptor(profileCompletionInterceptor).addPathPatterns("/api/**");
    }
}
