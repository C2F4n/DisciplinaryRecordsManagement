package org.example.disciplinaryrecordsmanagement.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.LocalTime;

@Validated
@ConfigurationProperties("app.report")
public record ReportPolicy(
        @NotNull LocalTime generationTime,
        @Min(1) @Max(120) int maxMonthsPerRun
) {}
