package com.example.albam.domain.storemember.dto;

import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Set;

public record StoreMemberResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        MemberRole role,
        int hourlyWage,
        MemberStatus status,
        LocalDateTime joinedAt,
        Set<DayOfWeek> availableDays,
        DayOfWeek weeklyHolidayDay
) {
    public static StoreMemberResponse from(StoreMember member) {
        return new StoreMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail(),
                member.getRole(),
                member.getHourlyWage(),
                member.getStatus(),
                member.getJoinedAt(),
                member.getAvailableDays(),
                member.getWeeklyHolidayDay()
        );
    }
}
