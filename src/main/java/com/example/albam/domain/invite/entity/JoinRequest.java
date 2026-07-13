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
