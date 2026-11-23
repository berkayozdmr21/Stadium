package com.stadyum.domain.model;

import com.stadyum.domain.DomainException;
import java.time.Duration;
import java.time.Instant;

/** Koltuk tutma kuralları: ne kadar süre ve en fazla kaç koltuk. */
public record HoldPolicy(Duration duration, int maxSeats) {

    public HoldPolicy {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Tutma süresi pozitif olmalı");
        }
        if (maxSeats < 1) {
            throw new IllegalArgumentException("En az bir koltuk tutulabilmeli");
        }
    }

    /** Süre ilk koltukla başlar; sonradan koltuk eklemek süreyi uzatmaz. */
    public Instant expiryFrom(Instant now) {
        return now.plus(duration);
    }

    public void ensureCanAdd(int currentSeatCount) {
        if (currentSeatCount >= maxSeats) {
            throw DomainException.maxSeatsExceeded(maxSeats);
        }
    }
}
