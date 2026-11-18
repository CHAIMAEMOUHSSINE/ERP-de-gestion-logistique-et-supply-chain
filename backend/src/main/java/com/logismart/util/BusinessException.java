package com.logismart.util;

/** Règle métier violée (stock insuffisant, transition d'état invalide...). Renvoyée en HTTP 409. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
