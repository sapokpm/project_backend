package com.example.shop.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record ReorderCategoriesRequest(@NotEmpty @Valid List<Item> items) {
    public record Item(@NotNull Long id, @NotNull @PositiveOrZero Integer sortOrder) {
    }
}
