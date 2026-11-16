package org.example.disciplinaryrecordsmanagement.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

public final class StatisticsDto {
    private StatisticsDto() {}

    public enum Period {
        DAY, WEEK, MONTH;

        public LocalDate start(LocalDate date) {
            return switch (this) {
                case DAY -> date;
                case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTH -> date.withDayOfMonth(1);
            };
        }

        public LocalDate shift(LocalDate date, long amount) {
            return switch (this) {
                case DAY -> date.plusDays(amount);
                case WEEK -> date.plusWeeks(amount);
                case MONTH -> date.plusMonths(amount);
            };
        }
    }

    public enum Dimension { STATUS, TYPE, DEPARTMENT, CLASS }

    public record Bucket(String key, String label, long count) {}
    public record Daily(LocalDate day, long count) {}
    public record TrendPoint(LocalDate start, long count) {}
    public record Summary(long total, List<Bucket> byStatus, List<Bucket> byType,
                          List<Bucket> byDepartment, List<Bucket> byClass,
                          List<TrendPoint> trend, boolean trendLimited) {}
}
