package org.example.disciplinaryrecordsmanagement.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.export")
public record ExportPolicy(@Min(1) @Max(50000) int maxRows) {}
