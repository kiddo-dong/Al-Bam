package com.example.albam.domain.shift.dto;

import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.entity.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalTime;

public record ShiftResponse(
        Long id,
        Long storeMemberId,
        String userName,
        /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean overnight,
        int breakMinutes,
        ShiftStatus status
) {
    public static ShiftResponse from(Shift shift, String userProfileImageUrl) {
        return new ShiftResponse(
                shift.getId(),
                shift.getStoreMember().getId(),
                shift.getStoreMember().getUser().getName(),
                userProfileImageUrl,
                shift.getWorkDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.isOvernight(),
                shift.getBreakMinutes(),
                shift.getStatus()
        );
    }
}
