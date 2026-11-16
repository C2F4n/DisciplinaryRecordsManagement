package org.example.disciplinaryrecordsmanagement.dto;

import org.example.disciplinaryrecordsmanagement.result.PageResult;

import java.time.LocalDate;

public final class RetentionDto {
    private RetentionDto() {}

    public record Day(LocalDate day, long total, long deferred) {}
    public record Week(LocalDate weekStart, LocalDate weekEnd, LocalDate expiresOn, long purgeable, long deferred) {}
    public record Overview(LocalDate asOf, boolean enabled, int years, long purgeable, long deferred, PageResult<Week> weeks) {}
    public record Run(long id, LocalDate weekStart, LocalDate weekEnd, long executedAt, long removedRecords) {}
}
