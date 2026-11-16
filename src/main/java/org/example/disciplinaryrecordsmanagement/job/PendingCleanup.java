package org.example.disciplinaryrecordsmanagement.job;

import org.example.disciplinaryrecordsmanagement.service.ViolationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PendingCleanup {
    private static final Logger LOG = LoggerFactory.getLogger(PendingCleanup.class);

    private final ViolationService records;

    PendingCleanup(ViolationService records) {
        this.records = records;
    }

    @Scheduled(fixedDelayString = "${app.policy.pending-cleanup-delay}")
    void markExpired() {
        int count = records.expirePending();
        if (count > 0) LOG.info("Expired pending violation records: count={}", count);
    }
}
