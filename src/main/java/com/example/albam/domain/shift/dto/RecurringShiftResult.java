package com.example.albam.domain.shift.dto;

import java.util.List;

public record RecurringShiftResult(List<ShiftResponse> created, List<SkippedShiftDate> skipped) {
}
