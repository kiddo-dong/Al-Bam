package com.example.albam.domain.leave.dto;

import com.example.albam.domain.leave.entity.LeaveUsage;
import java.time.LocalDate;

public record LeaveUsageResponse(Long id, LocalDate leaveDate) {

    public static LeaveUsageResponse from(LeaveUsage leaveUsage) {
        return new LeaveUsageResponse(leaveUsage.getId(), leaveUsage.getLeaveDate());
    }
}
