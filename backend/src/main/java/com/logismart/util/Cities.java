package com.logismart.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Villes marocaines desservies et calcul de distance routière approximative depuis l'entrepôt. */
public final class Cities {

    public record City(String name, double lat, double lon) {}

    public static final String WAREHOUSE = "Casablanca";

    private static final Map<String, City> CITIES = new LinkedHashMap<>();

    static {
        add("Casablanca", 33.5731, -7.5898);
        add("Rabat", 34.0209, -6.8416);
        add("Kénitra", 34.2610, -6.5802);
        add("El Jadida", 33.2316, -8.5007);
        add("Marrakech", 31.6295, -7.9811);
        add("Béni Mellal", 32.3373, -6.3498);
        add("Meknès", 33.8935, -5.5473);
        add("Fès", 34.0181, -5.0078);
        add("Tanger", 35.7595, -5.8340);
        add("Tétouan", 35.5785, -5.3684);
        add("Agadir", 30.4278, -9.5981);
        add("Oujda", 34.6867, -1.9114);
        add("Nador", 35.1681, -2.9335);
        add("Laâyoune", 27.1253, -13.1625);
        add("Dakhla", 23.6848, -15.9579);
    }

    private Cities() {}

    private static void add(String name, double lat, double lon) {
        CITIES.put(name, new City(name, lat, lon));
    }

    public static List<String> names() {
        return List.copyOf(CITIES.keySet());
    }

    public static boolean exists(String name) {
        return CITIES.containsKey(name);
    }

    /** Distance routière estimée (haversine x 1.25) entre l'entrepôt et la ville, en km. */
    public static int roadDistanceKm(String destination) {
        City from = CITIES.get(WAREHOUSE);
        City to = CITIES.get(destination);
        if (to == null) {
            return 300;
        }
        double r = 6371.0;
        double dLat = Math.toRadians(to.lat() - from.lat());
        double dLon = Math.toRadians(to.lon() - from.lon());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(from.lat())) * Math.cos(Math.toRadians(to.lat()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double km = 2 * r * Math.asin(Math.sqrt(a));
        return (int) Math.round(km * 1.25);
    }

    /** Durée de transit estimée en jours (400 km/jour, minimum 1 jour). */
    public static int transitDays(int distanceKm) {
        return Math.max(1, (int) Math.ceil(distanceKm / 400.0));
    }
}
