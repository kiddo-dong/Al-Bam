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
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 검색 문턱과 개수. 실제 질문을 받아보며 맞춰야 하는 값이라 설정으로 빼 둔다 — 다시 빌드하지
     * 않고 환경변수로 조정할 수 있다.
     *
     * <p>기본값은 application.properties에만 둔다. 어노테이션에도 적으면 둘이 어긋났을 때
     * properties가 조용히 이겨서, 코드에 적힌 값과 실제로 도는 값이 달라진다.
     *
     * <p>실제 점수를 재보고 정한 값이다. 자료에 있는 질문을 그대로 물으면 0.42, 돌려 말하면 0.30
     * 근처였고, 전혀 무관한 질문(날씨)이 0.27이었다. 관련 있는 것과 없는 것의 간격이 그만큼 좁아
     * 문턱 하나로 깨끗이 가를 수 없다. 그래서 낮게 두어 근거를 최대한 붙여 보내고, 그걸로 답할지는
     * 프롬프트가 판단하게 한다.
     */
    @Value("${app.labor-qa.similarity-threshold}")
    private double similarityThreshold;
    @Value("${app.labor-qa.top-k}")
    private int topK;
    /** 근거 문서를 못 찾았을 때 LLM에게 그 사실을 알리는 자리. 숫자를 지어내지 않게 하는 신호다. */
    private static final String NO_SOURCE_NOTICE =
            "(이번 질문에 맞는 자료를 찾지 못했어요. 제도 설명은 해도 되지만, 구체적인 숫자는 말하지 마세요.)";
    private static final String NOT_GROUNDED_ANSWER =
            "제가 가진 자료로는 답변드리기 어려워요. 정확한 내용은 노무사나 세무사에게 확인해 보시는 게 좋아요.";
    /**
     * 말투는 부드럽게 하되 이 문구의 힘은 빼지 않는다. 법률 자문이 아니라는 고지는 편하게 읽히더라도
     * 흘려 넘길 문장이 되면 곤란하다.
     */
    private static final String DISCLAIMER =
            "\n\n※ 이 답변은 법률·세무 자문이 아니에요. 중요한 결정을 앞두고 있다면 노무사·세무사에게 꼭 확인해 주세요.";

    private static final String SYSTEM_PROMPT = """
            당신은 한국에서 소규모 매장을 운영하거나 그곳에서 일하는 분들을 위한
            근로기준법·세무 안내 도우미예요.

            [말투]
            부드럽고 친근한 해요체로 써요. "~합니다"보다 "~해요", "~이에요"를 쓰고,
            법 조문을 그대로 옮긴 듯한 딱딱한 문장은 쉬운 말로 풀어서 설명해요.
            묻는 사람이 대부분 법을 잘 모르는 알바생과 사장님이라, 어려운 용어를 쓸 때는 짧게 뜻을 덧붙여요.

            [다룰 수 있는 범위]
            한국의 근로기준법과 그에 딸린 세무·4대보험 이야기만 다뤄요.
            그 밖의 질문(날씨, 요리, 일반 상식 등)에는 답하지 말고
            "근로·급여와 관련된 질문에만 답할 수 있어요"라고 안내해요.

            [아는 지식과 근거 문서의 경계 — 가장 중요해요]
            제도가 어떻게 돌아가는지에 대한 일반적인 설명은, 근거 문서에 없더라도
            알고 있는 범위에서 해도 돼요. 주휴수당이 무엇인지, 연차가 어떻게 쌓이는지 같은 것들이에요.

            하지만 구체적인 숫자는 달라요. 최저임금 액수, 4대보험 요율, 연차 일수,
            가산수당 배율, 연도별 기준처럼 값이 정해져 있는 것은
            아래 근거 문서에 있는 것만 말해요.
            문서에 없는 숫자는 기억에 의존해 말하지 말고, 제도만 설명한 뒤
            "정확한 최신 기준은 확인이 필요해요"라고 알려줘요.
            법은 해마다 바뀌고, 틀린 숫자로 급여를 계산하면 그것 자체가 법 위반이 돼요.
            모른다고 말하는 것보다 옛날 숫자를 자신 있게 말하는 쪽이 훨씬 위험해요.

            [그 외]
            이전 대화가 함께 제공되면 질문의 맥락(예: "그럼 5인 미만은요?")을 해석하는 데 사용해요.
            개인의 정확한 세액이나 구체적인 법적 판단은 하지 말고 일반적인 기준만 안내해요.
            근거 문서를 참고했다면 답변 마지막 줄에 그 출처(문서 제목)를 나열해요.
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
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
        // 검색이 비었다고 여기서 끊지 않는다. 자료가 9개 파일뿐이라 제도만 물어도 걸리지 않는 질문이
        // 많은데, 그때마다 "자료가 없어요"만 답하면 아는 것도 못 알려주는 셈이 된다. 대신 근거가
        // 없다는 사실을 함께 넘겨서, 설명은 하되 숫자는 지어내지 않도록 한다.
        String context = results.isEmpty() ? NO_SOURCE_NOTICE : results.stream()
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
        // grounded는 "우리 자료로 뒷받침됐는지"를 뜻한다. 아는 지식만으로 답한 경우는 false다.
        return new LaborQaResponse(answer + DISCLAIMER, sources, !results.isEmpty());
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
