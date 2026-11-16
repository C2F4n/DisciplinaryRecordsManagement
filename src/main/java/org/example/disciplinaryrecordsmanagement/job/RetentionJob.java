package org.example.disciplinaryrecordsmanagement.job;

import org.example.disciplinaryrecordsmanagement.service.RetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.retention.enabled", havingValue = "true")
class RetentionJob {
    private static final Logger LOG = LoggerFactory.getLogger(RetentionJob.class);
    private final RetentionService retention;

    RetentionJob(RetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${app.retention.cron}", zone = "${app.policy.time-zone}")
    void purgeExpiredWeeks() {
        for (var week : retention.nextWeeks()) {
            int removed = retention.purgeWeek(week);
            if (removed > 0) LOG.info("Expired formal records removed: weekStart={} count={}", week, removed);
        }
    }
}
