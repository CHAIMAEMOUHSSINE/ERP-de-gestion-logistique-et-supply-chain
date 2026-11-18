package com.logismart.ai;

import com.logismart.ai.DelayRiskModel.Sample;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DelayRiskModelTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 1, 9);

    private static List<Sample> syntheticHistory(int size) {
        Random rnd = new Random(1);
        String[] carriers = {"TransAtlas", "Express Maghreb", "RapidoCargo", "SaharaFreight"};
        double[] bias = {-0.3, -0.9, 0.2, 0.8};
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            double distance = 50 + rnd.nextInt(1600);
            double weight = 50 + rnd.nextInt(1500);
            int c = rnd.nextInt(carriers.length);
            LocalDate date = MONDAY.plusDays(rnd.nextInt(60));
            DayOfWeek dow = date.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            double logit = -3.6 + 2.0 * distance / 1000.0 + 0.9 * weight / 1000.0 + bias[c] + (weekend ? 0.7 : 0);
            boolean delayed = rnd.nextDouble() < 1.0 / (1.0 + Math.exp(-logit));
            samples.add(new Sample(distance, weight, carriers[c], date, delayed));
        }
        return samples;
    }

    @Test
    void learnsToRankRiskyShipmentsAboveSafeOnes() {
        DelayRiskModel model = new DelayRiskModel();

        DelayRiskModel.Report report = model.train(syntheticHistory(500));

        assertTrue(report.trained());
        assertTrue(report.auc() > 0.7, "AUC attendue > 0.7 mais obtenue " + report.auc());
        assertTrue(report.trainSize() > report.testSize());

        double risky = model.predict(new Sample(1500, 1400, "SaharaFreight", FRIDAY, false));
        double safe = model.predict(new Sample(80, 100, "Express Maghreb", MONDAY, false));
        assertTrue(risky > safe + 0.3, "risky=" + risky + " safe=" + safe);
    }

    @Test
    void probabilitiesStayBetweenZeroAndOne() {
        DelayRiskModel model = new DelayRiskModel();
        model.train(syntheticHistory(300));

        for (Sample s : syntheticHistory(50)) {
            double p = model.predict(s);
            assertTrue(p >= 0 && p <= 1);
        }
    }

    @Test
    void explanationNamesTheMainRiskFactors() {
        DelayRiskModel model = new DelayRiskModel();
        model.train(syntheticHistory(500));

        List<DelayRiskModel.Explanation> factors =
                model.explain(new Sample(1500, 1400, "SaharaFreight", FRIDAY, false));

        assertFalse(factors.isEmpty());
        assertTrue(factors.size() <= 3);
        assertTrue(factors.get(0).label().contains("Distance"), "facteur principal : " + factors.get(0).label());
    }

    @Test
    void doesNotTrainWithTooFewSamples() {
        DelayRiskModel model = new DelayRiskModel();

        DelayRiskModel.Report report = model.train(syntheticHistory(10));

        assertFalse(report.trained());
        // le modèle a priori reste utilisable
        double p = model.predict(new Sample(300, 200, "TransAtlas", MONDAY, false));
        assertTrue(p > 0 && p < 1);
    }

    @Test
    void riskLevelThresholds() {
        assertEquals("LOW", DelayRiskModel.riskLevel(0.1));
        assertEquals("MEDIUM", DelayRiskModel.riskLevel(0.3));
        assertEquals("HIGH", DelayRiskModel.riskLevel(0.7));
    }
}
