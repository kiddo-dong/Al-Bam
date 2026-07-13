package com.example.albam.domain.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AttendanceReportEntry(
        Long shiftId,
        Long attendanceId,
        Long storeMemberId,
        String userName,
        LocalDate workDate,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        WorkComplianceStatus status,
        long lateMinutes,
        long earlyLeaveMinutes
) {
}
