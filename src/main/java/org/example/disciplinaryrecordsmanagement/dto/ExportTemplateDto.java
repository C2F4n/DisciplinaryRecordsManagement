package org.example.disciplinaryrecordsmanagement.dto;

import jakarta.validation.constraints.*;
import org.example.disciplinaryrecordsmanagement.model.WorkbookColumn;

import java.util.Arrays;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.ReportConstants.*;

public final class ExportTemplateDto {
    private ExportTemplateDto() {}

    public enum Kind {
        MONTHLY_REPORT(1, MONTHLY_TITLE, false),
        FILTERED_VIOLATIONS(2, FILTERED_TITLE, true),
        BULLETIN(3, BULLETIN_TITLE, true);

        private final long auditId;
        private final String defaultTitle;
        private final List<WorkbookColumn> availableColumns;

        Kind(long auditId, String defaultTitle, boolean includesStatus) {
            this.auditId = auditId;
            this.defaultTitle = defaultTitle;
            this.availableColumns = Arrays.stream(WorkbookColumn.values())
                    .filter(column -> includesStatus || column != WorkbookColumn.STATUS).toList();
        }

        public long auditId() { return auditId; }
        public String defaultTitle() { return defaultTitle; }
        public List<WorkbookColumn> availableColumns() { return availableColumns; }
    }

    public record ColumnOption(WorkbookColumn code, String label, boolean required) {}
    public record Template(Kind kind, String title, List<WorkbookColumn> columns, List<ColumnOption> availableColumns,
                           long version) {}
    public record Change(@NotBlank @Size(max = MAX_WORKBOOK_TITLE_LENGTH) String title,
                         @NotEmpty @Size(max = MAX_WORKBOOK_COLUMNS) List<@NotNull WorkbookColumn> columns,
                         @NotNull @PositiveOrZero Long version) {}
}
