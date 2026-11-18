package com.logismart.ai;

/**
 * Lissage exponentiel double de Holt avec tendance amortie (damped trend).
 * Utilisé pour prévoir la demande hebdomadaire de chaque produit.
 */
public final class HoltForecaster {

    public record Result(double[] forecast, double rmse, double level, double trend) {}

    private HoltForecaster() {}

    public static Result forecast(double[] y, int horizon) {
        return forecast(y, horizon, 0.5, 0.3, 0.9);
    }

    /**
     * @param y       série historique (une valeur par période)
     * @param horizon nombre de périodes à prévoir
     * @param alpha   lissage du niveau (0..1)
     * @param beta    lissage de la tendance (0..1)
     * @param phi     amortissement de la tendance (0..1), évite les extrapolations explosives
     */
    public static Result forecast(double[] y, int horizon, double alpha, double beta, double phi) {
        int n = y.length;
        if (n == 0) {
            return new Result(new double[horizon], 0, 0, 0);
        }

        double level = y[0];
        double trend = 0;
        if (n > 1) {
            int k = Math.min(3, n - 1);
            trend = (y[k] - y[0]) / k;
        }

        double sse = 0;
        int count = 0;
        for (int t = 1; t < n; t++) {
            double predicted = level + phi * trend;
            double error = y[t] - predicted;
            sse += error * error;
            count++;

            double previousLevel = level;
            level = alpha * y[t] + (1 - alpha) * predicted;
            trend = beta * (level - previousLevel) + (1 - beta) * phi * trend;
        }

        double rmse = count > 0 ? Math.sqrt(sse / count) : 0;

        double[] forecast = new double[horizon];
        double damp = 1;
        double cumulative = 0;
        for (int h = 0; h < horizon; h++) {
            damp *= phi;
            cumulative += damp;
            forecast[h] = Math.max(0, level + cumulative * trend);
        }
        return new Result(forecast, rmse, level, trend);
    }
}
