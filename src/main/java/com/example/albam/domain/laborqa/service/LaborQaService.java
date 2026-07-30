package com.example.albam.domain.laborqa.service;

import com.example.albam.domain.laborqa.dto.LaborQaRequest;
import com.example.albam.domain.laborqa.dto.LaborQaResponse;
import com.example.albam.domain.laborqa.entity.LaborQaMessage;
import com.example.albam.domain.laborqa.entity.LaborQaRole;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 근로기준법·세무 Q&A를 RAG로 답변한다. LLM은 {@link #SYSTEM_PROMPT}로 반드시 검색된 근거 문서 범위
 * 안에서만 답하도록 지시받으며, 근거가 아예 없으면 LLM을 호출하지 않고 즉시 "자료 없음"으로 응답해
 * 환각을 원천 차단한다.
 *
 * <p>단발 질문({@link #ask})과 세션 기반 멀티턴({@link #askInSession}) 두 방식을 지원한다.
 */
@Service
@RequiredArgsConstructor
public class LaborQaService {

    private static final int TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final String NOT_GROUNDED_ANSWER =
            "제공된 자료로는 답변하기 어렵습니다. 정확한 판단은 노무사·세무사 상담을 권장드립니다.";
    private static final String DISCLAIMER =
            "\n\n※ 이 답변은 법률·세무 자문이 아니며, 정확한 판단은 노무사·세무사 상담이 필요합니다.";

    private static final String SYSTEM_PROMPT = """
            당신은 소규모 매장 근로자를 위한 근로기준법·세무 안내 도우미입니다.
            아래 제공된 근거 문서만 사용해서 답변하세요. 근거 문서에 없는 내용은 답변하지 말고
            "제공된 자료로는 답변하기 어렵습니다"라고 답하세요. 추측하거나 근거 밖의 지식을 덧붙이지 마세요.
            이전 대화가 함께 제공되면 질문의 맥락(예: "그럼 5인 미만은요?")을 해석하는 데만 사용하고,
            답변의 근거는 여전히 근거 문서에서만 가져오세요.
            개인의 정확한 세액이나 구체적인 법적 판단은 하지 말고 일반적인 기준만 안내하세요.
            답변 마지막 줄에는 참고한 출처(문서 제목)를 나열하세요.
            """;

    /**
     * {@link VectorStore}를 생성자에서 직접 주입받지 않고 {@link ObjectProvider}로 감싸는 이유: pgvector
     * 연결은 이 벡터스토어 빈이 실제로 처음 만들어질 때 이루어지는데, 생성자 주입이면 이 서비스가 만들어지는
     * 시점에 즉시 그 연결을 시도하게 되어 AiConfig의 {@code @Lazy}가 무력화된다. {@link #ask}가 실제로
     * 호출될 때만 {@code getObject()}로 지연 조회하도록 한다.
     */
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ChatClient chatClient;
    private final LaborQaSessionService sessionService;

    /** 단발 질문. 대화 이력을 저장하지도 참조하지도 않는다. */
    public LaborQaResponse ask(LaborQaRequest request) {
        return answer(request.question(), request.question(), "");
    }

    /**
     * 세션 기반 멀티턴 질문. 직전 대화를 프롬프트에 넣어 "그럼 ~는요?" 같은 후속 질문을 이해시키고,
     * 질문·답변을 세션에 저장한다.
     *
     * <p>트랜잭션 주의: LLM 호출(수 초)이 끝난 뒤에 저장만 별도 트랜잭션으로 수행한다 — LLM 응답을
     * 기다리는 동안 DB 커넥션을 붙잡아 두지 않기 위해 이 메서드 자체는 트랜잭션이 아니다.
     */
    public LaborQaResponse askInSession(Long sessionId, Long userId, LaborQaRequest request) {
        sessionService.requireOwnedSession(sessionId, userId);
        List<LaborQaMessage> history = sessionService.loadRecentMessages(sessionId);

        // 후속 질문은 대명사("그럼", "그거") 때문에 단독으로는 검색이 빗나가기 쉬워, 직전 사용자 질문을
        // 검색 쿼리에 함께 넣어 재현율을 높인다 (LLM 재작성 호출 없이 해결하는 저비용 방식).
        String retrievalQuery = lastUserQuestion(history)
                .map(previous -> previous + " " + request.question())
                .orElse(request.question());

        LaborQaResponse response = answer(retrievalQuery, request.question(), formatHistory(history));
        sessionService.recordExchange(sessionId, userId, request.question(), response.answer(),
                response.sources());
        return response;
    }

    private LaborQaResponse answer(String retrievalQuery, String question, String historyBlock) {
        VectorStore vectorStore = vectorStoreProvider.getObject();
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(retrievalQuery)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build());
        if (results.isEmpty()) {
            return new LaborQaResponse(NOT_GROUNDED_ANSWER, List.of(), false);
        }

        String context = results.stream()
                .map(document -> "[" + resolveSourceLabel(document) + "]\n" + document.getText())
                .collect(Collectors.joining("\n\n---\n\n"));
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(user -> user.text("{history}근거 문서:\n{context}\n\n질문: {question}")
                        .param("history", historyBlock)
                        .param("context", context)
                        .param("question", question))
                .call()
                .content();
        if (answer == null || answer.isBlank()) {
            return new LaborQaResponse(NOT_GROUNDED_ANSWER, List.of(), false);
        }

        List<String> sources = results.stream()
                .map(this::resolveSourceLabel)
                .distinct()
                .toList();
        return new LaborQaResponse(answer + DISCLAIMER, sources, true);
    }

    private Optional<String> lastUserQuestion(List<LaborQaMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getRole() == LaborQaRole.USER) {
                return Optional.of(history.get(i).getContent());
            }
        }
        return Optional.empty();
    }

    private String formatHistory(List<LaborQaMessage> history) {
        if (history.isEmpty()) {
            return "";
        }
        String lines = history.stream()
                .map(message -> (message.getRole() == LaborQaRole.USER ? "사용자: " : "도우미: ")
                        + message.getContent())
                .collect(Collectors.joining("\n"));
        return "이전 대화:\n" + lines + "\n\n";
    }

    /** 마크다운/JSON은 title 메타데이터를, PDF는 파일명+페이지 번호를 출처 표시로 쓴다. */
    private String resolveSourceLabel(Document document) {
        var metadata = document.getMetadata();
        if (metadata.containsKey("title")) {
            return String.valueOf(metadata.get("title"));
        }
        if (metadata.containsKey("file_name")) {
            Object pageNumber = metadata.get("page_number");
            return pageNumber != null
                    ? metadata.get("file_name") + " (p." + pageNumber + ")"
                    : String.valueOf(metadata.get("file_name"));
        }
        return "출처 미상";
    }
}
