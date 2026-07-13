package com.example.albam.domain.storemember.dto;

import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import java.time.DayOfWeek;
import java.util.Set;

public record UpdateStoreMemberRequest(
        MemberRole role,
        Integer hourlyWage,
        MemberStatus status,
        Set<DayOfWeek> availableDays,
        DayOfWeek weeklyHolidayDay
) {
}
