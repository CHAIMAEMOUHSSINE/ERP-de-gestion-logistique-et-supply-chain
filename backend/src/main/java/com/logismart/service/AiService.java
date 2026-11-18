package com.logismart.service;

import com.logismart.ai.DelayRiskModel;
import com.logismart.ai.DelayRiskModel.Sample;
import com.logismart.domain.ShipmentStatus;
import com.logismart.dto.AiDtos.*;
import com.logismart.dto.ShipmentDtos.ShipmentResponse;
import com.logismart.repository.ShipmentRepository;
import com.logismart.util.Carriers;
import com.logismart.util.Cities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final DelayRiskModel riskModel;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;
    private final ForecastService forecastService;

    /** Entraîne le modèle de retard au démarrage sur l'historique des livraisons. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        DelayRiskModel.Report report = retrain();
        if (report.trained()) {
            log.info("Modèle de retard entraîné : {} échantillons, précision {}, AUC {}",
                    report.trainSize() + report.testSize(),
                    Math.round(report.accuracy() * 100) / 100.0, Math.round(report.auc() * 100) / 100.0);
        } else {
            log.info("Pas assez de livraisons pour entraîner le modèle (minimum {}).",
                    DelayRiskModel.MIN_TRAINING_SAMPLES);
        }
    }

    public DelayRiskModel.Report retrain() {
        List<Sample> samples = shipmentRepository.findByStatus(ShipmentStatus.DELIVERED).stream()
                .filter(s -> s.getDelayed() != null)
                .map(ShipmentService::toSample)
                .toList();
        return riskModel.train(samples);
    }

    public DelayRiskModel.Report modelReport() {
        return riskModel.report();
    }

    /** Simulateur : risque de retard pour chaque transporteur sur une destination, un poids et une date. */
    public DelayPredictionResponse predictDelay(DelayPredictionRequest request) {
        if (!Cities.exists(request.destinationCity())) {
            throw new IllegalArgumentException("Ville non desservie : " + request.destinationCity());
        }
        int distance = Cities.roadDistanceKm(request.destinationCity());
        LocalDate shipDate = request.shipDate() != null ? request.shipDate() : LocalDate.now().plusDays(1);

        List<CarrierRisk> carriers = new ArrayList<>();
        for (String carrier : Carriers.ALL) {
            double p = riskModel.predict(new Sample(distance, request.weightKg(), carrier, shipDate, false));
            carriers.add(new CarrierRisk(carrier, Math.round(p * 1000) / 1000.0, DelayRiskModel.riskLevel(p)));
        }
        carriers.sort(Comparator.comparingDouble(CarrierRisk::probability));
        return new DelayPredictionResponse(distance, Cities.transitDays(distance), carriers,
                carriers.get(0).carrier());
    }

    /** Synthèse en langage naturel des signaux importants (stocks, retards, tendances). */
    public List<Insight> insights() {
        List<Insight> insights = new ArrayList<>();
        List<ForecastResponse> forecasts = forecastService.forecastAll();

        List<ForecastResponse> critical = forecasts.stream().filter(f -> "CRITICAL".equals(f.status())).toList();
        if (!critical.isEmpty()) {
            String detail = critical.stream().limit(3)
                    .map(f -> f.name() + " (rupture vers le " + f.stockoutDate() + ", délai fournisseur "
                            + f.leadTimeDays() + " j)")
                    .reduce((a, b) -> a + " ; " + b).orElse("");
            insights.add(new Insight("HIGH",
                    critical.size() + " produit(s) en rupture probable avant réapprovisionnement", detail));
        }

        long warnings = forecasts.stream().filter(f -> "WARNING".equals(f.status())).count();
        if (warnings > 0) {
            insights.add(new Insight("MEDIUM", warnings + " produit(s) à surveiller",
                    "Leur couverture de stock passe sous le délai fournisseur augmenté de 14 jours."));
        }

        List<ShipmentResponse> active = shipmentService.listActive();
        List<ShipmentResponse> highRisk = active.stream().filter(s -> "HIGH".equals(s.riskLevel())).toList();
        if (!highRisk.isEmpty()) {
            String detail = highRisk.stream().limit(3)
                    .map(s -> s.reference() + " vers " + s.destinationCity() + " ("
                            + Math.round(s.riskScore() * 100) + " %)")
                    .reduce((a, b) -> a + " ; " + b).orElse("");
            insights.add(new Insight("HIGH", highRisk.size() + " expédition(s) à fort risque de retard", detail));
        }

        long overdue = active.stream().filter(ShipmentResponse::overdue).count();
        if (overdue > 0) {
            insights.add(new Insight("HIGH", overdue + " expédition(s) en retard",
                    "La date de livraison promise est dépassée. Prévenez les clients concernés."));
        }

        List<ForecastResponse> rising = forecasts.stream()
                .filter(f -> "UP".equals(f.trend()) && f.avgDailyDemand() > 0
                        && f.forecastDailyDemand() >= f.avgDailyDemand() * 1.2)
                .limit(3).toList();
        if (!rising.isEmpty()) {
            insights.add(new Insight("INFO", "Demande en hausse",
                    "Hausse attendue de plus de 20 % pour : "
                            + rising.stream().map(ForecastResponse::name).reduce((a, b) -> a + ", " + b).orElse("")));
        }

        DelayRiskModel.Report report = riskModel.report();
        if (report.trained()) {
            insights.add(new Insight("INFO", "Modèle de retard à jour",
                    String.format("Entraîné sur %d livraisons : précision %.0f %%, AUC %.2f (taux de retard historique %.0f %%).",
                            report.trainSize() + report.testSize(), report.accuracy() * 100, report.auc(),
                            report.baseRate() * 100)));
        }

        if (insights.isEmpty()) {
            insights.add(new Insight("INFO", "Aucune alerte", "Stocks et expéditions sont sous contrôle."));
        }
        return insights;
    }
}
