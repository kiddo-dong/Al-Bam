package com.example.albam.domain.user.entity;

import com.example.albam.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String phone;

    private LocalDate birthDate;

    private LocalDateTime termsAgreedAt;

    /** 프로필 이미지의 S3 key (전체 URL이 아님). 공개 URL은 응답을 만들 때 조립한다. */
    private String profileImageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(name = "provider_id")
    private String providerId;

    /** 이메일 인증 여부. 소셜 가입은 provider가 이미 검증했으므로 true로 시작한다. */
    @Column(nullable = false)
    private boolean emailVerified;

    /** 탈퇴 시각. 근무 이력 보존을 위해 행을 지우지 않고 개인정보만 익명화한다. */
    private LocalDateTime deletedAt;

    public User(String email, String password, String name, String phone, LocalDate birthDate,
            LocalDateTime termsAgreedAt) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.termsAgreedAt = termsAgreedAt;
        this.provider = AuthProvider.LOCAL;
        this.emailVerified = false;
    }

    public User(String email, String name, AuthProvider provider, String providerId) {
        this.email = email;
        this.name = name;
        this.provider = provider;
        this.providerId = providerId;
        this.emailVerified = true;
    }

    /**
     * 인증을 마치지 않은 로컬 계정에 같은 이메일로 다시 가입할 때, 기존 행을 새 입력으로 덮어쓴다.
     *
     * <p>새 행을 만드는 대신 덮어쓰는 이유는 이메일이 unique이기 때문이다. 인증 전 계정은 소유자가
     * 증명된 적이 없어(메일함 접근을 보인 적이 없다) 이렇게 덮어써도 남의 계정을 건드리는 것이
     * 아니다. 덮어써도 여전히 인증을 통과해야 로그인할 수 있으므로 가로채기에도 쓰이지 않는다.
     */
    public void resubmitSignup(String password, String name, String phone, LocalDate birthDate) {
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.termsAgreedAt = LocalDateTime.now();
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    /**
     * 프로필 입력 완료 여부. 소셜 가입 직후에는 전화번호·생년월일·약관동의가 비어 있으며,
     * 입력 도중 창이 닫혀도 재로그인 후 이 값으로 미완성 상태를 감지해 이어서 입력받는다.
     */
    public boolean isProfileCompleted() {
        return phone != null && birthDate != null && termsAgreedAt != null;
    }

    /** 소셜 가입 등으로 비어 있는 추가 정보를 채우고 약관 동의 시각을 기록한다. */
    public void completeProfile(String name, String phone, LocalDate birthDate) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.phone = phone;
        this.birthDate = birthDate;
        this.termsAgreedAt = LocalDateTime.now();
    }

    /**
     * 탈퇴 처리: 개인정보를 익명화하고 로그인 불가능한 상태로 만든다.
     * 이메일은 unique 제약을 유지하면서 원래 주소를 해제하기 위해 대체 값으로 바꾼다.
     */
    public void anonymizeForWithdrawal() {
        this.email = "deleted-" + id + "@withdrawn.albam";
        this.name = "탈퇴회원";
        this.password = null;
        this.phone = null;
        this.birthDate = null;
        this.profileImageKey = null;
        this.providerId = null;
        this.emailVerified = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 프로필 수정.
     *
     * <p>생년월일은 값이 왔을 때만 바꾼다. 안 보냈다고 비워버리면 {@link #isProfileCompleted()}가
     * false가 되어 그때부터 서비스 이용이 막히는데, 이름만 고치려던 사람에게 일어날 일은 아니다.
     * (이름과 전화번호는 요청에서 필수라 항상 값이 있다.)
     */
    public void updateProfile(String name, String phone, LocalDate birthDate) {
        this.name = name;
        this.phone = phone;
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }

    public void changeProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }
}
