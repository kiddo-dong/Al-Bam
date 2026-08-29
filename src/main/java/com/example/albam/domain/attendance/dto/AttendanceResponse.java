package com.example.albam.domain.attendance.dto;

import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id,
        Long storeMemberId,
        String userName,
        /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        LocalDate workDate,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        int breakMinutes,
        AttendanceStatus status
) {
    public static AttendanceResponse from(Attendance attendance, String userProfileImageUrl) {
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getStoreMember().getId(),
                attendance.getStoreMember().getUser().getName(),
                userProfileImageUrl,
                attendance.getWorkDate(),
                attendance.getClockInAt(),
                attendance.getClockOutAt(),
                attendance.getBreakMinutes(),
                attendance.getStatus()
        );
    }
}
