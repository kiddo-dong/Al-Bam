package com.example.albam.domain.menu.entity;

import com.example.albam.domain.store.entity.Store;
import com.example.albam.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** 메뉴 원가 계산용 재료 단가. 구매가와 구매 용량으로 단위당 단가를 계산한다 (로스율 반영). */
@Getter
@Entity
@Table(name = "menu_ingredients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuIngredient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private String name;

    /** 구매 제품 정보 (예: "SizeUp · 과일프로젝트 딸기청 1.4kg"). */
    private String productInfo;

    /** 구매가 (원). */
    @Column(nullable = false)
    private int price;

    /** 구매 용량 (unit 기준). */
    @Column(nullable = false)
    private double packageQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngredientUnit unit;

    /** 로스율 % (0~99). 손질·증발 등으로 실제 사용 가능한 양이 줄어드는 비율. */
    @Column(nullable = false)
    private int lossRate;

    /** 자유 카테고리 (원두/커피, 유제품 등 매장이 직접 정의). */
    @Column(nullable = false)
    private String category;

    public MenuIngredient(Store store, String name, String productInfo, int price, double packageQty,
            IngredientUnit unit, int lossRate, String category) {
        this.store = store;
        this.name = name;
        this.productInfo = productInfo;
        this.price = price;
        this.packageQty = packageQty;
        this.unit = unit;
        this.lossRate = lossRate;
        this.category = category;
    }

    public void update(String name, String productInfo, int price, double packageQty, IngredientUnit unit,
            int lossRate, String category) {
        this.name = name;
        this.productInfo = productInfo;
        this.price = price;
        this.packageQty = packageQty;
        this.unit = unit;
        this.lossRate = lossRate;
        this.category = category;
    }

    /** 단위당 단가 = 구매가 ÷ (구매 용량 × (1 - 로스율)). 사용 가능량이 0 이하이면 0. */
    public double unitCost() {
        double usableQty = packageQty * (1 - lossRate / 100.0);
        return usableQty <= 0 ? 0 : price / usableQty;
    }
}
