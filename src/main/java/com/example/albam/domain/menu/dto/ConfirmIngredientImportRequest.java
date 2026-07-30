package com.example.albam.domain.menu.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 사용자가 미리보기에서 확인/수정한 재료 목록. 기존 재료 생성 요청 DTO를 그대로 재사용한다. */
public record ConfirmIngredientImportRequest(
        @NotEmpty @Valid List<MenuIngredientRequest> items
) {
}
