package org.example.disciplinaryrecordsmanagement.service;

import org.example.disciplinaryrecordsmanagement.dto.ExportTemplateDto.*;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.ExportTemplateMapper;
import org.example.disciplinaryrecordsmanagement.mapper.ExportTemplateMapper.Settings;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.example.disciplinaryrecordsmanagement.model.WorkbookColumn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.*;
import static org.example.disciplinaryrecordsmanagement.model.AuditEvent.*;

@Service
public class ExportTemplateService {
    private final ExportTemplateMapper mapper;
    private final AuditService audit;

    ExportTemplateService(ExportTemplateMapper mapper, AuditService audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Template> templates(Viewer admin) {
        admin.requireAdmin();
        return Arrays.stream(Kind.values()).map(this::template).toList();
    }

    Template template(Kind kind) {
        Settings settings = mapper.settings(kind);
        if (settings == null) return view(kind, kind.defaultTitle(), kind.availableColumns(), 0);
        var columns = Arrays.stream(settings.columnsCsv().split(",")).map(WorkbookColumn::valueOf).toList();
        return view(kind, settings.title(), columns, settings.version());
    }

    @Transactional
    public Template update(Viewer admin, Kind kind, Change input) {
        admin.requireAdmin();
        var columns = List.copyOf(input.columns());
        var included = new HashSet<>(columns);
        var allowed = kind.availableColumns();
        if (included.size() != columns.size() || !allowed.containsAll(included)
                || allowed.stream().filter(WorkbookColumn::required).anyMatch(column -> !included.contains(column))) {
            throw BusinessException.bad(EXPORT_TEMPLATE_COLUMNS_INVALID);
        }

        long version = input.version();
        var current = template(kind);
        if (current.version() != version) throw BusinessException.conflict(STALE_RECORD);
        String title = input.title().strip();
        String csv = columns.stream().map(WorkbookColumn::name).collect(Collectors.joining(","));
        var saved = new Settings(title, csv, version + 1);
        if (version == 0) {
            try {
                mapper.insert(kind, saved);
            } catch (DuplicateKeyException conflict) {
                throw BusinessException.conflict(STALE_RECORD);
            }
        } else if (mapper.update(kind, saved, version) != 1) {
            throw BusinessException.conflict(STALE_RECORD);
        }
        audit.record(admin.id(), UPDATE_EXPORT_TEMPLATE, Target.EXPORT_TEMPLATE, kind.auditId(),
                Long.toString(version), Long.toString(saved.version()));
        return view(kind, title, columns, saved.version());
    }

    private static Template view(Kind kind, String title, List<WorkbookColumn> columns, long version) {
        var options = kind.availableColumns().stream()
                .map(column -> new ColumnOption(column, column.label(), column.required())).toList();
        return new Template(kind, title, columns, options, version);
    }
}
