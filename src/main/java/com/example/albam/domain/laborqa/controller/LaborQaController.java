package com.example.albam.domain.laborqa.controller;

import com.example.albam.domain.laborqa.dto.LaborQaRequest;
import com.example.albam.domain.laborqa.dto.LaborQaResponse;
import com.example.albam.domain.laborqa.dto.LaborQaSessionDetailResponse;
import com.example.albam.domain.laborqa.dto.LaborQaSessionResponse;
import com.example.albam.domain.laborqa.service.LaborQaService;
import com.example.albam.domain.laborqa.service.LaborQaSessionService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.ratelimit.RateLimit;
import com.example.albam.global.security.CurrentUserId;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 근로기준법·세무 Q&A. 매장과 무관하게 로그인한 사용자라면 누구나(STAFF 포함) 사용할 수 있다. */
@RestController
@RequestMapping("/api/v1/labor-qa")
@RequiredArgsConstructor
public class LaborQaController {

    private final LaborQaService laborQaService;
    private final LaborQaSessionService sessionService;

    /** 단발 질문 (대화 이력 없음). 간단한 위젯형 UI에 적합. */
    @RateLimit(limit = 10, windowMinutes = 1)
    @PostMapping("/ask")
    public ApiResponse<LaborQaResponse> ask(@Valid @RequestBody LaborQaRequest request) {
        return ApiResponse.success(laborQaService.ask(request));
    }

    /** 대화 세션 생성. 이후 이 세션 안에서 후속 질문("그럼 5인 미만은요?")이 맥락을 유지한다. */
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<LaborQaSessionResponse>> createSession(@CurrentUserId Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(sessionService.createSession(userId)));
    }

    /** 내 대화 목록 (최신순). */
    @GetMapping("/sessions")
    public ApiResponse<List<LaborQaSessionResponse>> getMySessions(@CurrentUserId Long userId) {
        return ApiResponse.success(sessionService.getMySessions(userId));
    }

    /** 대화 상세 (전체 메시지 이력). 본인 세션만 조회 가능. */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<LaborQaSessionDetailResponse> getSession(@PathVariable Long sessionId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(sessionService.getSession(sessionId, userId));
    }

    /** 세션 안에서 질문. 직전 대화가 프롬프트에 포함되고, 질문·답변이 이력으로 저장된다. */
    @RateLimit(limit = 10, windowMinutes = 1)
    @PostMapping("/sessions/{sessionId}/ask")
    public ApiResponse<LaborQaResponse> askInSession(@PathVariable Long sessionId,
            @CurrentUserId Long userId, @Valid @RequestBody LaborQaRequest request) {
        return ApiResponse.success(laborQaService.askInSession(sessionId, userId, request));
    }

    /** 대화 삭제 (메시지 포함 하드 삭제). */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId, @CurrentUserId Long userId) {
        sessionService.deleteSession(sessionId, userId);
        return ApiResponse.ok();
    }
}
