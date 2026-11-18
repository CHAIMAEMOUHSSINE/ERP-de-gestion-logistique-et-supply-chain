package com.logismart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {}

    public record OrderLineRequest(@NotNull Long productId, @Positive int quantity) {}

    public record OrderRequest(@NotNull Long customerId, @NotEmpty @Valid List<OrderLineRequest> lines) {}

    public record OrderLineResponse(
            Long productId, String sku, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record ShipmentSummary(Long id, String reference, String status, String carrier, LocalDate plannedDelivery) {}

    public record OrderResponse(
            Long id, String reference, Long customerId, String customerName, String city, String status,
            LocalDateTime createdAt, BigDecimal totalAmount, List<OrderLineResponse> lines,
            ShipmentSummary shipment) {}
}
