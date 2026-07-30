package com.example.albam.domain.menu.dto;

import com.example.albam.domain.menu.entity.IngredientUnit;

/** 검증을 통과한 재료 추출 결과 한 건. confirm 시 {@link MenuIngredientRequest}로 변환된다. */
public record IngredientDraftItem(
        String name,
        String productInfo,
        int price,
        double packageQty,
        IngredientUnit unit,
        int lossRate,
        String category,
        String sourceRow
) {
}
