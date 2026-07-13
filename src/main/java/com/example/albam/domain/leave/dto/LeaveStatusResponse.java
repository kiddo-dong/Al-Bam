package com.example.albam.domain.leave.dto;

import java.util.List;

public record LeaveStatusResponse(
        int entitledDays,
        int usedDays,
        int remainingDays,
        List<LeaveUsageResponse> usages
) {
}
