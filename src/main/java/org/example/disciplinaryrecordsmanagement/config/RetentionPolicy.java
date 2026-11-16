package org.example.disciplinaryrecordsmanagement.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.example.disciplinaryrecordsmanagement.model.RetentionWindow;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.retention")
public record RetentionPolicy(boolean enabled, @Min(1) @Max(30) int years, @Min(1) @Max(260) int maxWeeksPerRun) {
    public static final DayOfWeek WEEK_START = DayOfWeek.MONDAY;
    public static final DayOfWeek WEEK_END = DayOfWeek.FRIDAY;

    public LocalDate expiresOn(LocalDate weekEnd) {
        return weekEnd.plusYears(years);
    }

    public LocalDate eligibleThrough(LocalDate today) {
        // A Friday on February 29 reaches its anniversary on February 28 in a non-leap year.
        LocalDate candidate = today.minusYears(years).plusDays(1)
                .with(TemporalAdjusters.previousOrSame(WEEK_END));
        return expiresOn(candidate).isAfter(today) ? candidate.minusWeeks(1) : candidate;
    }

    public RetentionWindow window(LocalDate today) {
        return new RetentionWindow(enabled ? eligibleThrough(today) : null);
    }
}
