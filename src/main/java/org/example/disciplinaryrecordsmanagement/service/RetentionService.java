package org.example.disciplinaryrecordsmanagement.service;

import org.example.disciplinaryrecordsmanagement.config.RetentionPolicy;
import org.example.disciplinaryrecordsmanagement.dto.RetentionDto.Overview;
import org.example.disciplinaryrecordsmanagement.dto.RetentionDto.Run;
import org.example.disciplinaryrecordsmanagement.dto.RetentionDto.Week;
import org.example.disciplinaryrecordsmanagement.mapper.RetentionMapper;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.TreeMap;

import static org.example.disciplinaryrecordsmanagement.config.RetentionPolicy.WEEK_END;
import static org.example.disciplinaryrecordsmanagement.config.RetentionPolicy.WEEK_START;
import static org.example.disciplinaryrecordsmanagement.model.RetentionWindow.LIMITED_STATUSES;
import static org.example.disciplinaryrecordsmanagement.model.ViolationStatus.HANDED_BACK;

@Service
public class RetentionService {
    private final RetentionMapper mapper;
    private final RetentionPolicy policy;
    private final AuditService audit;
    private final Clock clock;

    RetentionService(RetentionMapper mapper, RetentionPolicy policy, AuditService audit, Clock clock) {
        this.mapper = mapper;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    public Overview preview(Viewer viewer, int page, int size) {
        viewer.requireAdmin();
        var today = LocalDate.now(clock);
        var weeks = plan(today);
        int first = (int) Math.min((page - 1L) * size, weeks.size());
        int last = Math.min(first + size, weeks.size());
        return new Overview(today, policy.enabled(), policy.years(),
                weeks.stream().mapToLong(Week::purgeable).sum(),
                weeks.stream().mapToLong(Week::deferred).sum(),
                new PageResult<>(weeks.subList(first, last), weeks.size()));
    }

    @Transactional(readOnly = true)
    public PageResult<Run> history(Viewer viewer, int page, int size) {
        viewer.requireAdmin();
        return new PageResult<>(mapper.history(size, (page - 1L) * size), mapper.countRuns());
    }

    public List<LocalDate> nextWeeks() {
        if (!policy.enabled()) return List.of();
        return plan(LocalDate.now(clock)).stream().filter(week -> week.purgeable() > 0)
                .map(Week::weekStart).limit(policy.maxWeeksPerRun()).toList();
    }

    @Transactional
    public int purgeWeek(LocalDate weekStart) {
        if (!policy.enabled() || weekStart.getDayOfWeek() != WEEK_START) return 0;
        var weekEnd = weekStart.with(TemporalAdjusters.nextOrSame(WEEK_END));
        if (policy.expiresOn(weekEnd).isAfter(LocalDate.now(clock))) return 0;

        int removed = mapper.removeWeek(weekStart, weekEnd, LIMITED_STATUSES, HANDED_BACK);
        if (removed > 0) {
            mapper.recordRun(weekStart, weekEnd, clock.millis(), removed);
            audit.record(null, AuditEvent.PURGE_RETENTION_WEEK, Target.RETENTION_WEEK,
                    weekStart.toEpochDay(), null, Integer.toString(removed));
        }
        return removed;
    }

    private List<Week> plan(LocalDate today) {
        var days = mapper.previewDays(policy.eligibleThrough(today), LIMITED_STATUSES, HANDED_BACK);
        var weeks = new TreeMap<LocalDate, Counts>();
        for (var day : days) {
            if (day.day().getDayOfWeek().getValue() > WEEK_END.getValue()) continue;
            var start = day.day().with(TemporalAdjusters.previousOrSame(WEEK_START));
            weeks.merge(start, new Counts(day.total() - day.deferred(), day.deferred()),
                    (a, b) -> new Counts(a.purgeable() + b.purgeable(), a.deferred() + b.deferred()));
        }
        return weeks.entrySet().stream().map(entry -> {
            var end = entry.getKey().with(TemporalAdjusters.nextOrSame(WEEK_END));
            return new Week(entry.getKey(), end, policy.expiresOn(end), entry.getValue().purgeable(), entry.getValue().deferred());
        }).toList();
    }

    private record Counts(long purgeable, long deferred) {}
}
