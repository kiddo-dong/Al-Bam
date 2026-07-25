package com.example.albam.domain.shift.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ConfirmScheduleDraftRequest(
        @NotEmpty @Valid List<ScheduleDraftItem> items
) {
}
