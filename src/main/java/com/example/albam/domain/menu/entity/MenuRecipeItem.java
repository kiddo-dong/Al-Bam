package com.example.albam.domain.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 레시피 한 줄: 어떤 재료를 1잔에 얼마나 쓰는지. */
@Getter
@Entity
@Table(name = "menu_recipe_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuRecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private StoreMenu menu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private MenuIngredient ingredient;

    /** 1잔 사용량 (재료의 unit 기준). */
    @Column(nullable = false)
    private double amount;

    public MenuRecipeItem(StoreMenu menu, MenuIngredient ingredient, double amount) {
        this.menu = menu;
        this.ingredient = ingredient;
        this.amount = amount;
    }

    /** 이 줄의 1잔 재료비 = 단위단가 × 사용량. */
    public double lineCost() {
        return ingredient.unitCost() * amount;
    }
}
