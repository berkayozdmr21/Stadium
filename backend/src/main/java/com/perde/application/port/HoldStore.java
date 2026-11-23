package com.perde.application.port;

import com.perde.domain.model.Hold;
import com.perde.domain.model.HoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HoldStore {

    Hold create(Long eventId, String tokenHash, Instant now, Instant expiresAt);

    Optional<Hold> findByTokenHash(String tokenHash);

    /**
     * Tutma satırını işlem bitene kadar kilitler (SELECT ... FOR UPDATE).
     * Aynı tutma üzerindeki ekleme, çıkarma, satın alma ve süre dolumu böylece sıraya girer.
     */
    Optional<Hold> lockByTokenHash(String tokenHash);

    Optional<Hold> lockById(Long holdId);

    void updateStatus(Long holdId, HoldStatus status);

    List<Long> findOverdueIds(Instant now, int limit);
}
