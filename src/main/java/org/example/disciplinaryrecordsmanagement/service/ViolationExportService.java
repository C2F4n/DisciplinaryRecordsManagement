package org.example.disciplinaryrecordsmanagement.service;

import java.time.Clock;
import java.time.LocalDate;

import org.example.disciplinaryrecordsmanagement.config.ExportPolicy;
import org.example.disciplinaryrecordsmanagement.config.RetentionPolicy;
import org.example.disciplinaryrecordsmanagement.dto.ExportTemplateDto.Kind;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.Filter;
import org.example.disciplinaryrecordsmanagement.dto.WorkbookDownload;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.ViolationMapper;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.EXPORT_APPEALED_BULLETIN;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.EXPORT_PENDING_RECORDS;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.EXPORT_TOO_MANY_RECORDS;
import static org.example.disciplinaryrecordsmanagement.model.ViolationStatus.APPEALED;
import static org.example.disciplinaryrecordsmanagement.model.ViolationStatus.PENDING;

@Service
public class ViolationExportService {
    private final ViolationMapper records;
    private final ReportWorkbookWriter workbooks;
    private final AuditService audit;
    private final ExportPolicy policy;
    private final RetentionPolicy retention;
    private final Clock clock;

    ViolationExportService(ViolationMapper records, ReportWorkbookWriter workbooks, AuditService audit,
                           ExportPolicy policy, RetentionPolicy retention, Clock clock) {
        this.records = records;
        this.workbooks = workbooks;
        this.audit = audit;
        this.policy = policy;
        this.retention = retention;
        this.clock = clock;
    }

    @Transactional
    public WorkbookDownload download(Viewer viewer, Filter filters, boolean bulletin) {
        if (!viewer.allRecords()) throw BusinessException.forbidden();
        ViolationService.requireDateRange(filters);
        if (filters.status() == PENDING) throw BusinessException.bad(EXPORT_PENDING_RECORDS);
        if (bulletin && filters.status() == APPEALED) throw BusinessException.bad(EXPORT_APPEALED_BULLETIN);

        var today = LocalDate.now(clock);
        var rows = records.exportRows(filters, PENDING, bulletin ? APPEALED : null,
                retention.window(today), policy.maxRows() + 1);
        if (rows.size() > policy.maxRows()) throw BusinessException.bad(EXPORT_TOO_MANY_RECORDS.formatted(policy.maxRows()));
        var file = workbooks.writeFiltered(today, rows, bulletin ? Kind.BULLETIN : Kind.FILTERED_VIOLATIONS);
        audit.record(viewer.id(), bulletin ? AuditEvent.DOWNLOAD_BULLETIN : AuditEvent.DOWNLOAD_FILTERED_VIOLATIONS, Target.ACCOUNT,
                viewer.id(), null, Integer.toString(rows.size()));
        return file;
    }
}
