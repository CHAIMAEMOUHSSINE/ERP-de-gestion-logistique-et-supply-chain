package com.logismart.util;

import java.util.List;

/** Transporteurs partenaires. */
public final class Carriers {

    public static final List<String> ALL = List.of(
            "TransAtlas", "Express Maghreb", "RapidoCargo", "SaharaFreight");

    private Carriers() {}

    public static boolean exists(String name) {
        return ALL.contains(name);
    }
}
