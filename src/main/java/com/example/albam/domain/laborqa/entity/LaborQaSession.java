package com.example.albam.domain.laborqa.entity;

import com.example.albam.domain.user.entity.User;
import com.example.albam.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 근로기준법 Q&A 대화 세션. 매장이 아니라 사용자 소유의 전역 데이터다. */
@Getter
@Entity
@Table(name = "labor_qa_sessions", indexes = @Index(name = "idx_labor_qa_sessions_user_id", columnList = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaborQaSession extends BaseTimeEntity {

    private static final int TITLE_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 목록 표시용 제목. 첫 질문에서 잘라 자동 생성된다. */
    @Column(length = TITLE_MAX_LENGTH)
    private String title;

    public LaborQaSession(User user) {
        this.user = user;
    }

    /** 첫 질문이 들어올 때 한 번만 제목을 채운다. */
    public void fillTitleIfEmpty(String question) {
        if (this.title == null) {
            this.title = question.length() > TITLE_MAX_LENGTH
                    ? question.substring(0, TITLE_MAX_LENGTH) : question;
        }
    }
}
