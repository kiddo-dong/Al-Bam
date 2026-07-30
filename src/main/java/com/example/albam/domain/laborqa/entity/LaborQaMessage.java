package com.example.albam.domain.laborqa.entity;

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
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대화 세션 안의 메시지 한 건 (질문 또는 답변). */
@Getter
@Entity
@Table(name = "labor_qa_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaborQaMessage extends BaseTimeEntity {

    private static final String SOURCE_DELIMITER = "\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private LaborQaSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LaborQaRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 답변(ASSISTANT)의 참고 출처 목록. 출처 제목에 개행이 없으므로 개행으로 이어 붙여 한 컬럼에 저장한다. */
    @Column(columnDefinition = "TEXT")
    private String sources;

    public LaborQaMessage(LaborQaSession session, LaborQaRole role, String content, List<String> sources) {
        this.session = session;
        this.role = role;
        this.content = content;
        this.sources = sources == null || sources.isEmpty() ? null : String.join(SOURCE_DELIMITER, sources);
    }

    public List<String> getSourceList() {
        return sources == null ? List.of() : Arrays.asList(sources.split(SOURCE_DELIMITER));
    }
}
