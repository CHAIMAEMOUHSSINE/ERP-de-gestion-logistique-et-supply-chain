package com.logismart.service;

import com.logismart.ai.DelayRiskModel;
import com.logismart.ai.DelayRiskModel.Sample;
import com.logismart.domain.*;
import com.logismart.dto.ShipmentDtos.ShipmentResponse;
import com.logismart.repository.ShipmentRepository;
import com.logismart.util.BusinessException;
import com.logismart.util.Carriers;
import com.logismart.util.Cities;
import com.logismart.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private static final Set<ShipmentStatus> ACTIVE = Set.of(ShipmentStatus.PLANNED, ShipmentStatus.IN_TRANSIT);

    private final ShipmentRepository shipmentRepository;
    private final DelayRiskModel riskModel;

    @Transactional(readOnly = true)
    public List<ShipmentResponse> list(ShipmentStatus status) {
        List<Shipment> shipments = status == null
                ? shipmentRepository.findAllByOrderByShipDateDescIdDesc()
                : shipmentRepository.findByStatusOrderByShipDateDescIdDesc(status);
        return shipments.stream().map(this::toResponse).toList();
    }

    /** Expéditions en cours, triées par risque de retard décroissant. */
    @Transactional(readOnly = true)
    public List<ShipmentResponse> listActive() {
        return shipmentRepository.findByStatusIn(ACTIVE).stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(ShipmentResponse::riskScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShipmentResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ShipmentResponse dispatch(Long id) {
        Shipment shipment = find(id);
        if (shipment.getStatus() != ShipmentStatus.PLANNED) {
            throw new BusinessException("Seule une expédition planifiée peut être expédiée.");
        }
        shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        shipment.getOrder().setStatus(OrderStatus.SHIPPED);
        return toResponse(shipment);
    }

    /**
     * Livre une expédition. Sans delayDays, la date de livraison est aujourd'hui.
     * Avec delayDays (simulation de démonstration), la livraison est enregistrée à la date prévue + N jours,
     * ce qui permet de simuler des retards et d'alimenter le modèle IA.
     */
    @Transactional
    public ShipmentResponse deliver(Long id, Integer delayDays) {
        Shipment shipment = find(id);
        if (shipment.getStatus() != ShipmentStatus.IN_TRANSIT) {
            throw new BusinessException("Seule une expédition en transit peut être livrée.");
        }
        LocalDate deliveredAt = delayDays == null
                ? LocalDate.now()
                : shipment.getPlannedDelivery().plusDays(Math.max(0, delayDays));
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(deliveredAt);
        shipment.setDelayed(deliveredAt.isAfter(shipment.getPlannedDelivery()));
        shipment.getOrder().setStatus(OrderStatus.DELIVERED);
        return toResponse(shipment);
    }

    /**
     * Crée l'expédition d'une commande confirmée. Si aucun transporteur n'est imposé,
     * le modèle IA choisit celui dont le risque de retard est le plus faible.
     */
    @Transactional
    public Shipment createForOrder(SalesOrder order, String requestedCarrier) {
        String destination = order.getCustomer().getCity();
        int distance = Cities.roadDistanceKm(destination);
        double weight = order.getLines().stream()
                .mapToDouble(l -> l.getProduct().getWeightKg() * l.getQuantity()).sum();
        LocalDate shipDate = LocalDate.now().plusDays(1);

        String carrier = requestedCarrier;
        if (carrier == null || carrier.isBlank()) {
            carrier = bestCarrier(distance, weight, shipDate);
        } else if (!Carriers.exists(carrier)) {
            throw new IllegalArgumentException("Transporteur inconnu : " + carrier);
        }

        Shipment shipment = new Shipment();
        shipment.setReference("TMP-" + UUID.randomUUID());
        shipment.setOrder(order);
        shipment.setCarrier(carrier);
        shipment.setOriginCity(Cities.WAREHOUSE);
        shipment.setDestinationCity(destination);
        shipment.setDistanceKm(distance);
        shipment.setWeightKg(Math.round(weight * 10) / 10.0);
        shipment.setShipDate(shipDate);
        shipment.setPlannedDelivery(shipDate.plusDays(Cities.transitDays(distance)));
        shipment = shipmentRepository.save(shipment);
        shipment.setReference(String.format("EXP-%05d", shipment.getId()));
        order.setShipment(shipment);
        return shipment;
    }

    public String bestCarrier(double distanceKm, double weightKg, LocalDate shipDate) {
        String best = Carriers.ALL.get(0);
        double bestRisk = Double.MAX_VALUE;
        for (String carrier : Carriers.ALL) {
            double risk = riskModel.predict(new Sample(distanceKm, weightKg, carrier, shipDate, false));
            if (risk < bestRisk) {
                bestRisk = risk;
                best = carrier;
            }
        }
        return best;
    }

    public static Sample toSample(Shipment s) {
        return new Sample(s.getDistanceKm(), s.getWeightKg(), s.getCarrier(), s.getShipDate(),
                Boolean.TRUE.equals(s.getDelayed()));
    }

    ShipmentResponse toResponse(Shipment s) {
        boolean active = s.getStatus() != ShipmentStatus.DELIVERED;
        boolean overdue = active && s.getPlannedDelivery() != null && s.getPlannedDelivery().isBefore(LocalDate.now());

        Double risk = null;
        String level = null;
        List<String> factors = List.of();
        if (active) {
            Sample sample = toSample(s);
            risk = Math.round(riskModel.predict(sample) * 1000) / 1000.0;
            level = DelayRiskModel.riskLevel(risk);
            factors = riskModel.explain(sample).stream().map(DelayRiskModel.Explanation::label).toList();
        }
        SalesOrder order = s.getOrder();
        return new ShipmentResponse(s.getId(), s.getReference(), order.getId(), order.getReference(),
                order.getCustomer().getName(), s.getCarrier(), s.getOriginCity(), s.getDestinationCity(),
                s.getDistanceKm(), s.getWeightKg(), s.getStatus().name(), s.getShipDate(),
                s.getPlannedDelivery(), s.getDeliveredAt(), s.getDelayed(), overdue, risk, level, factors);
    }

    private Shipment find(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expédition introuvable : " + id));
    }
}
