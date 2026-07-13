package com.example.albam.domain.attendance.dto;

import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id,
        Long storeMemberId,
        String userName,
        LocalDate workDate,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        AttendanceStatus status
) {
    public static AttendanceResponse from(Attendance attendance) {
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getStoreMember().getId(),
                attendance.getStoreMember().getUser().getName(),
                attendance.getWorkDate(),
                attendance.getClockInAt(),
                attendance.getClockOutAt(),
                attendance.getStatus()
        );
    }
}
