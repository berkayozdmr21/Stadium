package com.perde.application.port;

import com.perde.domain.model.Booking;
import java.util.Optional;

public interface BookingStore {

    Booking save(Booking booking);

    Optional<Booking> findByCode(String code);
}
