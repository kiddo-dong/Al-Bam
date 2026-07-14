package com.example.albam.domain.user.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이메일 인증·비밀번호 재설정용 1회성 토큰. */
@Getter
@Entity
@Table(name = "email_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailTokenType type;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;

    public EmailToken(User user, String token, EmailTokenType type, LocalDateTime expiresAt) {
        this.user = user;
        this.token = token;
        this.type = type;
        this.expiresAt = expiresAt;
        this.used = false;
    }

    public boolean isUsable() {
        return !used && expiresAt.isAfter(LocalDateTime.now());
    }

    public void markUsed() {
        this.used = true;
    }
}
