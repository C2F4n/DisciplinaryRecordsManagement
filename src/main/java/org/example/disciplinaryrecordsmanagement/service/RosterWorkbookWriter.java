package org.example.disciplinaryrecordsmanagement.service;

import org.example.disciplinaryrecordsmanagement.dto.WorkbookDownload;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.RosterConstants.*;

@Component
class RosterWorkbookWriter {
    private static final int COLUMN_WIDTH_UNIT = 256;
    private static final int[] ROSTER_WIDTHS = {25, 18, 25, 19, 20};
    private static final int[] CLASS_ROSTER_WIDTHS = {28, 20, 20, 20};
    WorkbookDownload template() {
        return workbook(TEMPLATE_FILE, ROSTER_SHEET, ROSTER_HEADERS, ROSTER_WIDTHS);
    }

    WorkbookDownload classTemplate() {
        return workbook(CLASS_TEMPLATE_FILE, CLASS_ROSTER_SHEET, CLASS_ROSTER_HEADERS, CLASS_ROSTER_WIDTHS);
    }

    private static WorkbookDownload workbook(String fileName, String sheetName, List<String> headers, int[] widths) {
        try (Workbook workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(sheetName);
            var textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            var headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);

            var header = sheet.createRow(HEADER_ROW);
            for (int i = 0; i < headers.size(); i++) {
                text(header, i, headers.get(i), headerStyle);
                sheet.setColumnWidth(i, widths[i] * COLUMN_WIDTH_UNIT);
                sheet.setDefaultColumnStyle(i, textStyle);
            }
            sheet.createFreezePane(0, HEADER_ROW + 1);
            workbook.write(output);
            return new WorkbookDownload(fileName, output.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write roster workbook", e);
        }
    }

    private static void text(Row row, int index, String value, CellStyle style) {
        var cell = row.createCell(index, CellType.STRING);
        cell.setCellStyle(style);
        cell.setCellValue(value);
    }
}
