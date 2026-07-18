package com.example.albam.domain.menu.entity;

import com.example.albam.domain.store.entity.Store;
import com.example.albam.global.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 판매 메뉴와 레시피(재료 사용량). 재료비·원가율·이익 계산의 단위가 된다. */
@Getter
@Entity
@Table(name = "store_menus")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreMenu extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private String name;

    /** 판매가 (원). */
    @Column(nullable = false)
    private int sellingPrice;

    /** 자유 카테고리 (커피, 티, 디저트 등). */
    @Column(nullable = false)
    private String category;

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<MenuRecipeItem> recipeItems = new ArrayList<>();

    public StoreMenu(Store store, String name, int sellingPrice, String category) {
        this.store = store;
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.category = category;
    }

    public void update(String name, int sellingPrice, String category) {
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.category = category;
    }

    /** 레시피 전체 교체 (계산기 저장 방식과 동일). */
    public void replaceRecipe(List<MenuRecipeItem> items) {
        this.recipeItems.clear();
        this.recipeItems.addAll(items);
    }
}
