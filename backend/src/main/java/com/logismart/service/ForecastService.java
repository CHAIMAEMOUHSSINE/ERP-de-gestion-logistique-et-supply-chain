package com.logismart.service;

import com.logismart.ai.HoltForecaster;
import com.logismart.domain.MovementType;
import com.logismart.domain.Product;
import com.logismart.domain.StockMovement;
import com.logismart.dto.AiDtos.ForecastResponse;
import com.logismart.dto.AiDtos.WeekPoint;
import com.logismart.repository.ProductRepository;
import com.logismart.repository.StockMovementRepository;
import com.logismart.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Prévision de la demande par produit à partir des sorties de stock liées aux commandes.
 * Série hebdomadaire sur 12 semaines -> lissage de Holt amorti -> 4 semaines de prévision.
 * Dérive ensuite la couverture de stock, la date de rupture estimée et la quantité à réapprovisionner
 * (niveau cible + stock de sécurité à 95 %).
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    static final int HISTORY_WEEKS = 12;
    static final int FORECAST_WEEKS = 4;
    private static final double SERVICE_LEVEL_Z = 1.65;
    private static final int REVIEW_DAYS = 14;

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public List<ForecastResponse> forecastAll() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(HISTORY_WEEKS * 7L - 1);
        Map<Long, double[]> weekly = weeklyDemand(start);

        List<ForecastResponse> result = new ArrayList<>();
        for (Product p : productRepository.findAll()) {
            result.add(build(p, weekly.getOrDefault(p.getId(), new double[HISTORY_WEEKS]), start, today));
        }
        result.sort(Comparator
                .comparingInt((ForecastResponse f) -> statusRank(f.status()))
                .thenComparing(ForecastResponse::daysOfCover, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    @Transactional(readOnly = true)
    public ForecastResponse forecastOne(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable : " + productId));
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(HISTORY_WEEKS * 7L - 1);
        double[] series = weeklyDemand(start).getOrDefault(productId, new double[HISTORY_WEEKS]);
        return build(product, series, start, today);
    }

    private Map<Long, double[]> weeklyDemand(LocalDate start) {
        Map<Long, double[]> weekly = new HashMap<>();
        List<StockMovement> demand = movementRepository.findDemand(
                MovementType.OUT, StockMovement.REASON_ORDER, start.atStartOfDay());
        for (StockMovement m : demand) {
            long days = ChronoUnit.DAYS.between(start, m.getCreatedAt().toLocalDate());
            int week = (int) Math.min(HISTORY_WEEKS - 1, Math.max(0, days / 7));
            weekly.computeIfAbsent(m.getProduct().getId(), k -> new double[HISTORY_WEEKS])[week] += m.getQuantity();
        }
        return weekly;
    }

    private ForecastResponse build(Product p, double[] weekly, LocalDate start, LocalDate today) {
        HoltForecaster.Result holt = HoltForecaster.forecast(weekly, FORECAST_WEEKS);

        double recentAvg = average(weekly, HISTORY_WEEKS - 4, HISTORY_WEEKS);
        double forecastAvg = average(holt.forecast(), 0, holt.forecast().length);
        double avgDaily = recentAvg / 7.0;
        double dailyForecast = forecastAvg / 7.0;

        String trend = "STABLE";
        if (recentAvg > 1e-9) {
            double ratio = forecastAvg / recentAvg;
            trend = ratio > 1.1 ? "UP" : ratio < 0.9 ? "DOWN" : "STABLE";
        } else if (forecastAvg > 1e-9) {
            trend = "UP";
        }

        int lead = p.getLeadTimeDays();
        double sigmaDaily = holt.rmse() / Math.sqrt(7.0);
        double safetyStock = SERVICE_LEVEL_Z * sigmaDaily * Math.sqrt(lead);
        double orderUpTo = dailyForecast * (lead + REVIEW_DAYS) + safetyStock;
        int suggested = (int) Math.max(0, Math.ceil(orderUpTo - p.getStockQty()));

        Double cover = null;
        LocalDate stockout = null;
        String status = "OK";
        if (dailyForecast > 0.01) {
            cover = round1(p.getStockQty() / dailyForecast);
            stockout = today.plusDays((long) Math.floor(p.getStockQty() / dailyForecast));
            if (cover < lead) {
                status = "CRITICAL";
            } else if (cover < lead + REVIEW_DAYS) {
                status = "WARNING";
            }
        }

        List<WeekPoint> history = new ArrayList<>();
        for (int i = 0; i < weekly.length; i++) {
            history.add(new WeekPoint(start.plusDays(7L * i), round1(weekly[i])));
        }
        List<WeekPoint> forecast = new ArrayList<>();
        for (int i = 0; i < holt.forecast().length; i++) {
            forecast.add(new WeekPoint(start.plusDays(7L * (HISTORY_WEEKS + i)), round1(holt.forecast()[i])));
        }

        return new ForecastResponse(p.getId(), p.getSku(), p.getName(), p.getStockQty(), lead,
                round1(avgDaily), round1(dailyForecast), (int) Math.round(dailyForecast * 30), trend,
                cover, stockout, suggested, status, history, forecast);
    }

    private static int statusRank(String status) {
        return switch (status) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    private static double average(double[] values, int from, int to) {
        if (to <= from) {
            return 0;
        }
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += values[i];
        }
        return sum / (to - from);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
