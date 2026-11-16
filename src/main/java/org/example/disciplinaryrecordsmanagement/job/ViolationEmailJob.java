package org.example.disciplinaryrecordsmanagement.job;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.mail.MessagingException;

import org.example.disciplinaryrecordsmanagement.config.SmtpRuntime;
import org.example.disciplinaryrecordsmanagement.mapper.NotificationEmailMapper;
import org.example.disciplinaryrecordsmanagement.mapper.NotificationEmailMapper.Due;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static org.example.disciplinaryrecordsmanagement.mapper.NotificationEmailMapper.Status.FAILED;
import static org.example.disciplinaryrecordsmanagement.mapper.NotificationEmailMapper.Status.SENT;
import static org.example.disciplinaryrecordsmanagement.mapper.NotificationEmailMapper.Status.SKIPPED;

@Component
class ViolationEmailJob {
    private static final Logger LOG = LoggerFactory.getLogger(ViolationEmailJob.class);
    private static final String SUBJECT = "自律会工作台有一条新通知";
    private static final String BODY = "你好，你在机电工程学院自律会工作台有一条新通知。请登录工作台，在「通知」中查看。";
    private static final int BATCH_SIZE = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LEASE_TIME = Duration.ofMinutes(2);
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    private final NotificationEmailMapper emails;
    private final SmtpRuntime smtp;
    private final Clock clock;

    ViolationEmailJob(NotificationEmailMapper emails, SmtpRuntime smtp, Clock clock) {
        this.emails = emails;
        this.smtp = smtp;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.notice.email.poll-delay}", scheduler = "violationEmailScheduler")
    void deliver() {
        if (!smtp.active()) return;
        var delivery = smtp.delivery();
        for (Due item : emails.due(clock.millis(), BATCH_SIZE)) {
            deliver(item, delivery);
        }
    }

    private void deliver(Due item, SmtpRuntime.Delivery delivery) {
        long id = item.notificationId();
        String lease = UUID.randomUUID().toString();
        long now = clock.millis();
        if (emails.claim(id, lease, now, now + LEASE_TIME.toMillis()) != 1) return;
        String address = emails.eligibleAddress(id, LocalDate.now(clock));
        if (address == null) {
            emails.finish(id, lease, SKIPPED, clock.millis());
            return;
        }
        try {
            delivery.sendText(address, SUBJECT, BODY);
        } catch (MailException | MessagingException error) {
            if (item.attempts() + 1 >= MAX_ATTEMPTS) {
                emails.finish(id, lease, FAILED, clock.millis());
            } else {
                emails.retry(id, lease, clock.millis() + RETRY_DELAY.toMillis());
            }
            LOG.warn("Violation email delivery failed: notificationId={} attempt={} type={}",
                    id, item.attempts() + 1, error.getClass().getSimpleName());
            return;
        }
        emails.finish(id, lease, SENT, clock.millis());
    }
}
