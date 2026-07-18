package com.example.albam.domain.supplier.entity;

import com.example.albam.global.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발주 품목: 이 거래처에서 정기적으로 시키는 물건과 요일별 발주량.
 * 신입도 "무슨 요일에 뭘 얼마나 시키는지" 목록만 보고 발주할 수 있게 하는 지식 데이터다.
 * 수량은 "2박스", "10개"처럼 단위가 제각각이라 자유 텍스트로 둔다.
 */
@Getter
@Entity
@Table(name = "supplier_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(nullable = false)
    private String name;

    /** 규격/단위 설명 (예: "1L × 12개입", "1박스=100장"). */
    private String spec;

    private String memo;

    @Column(nullable = false)
    private int displayOrder;

    /** 요일별 발주량 (자유 텍스트). 발주 없는 요일은 키 자체가 없다. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "supplier_item_quantities", joinColumns = @JoinColumn(name = "item_id"))
    @MapKeyColumn(name = "day_of_week")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "quantity")
    private Map<DayOfWeek, String> weeklyQuantities = new HashMap<>();

    public SupplierItem(Supplier supplier, String name, String spec, String memo, int displayOrder,
            Map<DayOfWeek, String> weeklyQuantities) {
        this.supplier = supplier;
        this.name = name;
        this.spec = spec;
        this.memo = memo;
        this.displayOrder = displayOrder;
        if (weeklyQuantities != null) {
            this.weeklyQuantities = weeklyQuantities;
        }
    }

    public void update(String name, String spec, String memo, int displayOrder,
            Map<DayOfWeek, String> weeklyQuantities) {
        this.name = name;
        this.spec = spec;
        this.memo = memo;
        this.displayOrder = displayOrder;
        this.weeklyQuantities.clear();
        if (weeklyQuantities != null) {
            this.weeklyQuantities.putAll(weeklyQuantities);
        }
    }
}
