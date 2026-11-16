package org.example.disciplinaryrecordsmanagement.service;

import java.util.List;
import java.util.UUID;

import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.Resolution;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.ChangeDepartment;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.ChangeOption;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.Choice;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.Kind;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.NewInspection;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.Option;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.Settings;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.TypeAssignment;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.AccountMapper;
import org.example.disciplinaryrecordsmanagement.mapper.WorkflowMapper;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Marker;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.APPEAL_OPTION_REQUIRED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.DEPARTMENT_NOT_FOUND;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.INSPECTION_REQUIRED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.INVALID_APPEAL_OPTION;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.INVALID_INSPECTION;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.STALE_RECORD;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.WORKFLOW_LABEL_REQUIRED;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_WORKFLOW_CODE_LENGTH;
import static org.example.disciplinaryrecordsmanagement.model.AuditEvent.UPDATE_TYPE_DEPARTMENT;
import static org.example.disciplinaryrecordsmanagement.model.AuditEvent.UPDATE_WORKFLOW_OPTION;

@Service
public class WorkflowService {
    private static final long TYPE_SETTINGS_TARGET = 0L;
    private final WorkflowMapper mapper;
    private final AccountMapper accounts;
    private final AuditService audit;

    WorkflowService(WorkflowMapper mapper, AccountMapper accounts, AuditService audit) {
        this.mapper = mapper;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Settings settings(Viewer admin) {
        admin.requireAdmin();
        return new Settings(mapper.options(), mapper.typeAssignments(), accounts.departments());
    }

    List<Choice> choices(Kind kind) {
        return mapper.choices(kind);
    }

    @Transactional
    public Option addInspection(Viewer admin, NewInspection input) {
        admin.requireAdmin();
        String code = "I" + UUID.randomUUID().toString().replace("-", "").substring(0, MAX_WORKFLOW_CODE_LENGTH - 1);
        mapper.insertOption(Kind.INSPECTION, code, input.label().strip(), category(input.category()));
        var saved = mapper.option(code);
        audit.record(admin.id(), UPDATE_WORKFLOW_OPTION, Target.WORKFLOW_OPTION, saved.id(), null, "0", saved.label());
        return saved;
    }

    @Transactional
    public Option updateOption(Viewer admin, String code, ChangeOption input) {
        admin.requireAdmin();
        var existing = mapper.option(code);
        if (existing == null || !existing.code().equals(code)) throw BusinessException.missing();
        var options = mapper.lockOptions(existing.kind());
        var before = options.stream().filter(option -> option.code().equals(code)).findFirst().orElseThrow();
        if (before.version() != input.version()) throw BusinessException.conflict(STALE_RECORD);
        String label = input.label() == null || input.label().isBlank() ? null : input.label().strip();
        if (label == null && (input.enabled() || before.kind() == Kind.INSPECTION)) {
            throw BusinessException.bad(WORKFLOW_LABEL_REQUIRED);
        }
        if (before.kind() == Kind.INSPECTION && !input.enabled()
                && options.stream().noneMatch(option -> option.enabled() && !option.code().equals(code))) {
            throw BusinessException.bad(INSPECTION_REQUIRED);
        }
        String category = before.kind() == Kind.INSPECTION && input.category() != null
                ? category(input.category()) : before.category();
        if (mapper.updateOption(code, label, input.enabled(), input.version(), category) != 1) {
            throw BusinessException.conflict(STALE_RECORD);
        }
        var saved = mapper.option(code);
        audit.record(admin.id(), UPDATE_WORKFLOW_OPTION, Target.WORKFLOW_OPTION, before.id(),
                Long.toString(before.version()), Long.toString(saved.version()),
                "%s: %s -> %s; %s -> %s; %s -> %s".formatted(code, before.label(), saved.label(), before.enabled(),
                        saved.enabled(), before.category(), saved.category()));
        return saved;
    }

    @Transactional
    public void deleteInspection(Viewer admin, String code, long version) {
        admin.requireAdmin();
        var options = mapper.lockOptions(Kind.INSPECTION);
        var before = options.stream().filter(option -> option.code().equals(code))
                .findFirst().orElseThrow(BusinessException::missing);
        if (before.version() != version) throw BusinessException.conflict(STALE_RECORD);
        if (options.stream().noneMatch(option -> option.enabled() && !option.code().equals(code))) {
            throw BusinessException.bad(INSPECTION_REQUIRED);
        }
        if (mapper.deleteOption(Kind.INSPECTION, code, version) != 1) throw BusinessException.conflict(STALE_RECORD);
        audit.record(admin.id(), UPDATE_WORKFLOW_OPTION, Target.WORKFLOW_OPTION, before.id(),
                Long.toString(version), Marker.DELETED.name(), before.label());
    }

    private static String category(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Transactional
    public TypeAssignment updateType(Viewer admin, String code, ChangeDepartment input) {
        admin.requireAdmin();
        var before = mapper.typeAssignment(code);
        if (before == null || !before.code().equals(code)) throw BusinessException.missing();
        if (accounts.departmentExists(input.departmentCode()) == 0) throw BusinessException.bad(DEPARTMENT_NOT_FOUND);
        if (mapper.updateType(code, input.departmentCode(), input.version()) != 1) throw BusinessException.conflict(STALE_RECORD);
        var saved = mapper.typeAssignment(code);
        audit.record(admin.id(), UPDATE_TYPE_DEPARTMENT, Target.VIOLATION_TYPE, TYPE_SETTINGS_TARGET,
                before.departmentCode(), saved.departmentCode(), code);
        return saved;
    }

    String inspectionLabel(String code) {
        var option = mapper.option(code);
        if (option == null || option.kind() != Kind.INSPECTION || !option.enabled() || !option.code().equals(code)) {
            throw BusinessException.bad(INVALID_INSPECTION);
        }
        return option.label();
    }

    String appealLabel(Resolution resolution, String code) {
        if (code == null) {
            if (resolution == Resolution.APPEALED && mapper.activeAppeals(Kind.APPEAL, resolution) > 0) {
                throw BusinessException.bad(APPEAL_OPTION_REQUIRED);
            }
            return null;
        }
        var option = mapper.option(code);
        if (option == null || option.kind() != Kind.APPEAL || !option.enabled() || option.resolution() != resolution
                || !option.code().equals(code)) {
            throw BusinessException.bad(INVALID_APPEAL_OPTION);
        }
        return option.label();
    }
}
