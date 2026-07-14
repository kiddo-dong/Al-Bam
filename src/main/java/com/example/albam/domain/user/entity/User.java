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

    private String profileImageUrl;

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
        this.profileImageUrl = null;
        this.providerId = null;
        this.emailVerified = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public void changeProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
