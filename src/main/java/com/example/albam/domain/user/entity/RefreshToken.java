package com.example.albam.domain.user.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발급된 리프레시 토큰의 기록. 이 행이 있어야만 재발급이 되므로, 행을 지우는 것이 곧 토큰 폐기다.
 *
 * <p>이 테이블이 없던 시절에는 리프레시 토큰이 순수한 JWT여서 서명과 만료만으로 검증됐다. 그러면 서버가
 * 토큰을 무효화할 방법이 없어, 로그아웃해도(쿠키만 지움) 유출된 토큰은 만료일까지 계속 쓸 수 있었다.
 *
 * <p>토큰 원문이 아니라 SHA-256 해시를 저장한다. DB가 유출되더라도 그대로 쓸 수 있는 토큰이 함께
 * 넘어가지 않게 하기 위함이다. 토큰은 서명된 JWT라 엔트로피가 충분하므로 비밀번호와 달리 솔트나
 * 반복 해싱은 필요 없다.
 */
@Getter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 토큰 원문의 SHA-256 16진 표현(64자). */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * 만료 시각. 검증은 JWT 자체의 만료로도 걸리지만, 지난 행을 주기적으로 지우려면 DB에도 있어야 한다.
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
}
