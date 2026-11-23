package com.stadyum.domain.model;

public enum HoldStatus {
    /** Süresi dolmadıysa koltuk eklenebilir, çıkarılabilir, satın alınabilir. */
    ACTIVE,
    /** Satın almayla sonuçlandı. */
    CONFIRMED,
    /** Ziyaretçi kendisi bıraktı. */
    RELEASED,
    /** Süresi doldu, koltuklar yeniden satışa açıldı. */
    EXPIRED
}
