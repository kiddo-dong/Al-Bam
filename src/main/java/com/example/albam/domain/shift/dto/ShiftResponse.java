package com.example.albam.domain.shift.dto;

import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.entity.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalTime;

public record ShiftResponse(
        Long id,
        Long storeMemberId,
        String userName,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        ShiftStatus status
) {
    public static ShiftResponse from(Shift shift) {
        return new ShiftResponse(
                shift.getId(),
                shift.getStoreMember().getId(),
                shift.getStoreMember().getUser().getName(),
                shift.getWorkDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getStatus()
        );
    }
}
