package com.logismart.web;

import com.logismart.ai.DelayRiskModel;
import com.logismart.dto.AiDtos.*;
import com.logismart.dto.ShipmentDtos.ShipmentResponse;
import com.logismart.service.AiService;
import com.logismart.service.ForecastService;
import com.logismart.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final ForecastService forecastService;
    private final ShipmentService shipmentService;

    /** Prévision de demande et recommandation de réapprovisionnement pour tous les produits. */
    @GetMapping("/forecast")
    public List<ForecastResponse> forecast() {
        return forecastService.forecastAll();
    }

    @GetMapping("/forecast/{productId}")
    public ForecastResponse forecast(@PathVariable Long productId) {
        return forecastService.forecastOne(productId);
    }

    /** Expéditions en cours classées par risque de retard décroissant, avec les facteurs explicatifs. */
    @GetMapping("/delay-risk")
    public List<ShipmentResponse> delayRisk() {
        return shipmentService.listActive();
    }

    /** Simulateur : risque de retard par transporteur pour une destination et un poids donnés. */
    @PostMapping("/predict-delay")
    public DelayPredictionResponse predictDelay(@Valid @RequestBody DelayPredictionRequest request) {
        return aiService.predictDelay(request);
    }

    @GetMapping("/model")
    public DelayRiskModel.Report model() {
        return aiService.modelReport();
    }

    @PostMapping("/retrain")
    public DelayRiskModel.Report retrain() {
        return aiService.retrain();
    }

    @GetMapping("/insights")
    public List<Insight> insights() {
        return aiService.insights();
    }
}
