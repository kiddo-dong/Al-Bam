package com.example.albam.domain.checklist.entity;

import com.example.albam.domain.storemember.entity.StoreMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 날짜별 체크 기록 (항목당 하루 1건). 누가 언제 체크했는지 남는다. */
@Getter
@Entity
@Table(name = "checklist_completions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "work_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private ChecklistItem item;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by", nullable = false)
    private StoreMember checkedBy;

    @Column(nullable = false)
    private LocalDateTime checkedAt;

    public ChecklistCompletion(ChecklistItem item, LocalDate workDate, StoreMember checkedBy) {
        this.item = item;
        this.workDate = workDate;
        this.checkedBy = checkedBy;
        this.checkedAt = LocalDateTime.now();
    }
}
