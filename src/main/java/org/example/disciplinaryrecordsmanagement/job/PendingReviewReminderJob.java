package org.example.disciplinaryrecordsmanagement.job;

import org.example.disciplinaryrecordsmanagement.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PendingReviewReminderJob {
    private static final Logger LOG = LoggerFactory.getLogger(PendingReviewReminderJob.class);
    private final NotificationService notifications;

    PendingReviewReminderJob(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(cron = "${app.notice.pending-cron}", zone = "${app.policy.time-zone}")
    void remindPending() {
        int created = notifications.remindPending();
        if (created > 0) LOG.info("Pending review reminders sent: count={}", created);
    }
}
