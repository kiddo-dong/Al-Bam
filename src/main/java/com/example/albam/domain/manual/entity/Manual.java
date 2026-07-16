package com.example.albam.domain.manual.entity;

import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.global.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매장 매뉴얼 (레시피, 기기 사용법 등 영구적인 지식). 카테고리는 매장이 자유롭게 정한다. */
@Getter
@Entity
@Table(name = "manuals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Manual extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private StoreMember author;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int displayOrder;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "manual_images", joinColumns = @JoinColumn(name = "manual_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url")
    private List<String> imageUrls = new ArrayList<>();

    public Manual(Store store, StoreMember author, String category, String title, String content,
            int displayOrder, List<String> imageUrls) {
        this.store = store;
        this.author = author;
        this.category = category;
        this.title = title;
        this.content = content;
        this.displayOrder = displayOrder;
        if (imageUrls != null) {
            this.imageUrls.addAll(imageUrls);
        }
    }

    public void update(String category, String title, String content, int displayOrder,
            List<String> imageUrls) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.displayOrder = displayOrder;
        this.imageUrls.clear();
        if (imageUrls != null) {
            this.imageUrls.addAll(imageUrls);
        }
    }
}
