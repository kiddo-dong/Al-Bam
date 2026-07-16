package com.example.albam.domain.checklist.controller;

import com.example.albam.domain.checklist.dto.ChecklistItemRequest;
import com.example.albam.domain.checklist.dto.ChecklistItemResponse;
import com.example.albam.domain.checklist.dto.DailyChecklistEntry;
import com.example.albam.domain.checklist.service.ChecklistService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.security.CurrentUserId;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;

    /** 체크리스트 항목 등록 (OWNER/MANAGER). */
    @PostMapping("/api/v1/stores/{storeId}/checklist-items")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> addItem(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody ChecklistItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(checklistService.addItem(storeId, userId, request)));
    }

    /** 항목 마스터 목록 — 멤버 누구나. */
    @GetMapping("/api/v1/stores/{storeId}/checklist-items")
    public ApiResponse<List<ChecklistItemResponse>> getItems(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(checklistService.getItems(storeId, userId));
    }

    @PatchMapping("/api/v1/stores/{storeId}/checklist-items/{itemId}")
    public ApiResponse<ChecklistItemResponse> updateItem(@PathVariable Long storeId,
            @PathVariable Long itemId, @CurrentUserId Long userId,
            @Valid @RequestBody ChecklistItemRequest request) {
        return ApiResponse.success(checklistService.updateItem(storeId, itemId, userId, request));
    }

    @DeleteMapping("/api/v1/stores/{storeId}/checklist-items/{itemId}")
    public ApiResponse<Void> deleteItem(@PathVariable Long storeId, @PathVariable Long itemId,
            @CurrentUserId Long userId) {
        checklistService.deleteItem(storeId, itemId, userId);
        return ApiResponse.ok();
    }

    /** 특정 날짜(기본 오늘)의 체크 현황 — 멤버 누구나. */
    @GetMapping("/api/v1/stores/{storeId}/checklist")
    public ApiResponse<List<DailyChecklistEntry>> getDailyChecklist(@PathVariable Long storeId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(checklistService.getDailyChecklist(storeId, userId,
                date == null ? LocalDate.now() : date));
    }

    /** 항목 체크 (기본 오늘, 멱등) — 멤버 누구나. */
    @PostMapping("/api/v1/stores/{storeId}/checklist-items/{itemId}/check")
    public ApiResponse<Void> check(@PathVariable Long storeId, @PathVariable Long itemId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        checklistService.check(storeId, itemId, userId, date == null ? LocalDate.now() : date);
        return ApiResponse.ok();
    }

    /** 체크 해제. */
    @DeleteMapping("/api/v1/stores/{storeId}/checklist-items/{itemId}/check")
    public ApiResponse<Void> uncheck(@PathVariable Long storeId, @PathVariable Long itemId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        checklistService.uncheck(storeId, itemId, userId, date == null ? LocalDate.now() : date);
        return ApiResponse.ok();
    }
}
