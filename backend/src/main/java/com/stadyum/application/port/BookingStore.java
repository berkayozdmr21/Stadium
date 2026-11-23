package com.stadyum.application.port;

import com.stadyum.domain.model.Booking;
import java.util.Optional;

public interface BookingStore {

    Booking save(Booking booking);

    Optional<Booking> findByCode(String code);
}
