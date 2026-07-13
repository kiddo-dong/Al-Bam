package com.example.albam.domain.payroll.entity;

import com.example.albam.domain.storemember.entity.StoreMember;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payrolls", uniqueConstraints = @UniqueConstraint(
        columnNames = {"store_member_id", "target_year", "target_month"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payroll extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_member_id", nullable = false)
    private StoreMember storeMember;

    @Column(name = "target_year", nullable = false)
    private int targetYear;

    @Column(name = "target_month", nullable = false)
    private int targetMonth;

    @Column(nullable = false)
    private long regularPay;

    @Column(nullable = false)
    private long overtimePay;

    @Column(nullable = false)
    private long nightPay;

    /** 주휴일 근로에 대한 가산수당 (기본급과 별도). */
    @Column(nullable = false)
    private long holidayWorkPay;

    @Column(nullable = false)
    private long weeklyHolidayPay;

    /** 해당 월 연차 사용일에 대한 유급 수당. */
    @Column(nullable = false)
    private long leavePay;

    @Column(nullable = false)
    private long totalPay;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    public Payroll(StoreMember storeMember, int targetYear, int targetMonth) {
        this.storeMember = storeMember;
        this.targetYear = targetYear;
        this.targetMonth = targetMonth;
        this.generatedAt = LocalDateTime.now();
    }

    public void applyResult(long regularPay, long overtimePay, long nightPay, long holidayWorkPay,
            long weeklyHolidayPay, long leavePay) {
        this.regularPay = regularPay;
        this.overtimePay = overtimePay;
        this.nightPay = nightPay;
        this.holidayWorkPay = holidayWorkPay;
        this.weeklyHolidayPay = weeklyHolidayPay;
        this.leavePay = leavePay;
        this.totalPay = regularPay + overtimePay + nightPay + holidayWorkPay + weeklyHolidayPay + leavePay;
        this.generatedAt = LocalDateTime.now();
    }
}
