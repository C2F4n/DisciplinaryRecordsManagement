package org.example.disciplinaryrecordsmanagement.dto;

import java.time.LocalDate;

import org.example.disciplinaryrecordsmanagement.dto.MonthlyReportDto.Item;
import org.example.disciplinaryrecordsmanagement.model.ViolationStatus;

public record WorkbookRow(LocalDate occurredOn, String studentNo, String studentName, String className,
                          String counselor, String departmentName, String typeName, String reason,
                          String disposition, String inspectionScope, String remarks, ViolationStatus status, String inspectionLabel) {
    public static WorkbookRow from(Item item) {
        return new WorkbookRow(item.occurredOn(), item.studentNo(), item.studentName(), item.className(),
                item.counselor(), item.departmentName(), item.typeName(), item.reason(), item.disposition(),
                item.inspectionScope(), item.remarks(), null, item.inspectionLabel());
    }
}
