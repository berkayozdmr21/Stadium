package com.stadyum.domain.model;

/** Fiyat kategorisi. Fiyatlar kurgusaldır; gerçek bilet fiyatlarını yansıtmaz. */
public enum Zone {
    VIP("VIP", 250_000),
    KAT1("Kategori 1", 120_000),
    KAT2("Kategori 2", 80_000),
    KAT3("Kategori 3", 45_000);

    private final String label;
    private final long priceKurus;

    Zone(String label, long priceKurus) {
        this.label = label;
        this.priceKurus = priceKurus;
    }

    public String label() {
        return label;
    }

    public long priceKurus() {
        return priceKurus;
    }
}
