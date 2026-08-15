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
import org.springframework.context.annotation.Lazy;
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
     * 만들어 쓰므로 JPA 자동설정과 충돌하지 않는다.
     *
     * <p>{@code @Lazy}인 이유: 이 빈은 생성 시점에 실제 JDBC 커넥션을 연다. pgvector용 Postgres가 아직
     * 준비되지 않은 로컬 개발 환경에서도 앱 전체가 부팅 실패하지 않도록, 근로기준법 Q&A 기능이 실제로
     * 처음 쓰일 때까지 연결 시도를 미룬다(그 전까지는 다른 기능이 정상 동작한다).
     */
    @Bean
    @Lazy
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
                // 테이블이 없으면 만든다. 이미 있으면 그대로 쓴다.
                .initializeSchema(true)
                // 테이블을 지우지 않는다. 적재는 이제 기동 이벤트가 아니라 관리자 API로만 일어나므로,
                // 여기서 지우면 재시작할 때마다 적재해둔 벡터가 사라진다.
                // 중복은 청크 ID를 내용 해시로 고정해 덮어쓰기로 막는다(LaborQaIngestionService 참고).
                .removeExistingVectorStoreTable(false)
                .build();
    }
}
