package com.example.albam.domain.menu.dto;

import com.example.albam.domain.menu.entity.IngredientUnit;
import com.example.albam.domain.menu.entity.MenuRecipeItem;
import com.example.albam.domain.menu.entity.StoreMenu;
import java.util.List;

/** 메뉴 + 계산된 원가 지표. 재료비 = Σ(단위단가 × 사용량), 이익 = 판매가 - 재료비. */
public record MenuResponse(
        Long id,
        String name,
        String category,
        int sellingPrice,
        long ingredientCost,
        double ingredientCostRate,
        long profit,
        double profitRate,
        List<RecipeLineResponse> recipe
) {
    public record RecipeLineResponse(
            Long ingredientId,
            String ingredientName,
            IngredientUnit unit,
            double unitCost,
            double amount,
            long lineCost
    ) {
    }

    public static MenuResponse from(StoreMenu menu) {
        List<RecipeLineResponse> lines = menu.getRecipeItems().stream()
                .map(MenuResponse::toLine)
                .toList();
        double rawCost = menu.getRecipeItems().stream().mapToDouble(MenuRecipeItem::lineCost).sum();
        long ingredientCost = Math.round(rawCost);
        int sellingPrice = menu.getSellingPrice();
        long profit = sellingPrice - ingredientCost;
        double costRate = sellingPrice > 0 ? rawCost * 100.0 / sellingPrice : 0;
        double profitRate = sellingPrice > 0 ? profit * 100.0 / sellingPrice : 0;
        return new MenuResponse(menu.getId(), menu.getName(), menu.getCategory(), sellingPrice,
                ingredientCost, costRate, profit, profitRate, lines);
    }

    private static RecipeLineResponse toLine(MenuRecipeItem item) {
        return new RecipeLineResponse(item.getIngredient().getId(), item.getIngredient().getName(),
                item.getIngredient().getUnit(), item.getIngredient().unitCost(), item.getAmount(),
                Math.round(item.lineCost()));
    }
}
