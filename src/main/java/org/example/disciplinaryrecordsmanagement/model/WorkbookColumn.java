package org.example.disciplinaryrecordsmanagement.model;

import org.example.disciplinaryrecordsmanagement.dto.WorkbookRow;

import java.util.function.Function;

public enum WorkbookColumn {
    DATE("日期", 14, WorkbookRow::occurredOn),
    CLASS("班级", 18, WorkbookRow::className),
    NAME("姓名", 14, WorkbookRow::studentName),
    NUMBER("学号", 22, WorkbookRow::studentNo),
    TYPE("违纪类型", 24, WorkbookRow::typeName),
    REASON("违纪原因", 42, WorkbookRow::reason),
    DISPOSITION("学院处理意见", 38, WorkbookRow::disposition),
    INSPECTION("检查项目", 19, row -> InspectionLabels.display(row.inspectionScope(), row.inspectionLabel())),
    COUNSELOR("辅导员", 16, WorkbookRow::counselor),
    REMARKS("备注", 30, WorkbookRow::remarks),
    DEPARTMENT("所属部门", 19, WorkbookRow::departmentName),
    STATUS("状态", 14, row -> row.status().label());

    private final String label;
    private final int width;
    private final Function<WorkbookRow, ?> value;

    WorkbookColumn(String label, int width, Function<WorkbookRow, ?> value) {
        this.label = label;
        this.width = width;
        this.value = value;
    }

    public String label() { return label; }
    public int width() { return width; }
    public Object value(WorkbookRow row) { return value.apply(row); }

    public boolean required() {
        return this != REMARKS && this != DEPARTMENT;
    }
}
