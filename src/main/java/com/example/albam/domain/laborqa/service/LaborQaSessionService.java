package com.example.albam.domain.laborqa.service;

import com.example.albam.domain.laborqa.dto.LaborQaSessionDetailResponse;
import com.example.albam.domain.laborqa.dto.LaborQaSessionResponse;
import com.example.albam.domain.laborqa.entity.LaborQaMessage;
import com.example.albam.domain.laborqa.entity.LaborQaRole;
import com.example.albam.domain.laborqa.entity.LaborQaSession;
import com.example.albam.domain.laborqa.repository.LaborQaMessageRepository;
import com.example.albam.domain.laborqa.repository.LaborQaSessionRepository;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 근로기준법 Q&A 대화 세션의 CRUD와 메시지 영속화를 담당한다. LLM 호출 자체는 {@link LaborQaService}가 한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LaborQaSessionService {

    private final LaborQaSessionRepository sessionRepository;
    private final LaborQaMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public LaborQaSessionResponse createSession(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        return LaborQaSessionResponse.from(sessionRepository.save(new LaborQaSession(user)));
    }

    public List<LaborQaSessionResponse> getMySessions(Long userId) {
        return sessionRepository.findAllByUserIdOrderByIdDesc(userId).stream()
                .map(LaborQaSessionResponse::from)
                .toList();
    }

    public LaborQaSessionDetailResponse getSession(Long sessionId, Long userId) {
        LaborQaSession session = requireOwnedSession(sessionId, userId);
        return LaborQaSessionDetailResponse.from(session,
                messageRepository.findAllBySessionIdOrderByIdAsc(sessionId));
    }

    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        LaborQaSession session = requireOwnedSession(sessionId, userId);
        messageRepository.deleteBySessionId(session.getId());
        sessionRepository.delete(session);
    }

    /** 소유자 검증. 남의 세션은 존재 여부도 노출하지 않도록 404로 응답한다. */
    LaborQaSession requireOwnedSession(Long sessionId, Long userId) {
        LaborQaSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("대화를 찾을 수 없습니다."));
        if (!session.getUser().getId().equals(userId)) {
            throw new NotFoundException("대화를 찾을 수 없습니다.");
        }
        return session;
    }

    /** 프롬프트용 최근 대화, 오래된 것부터 정렬해 반환. 토큰 비용이 무한히 늘지 않도록 최근 10건(질문+답변 합산)으로 제한. */
    List<LaborQaMessage> loadRecentMessages(Long sessionId) {
        List<LaborQaMessage> recent = messageRepository.findTop10BySessionIdOrderByIdDesc(sessionId);
        return recent.reversed();
    }

    /**
     * 질문·답변 한 쌍을 저장한다. LLM 호출이 끝난 뒤에만 호출되므로, LLM 응답 대기 중에 DB 커넥션을
     * 트랜잭션으로 붙잡아 두지 않는다 (LaborQaService.askInSession 참고).
     */
    @Transactional
    public void recordExchange(Long sessionId, Long userId, String question, String answer,
            List<String> sources) {
        LaborQaSession session = requireOwnedSession(sessionId, userId);
        session.fillTitleIfEmpty(question);
        messageRepository.save(new LaborQaMessage(session, LaborQaRole.USER, question, null));
        messageRepository.save(new LaborQaMessage(session, LaborQaRole.ASSISTANT, answer, sources));
    }
}
