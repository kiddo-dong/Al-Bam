package com.example.albam.domain.leave.entity;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 연차 사용 기록. 하루 단위로 기록하며 같은 날짜에 중복 사용할 수 없다. */
@Getter
@Entity
@Table(name = "leave_usages", uniqueConstraints = @UniqueConstraint(columnNames = {"store_member_id", "leave_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveUsage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_member_id", nullable = false)
    private StoreMember storeMember;

    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    public LeaveUsage(StoreMember storeMember, LocalDate leaveDate) {
        this.storeMember = storeMember;
        this.leaveDate = leaveDate;
    }
}
