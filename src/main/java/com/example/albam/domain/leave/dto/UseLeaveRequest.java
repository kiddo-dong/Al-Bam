package com.example.albam.domain.leave.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UseLeaveRequest(@NotNull LocalDate leaveDate) {
}
