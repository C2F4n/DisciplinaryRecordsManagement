package org.example.disciplinaryrecordsmanagement.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.example.disciplinaryrecordsmanagement.dto.AccountDto.NewGroup;
import org.example.disciplinaryrecordsmanagement.dto.CounselorDto.Create;
import org.example.disciplinaryrecordsmanagement.dto.RosterDto.Check;
import org.example.disciplinaryrecordsmanagement.dto.RosterDto.ClassImport;
import org.example.disciplinaryrecordsmanagement.dto.RosterDto.Issue;
import org.example.disciplinaryrecordsmanagement.dto.WorkbookDownload;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.AccountMapper;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.CLASS_COUNSELOR_COUNT;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.CLASS_COUNSELOR_DUPLICATE;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_CLASS_EXISTS;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_CLASS_NAME_INVALID;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_COUNSELOR_NAME_INVALID;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROSTER_IMPORT_ROW_ERROR;
import static org.example.disciplinaryrecordsmanagement.constant.RosterConstants.CLASS_ROSTER_HEADERS;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_CLASS_NAME_LENGTH;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_COUNSELOR_NAME_LENGTH;
import static org.example.disciplinaryrecordsmanagement.service.RosterWorkbookReader.text;

@Service
public class ClassRosterImportService {
    private final RosterWorkbookReader reader;
    private final RosterWorkbookWriter writer;
    private final AccountMapper mapper;
    private final AccountService accounts;
    private final CounselorService counselors;

    ClassRosterImportService(RosterWorkbookReader reader, RosterWorkbookWriter writer,
                             AccountMapper mapper, AccountService accounts, CounselorService counselors) {
        this.reader = reader;
        this.writer = writer;
        this.mapper = mapper;
        this.accounts = accounts;
        this.counselors = counselors;
    }

    public WorkbookDownload template(Viewer viewer) {
        viewer.requireAdmin();
        return writer.classTemplate();
    }

    @Transactional(readOnly = true)
    public Check check(Viewer viewer, MultipartFile file) {
        viewer.requireAdmin();
        var result = validate(file);
        return new Check(result.rows().size(), result.issues().isEmpty(), result.issues());
    }

    @Transactional
    public ClassImport importRoster(Viewer viewer, MultipartFile file) {
        viewer.requireAdmin();
        var result = validate(file);
        if (!result.issues().isEmpty()) {
            var first = result.issues().getFirst();
            throw BusinessException.bad(ROSTER_IMPORT_ROW_ERROR.formatted(first.row(), first.message()));
        }
        int classCount = 0;
        int counselorCount = 0;
        for (var row : result.rows()) {
            for (String name : row.counselors()) {
                if (!counselors.exists(name)) {
                    counselors.create(viewer, new Create(name));
                    counselorCount++;
                }
            }
            if (!row.name().isEmpty()) {
                accounts.createGroup(viewer, new NewGroup(row.name(), null, row.counselors()));
                classCount++;
            }
        }
        return new ClassImport(classCount, counselorCount);
    }

    private Validation validate(MultipartFile file) {
        var rows = reader.read(file, CLASS_ROSTER_HEADERS, (row, formatter, issues) -> new ImportRow(
                row.getRowNum() + 1, text(row.getCell(0), formatter),
                IntStream.range(1, CLASS_ROSTER_HEADERS.size()).mapToObj(i -> text(row.getCell(i), formatter))
                        .filter(name -> !name.isEmpty()).toList(), issues));
        var knownClasses = mapper.classNames().stream().map(ClassRosterImportService::normalized)
                .collect(Collectors.toCollection(HashSet::new));
        var issues = new ArrayList<Issue>();
        for (var row : rows) {
            var errors = new ArrayList<>(row.issues());
            if (row.name().length() > MAX_CLASS_NAME_LENGTH) errors.add(ROSTER_CLASS_NAME_INVALID.formatted(MAX_CLASS_NAME_LENGTH));
            if (!row.name().isEmpty() && !knownClasses.add(normalized(row.name()))) errors.add(ROSTER_CLASS_EXISTS);
            if (row.counselors().isEmpty()) errors.add(CLASS_COUNSELOR_COUNT);
            if (row.counselors().stream().anyMatch(name -> name.length() > MAX_COUNSELOR_NAME_LENGTH)) {
                errors.add(ROSTER_COUNSELOR_NAME_INVALID.formatted(MAX_COUNSELOR_NAME_LENGTH));
            }
            if (row.counselors().stream().map(ClassRosterImportService::normalized).distinct().count() != row.counselors().size()) {
                errors.add(CLASS_COUNSELOR_DUPLICATE);
            }
            if (!errors.isEmpty()) issues.add(new Issue(row.line(), String.join("；", errors)));
        }
        return new Validation(rows, issues);
    }

    private static String normalized(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private record ImportRow(int line, String name, List<String> counselors, List<String> issues) {}
    private record Validation(List<ImportRow> rows, List<Issue> issues) {}
}
