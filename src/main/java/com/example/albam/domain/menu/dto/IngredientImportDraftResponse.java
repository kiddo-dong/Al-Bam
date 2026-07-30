package com.example.albam.domain.menu.dto;

import java.util.List;

/**
 * 엑셀/CSV에서 AI가 추출한 재료 초안. 저장되지 않으며, 사용자가 미리보기에서 확인·수정한 뒤
 * /menu-import/ingredients/confirm 으로 다시 보내야 실제 등록된다.
 */
public record IngredientImportDraftResponse(
        List<IngredientDraftItem> accepted,
        List<RejectedIngredientDraftItem> rejected,
        String aiNote
) {
}
