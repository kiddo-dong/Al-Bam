package com.example.albam.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    /**
     * 근로기준법 Q&A용 인메모리 벡터 저장소. 문서량이 적어(수십 청크) 별도 벡터DB 없이 앱 기동 시마다
     * {@code LaborQaIngestionService}가 재구축한다. 매장 수가 늘어나 성능 이슈가 생기면 그때 pgvector 등으로
     * 이관을 검토한다.
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
