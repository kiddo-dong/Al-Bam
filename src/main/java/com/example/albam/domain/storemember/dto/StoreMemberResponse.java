package com.example.albam.domain.storemember.dto;

import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.entity.TaxMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Set;

public record StoreMemberResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        MemberRole role,
        /** 매장이 부르는 직함. 안 정했으면 null이며, 그때는 역할 이름을 보여주면 된다. */
        String title,
        int hourlyWage,
        MemberStatus status,
        LocalDateTime joinedAt,
        LocalDateTime resignedAt,
        Set<DayOfWeek> availableDays,
        DayOfWeek weeklyHolidayDay,
        TaxMode taxMode
) {
    /** 사진 URL은 엔티티에 없는 값이라(저장된 것은 S3 key다) 서비스가 조립해 넘긴다. */
    public static StoreMemberResponse from(StoreMember member, String userProfileImageUrl) {
        return new StoreMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail(),
                userProfileImageUrl,
                member.getRole(),
                member.getTitle(),
                member.getHourlyWage(),
                member.getStatus(),
                member.getJoinedAt(),
                member.getResignedAt(),
                member.getAvailableDays(),
                member.getWeeklyHolidayDay(),
                member.getTaxMode()
        );
    }
}
