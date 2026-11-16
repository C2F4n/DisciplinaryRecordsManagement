package org.example.disciplinaryrecordsmanagement.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalDate;

import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_VIOLATION_NOTE_LENGTH;

@Validated
@ConfigurationProperties("app.policy")
public record BusinessPolicy(
        @NotBlank String timeZone,
        @Min(1) int pendingDays,
        @Min(1) int editDays,
        @Min(1) int enrollmentYears,
        @NotNull @DurationMin(seconds = 1) Duration pendingCleanupDelay,
        @NotBlank @Size(max = MAX_VIOLATION_NOTE_LENGTH) String defaultDisposition,
        @NotNull @DurationMin(seconds = 1) Duration loginWindow,
        @Min(1) int loginAttemptsPerIp,
        @Min(1) int loginMaxTrackedSources
) {
    public LocalDate oldestPending(LocalDate today) {
        return today.minusDays(pendingDays - 1L);
    }

    public boolean withinEditWindow(LocalDate occurredOn, LocalDate today) {
        return !occurredOn.isAfter(today) && !occurredOn.isBefore(today.minusDays(editDays - 1L));
    }
}
