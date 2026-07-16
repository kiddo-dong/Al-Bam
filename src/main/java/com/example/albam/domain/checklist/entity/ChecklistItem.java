package com.example.albam.domain.checklist.entity;

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

/** 오픈/마감 체크리스트 항목 마스터. 한 번 등록하면 매일 반복된다. */
@Getter
@Entity
@Table(name = "checklist_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChecklistType type;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private int displayOrder;

    public ChecklistItem(Store store, ChecklistType type, String content, int displayOrder) {
        this.store = store;
        this.type = type;
        this.content = content;
        this.displayOrder = displayOrder;
    }

    public void update(ChecklistType type, String content, int displayOrder) {
        this.type = type;
        this.content = content;
        this.displayOrder = displayOrder;
    }
}
