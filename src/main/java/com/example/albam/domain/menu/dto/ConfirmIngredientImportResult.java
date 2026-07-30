package com.example.albam.domain.menu.dto;

import java.util.List;

public record ConfirmIngredientImportResult(
        List<MenuIngredientResponse> created,
        List<RejectedIngredientDraftItem> rejected
) {
}
