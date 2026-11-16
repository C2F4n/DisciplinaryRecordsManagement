package org.example.disciplinaryrecordsmanagement.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;

import org.example.disciplinaryrecordsmanagement.config.ReportPolicy;
import org.example.disciplinaryrecordsmanagement.dto.MonthlyReportDto.Detail;
import org.example.disciplinaryrecordsmanagement.dto.MonthlyReportDto.Item;
import org.example.disciplinaryrecordsmanagement.dto.MonthlyReportDto.Summary;
import org.example.disciplinaryrecordsmanagement.dto.WorkbookDownload;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.MonthlyReportMapper;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static org.example.disciplinaryrecordsmanagement.constant.ReportConstants.GENERATION_LOCK_ID;
import static org.example.disciplinaryrecordsmanagement.constant.ReportConstants.SNAPSHOT_INSERT_BATCH;
import static org.example.disciplinaryrecordsmanagement.model.ViolationStatus.HANDED_BACK;
import static org.example.disciplinaryrecordsmanagement.model.ViolationStatus.RECORDED;

@Service
public class MonthlyReportService {
    private final MonthlyReportMapper mapper;
    private final ReportWorkbookWriter workbooks;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ReportPolicy policy;
    private final Clock clock;

    MonthlyReportService(MonthlyReportMapper mapper, ReportWorkbookWriter workbooks, AuditService audit,
                         NotificationService notifications, ReportPolicy policy, Clock clock) {
        this.mapper = mapper;
        this.workbooks = workbooks;
        this.audit = audit;
        this.notifications = notifications;
        this.policy = policy;
        this.clock = clock;
    }

    public YearMonth latestDueMonth() {
        LocalDateTime now = LocalDateTime.now(clock);
        YearMonth thisMonth = YearMonth.from(now);
        return thisMonth.minusMonths(now.isBefore(thisMonth.atDay(1).atTime(policy.generationTime())) ? 2 : 1);
    }

    public YearMonth nextDueMonth() {
        YearMonth due = latestDueMonth();
        var latest = mapper.latestMonth();
        if (latest != null) {
            YearMonth next = YearMonth.from(latest).plusMonths(1);
            return next.isAfter(due) ? null : next;
        }
        Long earliest = mapper.earliestUnreported(HANDED_BACK);
        if (earliest == null) return due;
        YearMonth first = YearMonth.from(Instant.ofEpochMilli(earliest).atZone(clock.getZone()));
        return first.isBefore(due) ? first : due;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean seal(YearMonth month) {
        if (month.isAfter(latestDueMonth())) return false;
        if (mapper.lockGeneration(GENERATION_LOCK_ID) == null) {
            throw new IllegalStateException("Monthly report generation lock is missing");
        }
        var monthStart = month.atDay(1);
        if (mapper.byMonth(monthStart) != null) return false;

        // Scheduling time is separate from eligibility: no handback from the first of next month enters this report.
        long cutoff = month.plusMonths(1).atDay(1).atStartOfDay(clock.getZone()).toInstant().toEpochMilli();
        var rows = mapper.lockCandidates(HANDED_BACK, cutoff).stream()
                .sorted(Comparator.comparing(Item::occurredOn).thenComparingLong(Item::sourceRecordId))
                .toList();
        var download = workbooks.write(month, rows);
        mapper.insertReport(monthStart, clock.millis(), rows.size());
        long reportId = mapper.byMonth(monthStart).id();
        for (int from = 0; from < rows.size(); from += SNAPSHOT_INSERT_BATCH) {
            mapper.insertItems(reportId, rows.subList(from, Math.min(from + SNAPSHOT_INSERT_BATCH, rows.size())));
        }
        mapper.storeFile(reportId, download.fileName(), download.content());
        if (mapper.markRecorded(reportId, HANDED_BACK, RECORDED) != rows.size()) {
            throw new IllegalStateException("Monthly snapshot count did not match the updated records");
        }
        audit.record(null, AuditEvent.GENERATE_MONTHLY_REPORT, Target.MONTHLY_REPORT, reportId, null, month.toString());
        notifications.reportPending(month, reportId);
        return true;
    }

    @Transactional(readOnly = true)
    public PageResult<Summary> list(Viewer viewer, int page, int size) {
        requireReportAccess(viewer);
        return new PageResult<>(mapper.list(size, (page - 1L) * size), mapper.count());
    }

    @Transactional(readOnly = true)
    public Detail detail(Viewer viewer, long id, int page, int size) {
        requireReportAccess(viewer);
        Summary report = mapper.byId(id);
        if (report == null) throw BusinessException.missing();
        return new Detail(report, new PageResult<>(mapper.items(id, size, (page - 1L) * size), report.recordCount()));
    }

    @Transactional
    public WorkbookDownload download(Viewer viewer, long id) {
        requireReportAccess(viewer);
        WorkbookDownload file = mapper.file(id);
        if (file == null) throw BusinessException.missing();
        audit.record(viewer.id(), AuditEvent.DOWNLOAD_MONTHLY_REPORT, Target.MONTHLY_REPORT, id, null, null);
        return file;
    }

    private static void requireReportAccess(Viewer viewer) {
        if (!viewer.canExportReports()) throw BusinessException.forbidden();
    }
}
