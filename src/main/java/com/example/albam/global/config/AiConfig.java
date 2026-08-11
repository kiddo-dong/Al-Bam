package com.example.albam.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    /**
     * 근로기준법 Q&A용 벡터 저장소가 쓰는 S3 Vectors 클라이언트.
     * VectorStore 빈 자체는 spring-ai-starter-vector-store-s3의 자동설정이
     * {@code spring.ai.vectorstore.s3.*} 설정을 읽어 만들어준다.
     *
     * <p>{@code @Lazy}인 이유: 벡터 버킷·인덱스가 아직 없는 로컬 개발 환경에서도 앱 전체가 기동에
     * 실패하지 않도록, 근로기준법 Q&A가 실제로 처음 쓰일 때까지 연결을 미룬다(그 전까지 다른 기능은
     * 정상 동작한다). 파일 업로드용 S3Client와는 다른 서비스 클라이언트라 별도로 만든다.
     */
    @Bean
    @Lazy
    public S3VectorsClient s3VectorsClient(@Value("${aws.region}") String region) {
        return S3VectorsClient.builder()
                .region(Region.of(region))
                .build();
    }
}
