package com.example.albam.domain.handover.entity;

import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.storemember.entity.StoreMember;
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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 교대 인수인계 노트 ("우유 떨어짐, 6시 예약 있음"). 멤버 누구나 작성한다. */
@Getter
@Entity
@Table(name = "handover_notes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoverNote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private StoreMember author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDate workDate;

    public HandoverNote(Store store, StoreMember author, String content, LocalDate workDate) {
        this.store = store;
        this.author = author;
        this.content = content;
        this.workDate = workDate;
    }
}
