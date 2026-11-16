package org.example.disciplinaryrecordsmanagement.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.example.disciplinaryrecordsmanagement.config.BusinessPolicy;
import org.example.disciplinaryrecordsmanagement.config.SmtpRuntime;
import org.example.disciplinaryrecordsmanagement.dto.NotificationDto.Notice;
import org.example.disciplinaryrecordsmanagement.dto.NotificationDto.Unread;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.NotificationEmailMapper;
import org.example.disciplinaryrecordsmanagement.mapper.NotificationMapper;
import org.example.disciplinaryrecordsmanagement.model.NotificationType;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.example.disciplinaryrecordsmanagement.constant.DepartmentCodes.DISCIPLINE;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.REPORT_PENDING_REVIEW;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.REVIEW_OVERDUE;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.VIOLATION_APPEALED;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.VIOLATION_APPROVED;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.VIOLATION_CORRECTED;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.VIOLATION_HANDED_BACK;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.VIOLATION_REGISTERED;
import static org.example.disciplinaryrecordsmanagement.model.Role.DEPARTMENT_HEAD;
import static org.example.disciplinaryrecordsmanagement.model.ViolationStatus.PENDING;

@Service
public class NotificationService {
    private static final List<NotificationType> STUDENT_NOTICE_TYPES = List.of(VIOLATION_REGISTERED, VIOLATION_APPROVED,
            VIOLATION_HANDED_BACK, VIOLATION_APPEALED, VIOLATION_CORRECTED);
    private final NotificationMapper notifications;
    private final NotificationEmailMapper emails;
    private final SmtpRuntime smtp;
    private final BusinessPolicy policy;
    private final Clock clock;

    NotificationService(NotificationMapper notifications, NotificationEmailMapper emails,
                        SmtpRuntime smtp, BusinessPolicy policy, Clock clock) {
        this.notifications = notifications;
        this.emails = emails;
        this.smtp = smtp;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<Notice> list(Viewer viewer, int page, int size) {
        return new PageResult<>(notifications.list(viewer.id(), size, (page - 1L) * size)
                .stream().map(Notice::from).toList(), notifications.count(viewer.id()));
    }

    public Unread unread(Viewer viewer) {
        return new Unread(notifications.unread(viewer.id()));
    }

    @Transactional
    public void markRead(Viewer viewer, long id) {
        if (notifications.markRead(viewer.id(), id, clock.millis()) == 0 && notifications.owns(viewer.id(), id) == 0) {
            throw BusinessException.missing();
        }
    }

    public void markAllRead(Viewer viewer) {
        notifications.markAllRead(viewer.id(), clock.millis());
    }

    void student(long student, NotificationType type, long recordId, long revision) {
        long now = clock.millis();
        notifications.notifyStudent(student, type, recordId, revision, now);
        if (smtp.active() && type == VIOLATION_REGISTERED) {
            emails.enqueue(student, type, recordId, revision, now);
        }
    }

    void removeStudentNotices(long student, long recordId) {
        notifications.removeStudentNotices(student, recordId, STUDENT_NOTICE_TYPES);
    }

    void user(long recipient, NotificationType type, long recordId, long revision) {
        notifications.notifyUser(recipient, type, recordId, revision, clock.millis());
    }

    public int remindPending() {
        var now = clock.instant();
        LocalDate today = now.atZone(clock.getZone()).toLocalDate();
        long cutoff = today.atStartOfDay(clock.getZone()).toInstant().toEpochMilli();
        return notifications.remindOverdue(REVIEW_OVERDUE, DEPARTMENT_HEAD, PENDING, cutoff,
                policy.oldestPending(today), now.toEpochMilli());
    }

    void reportPending(YearMonth period, long reportId) {
        var now = clock.instant();
        LocalDate oldestPending = policy.oldestPending(now.atZone(clock.getZone()).toLocalDate());
        LocalDate from = period.atDay(1);
        if (oldestPending.isAfter(from)) from = oldestPending;
        LocalDate until = period.plusMonths(1).atDay(1);
        if (!from.isBefore(until)) return;
        int count = notifications.pendingForMonth(PENDING, from, until);
        if (count > 0) {
            notifications.notifyReportPending(DEPARTMENT_HEAD, DISCIPLINE, REPORT_PENDING_REVIEW,
                    reportId, count, now.toEpochMilli());
        }
    }
}
