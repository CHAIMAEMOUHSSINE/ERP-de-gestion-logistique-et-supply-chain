package com.logismart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

public final class AiDtos {

    private AiDtos() {}

    public record WeekPoint(LocalDate weekStart, double quantity) {}

    /**
     * status : CRITICAL (rupture avant l'arrivée d'un réapprovisionnement), WARNING ou OK.
     * trend : UP, DOWN ou STABLE.
     */
    public record ForecastResponse(
            Long productId, String sku, String name, int stockQty, int leadTimeDays,
            double avgDailyDemand, double forecastDailyDemand, int forecast30d, String trend,
            Double daysOfCover, LocalDate stockoutDate, int suggestedReorderQty, String status,
            List<WeekPoint> history, List<WeekPoint> forecast) {}

    public record DelayPredictionRequest(
            @NotBlank String destinationCity, @Positive double weightKg, LocalDate shipDate) {}

    public record CarrierRisk(String carrier, double probability, String level) {}

    public record DelayPredictionResponse(
            int distanceKm, int transitDays, List<CarrierRisk> carriers, String recommendedCarrier) {}

    public record Insight(String severity, String title, String detail) {}
}
