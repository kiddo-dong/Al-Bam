package com.example.albam.global.config;

import javax.sql.DataSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AiConfig {

    private static final int EMBEDDING_DIMENSIONS = 1536; // text-embedding-3-small

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    /**
     * 근로기준법 Q&A용 벡터 저장소. 메인 데이터(MySQL)와 완전히 분리된 전용 PostgreSQL(pgvector)에 저장한다.
     * 이 DataSource는 JPA가 쓰는 기본 DataSource와 별개이며, Spring 빈으로 등록하지 않고 여기서만 직접
     * 만들어 쓰므로 JPA 자동설정과 충돌하지 않는다. 앱 기동 시 {@code initializeSchema(true)}로 pgvector
     * 확장·테이블을 자동 생성한다.
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel,
            @Value("${app.vector-store.datasource.url}") String url,
            @Value("${app.vector-store.datasource.username}") String username,
            @Value("${app.vector-store.datasource.password}") String password) {
        DataSource vectorStoreDataSource = DataSourceBuilder.create()
                .driverClassName("org.postgresql.Driver")
                .url(url)
                .username(username)
                .password(password)
                .build();
        JdbcTemplate vectorStoreJdbcTemplate = new JdbcTemplate(vectorStoreDataSource);
        return PgVectorStore.builder(vectorStoreJdbcTemplate, embeddingModel)
                .dimensions(EMBEDDING_DIMENSIONS)
                .initializeSchema(true)
                // LaborQaIngestionService가 기동마다 문서를 통째로 다시 넣으므로, 이전 데이터를 지우지 않으면
                // 재시작할 때마다 같은 내용이 중복 적재된다. 이 시점 기준 코퍼스가 작아 매번 새로 채워도
                // 무방하므로 기동 시 테이블을 비우고 새로 채우는 방식을 택한다.
                .removeExistingVectorStoreTable(true)
                .build();
    }
}
