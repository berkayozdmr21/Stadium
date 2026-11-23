package com.stadyum.api;

import com.stadyum.application.service.BookingService;
import com.stadyum.application.service.HoldService;
import com.stadyum.application.view.BookingView;
import com.stadyum.application.view.HoldState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ziyaretçinin bir etkinlikteki koltuk tutması. Hesap açmak gerekmez: ilk koltukla birlikte sunucu
 * gizli bir anahtar verir, sonraki istekler bu anahtarı {@code X-Hold-Token} başlığında taşır.
 */
@RestController
@RequestMapping("/api/events/{slug}/hold")
class HoldController {

    static final String TOKEN_HEADER = "X-Hold-Token";

    record ConfirmRequest(
            @NotBlank(message = "Ad soyad gerekli.")
            @Size(min = 2, max = 120, message = "Ad soyad 2 ile 120 karakter arasında olmalı.")
            String name,
            @NotBlank(message = "E-posta gerekli.")
            @Email(message = "Geçerli bir e-posta adresi gir.")
            @Size(max = 254, message = "E-posta çok uzun.")
            String email) {
    }

    private final HoldService holds;
    private final BookingService bookings;

    HoldController(HoldService holds, BookingService bookings) {
        this.holds = holds;
        this.bookings = bookings;
    }

    @GetMapping
    HoldState current(@PathVariable String slug,
                      @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return holds.current(slug, token);
    }

    /** PUT: aynı koltuk için tekrar çağrılması sonucu değiştirmez. */
    @PutMapping("/seats/{seatId}")
    HoldState addSeat(@PathVariable String slug, @PathVariable long seatId,
                      @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return holds.addSeat(slug, seatId, token);
    }

    @DeleteMapping("/seats/{seatId}")
    HoldState removeSeat(@PathVariable String slug, @PathVariable long seatId,
                         @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return holds.removeSeat(slug, seatId, token);
    }

    @DeleteMapping
    HoldState releaseAll(@PathVariable String slug,
                         @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return holds.releaseAll(slug, token);
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    BookingView confirm(@PathVariable String slug,
                        @RequestHeader(name = TOKEN_HEADER, required = false) String token,
                        @Valid @RequestBody ConfirmRequest request) {
        return bookings.confirm(slug, token, request.name(), request.email());
    }
}
