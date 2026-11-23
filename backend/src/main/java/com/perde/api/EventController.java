package com.perde.api;

import com.perde.application.service.CatalogService;
import com.perde.application.view.EventSummary;
import com.perde.application.view.SeatMapView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
class EventController {

    private final CatalogService catalog;

    EventController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<EventSummary> list() {
        return catalog.listEvents();
    }

    /** Koltuk haritası. Ziyaretçi anahtarını gönderirse kendi tuttuğu koltuklar işaretli gelir. */
    @GetMapping("/{slug}")
    SeatMapView seatMap(@PathVariable String slug,
                        @RequestHeader(name = HoldController.TOKEN_HEADER, required = false) String token) {
        return catalog.seatMap(slug, token);
    }
}
