package com.logismart.ai;

import com.logismart.util.Carriers;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Modèle de prédiction du risque de retard de livraison : régression logistique
 * entraînée par descente de gradient (L2), sans dépendance externe.
 *
 * Variables : distance, poids, expédition en fin de semaine, transporteur (one-hot).
 * Le modèle démarre avec des coefficients a priori puis est entraîné sur l'historique
 * des expéditions livrées (voir AiService#retrain).
 */
@Component
public class DelayRiskModel {

    public static final int MIN_TRAINING_SAMPLES = 30;

    public record Sample(double distanceKm, double weightKg, String carrier, LocalDate shipDate, boolean delayed) {}

    public record Coefficient(String feature, double weight) {}

    public record Explanation(String label, double impact) {}

    public record Report(boolean trained, int trainSize, int testSize, double accuracy, double auc,
                         double baseRate, Instant trainedAt, List<Coefficient> coefficients) {}

    private static final List<String> CARRIERS = Carriers.ALL;
    private static final int NUM_FEATURES = 3 + CARRIERS.size();
    private static final int FEATURE_DISTANCE = 0;
    private static final int FEATURE_WEIGHT = 1;
    private static final int FEATURE_WEEKEND = 2;

    private record State(double[] weights, double bias, double[] mean, double[] std, Report report) {}

    private volatile State state = prior();

    // ------------------------------------------------------------------ prédiction

    public double predict(Sample sample) {
        State s = state;
        double[] z = scale(raw(sample), s.mean(), s.std());
        return sigmoid(dot(s.weights(), z) + s.bias());
    }

    public static String riskLevel(double probability) {
        if (probability >= 0.5) {
            return "HIGH";
        }
        if (probability >= 0.25) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /** Principaux facteurs qui augmentent le risque pour cette expédition (explicabilité). */
    public List<Explanation> explain(Sample sample) {
        State s = state;
        double[] z = scale(raw(sample), s.mean(), s.std());
        List<Explanation> result = new ArrayList<>();
        for (int i = 0; i < NUM_FEATURES; i++) {
            double impact = s.weights()[i] * z[i];
            if (impact < 0.1) {
                continue;
            }
            String label = switch (i) {
                case FEATURE_DISTANCE -> "Distance élevée (" + Math.round(sample.distanceKm()) + " km)";
                case FEATURE_WEIGHT -> "Charge lourde (" + Math.round(sample.weightKg()) + " kg)";
                case FEATURE_WEEKEND -> "Départ en fin de semaine";
                default -> "Transporteur moins fiable (" + CARRIERS.get(i - 3) + ")";
            };
            result.add(new Explanation(label, impact));
        }
        result.sort(Comparator.comparingDouble(Explanation::impact).reversed());
        return result.size() > 3 ? List.copyOf(result.subList(0, 3)) : List.copyOf(result);
    }

    public Report report() {
        return state.report();
    }

    // ------------------------------------------------------------------ entraînement

    public synchronized Report train(List<Sample> samples) {
        if (samples.size() < MIN_TRAINING_SAMPLES) {
            return state.report();
        }

        List<Sample> training = new ArrayList<>();
        List<Sample> test = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            (i % 5 == 0 ? test : training).add(samples.get(i));
        }

        double[] mean = new double[NUM_FEATURES];
        double[] std = new double[NUM_FEATURES];
        java.util.Arrays.fill(std, 1.0);
        for (int f : new int[]{FEATURE_DISTANCE, FEATURE_WEIGHT}) {
            double sum = 0;
            for (Sample s : training) {
                sum += raw(s)[f];
            }
            mean[f] = sum / training.size();
            double variance = 0;
            for (Sample s : training) {
                double d = raw(s)[f] - mean[f];
                variance += d * d;
            }
            std[f] = Math.max(1e-6, Math.sqrt(variance / training.size()));
        }

        double[][] x = new double[training.size()][];
        double[] y = new double[training.size()];
        for (int i = 0; i < training.size(); i++) {
            x[i] = scale(raw(training.get(i)), mean, std);
            y[i] = training.get(i).delayed() ? 1 : 0;
        }

        double[] w = new double[NUM_FEATURES];
        double b = 0;
        double learningRate = 0.3;
        double lambda = 0.01;
        int n = training.size();
        for (int epoch = 0; epoch < 3000; epoch++) {
            double[] gradient = new double[NUM_FEATURES];
            double gradientBias = 0;
            for (int i = 0; i < n; i++) {
                double error = sigmoid(dot(w, x[i]) + b) - y[i];
                for (int j = 0; j < NUM_FEATURES; j++) {
                    gradient[j] += error * x[i][j];
                }
                gradientBias += error;
            }
            for (int j = 0; j < NUM_FEATURES; j++) {
                w[j] -= learningRate * (gradient[j] / n + lambda * w[j]);
            }
            b -= learningRate * gradientBias / n;
        }

        // Évaluation sur l'échantillon de test (jamais vu pendant l'entraînement)
        double[] scores = new double[test.size()];
        boolean[] labels = new boolean[test.size()];
        int correct = 0;
        for (int i = 0; i < test.size(); i++) {
            double[] z = scale(raw(test.get(i)), mean, std);
            scores[i] = sigmoid(dot(w, z) + b);
            labels[i] = test.get(i).delayed();
            if ((scores[i] >= 0.5) == labels[i]) {
                correct++;
            }
        }
        double accuracy = test.isEmpty() ? 0 : (double) correct / test.size();
        double baseRate = samples.stream().filter(Sample::delayed).count() / (double) samples.size();

        Report report = new Report(true, training.size(), test.size(), accuracy,
                auc(scores, labels), baseRate, Instant.now(), coefficients(w));
        state = new State(w, b, mean, std, report);
        return report;
    }

    // ------------------------------------------------------------------ utilitaires

    private static State prior() {
        double[] mean = new double[NUM_FEATURES];
        double[] std = new double[NUM_FEATURES];
        java.util.Arrays.fill(std, 1.0);
        mean[FEATURE_DISTANCE] = 400;
        std[FEATURE_DISTANCE] = 400;
        mean[FEATURE_WEIGHT] = 250;
        std[FEATURE_WEIGHT] = 250;

        double[] w = new double[NUM_FEATURES];
        w[FEATURE_DISTANCE] = 0.9;
        w[FEATURE_WEIGHT] = 0.5;
        w[FEATURE_WEEKEND] = 0.4;

        Report report = new Report(false, 0, 0, 0, 0, 0, null, coefficients(w));
        return new State(w, -1.2, mean, std, report);
    }

    private static List<Coefficient> coefficients(double[] w) {
        List<Coefficient> list = new ArrayList<>();
        list.add(new Coefficient("Distance", w[FEATURE_DISTANCE]));
        list.add(new Coefficient("Poids", w[FEATURE_WEIGHT]));
        list.add(new Coefficient("Départ fin de semaine", w[FEATURE_WEEKEND]));
        for (int i = 0; i < CARRIERS.size(); i++) {
            list.add(new Coefficient("Transporteur " + CARRIERS.get(i), w[3 + i]));
        }
        return List.copyOf(list);
    }

    private static double[] raw(Sample s) {
        double[] x = new double[NUM_FEATURES];
        x[FEATURE_DISTANCE] = s.distanceKm();
        x[FEATURE_WEIGHT] = s.weightKg();
        DayOfWeek day = s.shipDate().getDayOfWeek();
        x[FEATURE_WEEKEND] = (day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) ? 1 : 0;
        int carrierIndex = CARRIERS.indexOf(s.carrier());
        if (carrierIndex >= 0) {
            x[3 + carrierIndex] = 1;
        }
        return x;
    }

    private static double[] scale(double[] x, double[] mean, double[] std) {
        double[] z = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            z[i] = (x[i] - mean[i]) / std[i];
        }
        return z;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private static double sigmoid(double v) {
        return 1.0 / (1.0 + Math.exp(-v));
    }

    /** Aire sous la courbe ROC : probabilité qu'un retard réel reçoive un score plus élevé qu'une livraison à l'heure. */
    private static double auc(double[] scores, boolean[] labels) {
        double wins = 0;
        long pairs = 0;
        for (int i = 0; i < scores.length; i++) {
            if (!labels[i]) {
                continue;
            }
            for (int j = 0; j < scores.length; j++) {
                if (labels[j]) {
                    continue;
                }
                pairs++;
                if (scores[i] > scores[j]) {
                    wins += 1;
                } else if (scores[i] == scores[j]) {
                    wins += 0.5;
                }
            }
        }
        return pairs == 0 ? 0.5 : wins / pairs;
    }
}
