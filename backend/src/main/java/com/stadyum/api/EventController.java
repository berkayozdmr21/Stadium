package com.stadyum.api;

import com.stadyum.application.service.CatalogService;
import com.stadyum.application.view.EventSummary;
import com.stadyum.application.view.LayoutView;
import com.stadyum.application.view.StadiumView;
import com.stadyum.application.view.StadiumView.BlockSeats;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class EventController {

    private final CatalogService catalog;

    EventController(CatalogService catalog) {
        this.catalog = catalog;
    }

    /** Stadyumun sabit yapısı (bloklar, sıralar, geometri). Maçtan bağımsızdır. */
    @GetMapping("/stadium")
    LayoutView layout() {
        return catalog.layout();
    }

    @GetMapping("/events")
    List<EventSummary> list() {
        return catalog.listEvents();
    }

    /** Bir maçın blok bazında dolulukları. Ziyaretçi anahtarını gönderirse kendi tutması da gelir. */
    @GetMapping("/events/{slug}")
    StadiumView stadium(@PathVariable String slug,
                        @RequestHeader(name = HoldController.TOKEN_HEADER, required = false) String token) {
        return catalog.stadium(slug, token);
    }

    /** Bir bloğun tek tek koltukları. Arayüz bloğa girildiğinde ister. */
    @GetMapping("/events/{slug}/blocks/{block}")
    BlockSeats blockSeats(@PathVariable String slug, @PathVariable String block,
                          @RequestHeader(name = HoldController.TOKEN_HEADER, required = false) String token) {
        return catalog.blockSeats(slug, block, token);
    }
}
