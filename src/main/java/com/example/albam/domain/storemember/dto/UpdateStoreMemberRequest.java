package com.example.albam.domain.storemember.dto;

import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;
import jakarta.validation.constraints.Size;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.TaxMode;
import java.time.DayOfWeek;
import java.util.Set;

public record UpdateStoreMemberRequest(
        MemberRole role,
        /** 매장이 부르는 직함. 빈 문자열을 보내면 직함을 없앤다. 권한과는 무관하다. */
        @Size(max = StoreMember.TITLE_MAX_LENGTH,
                message = "직함은 " + StoreMember.TITLE_MAX_LENGTH + "자를 넘을 수 없습니다.")
        String title,
        Integer hourlyWage,
        MemberStatus status,
        Set<DayOfWeek> availableDays,
        DayOfWeek weeklyHolidayDay,
        TaxMode taxMode
) {
}
