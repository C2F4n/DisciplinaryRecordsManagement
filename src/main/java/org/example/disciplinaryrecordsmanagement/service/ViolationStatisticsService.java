package org.example.disciplinaryrecordsmanagement.service;

import org.example.disciplinaryrecordsmanagement.config.BusinessPolicy;
import org.example.disciplinaryrecordsmanagement.config.RetentionPolicy;
import org.example.disciplinaryrecordsmanagement.dto.StatisticsDto.*;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.Filter;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.ViolationMapper;
import org.example.disciplinaryrecordsmanagement.model.ViolationStatus;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_TREND_BUCKETS;

@Service
public class ViolationStatisticsService {
    private final ViolationMapper records;
    private final BusinessPolicy policy;
    private final RetentionPolicy retention;
    private final Clock clock;

    ViolationStatisticsService(ViolationMapper records, BusinessPolicy policy, RetentionPolicy retention, Clock clock) {
        this.records = records;
        this.policy = policy;
        this.retention = retention;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary summarize(Viewer viewer, Filter filter, Period period) {
        if (!viewer.canViewStatistics()) throw BusinessException.forbidden();
        ViolationService.requireDateRange(filter);
        var scope = ViolationService.scopeFor(viewer);
        var today = LocalDate.now(clock);
        var oldestPending = policy.oldestPending(today);
        var window = retention.window(today);
        var status = records.statisticsBuckets(scope, filter, oldestPending, window, Dimension.STATUS).stream()
                .map(row -> new Bucket(row.key(), ViolationStatus.valueOf(row.key()).label(), row.count()))
                .toList();
        long total = status.stream().mapToLong(Bucket::count).sum();
        if (total == 0) return new Summary(0, List.of(), List.of(), List.of(), List.of(), List.of(), false);

        var daily = records.statisticsByDay(scope, filter, oldestPending, window);
        var trend = trend(daily, filter, period);
        return new Summary(total, status,
                records.statisticsBuckets(scope, filter, oldestPending, window, Dimension.TYPE),
                records.statisticsBuckets(scope, filter, oldestPending, window, Dimension.DEPARTMENT),
                records.statisticsBuckets(scope, filter, oldestPending, window, Dimension.CLASS),
                trend.points(), trend.limited());
    }

    private static Trend trend(List<Daily> daily, Filter filter, Period period) {
        LocalDate from = period.start(filter.from() == null ? daily.getFirst().day() : filter.from());
        LocalDate to = period.start(filter.to() == null ? daily.getLast().day() : filter.to());
        LocalDate recent = period.shift(to, -(MAX_TREND_BUCKETS - 1L));
        boolean limited = recent.isAfter(from);
        if (limited) from = recent;

        var counts = new HashMap<LocalDate, Long>();
        for (Daily day : daily) counts.merge(period.start(day.day()), day.count(), Long::sum);
        var points = new ArrayList<TrendPoint>();
        for (LocalDate day = from; ; day = period.shift(day, 1)) {
            points.add(new TrendPoint(day, counts.getOrDefault(day, 0L)));
            if (day.equals(to)) break;
        }
        return new Trend(points, limited);
    }

    private record Trend(List<TrendPoint> points, boolean limited) {}
}
