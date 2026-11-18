package com.logismart.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ProductDtos {

    private ProductDtos() {}

    public record ProductRequest(
            @NotBlank String sku,
            @NotBlank String name,
            String category,
            @NotNull @DecimalMin("0.0") BigDecimal unitPrice,
            @PositiveOrZero double weightKg,
            @PositiveOrZero int stockQty,
            @PositiveOrZero int reorderLevel,
            @Positive int leadTimeDays) {}

    public record ProductResponse(
            Long id, String sku, String name, String category, BigDecimal unitPrice, double weightKg,
            int stockQty, int reorderLevel, int leadTimeDays, boolean lowStock) {}

    public record ReceiveRequest(@Positive int quantity, String reference) {}

    public record MovementResponse(
            Long id, String type, int quantity, String reason, String reference, LocalDateTime createdAt) {}
}
