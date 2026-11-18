package com.logismart.dto;

import java.time.LocalDate;
import java.util.List;

public final class ShipmentDtos {

    private ShipmentDtos() {}

    /**
     * riskScore / riskLevel / riskFactors sont renseignés uniquement pour les expéditions
     * non livrées (prédiction du modèle IA).
     */
    public record ShipmentResponse(
            Long id, String reference, Long orderId, String orderReference, String customerName,
            String carrier, String originCity, String destinationCity, int distanceKm, double weightKg,
            String status, LocalDate shipDate, LocalDate plannedDelivery, LocalDate deliveredAt,
            Boolean delayed, boolean overdue, Double riskScore, String riskLevel, List<String> riskFactors) {}
}
