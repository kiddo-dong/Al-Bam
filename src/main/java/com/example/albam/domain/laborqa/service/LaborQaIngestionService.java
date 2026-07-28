package com.example.albam.domain.laborqa.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 {@code src/main/resources/labor-docs/*.md} 문서를 읽어 벡터 저장소에 적재한다.
 * 문서량이 적어(수십 청크) 기동마다 재적재하는 단순한 방식을 쓰며, 별도 캐시나 증분 갱신은 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LaborQaIngestionService {

    private static final String MARKDOWN_LOCATION_PATTERN = "classpath:labor-docs/*.md";

    private final VectorStore vectorStore;

    @EventListener(ApplicationReadyEvent.class)
    public void ingest() {
        MarkdownDocumentReader reader = new MarkdownDocumentReader(MARKDOWN_LOCATION_PATTERN,
                MarkdownDocumentReaderConfig.defaultConfig());
        List<Document> rawDocuments = reader.get();
        List<Document> chunks = new TokenTextSplitter().split(rawDocuments);
        vectorStore.add(chunks);
        log.info("근로기준법 Q&A 지식베이스 적재 완료: 문서 {}개 -> 청크 {}개", rawDocuments.size(), chunks.size());
    }
}
