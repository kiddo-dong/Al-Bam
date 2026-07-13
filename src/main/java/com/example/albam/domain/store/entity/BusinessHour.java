package com.example.albam.domain.store.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessHour {

    private LocalTime openTime;

    private LocalTime closeTime;

    @Column(nullable = false)
    private boolean closed;

    public BusinessHour(LocalTime openTime, LocalTime closeTime, boolean closed) {
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.closed = closed;
    }
}
