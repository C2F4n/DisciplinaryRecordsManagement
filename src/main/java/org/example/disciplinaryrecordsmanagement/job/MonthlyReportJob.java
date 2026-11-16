package org.example.disciplinaryrecordsmanagement.job;

import org.example.disciplinaryrecordsmanagement.config.ReportPolicy;
import org.example.disciplinaryrecordsmanagement.service.MonthlyReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.report.enabled", havingValue = "true", matchIfMissing = true)
class MonthlyReportJob {
    private static final Logger LOG = LoggerFactory.getLogger(MonthlyReportJob.class);

    private final MonthlyReportService reports;
    private final ReportPolicy policy;

    MonthlyReportJob(MonthlyReportService reports, ReportPolicy policy) {
        this.reports = reports;
        this.policy = policy;
    }

    @Scheduled(cron = "${app.report.poll-cron}", zone = "${app.policy.time-zone}")
    void checkDueMonths() {
        for (int i = 0; i < policy.maxMonthsPerRun(); i++) {
            var month = reports.nextDueMonth();
            if (month == null) return;
            if (reports.seal(month)) LOG.info("Monthly report sealed: month={}", month);
        }
    }
}
