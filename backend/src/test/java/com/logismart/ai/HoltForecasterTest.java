package com.logismart.ai;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class HoltForecasterTest {

    @Test
    void constantSeriesForecastsTheSameLevel() {
        double[] y = new double[12];
        Arrays.fill(y, 10);

        HoltForecaster.Result result = HoltForecaster.forecast(y, 4);

        assertEquals(4, result.forecast().length);
        for (double f : result.forecast()) {
            assertEquals(10, f, 0.5);
        }
        assertEquals(0, result.rmse(), 1e-9);
    }

    @Test
    void risingSeriesForecastsAboveTheLastObservation() {
        double[] y = new double[12];
        for (int i = 0; i < y.length; i++) {
            y[i] = 10 + 2 * i;
        }

        HoltForecaster.Result result = HoltForecaster.forecast(y, 4);

        assertTrue(result.forecast()[0] > y[11], "la première prévision doit dépasser la dernière valeur");
        assertTrue(result.forecast()[3] > result.forecast()[0], "la tendance haussière doit se poursuivre");
    }

    @Test
    void forecastsAreNeverNegative() {
        double[] y = {40, 30, 20, 10, 5, 2, 1, 0, 0, 0, 0, 0};

        HoltForecaster.Result result = HoltForecaster.forecast(y, 6);

        for (double f : result.forecast()) {
            assertTrue(f >= 0);
        }
    }

    @Test
    void emptySeriesGivesZeroForecast() {
        HoltForecaster.Result result = HoltForecaster.forecast(new double[0], 3);

        assertArrayEquals(new double[3], result.forecast());
    }
}
