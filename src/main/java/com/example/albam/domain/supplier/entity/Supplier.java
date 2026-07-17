package com.example.albam.domain.supplier.entity;

import com.example.albam.domain.store.entity.Store;
import com.example.albam.global.common.BaseTimeEntity;
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

/**
 * 매장 거래처(발주처) 디렉토리. "뭘 어디서 시키는지"를 기록한다.
 * 발주서 작성·재고 추적 같은 발주 관리는 스코프 밖이며, 로그인 계정 정보는 저장하지 않는다.
 */
@Getter
@Entity
@Table(name = "suppliers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supplier extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private String name;

    /** 자유 카테고리 (식자재/포장재/원두 등 매장이 직접 정의). */
    @Column(nullable = false)
    private String category;

    private String siteUrl;

    private String phone;

    /** 발주 요일, 최소주문금액, 담당자 등 자유 기입. */
    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(nullable = false)
    private int displayOrder;

    public Supplier(Store store, String name, String category, String siteUrl, String phone, String memo,
            int displayOrder) {
        this.store = store;
        this.name = name;
        this.category = category;
        this.siteUrl = siteUrl;
        this.phone = phone;
        this.memo = memo;
        this.displayOrder = displayOrder;
    }

    public void update(String name, String category, String siteUrl, String phone, String memo,
            int displayOrder) {
        this.name = name;
        this.category = category;
        this.siteUrl = siteUrl;
        this.phone = phone;
        this.memo = memo;
        this.displayOrder = displayOrder;
    }
}
