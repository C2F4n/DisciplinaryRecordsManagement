package org.example.disciplinaryrecordsmanagement.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.roster")
public record RosterPolicy(@Min(1) @Max(3000) int maxRows, @NotNull DataSize maxFileSize) {}
