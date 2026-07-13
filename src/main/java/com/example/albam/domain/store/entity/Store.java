package com.example.albam.domain.store.entity;

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
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "stores")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;

    private String businessRegistrationNumber;

    @Enumerated(EnumType.STRING)
    private StoreCategory category;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "store_business_hours", joinColumns = @JoinColumn(name = "store_id"))
    @MapKeyColumn(name = "day_of_week")
    @MapKeyEnumerated(EnumType.STRING)
    private Map<DayOfWeek, BusinessHour> businessHours = new HashMap<>();

    @Column(nullable = false, unique = true, length = 6)
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakPolicy breakPolicy;

    /** 상시 근로자 5인 미만 사업장 여부. 5인 미만은 연장·야간·휴일 가산수당과 주 52시간 상한이 적용되지 않는다. */
    @Column(nullable = false)
    private boolean smallBusiness;

    public Store(String name, String address, String businessRegistrationNumber, StoreCategory category,
            Map<DayOfWeek, BusinessHour> businessHours, String inviteCode, BreakPolicy breakPolicy,
            Boolean smallBusiness) {
        this.name = name;
        this.address = address;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.category = category;
        if (businessHours != null) {
            this.businessHours = businessHours;
        }
        this.inviteCode = inviteCode;
        this.breakPolicy = breakPolicy == null ? BreakPolicy.STATUTORY : breakPolicy;
        this.smallBusiness = Boolean.TRUE.equals(smallBusiness);
    }

    public void changeInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public void update(String name, String address, String businessRegistrationNumber, StoreCategory category,
            Map<DayOfWeek, BusinessHour> businessHours, BreakPolicy breakPolicy, Boolean smallBusiness) {
        this.name = name;
        this.address = address;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.category = category;
        this.businessHours.clear();
        if (businessHours != null) {
            this.businessHours.putAll(businessHours);
        }
        this.breakPolicy = breakPolicy == null ? BreakPolicy.STATUTORY : breakPolicy;
        this.smallBusiness = Boolean.TRUE.equals(smallBusiness);
    }
}
