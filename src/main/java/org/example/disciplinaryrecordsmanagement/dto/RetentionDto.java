package org.example.disciplinaryrecordsmanagement.dto;

import java.time.LocalDate;

import org.example.disciplinaryrecordsmanagement.result.PageResult;

public final class RetentionDto {
    private RetentionDto() {}

    public record Day(LocalDate day, long total, long deferred) {}
    public record Week(LocalDate weekStart, LocalDate weekEnd, LocalDate expiresOn, long purgeable, long deferred) {}
    public record Overview(LocalDate asOf, boolean enabled, int years, long purgeable, long deferred, PageResult<Week> weeks) {}
    public record Run(long id, LocalDate weekStart, LocalDate weekEnd, long executedAt, long removedRecords) {}
}
