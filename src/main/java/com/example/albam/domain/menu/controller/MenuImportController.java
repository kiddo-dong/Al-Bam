package com.example.albam.domain.menu.controller;

import com.example.albam.domain.menu.dto.ConfirmIngredientImportRequest;
import com.example.albam.domain.menu.dto.ConfirmIngredientImportResult;
import com.example.albam.domain.menu.dto.IngredientImportDraftResponse;
import com.example.albam.domain.menu.service.MenuImportService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 엑셀/CSV 원가표 AI 마이그레이션 (OWNER/MANAGER 전용 — 원가는 민감정보). */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/menu-import")
@RequiredArgsConstructor
public class MenuImportController {

    private final MenuImportService menuImportService;

    /** 엑셀(xlsx/xls)/CSV 업로드 → AI가 재료 목록 초안 추출 (저장 안 됨, 미리보기용). */
    @PostMapping("/ingredients/analyze")
    public ApiResponse<IngredientImportDraftResponse> analyzeIngredients(@PathVariable Long storeId,
            @CurrentUserId Long userId, @RequestParam MultipartFile file) {
        return ApiResponse.success(menuImportService.analyze(storeId, userId, file));
    }

    /** 사용자가 미리보기에서 확인/수정한 재료 목록을 실제 등록. */
    @PostMapping("/ingredients/confirm")
    public ResponseEntity<ApiResponse<ConfirmIngredientImportResult>> confirmIngredients(
            @PathVariable Long storeId, @CurrentUserId Long userId,
            @Valid @RequestBody ConfirmIngredientImportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(menuImportService.confirm(storeId, userId, request)));
    }
}
