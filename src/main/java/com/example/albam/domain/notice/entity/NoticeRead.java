package com.example.albam.domain.notice.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공지 확인 기록 (누가 언제 확인 버튼을 눌렀는지). */
@Getter
@Entity
@Table(name = "notice_reads", uniqueConstraints = @UniqueConstraint(columnNames = {"notice_id", "store_member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_member_id", nullable = false)
    private StoreMember storeMember;

    @Column(nullable = false)
    private LocalDateTime readAt;

    public NoticeRead(Notice notice, StoreMember storeMember) {
        this.notice = notice;
        this.storeMember = storeMember;
        this.readAt = LocalDateTime.now();
    }
}
