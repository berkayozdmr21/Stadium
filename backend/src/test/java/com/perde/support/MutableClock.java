package com.perde.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Testlerin zamanı ileri sarabilmesi için elle ilerletilen saat. */
public class MutableClock extends Clock {

    public static final Instant START = Instant.parse("2030-01-01T12:00:00Z");

    private volatile Instant now = START;

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void reset() {
        now = START;
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
