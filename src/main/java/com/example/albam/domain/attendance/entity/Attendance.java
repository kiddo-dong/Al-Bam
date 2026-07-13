package com.example.albam.domain.attendance.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "attendances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_member_id", nullable = false)
    private StoreMember storeMember;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private LocalDateTime clockInAt;

    private LocalDateTime clockOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    public Attendance(StoreMember storeMember, LocalDateTime clockInAt) {
        this.storeMember = storeMember;
        this.workDate = clockInAt.toLocalDate();
        this.clockInAt = clockInAt;
        this.status = AttendanceStatus.WORKING;
    }

    public void clockOut(LocalDateTime clockOutAt) {
        if (status == AttendanceStatus.DONE) {
            throw new InvalidRequestException("이미 퇴근 처리된 근무입니다.");
        }
        if (!clockOutAt.isAfter(this.clockInAt)) {
            throw new InvalidRequestException("퇴근 시각은 출근 시각 이후여야 합니다.");
        }
        this.clockOutAt = clockOutAt;
        this.status = AttendanceStatus.DONE;
    }
}
