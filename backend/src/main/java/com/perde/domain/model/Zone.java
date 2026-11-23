package com.perde.domain.model;

/** Salondaki fiyat bölgesi. */
public enum Zone {
    VIP("VIP"),
    SALON("Salon"),
    BALKON("Balkon");

    private final String label;

    Zone(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
