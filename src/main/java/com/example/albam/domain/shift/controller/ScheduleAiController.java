package com.example.albam.domain.shift.controller;

import com.example.albam.domain.shift.dto.ConfirmScheduleDraftRequest;
import com.example.albam.domain.shift.dto.ConfirmScheduleDraftResult;
import com.example.albam.domain.shift.dto.ScheduleDraftRequest;
import com.example.albam.domain.shift.dto.ScheduleDraftResponse;
import com.example.albam.domain.shift.service.ScheduleAiService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.ratelimit.RateLimit;
import com.example.albam.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/shifts/ai-draft")
@RequiredArgsConstructor
public class ScheduleAiController {

    private final ScheduleAiService scheduleAiService;

    /** AI 스케줄 초안 생성 (OWNER/MANAGER). 저장되지 않으며, 기존 법정 검증을 통과한 항목만 accepted로 내려온다. */
    @RateLimit(limit = 5, windowMinutes = 10)
    @PostMapping
    public ApiResponse<ScheduleDraftResponse> generateDraft(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody ScheduleDraftRequest request) {
        return ApiResponse.success(scheduleAiService.generateDraft(storeId, userId, request));
    }

    /** 사용자가 확인/수정한 초안을 실제 스케줄로 확정 저장 (OWNER/MANAGER). */
    @PostMapping("/confirm")
    public ApiResponse<ConfirmScheduleDraftResult> confirmDraft(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody ConfirmScheduleDraftRequest request) {
        return ApiResponse.success(scheduleAiService.confirmDraft(storeId, userId, request));
    }
}
