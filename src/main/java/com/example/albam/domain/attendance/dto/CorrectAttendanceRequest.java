package com.example.albam.domain.attendance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CorrectAttendanceRequest(
        @NotNull LocalDateTime clockInAt,
        LocalDateTime clockOutAt
) {
}
