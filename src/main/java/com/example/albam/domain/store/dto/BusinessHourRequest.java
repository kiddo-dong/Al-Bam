package com.example.albam.domain.store.dto;

import java.time.LocalTime;

public record BusinessHourRequest(LocalTime openTime, LocalTime closeTime, boolean closed) {
}
