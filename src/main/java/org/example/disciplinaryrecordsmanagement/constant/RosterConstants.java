package org.example.disciplinaryrecordsmanagement.constant;

import java.util.List;

public final class RosterConstants {
    public static final String ROSTER_SHEET = "学生名册";
    public static final String TEMPLATE_FILE = "学生名册导入模板.xlsx";
    public static final String CLASS_TEMPLATE_FILE = "班级与辅导员导入模板.xlsx";
    public static final String CLASS_ROSTER_SHEET = "班级与辅导员";
    public static final String XLSX_SUFFIX = ".xlsx";
    public static final List<String> ROSTER_HEADERS = List.of("学号", "姓名", "班级", "手机号", "入学日期");
    public static final List<String> CLASS_ROSTER_HEADERS = List.of("班级名称", "辅导员1", "辅导员2", "辅导员3");
    public static final int HEADER_ROW = 0;
    public static final String DATE_INPUT = "\\d{4}-\\d{2}-\\d{2}";

    private RosterConstants() {}
}
