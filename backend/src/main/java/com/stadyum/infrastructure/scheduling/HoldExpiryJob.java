package com.stadyum.infrastructure.scheduling;

import com.stadyum.application.service.HoldExpiryService;
import com.stadyum.infrastructure.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Süresi dolan tutmaları düzenli aralıklarla kapatır. Her tutma kendi işleminde ele alınır. */
@Component
@ConditionalOnProperty(prefix = "stadyum.expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final HoldExpiryService service;
    private final int batchSize;

    HoldExpiryJob(HoldExpiryService service, AppProperties props) {
        this.service = service;
        this.batchSize = props.expiry().batchSize();
    }

    @Scheduled(fixedDelayString = "${stadyum.expiry.interval-ms:2000}")
    void run() {
        int expired = 0;
        for (Long holdId : service.findOverdue(batchSize)) {
            try {
                if (service.expire(holdId)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                // Biri başarısız olursa diğerleri yine işlenir; bu tutma sonraki turda yeniden denenir.
                log.warn("Tutma {} kapatılamadı: {}", holdId, e.getMessage());
            }
        }
        if (expired > 0) {
            log.debug("{} tutmanın süresi doldu", expired);
        }
    }
}
