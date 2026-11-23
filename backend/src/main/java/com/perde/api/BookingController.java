package com.perde.api;

import com.perde.application.service.BookingService;
import com.perde.application.view.BookingView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
class BookingController {

    private final BookingService bookings;

    BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping("/{code}")
    BookingView byCode(@PathVariable String code) {
        return bookings.findByCode(code);
    }
}
