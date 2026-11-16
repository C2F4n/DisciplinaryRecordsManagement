package org.example.disciplinaryrecordsmanagement.dto;

import java.time.LocalDate;

import org.example.disciplinaryrecordsmanagement.model.InspectionLabels;
import org.example.disciplinaryrecordsmanagement.result.PageResult;

public final class MonthlyReportDto {
    private MonthlyReportDto() {}

    public record Summary(long id, LocalDate monthStart, long generatedAt, int recordCount) {}

    public record Item(long sourceRecordId, LocalDate occurredOn, String studentNo, String studentName,
                       String className, String counselor, String departmentName, String typeName,
                       String reason, String disposition, String inspectionScope, String remarks, String inspectionLabel) {
        @Override
        public String inspectionLabel() {
            return InspectionLabels.display(inspectionScope, inspectionLabel);
        }
    }

    public record Detail(Summary report, PageResult<Item> entries) {}

}
