package com.example.albam.domain.shift.entity;

import com.example.albam.domain.store.entity.Store;
import com.example.albam.global.common.BaseTimeEntity;
import com.example.albam.global.exception.InvalidRequestException;
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
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매장별 시프트 템플릿 ("오픈" 09:00~15:00 등). 스케줄 생성 시 프론트가 템플릿의 시간을
 * 복사해 넣는 프리셋이며, 템플릿을 나중에 수정해도 이미 생성된 스케줄은 바뀌지 않는다.
 */
@Getter
@Entity
@Table(name = "shift_templates", uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "name"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShiftTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    /** 휴게시간(분). null이면 스케줄 생성 시 매장 휴게 정책에 따라 자동 계산에 맡긴다. */
    private Integer breakMinutes;

    @Column(nullable = false)
    private int displayOrder;

    public ShiftTemplate(Store store, String name, LocalTime startTime, LocalTime endTime,
            Integer breakMinutes, int displayOrder) {
        validateTimeRange(startTime, endTime);
        this.store = store;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.displayOrder = displayOrder;
    }

    public void update(String name, LocalTime startTime, LocalTime endTime, Integer breakMinutes,
            int displayOrder) {
        validateTimeRange(startTime, endTime);
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.displayOrder = displayOrder;
    }

    /** 자정을 넘는 야간 템플릿(예: 22:00~06:00) 여부. */
    public boolean isOvernight() {
        return endTime.isBefore(startTime);
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime.equals(endTime)) {
            throw new InvalidRequestException("시작 시각과 종료 시각이 같을 수 없습니다.");
        }
    }
}
