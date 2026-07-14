package com.example.albam.domain.storemember.controller;

import com.example.albam.domain.storemember.dto.StoreMemberResponse;
import com.example.albam.domain.storemember.dto.StoreMemberSummaryResponse;
import com.example.albam.domain.storemember.dto.UpdateAvailableDaysRequest;
import com.example.albam.domain.storemember.dto.UpdateStoreMemberRequest;
import com.example.albam.domain.storemember.service.StoreMemberService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.security.CurrentUserId;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/members")
@RequiredArgsConstructor
public class StoreMemberController {

    private final StoreMemberService storeMemberService;

    /** 전체 상세 목록 (민감정보 포함) — OWNER 전용. */
    @GetMapping
    public ApiResponse<List<StoreMemberResponse>> getMembers(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(storeMemberService.getMembers(storeId, userId));
    }

    /** 이름·역할만 담은 요약 목록 — 매장 멤버 누구나. */
    @GetMapping("/summary")
    public ApiResponse<List<StoreMemberSummaryResponse>> getMemberSummaries(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(storeMemberService.getMemberSummaries(storeId, userId));
    }

    @PatchMapping("/{memberId}")
    public ApiResponse<StoreMemberResponse> updateMember(@PathVariable Long storeId,
            @PathVariable Long memberId, @CurrentUserId Long userId,
            @RequestBody UpdateStoreMemberRequest request) {
        return ApiResponse.success(storeMemberService.updateMember(storeId, memberId, userId, request));
    }

    @PatchMapping("/me/available-days")
    public ApiResponse<StoreMemberResponse> updateMyAvailableDays(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody UpdateAvailableDaysRequest request) {
        return ApiResponse.success(storeMemberService.updateMyAvailableDays(storeId, userId, request));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> leaveStore(@PathVariable Long storeId, @CurrentUserId Long userId) {
        storeMemberService.leaveStore(storeId, userId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable Long storeId, @PathVariable Long memberId,
            @CurrentUserId Long userId) {
        storeMemberService.removeMember(storeId, memberId, userId);
        return ApiResponse.ok();
    }
}
