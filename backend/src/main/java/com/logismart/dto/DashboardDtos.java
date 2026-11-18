package com.logismart.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record Kpis(
            long totalProducts, long lowStockCount, BigDecimal stockValue,
            long pendingOrders, long activeShipments, long atRiskShipments, long overdueShipments,
            BigDecimal revenue30d, double revenueGrowthPct, double onTimeRate) {}

    public record DailyAmount(LocalDate date, BigDecimal amount) {}

    public record ProductQuantity(String name, long quantity) {}

    public record CarrierStat(String carrier, long shipments, double onTimeRate) {}

    public record DashboardResponse(
            Kpis kpis, Map<String, Long> ordersByStatus, List<DailyAmount> revenueByDay,
            List<ProductQuantity> topProducts, List<CarrierStat> carrierPerformance) {}
}
