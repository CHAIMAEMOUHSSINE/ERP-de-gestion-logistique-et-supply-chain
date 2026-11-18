package com.logismart.config;

import com.logismart.domain.*;
import com.logismart.repository.*;
import com.logismart.util.Carriers;
import com.logismart.util.Cities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Génère un jeu de données de démonstration cohérent : 90 jours d'historique de commandes,
 * mouvements de stock et expéditions. Les retards sont tirés selon une loi logistique cachée
 * (distance, poids, transporteur, départ en fin de semaine) que le modèle IA doit retrouver.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final int HISTORY_DAYS = 90;

    private record ProductSpec(String sku, String name, String category, double price, double weightKg,
                               int typicalLineQty, double popularity, double weeklyTrend,
                               int leadTimeDays, int reorderLevel) {}

    private static final List<ProductSpec> PRODUCTS = List.of(
            new ProductSpec("CIM-001", "Ciment CPJ 45 (sac 50 kg)", "Ciments", 72, 50, 20, 10, 0.015, 5, 150),
            new ProductSpec("CIM-002", "Ciment blanc (sac 25 kg)", "Ciments", 130, 25, 8, 3, 0.0, 7, 60),
            new ProductSpec("ARM-010", "Fer à béton Ø12 (barre 12 m)", "Aciers", 98, 10.7, 30, 8, 0.025, 6, 200),
            new ProductSpec("ARM-011", "Treillis soudé (panneau)", "Aciers", 210, 22, 10, 4, -0.015, 8, 50),
            new ProductSpec("BRI-020", "Brique creuse 12 trous", "Maçonnerie", 3.2, 2.4, 200, 7, 0.0, 4, 800),
            new ProductSpec("BRI-021", "Parpaing 20 cm", "Maçonnerie", 6.5, 14, 80, 6, 0.01, 4, 300),
            new ProductSpec("GRA-030", "Gravier 8/15 (sac 40 kg)", "Granulats", 38, 40, 25, 5, 0.0, 3, 120),
            new ProductSpec("PEI-040", "Peinture façade 25 L", "Peinture", 420, 30, 4, 4, 0.03, 9, 20),
            new ProductSpec("PEI-041", "Enduit de finition 25 kg", "Peinture", 95, 25, 12, 4, 0.0, 6, 40),
            new ProductSpec("CAR-050", "Carrelage 60x60 (m²)", "Carrelage", 145, 22, 40, 6, 0.02, 10, 150),
            new ProductSpec("CAR-051", "Colle carrelage 25 kg", "Carrelage", 68, 25, 15, 4, 0.0, 5, 50),
            new ProductSpec("PLO-060", "Tube PVC Ø100 (6 m)", "Plomberie", 85, 6.5, 20, 4, -0.025, 6, 60),
            new ProductSpec("PLO-061", "Robinet mélangeur", "Plomberie", 240, 1.2, 6, 2, 0.0, 12, 15),
            new ProductSpec("ELE-070", "Câble électrique 2,5 mm² (100 m)", "Électricité", 390, 9, 5, 4, 0.035, 8, 20),
            new ProductSpec("ELE-071", "Disjoncteur 16 A", "Électricité", 48, 0.15, 30, 3, 0.01, 7, 80),
            new ProductSpec("OUT-080", "Perceuse à percussion", "Outillage", 780, 2.8, 2, 1.5, 0.0, 14, 6),
            new ProductSpec("OUT-081", "Casque de chantier", "Outillage", 35, 0.4, 25, 2, 0.0, 10, 60),
            new ProductSpec("ISO-090", "Isolant thermique (rouleau)", "Isolation", 310, 8, 6, 3, 0.04, 11, 20));

    /** Produits dont le stock final est volontairement très bas, pour la démonstration des alertes IA. */
    private static final Set<String> CRITICAL_SKUS = Set.of("CIM-001", "ARM-010", "CAR-050", "ELE-070");
    private static final Set<String> WARNING_SKUS = Set.of("PEI-040", "ISO-090");

    private record CustomerSpec(String name, String city, double weight) {}

    private static final List<CustomerSpec> CUSTOMERS = List.of(
            new CustomerSpec("Atlas Matériaux", "Casablanca", 3),
            new CustomerSpec("BTP Rabat Construction", "Rabat", 3),
            new CustomerSpec("Kénitra Industrie", "Kénitra", 2),
            new CustomerSpec("El Jadida Bâtiment", "El Jadida", 2),
            new CustomerSpec("Marrakech Bâtiment", "Marrakech", 3),
            new CustomerSpec("Béni Mellal Agri", "Béni Mellal", 1.5),
            new CustomerSpec("Meknès Matériaux", "Meknès", 2),
            new CustomerSpec("Fès Distribution", "Fès", 2.5),
            new CustomerSpec("Tanger Logistics Hub", "Tanger", 2.5),
            new CustomerSpec("Tétouan Négoce", "Tétouan", 1),
            new CustomerSpec("Agadir Négoce", "Agadir", 2),
            new CustomerSpec("Oujda Équipements", "Oujda", 1),
            new CustomerSpec("Nador Import", "Nador", 1),
            new CustomerSpec("Sahara Supplies", "Laâyoune", 0.6),
            new CustomerSpec("Dakhla Pêche & Port", "Dakhla", 0.4));

    /** Fiabilité cachée des transporteurs dans les données générées (plus grand = plus de retards). */
    private static final Map<String, Double> CARRIER_BIAS = Map.of(
            "TransAtlas", -0.3, "Express Maghreb", -0.9, "RapidoCargo", 0.2, "SaharaFreight", 0.8);

    private final AppUserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final SalesOrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final StockMovementRepository movementRepository;
    private final PasswordEncoder passwordEncoder;

    private final Random rnd = new Random(42);
    private final List<StockMovement> movements = new ArrayList<>();
    private int[] stock;
    private double[] demandLast28Days;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        log.info("Initialisation des données de démonstration...");

        userRepository.save(new AppUser("admin", passwordEncoder.encode("admin123"), "Administrateur", Role.ADMIN));
        userRepository.save(new AppUser("manager", passwordEncoder.encode("manager123"),
                "Responsable Logistique", Role.MANAGER));

        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < CUSTOMERS.size(); i++) {
            CustomerSpec c = CUSTOMERS.get(i);
            customers.add(customerRepository.save(new Customer(c.name(), c.city(),
                    "contact" + (i + 1) + "@client.ma",
                    String.format("+212 6%02d %02d %02d %02d", rnd.nextInt(100), rnd.nextInt(100),
                            rnd.nextInt(100), rnd.nextInt(100)))));
        }

        List<Product> products = new ArrayList<>();
        for (ProductSpec s : PRODUCTS) {
            Product p = new Product();
            p.setSku(s.sku());
            p.setName(s.name());
            p.setCategory(s.category());
            p.setUnitPrice(BigDecimal.valueOf(s.price()));
            p.setWeightKg(s.weightKg());
            p.setReorderLevel(s.reorderLevel());
            p.setLeadTimeDays(s.leadTimeDays());
            products.add(productRepository.save(p));
        }

        stock = new int[PRODUCTS.size()];
        demandLast28Days = new double[PRODUCTS.size()];
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // Stock initial au début de la période
        for (int i = 0; i < PRODUCTS.size(); i++) {
            ProductSpec s = PRODUCTS.get(i);
            stock[i] = s.reorderLevel() * 4 + s.typicalLineQty() * 15;
            movements.add(new StockMovement(products.get(i), MovementType.IN, stock[i],
                    StockMovement.REASON_RECEPTION, "Stock initial", today.minusDays(HISTORY_DAYS).atTime(7, 0)));
        }

        // Historique : de J-90 à J-1
        for (int d = -HISTORY_DAYS; d <= -1; d++) {
            LocalDate date = today.plusDays(d);
            int orders = ordersForDay(date.getDayOfWeek());
            for (int k = 0; k < orders; k++) {
                LocalDateTime createdAt = date.atTime(8 + rnd.nextInt(9), rnd.nextInt(60));
                double weeksElapsed = (d + HISTORY_DAYS) / 7.0;
                SalesOrder order = buildOrder(customers, products, createdAt, 1 + rnd.nextInt(3), weeksElapsed);

                if (rnd.nextDouble() < 0.03) {
                    order.setStatus(OrderStatus.CANCELLED);
                    continue;
                }
                applyStockOut(order, products, createdAt, d >= -28);
                createShipment(order, date.plusDays(1), today);
            }
        }

        // Aujourd'hui : 2 commandes confirmées (expéditions planifiées) et 3 en attente
        for (int k = 0; k < 5; k++) {
            LocalDateTime createdAt = now.minusMinutes(20 + rnd.nextInt(280));
            SalesOrder order = buildOrder(customers, products, createdAt, 1 + rnd.nextInt(2), HISTORY_DAYS / 7.0);
            if (k < 2) {
                applyStockOut(order, products, createdAt, true);
                createShipment(order, today.plusDays(1), today);
            }
        }

        // Scénarios de démonstration : quelques produits proches de la rupture
        for (int i = 0; i < PRODUCTS.size(); i++) {
            ProductSpec s = PRODUCTS.get(i);
            double avgDaily = demandLast28Days[i] / 28.0;
            int target;
            if (CRITICAL_SKUS.contains(s.sku())) {
                target = (int) Math.max(2, Math.round(avgDaily * 2.0));
            } else if (WARNING_SKUS.contains(s.sku())) {
                target = (int) Math.max(5, Math.round(avgDaily * (s.leadTimeDays() + 6)));
            } else {
                continue;
            }
            if (stock[i] > target) {
                movements.add(new StockMovement(products.get(i), MovementType.OUT, stock[i] - target,
                        StockMovement.REASON_ADJUST, "Inventaire - écart constaté", now.minusMinutes(5)));
                stock[i] = target;
            }
        }

        for (int i = 0; i < products.size(); i++) {
            products.get(i).setStockQty(stock[i]);
        }
        productRepository.saveAll(products);
        movementRepository.saveAll(movements);

        log.info("Données de démonstration créées : {} produits, {} clients, {} commandes, {} expéditions.",
                products.size(), customers.size(), orderRepository.count(), shipmentRepository.count());
        log.info("Comptes de démonstration : admin / admin123  et  manager / manager123");
    }

    // ------------------------------------------------------------------ génération

    private int ordersForDay(DayOfWeek day) {
        return switch (day) {
            case SUNDAY -> rnd.nextInt(2);
            case SATURDAY -> rnd.nextInt(3);
            default -> 1 + rnd.nextInt(4);
        };
    }

    private SalesOrder buildOrder(List<Customer> customers, List<Product> products, LocalDateTime createdAt,
                                  int lineCount, double weeksElapsed) {
        Customer customer = customers.get(weightedIndex(CUSTOMERS.stream().mapToDouble(CustomerSpec::weight).toArray()));
        SalesOrder order = new SalesOrder();
        order.setReference("TMP-" + UUID.randomUUID());
        order.setCustomer(customer);
        order.setCreatedAt(createdAt);

        double[] popularity = PRODUCTS.stream().mapToDouble(ProductSpec::popularity).toArray();
        List<Integer> chosen = new ArrayList<>();
        while (chosen.size() < lineCount) {
            int idx = weightedIndex(popularity);
            if (!chosen.contains(idx)) {
                chosen.add(idx);
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int idx : chosen) {
            ProductSpec spec = PRODUCTS.get(idx);
            double trend = Math.pow(1 + spec.weeklyTrend(), weeksElapsed);
            int qty = (int) Math.max(1, Math.round(spec.typicalLineQty() * trend * (0.5 + rnd.nextDouble() * 1.5)));
            OrderLine line = new OrderLine(products.get(idx), qty);
            order.addLine(line);
            total = total.add(line.lineTotal());
        }
        order.setTotalAmount(total);
        order = orderRepository.save(order);
        order.setReference(String.format("CMD-%05d", order.getId()));
        return order;
    }

    /** Décrémente le stock (avec réapprovisionnement automatique simulé si besoin) et trace les mouvements. */
    private void applyStockOut(SalesOrder order, List<Product> products, LocalDateTime at, boolean countDemand) {
        for (OrderLine line : order.getLines()) {
            int idx = products.indexOf(line.getProduct());
            ProductSpec spec = PRODUCTS.get(idx);
            if (stock[idx] < line.getQuantity()) {
                int batch = spec.reorderLevel() * 3 + spec.typicalLineQty() * 12;
                stock[idx] += batch;
                movements.add(new StockMovement(line.getProduct(), MovementType.IN, batch,
                        StockMovement.REASON_RECEPTION, "Réception fournisseur", at.minusMinutes(30)));
            }
            stock[idx] -= line.getQuantity();
            movements.add(new StockMovement(line.getProduct(), MovementType.OUT, line.getQuantity(),
                    StockMovement.REASON_ORDER, order.getReference(), at));
            if (countDemand) {
                demandLast28Days[idx] += line.getQuantity();
            }
        }
    }

    private void createShipment(SalesOrder order, LocalDate shipDate, LocalDate today) {
        String destination = order.getCustomer().getCity();
        int distance = Cities.roadDistanceKm(destination);
        double weight = order.getLines().stream()
                .mapToDouble(l -> l.getProduct().getWeightKg() * l.getQuantity()).sum();
        String carrier = Carriers.ALL.get(rnd.nextInt(Carriers.ALL.size()));
        LocalDate planned = shipDate.plusDays(Cities.transitDays(distance));

        // Loi cachée du retard (ce que le modèle IA doit apprendre)
        DayOfWeek dow = shipDate.getDayOfWeek();
        boolean weekend = dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        double logit = -3.6 + 2.0 * distance / 1000.0 + 0.9 * Math.min(weight, 2000) / 1000.0
                + CARRIER_BIAS.get(carrier) + (weekend ? 0.7 : 0);
        boolean delayed = rnd.nextDouble() < 1.0 / (1.0 + Math.exp(-logit));
        LocalDate actual = planned.plusDays(delayed ? 1 + rnd.nextInt(3) : 0);

        Shipment shipment = new Shipment();
        shipment.setReference("TMP-" + UUID.randomUUID());
        shipment.setOrder(order);
        shipment.setCarrier(carrier);
        shipment.setOriginCity(Cities.WAREHOUSE);
        shipment.setDestinationCity(destination);
        shipment.setDistanceKm(distance);
        shipment.setWeightKg(Math.round(weight * 10) / 10.0);
        shipment.setShipDate(shipDate);
        shipment.setPlannedDelivery(planned);

        if (shipDate.isAfter(today)) {
            shipment.setStatus(ShipmentStatus.PLANNED);
            order.setStatus(OrderStatus.CONFIRMED);
        } else if (!actual.isAfter(today)) {
            shipment.setStatus(ShipmentStatus.DELIVERED);
            shipment.setDeliveredAt(actual);
            shipment.setDelayed(delayed);
            order.setStatus(OrderStatus.DELIVERED);
        } else {
            shipment.setStatus(ShipmentStatus.IN_TRANSIT);
            order.setStatus(OrderStatus.SHIPPED);
        }
        shipment = shipmentRepository.save(shipment);
        shipment.setReference(String.format("EXP-%05d", shipment.getId()));
        order.setShipment(shipment);
    }

    private int weightedIndex(double[] weights) {
        double total = Arrays.stream(weights).sum();
        double r = rnd.nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (r <= cumulative) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
