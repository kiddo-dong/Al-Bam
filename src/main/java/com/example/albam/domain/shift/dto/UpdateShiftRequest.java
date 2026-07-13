package com.example.albam.domain.shift.dto;

import com.example.albam.domain.shift.entity.ShiftStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateShiftRequest(
        @NotNull LocalDate workDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull ShiftStatus status
) {
}
