package com.example.albam.domain.laborqa.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 {@code src/main/resources/labor-docs/} 아래 마크다운(.md)·PDF(.pdf)·JSON(.json) 문서를
 * 읽어 벡터 저장소(PostgreSQL/pgvector)에 적재한다. 문서량이 적어(수십~수백 청크) 기동마다 테이블을 비우고
 * 재적재하는 단순한 방식을 쓰며(AiConfig의 {@code removeExistingVectorStoreTable(true)} 참고), 별도
 * 캐시나 증분 갱신은 하지 않는다.
 *
 * <p>JSON 파일은 {@code [{"title": "...", "content": "..."}, ...]} 형태의 배열이어야 하며, title은
 * 답변 출처 표시에 쓰인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LaborQaIngestionService {

    private static final String MARKDOWN_LOCATION_PATTERN = "classpath:labor-docs/*.md";
    private static final String PDF_LOCATION_PATTERN = "classpath:labor-docs/pdf/*.pdf";
    private static final String JSON_LOCATION_PATTERN = "classpath:labor-docs/json/*.json";

    private final VectorStore vectorStore;

    @EventListener(ApplicationReadyEvent.class)
    public void ingest() {
        List<Document> rawDocuments = new ArrayList<>();
        rawDocuments.addAll(readMarkdown());
        rawDocuments.addAll(readPdfs());
        rawDocuments.addAll(readJson());

        List<Document> chunks = new TokenTextSplitter().split(rawDocuments);
        vectorStore.add(chunks);
        log.info("근로기준법 Q&A 지식베이스 적재 완료: 문서 {}개 -> 청크 {}개", rawDocuments.size(), chunks.size());
    }

    private List<Document> readMarkdown() {
        return new MarkdownDocumentReader(MARKDOWN_LOCATION_PATTERN, MarkdownDocumentReaderConfig.defaultConfig())
                .get();
    }

    private List<Document> readPdfs() {
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resolveResources(PDF_LOCATION_PATTERN)) {
            documents.addAll(new PagePdfDocumentReader(resource).get());
        }
        return documents;
    }

    private List<Document> readJson() {
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resolveResources(JSON_LOCATION_PATTERN)) {
            documents.addAll(new JsonReader(resource,
                    item -> Map.of("title", item.getOrDefault("title", "출처 미상")), "content").get());
        }
        return documents;
    }

    private List<Resource> resolveResources(String locationPattern) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
            return List.of(resources);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
