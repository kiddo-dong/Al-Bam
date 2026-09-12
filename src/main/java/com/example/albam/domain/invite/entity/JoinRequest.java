package com.example.albam.domain.invite.entity;

import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.user.entity.User;
import com.example.albam.global.common.BaseTimeEntity;
import com.example.albam.global.exception.InvalidRequestException;
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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "join_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JoinRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JoinRequestStatus status;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime decidedAt;

    @Enumerated(EnumType.STRING)
    private MemberRole decidedRole;

    /**
     * 낙관적 락 버전. 매니저 둘이 같은 신청을 동시에 승인·거절하면 늦게 저장한 쪽이 이 값이 달라진
     * 것을 보고 실패한다. 없으면 나중 쪽이 앞의 결정을 조용히 덮어써, 거절됐는데 멤버가 생긴 상태가 남는다.
     */
    @Version
    private Long version;

    public JoinRequest(Store store, User user) {
        this.store = store;
        this.user = user;
        this.status = JoinRequestStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    public void approve(MemberRole role) {
        validatePending();
        this.status = JoinRequestStatus.APPROVED;
        this.decidedRole = role;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject() {
        validatePending();
        this.status = JoinRequestStatus.REJECTED;
        this.decidedAt = LocalDateTime.now();
    }

    private void validatePending() {
        if (this.status != JoinRequestStatus.PENDING) {
            throw new InvalidRequestException("이미 처리된 가입 신청입니다.");
        }
    }
}
