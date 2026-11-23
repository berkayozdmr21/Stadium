package com.stadyum.application.service;

import com.stadyum.application.port.BookingStore;
import com.stadyum.application.port.EventStore;
import com.stadyum.application.port.HoldStore;
import com.stadyum.application.port.SeatChangePublisher;
import com.stadyum.application.port.SeatStore;
import com.stadyum.application.port.Secrets;
import com.stadyum.application.view.BookingView;
import com.stadyum.application.view.SeatChange;
import com.stadyum.domain.DomainException;
import com.stadyum.domain.model.Booking;
import com.stadyum.domain.model.Event;
import com.stadyum.domain.model.Hold;
import com.stadyum.domain.model.HoldStatus;
import com.stadyum.domain.model.Seat;
import com.stadyum.domain.model.SeatStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final EventStore events;
    private final SeatStore seats;
    private final HoldStore holds;
    private final BookingStore bookings;
    private final SeatChangePublisher publisher;
    private final Secrets secrets;
    private final HoldLifecycle lifecycle;
    private final Clock clock;

    BookingService(EventStore events, SeatStore seats, HoldStore holds, BookingStore bookings,
                   SeatChangePublisher publisher, Secrets secrets, HoldLifecycle lifecycle, Clock clock) {
        this.events = events;
        this.seats = seats;
        this.holds = holds;
        this.bookings = bookings;
        this.publisher = publisher;
        this.secrets = secrets;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    /**
     * Tutulan koltukları satın alır. Ödeme adımı simülasyondur: gerçek bir ödeme sağlayıcısı çağrılmaz.
     * Tutma satırı kilitli olduğundan, süre dolumu ile satın alma aynı anda gerçekleşemez.
     */
    @Transactional
    public BookingView confirm(String slug, String token, String customerName, String customerEmail) {
        Instant now = clock.instant();
        Event event = events.findBySlug(slug).orElseThrow(DomainException::eventNotFound);
        if (!event.isOnSale(now)) {
            throw DomainException.salesClosed();
        }
        Hold hold = lifecycle.lockUsable(event, token, now).orElseThrow(DomainException::holdExpired);

        List<Seat> held = seats.findByHold(hold.id());
        if (held.isEmpty()) {
            throw DomainException.emptyHold();
        }
        long total = held.stream().mapToLong(Seat::priceKurus).sum();

        Booking booking = bookings.save(new Booking(
                null,
                secrets.newBookingCode(),
                event.id(),
                hold.id(),
                normalizeName(customerName),
                customerEmail.trim().toLowerCase(Locale.ROOT),
                total,
                now));

        int sold = seats.markSold(hold.id(), booking.id());
        if (sold != held.size()) {
            // Tutma kilitliyken olmamalı; olursa hiçbir şeyi kaydetmeden geri al.
            throw new IllegalStateException("Satılan koltuk sayısı tutulanla uyuşmuyor: " + sold + " != " + held.size());
        }
        holds.updateStatus(hold.id(), HoldStatus.CONFIRMED);
        publisher.publish(SeatChange.of(event.slug(), held, SeatStatus.SOLD));

        return toView(booking, event, held);
    }

    /** Bilet kodu tahmin edilemez olduğu için kodu bilen bileti görebilir. */
    @Transactional(readOnly = true)
    public BookingView findByCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        Booking booking = bookings.findByCode(normalized).orElseThrow(DomainException::bookingNotFound);
        Event event = events.findById(booking.eventId()).orElseThrow(DomainException::eventNotFound);
        return toView(booking, event, seats.findByBooking(booking.id()));
    }

    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private static BookingView toView(Booking booking, Event event, List<Seat> seats) {
        return new BookingView(
                booking.code(),
                event.slug(),
                event.title(),
                event.category(),
                event.venue(),
                event.city(),
                event.startsAt(),
                event.hue(),
                seats.stream()
                        .map(s -> new BookingView.TicketSeat(s.blockCode(), s.rowLabel(), s.number(), s.zone().label(), s.priceKurus()))
                        .toList(),
                booking.totalKurus(),
                booking.customerName(),
                booking.createdAt());
    }
}
