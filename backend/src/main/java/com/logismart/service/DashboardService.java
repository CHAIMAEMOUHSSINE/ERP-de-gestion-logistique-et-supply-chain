package com.logismart.service;

import com.logismart.domain.*;
import com.logismart.dto.DashboardDtos.*;
import com.logismart.dto.ShipmentDtos.ShipmentResponse;
import com.logismart.repository.ProductRepository;
import com.logismart.repository.SalesOrderRepository;
import com.logismart.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository productRepository;
    private final SalesOrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;

    @Transactional(readOnly = true)
    public DashboardResponse build() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // --- Stock
        List<Product> products = productRepository.findAll();
        long lowStock = products.stream().filter(p -> p.getStockQty() <= p.getReorderLevel()).count();
        BigDecimal stockValue = products.stream()
                .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getStockQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- Commandes
        List<SalesOrder> allOrders = orderRepository.findAll();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        allOrders.forEach(o -> byStatus.merge(o.getStatus().name(), 1L, Long::sum));

        List<SalesOrder> valid = allOrders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();
        LocalDateTime since30 = now.minusDays(30);
        LocalDateTime since60 = now.minusDays(60);
        BigDecimal revenue30 = sum(valid.stream().filter(o -> o.getCreatedAt().isAfter(since30)).toList());
        BigDecimal revenuePrev = sum(valid.stream()
                .filter(o -> o.getCreatedAt().isAfter(since60) && !o.getCreatedAt().isAfter(since30)).toList());
        double growth = revenuePrev.signum() > 0
                ? revenue30.subtract(revenuePrev).multiply(BigDecimal.valueOf(100))
                .divide(revenuePrev, 1, RoundingMode.HALF_UP).doubleValue()
                : 0;

        // --- Expéditions
        List<ShipmentResponse> active = shipmentService.listActive();
        long atRisk = active.stream().filter(s -> s.riskScore() != null && s.riskScore() >= 0.5).count();
        long overdue = active.stream().filter(ShipmentResponse::overdue).count();

        List<Shipment> delivered = shipmentRepository.findByStatus(ShipmentStatus.DELIVERED);
        List<Shipment> recentDelivered = delivered.stream()
                .filter(s -> s.getDeliveredAt() != null && !s.getDeliveredAt().isBefore(today.minusDays(60)))
                .toList();
        double onTime = rate(recentDelivered);

        Kpis kpis = new Kpis(products.size(), lowStock, stockValue.setScale(0, RoundingMode.HALF_UP),
                byStatus.get("PENDING"), active.size(), atRisk, overdue,
                revenue30.setScale(0, RoundingMode.HALF_UP), growth, onTime);

        // --- Chiffre d'affaires par jour (30 derniers jours)
        Map<LocalDate, BigDecimal> perDay = new TreeMap<>();
        for (int i = 29; i >= 0; i--) {
            perDay.put(today.minusDays(i), BigDecimal.ZERO);
        }
        for (SalesOrder o : valid) {
            LocalDate d = o.getCreatedAt().toLocalDate();
            if (perDay.containsKey(d)) {
                perDay.merge(d, o.getTotalAmount(), BigDecimal::add);
            }
        }
        List<DailyAmount> revenueByDay = perDay.entrySet().stream()
                .map(e -> new DailyAmount(e.getKey(), e.getValue().setScale(0, RoundingMode.HALF_UP)))
                .toList();

        // --- Top produits (30 jours)
        Map<String, Long> qtyByProduct = new HashMap<>();
        valid.stream().filter(o -> o.getCreatedAt().isAfter(since30))
                .forEach(o -> o.getLines().forEach(l ->
                        qtyByProduct.merge(l.getProduct().getName(), (long) l.getQuantity(), Long::sum)));
        List<ProductQuantity> top = qtyByProduct.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new ProductQuantity(e.getKey(), e.getValue()))
                .toList();

        // --- Performance des transporteurs (90 jours)
        Map<String, List<Shipment>> byCarrier = delivered.stream()
                .filter(s -> s.getDeliveredAt() != null && !s.getDeliveredAt().isBefore(today.minusDays(90)))
                .collect(Collectors.groupingBy(Shipment::getCarrier));
        List<CarrierStat> carriers = byCarrier.entrySet().stream()
                .map(e -> new CarrierStat(e.getKey(), e.getValue().size(), rate(e.getValue())))
                .sorted(Comparator.comparingDouble(CarrierStat::onTimeRate).reversed())
                .toList();

        return new DashboardResponse(kpis, byStatus, revenueByDay, top, carriers);
    }

    private static BigDecimal sum(List<SalesOrder> orders) {
        return orders.stream().map(SalesOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Taux de livraisons à l'heure en pourcentage (0-100, une décimale). */
    private static double rate(List<Shipment> delivered) {
        if (delivered.isEmpty()) {
            return 0;
        }
        long onTime = delivered.stream().filter(s -> !Boolean.TRUE.equals(s.getDelayed())).count();
        return Math.round(onTime * 1000.0 / delivered.size()) / 10.0;
    }
}
