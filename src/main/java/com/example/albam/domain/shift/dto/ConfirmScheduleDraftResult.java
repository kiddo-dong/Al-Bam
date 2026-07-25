package com.example.albam.domain.shift.dto;

import java.util.List;

public record ConfirmScheduleDraftResult(
        List<ShiftResponse> created,
        List<RejectedScheduleDraftItem> rejected
) {
}
