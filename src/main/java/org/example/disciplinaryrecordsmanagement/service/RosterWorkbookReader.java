package org.example.disciplinaryrecordsmanagement.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.disciplinaryrecordsmanagement.config.RosterPolicy;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.*;
import static org.example.disciplinaryrecordsmanagement.constant.RosterConstants.*;

@Component
class RosterWorkbookReader {
    private final RosterPolicy policy;

    RosterWorkbookReader(RosterPolicy policy) {
        this.policy = policy;
    }

    interface RowDecoder<T> {
        T decode(Row row, DataFormatter formatter, List<String> issues);
    }

    <T> List<T> read(MultipartFile file, List<String> headers, RowDecoder<T> decoder) {
        if (file.getSize() > policy.maxFileSize().toBytes()) throw BusinessException.uploadTooLarge();
        String fileName = file.getOriginalFilename();
        if (file.isEmpty() || fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(XLSX_SUFFIX)) {
            throw BusinessException.bad(ROSTER_INVALID_FILE);
        }
        try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() != 1) throw BusinessException.bad(ROSTER_SINGLE_SHEET);
            var sheet = workbook.getSheetAt(0);
            var formatter = new DataFormatter(Locale.ROOT);
            var header = sheet.getRow(HEADER_ROW);
            if (header == null || hasExtraValues(header, headers.size())) throw BusinessException.bad(ROSTER_HEADER_INVALID);
            for (int i = 0; i < headers.size(); i++) {
                if (!headers.get(i).equals(text(header.getCell(i), formatter))) {
                    throw BusinessException.bad(ROSTER_HEADER_INVALID);
                }
            }

            var rows = new ArrayList<T>();
            for (Row row : sheet) {
                if (row.getRowNum() == HEADER_ROW || !hasValues(row)) continue;
                if (rows.size() >= policy.maxRows()) throw BusinessException.bad(ROSTER_TOO_MANY_ROWS.formatted(policy.maxRows()));
                var issues = new ArrayList<String>();
                if (hasExtraValues(row, headers.size())) issues.add(ROSTER_EXTRA_COLUMNS);
                for (int i = 0; i < headers.size(); i++) {
                    var cell = row.getCell(i);
                    if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)) {
                        issues.add(ROSTER_FORMULA_NOT_ALLOWED);
                        break;
                    }
                }
                rows.add(decoder.decode(row, formatter, issues));
            }
            if (rows.isEmpty()) throw BusinessException.bad(ROSTER_EMPTY);
            return rows;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw BusinessException.bad(ROSTER_INVALID_FILE);
        }
    }

    private static boolean hasValues(Row row) {
        for (Cell cell : row) if (hasValue(cell)) return true;
        return false;
    }

    private static boolean hasExtraValues(Row row, int columns) {
        for (Cell cell : row) if (cell.getColumnIndex() >= columns && hasValue(cell)) return true;
        return false;
    }

    private static boolean hasValue(Cell cell) {
        return switch (cell.getCellType()) {
            case BLANK, _NONE -> false;
            case STRING -> !cell.getStringCellValue().isBlank();
            default -> true;
        };
    }

    static String text(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) return "";
        return formatter.formatCellValue(cell).strip();
    }
}
