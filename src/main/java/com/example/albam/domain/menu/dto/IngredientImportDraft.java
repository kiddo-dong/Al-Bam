package com.example.albam.domain.menu.dto;

import java.util.List;

/** LLM이 엑셀/CSV에서 추출한 재료 목록 JSON을 역직렬화하기 위한 내부 전용 DTO. 신뢰하지 않는 입력이므로
 * 이 값 그대로 저장하지 않고 반드시 서버 검증을 거친 뒤 사용자 확인(confirm)을 받아야 한다. */
public record IngredientImportDraft(
        List<ExtractedIngredient> ingredients,
        String note
) {
    /** unit은 G/ML/EA 문자열, sourceRow는 원본 파일에서의 행 표시(예: "재료시트 3행")로 미리보기 대조용. */
    public record ExtractedIngredient(
            String name,
            String productInfo,
            Integer price,
            Double packageQty,
            String unit,
            Integer lossRate,
            String category,
            String sourceRow
    ) {
    }
}
