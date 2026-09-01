package com.example.albam.domain.storemember.entity;

import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.user.entity.User;
import com.example.albam.global.common.BaseTimeEntity;
import com.example.albam.global.exception.InvalidRequestException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "store_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "user_id"}),
        indexes = @Index(name = "idx_store_members_user_id_status", columnList = "user_id, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreMember extends BaseTimeEntity {

    /** 화면에 배지로 들어가는 값이라, 길면 목록이 무너진다. */
    public static final int TITLE_MAX_LENGTH = 20;

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
    private MemberRole role;

    /**
     * 매장이 부르는 직함. "주방장", "홀팀장"처럼 자유롭게 적는다. 안 정했으면 null.
     *
     * <p>권한과는 무관하다. 무엇을 할 수 있는지는 {@code role}만 결정하며, 이 값은 화면에 보이는
     * 이름일 뿐이다. 둘을 섞으면 직함을 고치다 권한이 함께 바뀌어, 이름 하나 바꿨다가 급여 정보가
     * 열리는 일이 생긴다.
     */
    @Column(length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false)
    private int hourlyWage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    /** 퇴사 처리 시각. ACTIVE 상태에서는 null. */
    private LocalDateTime resignedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "store_member_available_days", joinColumns = @JoinColumn(name = "store_member_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> availableDays = new HashSet<>();

    /** 주휴일(유급휴일). 이 요일 근무는 휴일근로 가산 대상이다. 미지정 시 휴일근로 가산 없음. */
    @Enumerated(EnumType.STRING)
    private DayOfWeek weeklyHolidayDay;

    /** 급여 공제 방식 (기본: 공제 없음). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaxMode taxMode;

    public StoreMember(Store store, User user, MemberRole role, int hourlyWage) {
        this.store = store;
        this.user = user;
        this.role = role;
        this.hourlyWage = hourlyWage;
        this.status = MemberStatus.ACTIVE;
        this.joinedAt = LocalDateTime.now();
        this.taxMode = TaxMode.NONE;
    }

    public void changeRole(MemberRole role) {
        this.role = role;
    }

    /** 빈 문자열은 "직함 없음"으로 본다 — 화면에서 지웠을 때 공백이 남지 않게 한다. */
    public void changeTitle(String title) {
        this.title = title == null || title.isBlank() ? null : title.strip();
    }

    public void changeHourlyWage(int hourlyWage) {
        this.hourlyWage = hourlyWage;
    }

    public void changeStatus(MemberStatus status) {
        this.status = status;
        this.resignedAt = status == MemberStatus.INACTIVE ? LocalDateTime.now() : null;
    }

    /** 퇴사 처리: 근무 이력(근태·급여)을 보존하기 위해 행을 지우지 않고 비활성화한다. */
    public void resign() {
        if (this.status == MemberStatus.INACTIVE) {
            throw new InvalidRequestException("이미 퇴사 처리된 멤버입니다.");
        }
        changeStatus(MemberStatus.INACTIVE);
    }

    /** 재입사: 퇴사했던 멤버를 새 역할로 다시 활성화한다. */
    public void rejoin(MemberRole role) {
        this.role = role;
        this.joinedAt = LocalDateTime.now();
        changeStatus(MemberStatus.ACTIVE);
    }

    public void changeAvailableDays(Set<DayOfWeek> availableDays) {
        this.availableDays.clear();
        if (availableDays != null) {
            this.availableDays.addAll(availableDays);
        }
    }

    public void changeWeeklyHolidayDay(DayOfWeek weeklyHolidayDay) {
        this.weeklyHolidayDay = weeklyHolidayDay;
    }

    public void changeTaxMode(TaxMode taxMode) {
        this.taxMode = taxMode;
    }

    public boolean isOwnerOrManager() {
        return role == MemberRole.OWNER || role == MemberRole.MANAGER;
    }
}
