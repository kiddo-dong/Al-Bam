package com.example.albam.domain.invite.dto;

import com.example.albam.domain.storemember.entity.MemberRole;
import jakarta.validation.constraints.NotNull;

public record ApproveJoinRequestRequest(@NotNull MemberRole role) {
}
