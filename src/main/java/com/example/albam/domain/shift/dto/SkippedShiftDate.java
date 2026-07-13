package com.example.albam.domain.shift.dto;

import java.time.LocalDate;

public record SkippedShiftDate(LocalDate date, String reason) {
}
