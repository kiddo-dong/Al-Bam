package com.example.albam.domain.storemember.entity;

import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.user.entity.User;
import com.example.albam.global.common.BaseTimeEntity;
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
@Table(name = "store_members", uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreMember extends BaseTimeEntity {

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

    @Column(nullable = false)
    private int hourlyWage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "store_member_available_days", joinColumns = @JoinColumn(name = "store_member_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> availableDays = new HashSet<>();

    public StoreMember(Store store, User user, MemberRole role, int hourlyWage) {
        this.store = store;
        this.user = user;
        this.role = role;
        this.hourlyWage = hourlyWage;
        this.status = MemberStatus.ACTIVE;
        this.joinedAt = LocalDateTime.now();
    }

    public void changeRole(MemberRole role) {
        this.role = role;
    }

    public void changeHourlyWage(int hourlyWage) {
        this.hourlyWage = hourlyWage;
    }

    public void changeStatus(MemberStatus status) {
        this.status = status;
    }

    public void changeAvailableDays(Set<DayOfWeek> availableDays) {
        this.availableDays.clear();
        if (availableDays != null) {
            this.availableDays.addAll(availableDays);
        }
    }

    public boolean isOwnerOrManager() {
        return role == MemberRole.OWNER || role == MemberRole.MANAGER;
    }
}
