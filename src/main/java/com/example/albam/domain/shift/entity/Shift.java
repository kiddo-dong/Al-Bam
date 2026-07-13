package com.example.albam.domain.shift.entity;

import com.example.albam.domain.storemember.entity.StoreMember;
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
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "shifts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shift extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_member_id", nullable = false)
    private StoreMember storeMember;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status;

    public Shift(StoreMember storeMember, LocalDate workDate, LocalTime startTime, LocalTime endTime) {
        validateTimeRange(startTime, endTime);
        this.storeMember = storeMember;
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = ShiftStatus.SCHEDULED;
    }

    public void update(LocalDate workDate, LocalTime startTime, LocalTime endTime, ShiftStatus status) {
        validateTimeRange(startTime, endTime);
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new InvalidRequestException("종료 시각은 시작 시각 이후여야 합니다.");
        }
    }
}
