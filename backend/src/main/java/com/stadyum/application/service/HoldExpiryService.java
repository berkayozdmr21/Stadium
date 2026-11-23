package com.stadyum.application.service;

import com.stadyum.application.port.EventStore;
import com.stadyum.application.port.HoldStore;
import com.stadyum.domain.model.Hold;
import com.stadyum.domain.model.HoldStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Süresi dolan tutmaları kapatıp koltukları satışa geri açar. Zamanlanmış bir iş tarafından çağrılır. */
@Service
public class HoldExpiryService {

    private final HoldStore holds;
    private final EventStore events;
    private final HoldLifecycle lifecycle;
    private final Clock clock;

    HoldExpiryService(HoldStore holds, EventStore events, HoldLifecycle lifecycle, Clock clock) {
        this.holds = holds;
        this.events = events;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Long> findOverdue(int limit) {
        return holds.findOverdueIds(clock.instant(), limit);
    }

    /**
     * Tek bir tutmayı kendi işleminde kapatır. Satırı kilitledikten sonra durumu yeniden denetler:
     * arada satın alındıysa ya da bırakıldıysa dokunmaz.
     *
     * @return gerçekten süresi dolmuş olarak kapatıldıysa true
     */
    @Transactional
    public boolean expire(Long holdId) {
        Instant now = clock.instant();
        Optional<Hold> hold = holds.lockById(holdId).filter(h -> h.isOverdue(now));
        if (hold.isEmpty()) {
            return false;
        }
        String slug = events.findById(hold.get().eventId()).orElseThrow().slug();
        lifecycle.close(hold.get(), slug, HoldStatus.EXPIRED);
        return true;
    }
}
