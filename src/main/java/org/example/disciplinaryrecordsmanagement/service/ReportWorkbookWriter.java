package org.example.disciplinaryrecordsmanagement.service;

import org.example.disciplinaryrecordsmanagement.dto.MonthlyReportDto.Item;
import org.example.disciplinaryrecordsmanagement.dto.ExportTemplateDto.Kind;
import org.example.disciplinaryrecordsmanagement.dto.ExportTemplateDto.Template;
import org.example.disciplinaryrecordsmanagement.dto.WorkbookDownload;
import org.example.disciplinaryrecordsmanagement.dto.WorkbookRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
class ReportWorkbookWriter {
    private static final int TITLE_ROW = 0;
    private static final int SUMMARY_ROW = 1;
    private static final int HEADER_ROW = 2;
    private static final int EXCEL_COLUMN_WIDTH_UNIT = 256;
    private static final String DATE_FORMAT = "yyyy-mm-dd";

    private final ExportTemplateService templates;

    ReportWorkbookWriter(ExportTemplateService templates) {
        this.templates = templates;
    }

    WorkbookDownload write(YearMonth month, List<Item> rows) {
        return write(month.toString(), templates.template(Kind.MONTHLY_REPORT),
                "按交回时间归月 · 本期共 " + rows.size() + " 条 · 明细已冻结",
                rows.stream().map(WorkbookRow::from).toList());
    }

    WorkbookDownload writeFiltered(LocalDate date, List<WorkbookRow> rows, Kind kind) {
        return write(date.toString(), templates.template(kind),
                "按筛选条件导出 · 共 " + rows.size() + " 条", rows);
    }

    private WorkbookDownload write(String period, Template template, String summaryText, List<WorkbookRow> rows) {
        try (Workbook workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            String defaultTitle = template.kind().defaultTitle();
            Sheet sheet = workbook.createSheet(defaultTitle);
            var columns = template.columns();
            int lastColumn = columns.size() - 1;
            CellStyle titleStyle = heading(workbook, (short) 16);
            CellStyle headerStyle = heading(workbook, (short) 11);
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setWrapText(true);
            textStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.cloneStyleFrom(textStyle);
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat(DATE_FORMAT));

            Row titleRow = sheet.createRow(TITLE_ROW);
            titleRow.setHeightInPoints(29);
            cell(titleRow, 0, period + " " + template.title(), titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(TITLE_ROW, TITLE_ROW, 0, lastColumn));
            Row summary = sheet.createRow(SUMMARY_ROW);
            cell(summary, 0, summaryText, textStyle);
            sheet.addMergedRegion(new CellRangeAddress(SUMMARY_ROW, SUMMARY_ROW, 0, lastColumn));

            Row headers = sheet.createRow(HEADER_ROW);
            headers.setHeightInPoints(23);
            for (int i = 0; i < columns.size(); i++) {
                var column = columns.get(i);
                cell(headers, i, column.label(), headerStyle);
                sheet.setColumnWidth(i, column.width() * EXCEL_COLUMN_WIDTH_UNIT);
            }
            int index = HEADER_ROW + 1;
            for (WorkbookRow item : rows) {
                Row row = sheet.createRow(index++);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = columns.get(i).value(item);
                    if (value instanceof LocalDate date) {
                        Cell cell = row.createCell(i);
                        cell.setCellValue(date);
                        cell.setCellStyle(dateStyle);
                    } else {
                        // Text cells preserve leading zeros and cannot turn user input into a formula.
                        cell(row, i, (String) value, textStyle);
                    }
                }
            }
            sheet.createFreezePane(0, HEADER_ROW + 1);
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, index - 1, 0, lastColumn));
            workbook.write(output);
            return new WorkbookDownload(period + " " + defaultTitle + ".xlsx", output.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the violation workbook", e);
        }
    }

    private static CellStyle heading(Workbook workbook, short points) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints(points);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static void cell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index, CellType.STRING);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }
}
