package com.stadyum.application.port;

/** Tahmin edilemez değer üretimi ve özetleme. */
public interface Secrets {

    /** Ziyaretçiye verilen, tutmanın sahibi olduğunu kanıtlayan gizli anahtar. */
    String newHoldToken();

    /** Anahtarın veritabanında saklanan özeti; anahtarın kendisi saklanmaz. */
    String hash(String token);

    String newBookingCode();
}
