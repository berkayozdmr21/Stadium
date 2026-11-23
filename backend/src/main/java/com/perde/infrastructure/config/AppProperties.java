package com.perde.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** `perde.*` ayarları. Hepsinin güvenli bir varsayılanı vardır. */
@ConfigurationProperties(prefix = "perde")
public record AppProperties(
        @DefaultValue("300") int holdSeconds,
        @DefaultValue("6") int maxSeats,
        @DefaultValue("http://localhost:5173") List<String> allowedOrigins,
        @DefaultValue Seed seed,
        @DefaultValue Expiry expiry,
        @DefaultValue Crowd crowd,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Realtime realtime) {

    /** Boş veritabanına örnek etkinlikleri yükler. */
    public record Seed(@DefaultValue("true") boolean enabled) {
    }

    /** Süresi dolan tutmaları temizleyen iş. */
    public record Expiry(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("2000") long intervalMs,
            @DefaultValue("200") int batchSize) {
    }

    /** Demo kalabalığı: gerçek ziyaretçi yokken de haritanın canlı görünmesi için sahte ziyaretçiler. */
    public record Crowd(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("1500") long intervalMs,
            @DefaultValue("8") int maxActive,
            @DefaultValue("0.6") double maxSoldRatio) {
    }

    /** Yazma isteklerinde IP başına dakikalık sınır. */
    public record RateLimit(@DefaultValue("240") int requestsPerMinute) {
    }

    public record Realtime(@DefaultValue("2000") int maxConnections) {
    }
}
