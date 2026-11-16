package org.example.disciplinaryrecordsmanagement.constant;

public final class ReportConstants {
    public static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String MONTHLY_TITLE = "违纪统计月报";
    public static final String FILTERED_TITLE = "正式违纪记录";
    public static final String BULLETIN_TITLE = "违纪通报表";
    public static final int MAX_WORKBOOK_TITLE_LENGTH = 80;
    public static final int MAX_WORKBOOK_COLUMNS = 12;
    public static final int GENERATION_LOCK_ID = 1;
    public static final int SNAPSHOT_INSERT_BATCH = 200;

    private ReportConstants() {}
}
