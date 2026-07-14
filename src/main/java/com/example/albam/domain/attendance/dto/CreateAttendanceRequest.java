package com.example.albam.domain.attendance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateAttendanceRequest(
        @NotNull Long storeMemberId,
        @NotNull LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        @Min(0) Integer breakMinutes
) {
}
