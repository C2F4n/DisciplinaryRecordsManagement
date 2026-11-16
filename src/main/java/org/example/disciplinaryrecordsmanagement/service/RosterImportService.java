package org.example.disciplinaryrecordsmanagement.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.example.disciplinaryrecordsmanagement.config.BusinessPolicy;
import org.example.disciplinaryrecordsmanagement.dto.AccountDto.Group;
import org.example.disciplinaryrecordsmanagement.dto.AccountDto.NewStudent;
import org.example.disciplinaryrecordsmanagement.dto.RosterDto.Check;
import org.example.disciplinaryrecordsmanagement.dto.RosterDto.Issue;
import org.example.disciplinaryrecordsmanagement.dto.RosterDto.StudentImport;
import org.example.disciplinaryrecordsmanagement.dto.WorkbookDownload;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.AccountMapper;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ENROLLMENT_EXPIRED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ENROLLMENT_IN_FUTURE;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_CLASS_NOT_FOUND;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_DATE_INVALID;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_DUPLICATE_IN_FILE;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_IMPORT_ROW_ERROR;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_PHONE_INVALID;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_STUDENT_EXISTS;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_STUDENT_NAME_INVALID;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_STUDENT_NUMBER_INVALID;
import static org.example.disciplinaryrecordsmanagement.constant.RosterConstants.DATE_INPUT;
import static org.example.disciplinaryrecordsmanagement.constant.RosterConstants.ROSTER_HEADERS;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_STUDENT_NAME_LENGTH;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.OPTIONAL_PHONE;
import static org.example.disciplinaryrecordsmanagement.service.RosterWorkbookReader.text;

@Service
public class RosterImportService {
    private final AccountService accounts;
    private final AccountMapper mapper;
    private final RosterMaintenanceService roster;
    private final RosterWorkbookReader reader;
    private final BusinessPolicy business;
    private final RosterWorkbookWriter workbooks;
    private final Clock clock;

    RosterImportService(AccountService accounts, AccountMapper mapper, RosterMaintenanceService roster, RosterWorkbookReader reader,
                        BusinessPolicy business, RosterWorkbookWriter workbooks, Clock clock) {
        this.accounts = accounts;
        this.mapper = mapper;
        this.roster = roster;
        this.reader = reader;
        this.business = business;
        this.workbooks = workbooks;
        this.clock = clock;
    }

    public WorkbookDownload template(Viewer viewer) {
        viewer.requireAdmin();
        return workbooks.template();
    }

    @Transactional(readOnly = true)
    public Check check(Viewer viewer, MultipartFile file) {
        viewer.requireAdmin();
        var validation = validate(file);
        return new Check(validation.rows(), validation.issues().isEmpty(), validation.issues());
    }

    @Transactional
    public StudentImport importRoster(Viewer viewer, MultipartFile file) {
        viewer.requireAdmin();
        var validation = validate(file);
        if (!validation.issues().isEmpty()) {
            var first = validation.issues().getFirst();
            throw BusinessException.bad(ROSTER_IMPORT_ROW_ERROR.formatted(first.row(), first.message()));
        }
        for (var student : validation.accepted()) {
            accounts.createStudent(viewer, student);
        }
        return new StudentImport(validation.accepted().size());
    }

    private Validation validate(MultipartFile file) {
        var rows = reader.read(file, ROSTER_HEADERS, (row, formatter, issues) -> new InputRow(row.getRowNum() + 1,
                text(row.getCell(0), formatter), text(row.getCell(1), formatter),
                text(row.getCell(2), formatter), text(row.getCell(3), formatter), date(row.getCell(4), formatter), issues));
        var rule = roster.studentNumberRule();
        String numberError = ROSTER_STUDENT_NUMBER_INVALID.formatted(rule.requirement());
        var groups = mapper.groups().stream().collect(Collectors.toMap(Group::name, Function.identity()));
        var candidates = rows.stream().map(InputRow::studentNo).filter(rule::accepts).distinct().toList();
        var existing = candidates.isEmpty() ? new HashSet<String>() : new HashSet<>(mapper.existingStudentNumbers(candidates));
        var seen = new HashSet<String>();
        var accepted = new ArrayList<NewStudent>();
        var issues = new ArrayList<Issue>();
        var today = LocalDate.now(clock);
        for (InputRow row : rows) {
            var errors = new ArrayList<>(row.issues());
            var group = groups.get(row.className());
            if (!rule.accepts(row.studentNo())) errors.add(numberError);
            else {
                if (!seen.add(row.studentNo())) errors.add(ROSTER_DUPLICATE_IN_FILE);
                if (existing.contains(row.studentNo())) errors.add(ROSTER_STUDENT_EXISTS);
            }
            if (row.name().isBlank() || row.name().length() > MAX_STUDENT_NAME_LENGTH) {
                errors.add(ROSTER_STUDENT_NAME_INVALID.formatted(MAX_STUDENT_NAME_LENGTH));
            }
            if (group == null) errors.add(ROSTER_CLASS_NOT_FOUND);
            if (!row.phone().matches(OPTIONAL_PHONE)) errors.add(ROSTER_PHONE_INVALID);
            if (row.enrolledOn() == null) errors.add(ROSTER_DATE_INVALID);
            else if (row.enrolledOn().isAfter(today)) errors.add(ENROLLMENT_IN_FUTURE);
            else if (row.enrolledOn().plusYears(business.enrollmentYears()).minusDays(1).isBefore(today)) errors.add(ENROLLMENT_EXPIRED);

            if (!errors.isEmpty()) issues.add(new Issue(row.line(), String.join("；", errors)));
            else accepted.add(new NewStudent(row.studentNo(), row.name(), group.id(), row.phone(), row.enrolledOn()));
        }
        return new Validation(rows.size(), List.copyOf(issues), List.copyOf(accepted));
    }

    private static LocalDate date(Cell cell, DataFormatter formatter) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            var dateTime = cell.getLocalDateTimeCellValue();
            return dateTime == null ? null : dateTime.toLocalDate();
        }
        String input = text(cell, formatter);
        if (!input.matches(DATE_INPUT)) return null;
        try { return LocalDate.parse(input); }
        catch (DateTimeParseException e) { return null; }
    }

    private record InputRow(int line, String studentNo, String name, String className, String phone,
                            LocalDate enrolledOn, List<String> issues) {}
    private record Validation(int rows, List<Issue> issues, List<NewStudent> accepted) {}
}
