package org.example.disciplinaryrecordsmanagement.service;

import org.example.disciplinaryrecordsmanagement.config.BusinessPolicy;
import org.example.disciplinaryrecordsmanagement.config.RetentionPolicy;
import org.example.disciplinaryrecordsmanagement.dto.AccountDto.Candidate;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.*;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.Kind;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.AccountMapper;
import org.example.disciplinaryrecordsmanagement.mapper.ViolationMapper;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;
import org.example.disciplinaryrecordsmanagement.model.InspectionLabels;
import org.example.disciplinaryrecordsmanagement.model.ViolationStatus;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.example.disciplinaryrecordsmanagement.result.PageResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.*;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.*;
import static org.example.disciplinaryrecordsmanagement.model.Role.*;
import static org.example.disciplinaryrecordsmanagement.model.ViolationStatus.*;

@Service
public class ViolationService {
    private static final LocalDate MIN_OCCURRENCE_DATE = LocalDate.of(1000, 1, 1);
    private final ViolationMapper records;
    private final AccountMapper accounts;
    private final AuditService audit;
    private final NotificationService notifications;
    private final BusinessPolicy policy;
    private final RetentionPolicy retention;
    private final WorkflowService workflow;
    private final Clock clock;

    ViolationService(ViolationMapper records, AccountMapper accounts, AuditService audit,
                     NotificationService notifications, BusinessPolicy policy, RetentionPolicy retention,
                     WorkflowService workflow, Clock clock) {
        this.records = records;
        this.accounts = accounts;
        this.audit = audit;
        this.notifications = notifications;
        this.policy = policy;
        this.retention = retention;
        this.workflow = workflow;
        this.clock = clock;
    }

    public Metadata metadata(Viewer viewer) {
        return new Metadata(accounts.departments(), records.types(), LocalDate.now(clock), policy.defaultDisposition(),
                workflow.choices(Kind.INSPECTION), viewer.office() ? workflow.choices(Kind.APPEAL) : List.of());
    }

    public List<Candidate> lookup(Viewer viewer, String search) {
        if (!viewer.coreAdmin() && viewer.scopes(OFFICER).isEmpty()) throw BusinessException.forbidden();
        return accounts.findStudents(search.strip(), LocalDate.now(clock));
    }

    public int expirePending() {
        return records.expirePending(policy.oldestPending(LocalDate.now(clock)), clock.millis(), ViolationStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public PageResult<Entry> list(Viewer viewer, Filter query, int page, int size) {
        requireDateRange(query);
        var scope = scopeFor(viewer);
        LocalDate today = LocalDate.now(clock);
        LocalDate oldestPending = policy.oldestPending(today);
        var window = retention.window(today);
        return new PageResult<>(records.list(scope, query, oldestPending, window, size, (page - 1L) * size)
                .stream().map(row -> entry(viewer, row, today)).toList(), records.count(scope, query, oldestPending, window));
    }

    public Entry find(Viewer viewer, long id) {
        LocalDate today = LocalDate.now(clock);
        var row = visible(viewer, id, today);
        return entry(viewer, row, today);
    }

    @Transactional
    public Entry create(Viewer viewer, Create input) {
        LocalDate today = LocalDate.now(clock);
        var type = records.type(input.typeCode());
        if (type == null || !type.code().equals(input.typeCode())) throw BusinessException.bad(INVALID_VIOLATION_TYPE);
        if (!viewer.coreAdmin() && !viewer.scopes(OFFICER).contains(type.departmentCode())) {
            throw BusinessException.forbidden();
        }
        requireOccurrenceDate(input.occurredOn(), today);
        if (!viewer.coreAdmin() && !policy.withinEditWindow(input.occurredOn(), today)) {
            throw BusinessException.bad(OUTSIDE_EDIT_WINDOW.formatted(policy.editDays()));
        }
        if (input.occurredOn().isBefore(policy.oldestPending(today))) throw BusinessException.bad(PENDING_EXPIRED);
        var student = boundStudent(input.studentNo(), today);
        String disposition = input.disposition() == null || input.disposition().isBlank()
                ? policy.defaultDisposition() : input.disposition().strip();
        String inspectionLabel = workflow.inspectionLabel(input.inspectionScope());
        var draft = new ViolationMapper.Draft(student, input, type.departmentCode(), disposition, inspectionLabel,
                viewer.id(), clock.millis());
        records.insert(draft);
        audit.record(viewer.id(), AuditEvent.CREATE_RECORD, Target.VIOLATION, draft.id, null, ViolationStatus.PENDING.name());
        notifications.student(student.id(), VIOLATION_REGISTERED, draft.id, 0);
        return entry(viewer, records.byId(draft.id), today);
    }

    @Transactional
    public Entry edit(Viewer viewer, long id, Edit input) {
        LocalDate today = LocalDate.now(clock);
        var row = visible(viewer, id, today);
        if (!canEdit(viewer, row, today)) throw BusinessException.forbidden();
        String inspectionLabel = row.inspectionScope().equals(input.inspectionScope()) ? row.inspectionLabel()
                : workflow.inspectionLabel(input.inspectionScope());
        requireUpdated(records.edit(id, input, inspectionLabel));
        audit.record(viewer.id(), AuditEvent.UPDATE_RECORD, Target.VIOLATION, id, row.status().name(), row.status().name());
        return entry(viewer, records.byId(id), today);
    }

    @Transactional
    public Entry correct(Viewer viewer, long id, Correction input) {
        viewer.requireAdmin();
        LocalDate today = LocalDate.now(clock);
        var previous = visible(viewer, id, today);
        if (previous.status() == RECORDED) throw BusinessException.conflict(ARCHIVED_CORRECTION);
        if (previous.version() != input.version()) throw BusinessException.conflict(STALE_RECORD);
        requireOccurrenceDate(input.occurredOn(), today);
        var type = records.type(input.typeCode());
        if (type == null || !type.code().equals(input.typeCode())) throw BusinessException.bad(INVALID_VIOLATION_TYPE);
        boolean reassigned = !previous.studentNo().equals(input.studentNo());
        validateCorrection(previous, input, reassigned, today);
        var student = reassigned ? boundStudent(input.studentNo(), today) : null;
        Long handedBackAt = correctionHandbackTime(previous, input);

        String department = previous.typeCode().equals(type.code()) ? previous.departmentCode() : type.departmentCode();
        String appealLabel = previous.status() == input.status() ? previous.appealLabel() : null;
        requireUpdated(records.correct(id, input, department, student, handedBackAt, appealLabel));
        var updated = records.byId(id);
        audit.record(viewer.id(), AuditEvent.CORRECT_VIOLATION, Target.VIOLATION, id, previous.status().name(),
                updated.status().name(), correctionAudit(previous, updated, input.correctionReason().strip()));
        if (reassigned) notifications.removeStudentNotices(previous.studentId(), id);
        notifications.student(updated.studentId(), VIOLATION_CORRECTED, id, updated.version());
        return entry(viewer, updated, today);
    }

    private void validateCorrection(ViolationMapper.Row previous, Correction input, boolean reassigned, LocalDate today) {
        ViolationStatus target = input.status();
        boolean wasResolved = previous.status() == HANDED_BACK || previous.status() == APPEALED;
        if (target != previous.status() && (!wasResolved || target == RECORDED || target == PENDING)) {
            throw BusinessException.bad(CORRECTION_STATUS_INVALID);
        }
        if (reassigned && wasResolved && target != APPROVED) {
            throw BusinessException.bad(CORRECTION_REOPEN_REQUIRED);
        }
        if (target == PENDING && input.occurredOn().isBefore(policy.oldestPending(today))) {
            throw BusinessException.bad(PENDING_EXPIRED);
        }
        if (retention.window(today).excludes(input.occurredOn(), target)) {
            throw BusinessException.bad(CORRECTION_OUTSIDE_RETENTION);
        }
        if (!reassigned && previous.occurredOn().equals(input.occurredOn())
                && previous.typeCode().equals(input.typeCode()) && previous.status() == target) {
            throw BusinessException.bad(CORRECTION_NO_CHANGE);
        }
    }

    private Long correctionHandbackTime(ViolationMapper.Row previous, Correction input) {
        if (input.status() != HANDED_BACK) return null;
        Long original = previous.handedBackAt();
        if (original == null) return clock.millis();
        LocalDate handbackDate = Instant.ofEpochMilli(original).atZone(clock.getZone()).toLocalDate();
        if (input.occurredOn().isAfter(handbackDate)) throw BusinessException.bad(CORRECTION_DATE_AFTER_RETURN);
        return original;
    }

    private static String correctionAudit(ViolationMapper.Row before, ViolationMapper.Row after, String reason) {
        return "studentId %d -> %d; occurredOn %s -> %s; type %s -> %s; version %d -> %d%n%s".formatted(
                before.studentId(), after.studentId(), before.occurredOn(), after.occurredOn(),
                before.typeCode(), after.typeCode(), before.version(), after.version(), reason);
    }

    private AccountMapper.Student boundStudent(String number, LocalDate today) {
        if (accounts.lockStudentByNumber(number) == null) throw BusinessException.bad(NO_BOUND_STUDENT);
        var student = accounts.student(number);
        if (student == null || student.validUntil().isBefore(today) || accounts.hasEnabledBinding(student.id()) == 0) {
            throw BusinessException.bad(NO_BOUND_STUDENT);
        }
        return student;
    }

    private static void requireOccurrenceDate(LocalDate date, LocalDate today) {
        if (date.isBefore(MIN_OCCURRENCE_DATE) || date.isAfter(today)
                || date.getDayOfWeek().getValue() > DayOfWeek.FRIDAY.getValue()) {
            throw BusinessException.bad(INVALID_OCCURRENCE_DATE);
        }
    }

    @Transactional
    public void delete(Viewer viewer, long id, long version) {
        LocalDate today = LocalDate.now(clock);
        var row = visible(viewer, id, today);
        if (!canEdit(viewer, row, today)) throw BusinessException.forbidden();
        requireUpdated(records.delete(id, version, clock.millis()));
        audit.record(viewer.id(), AuditEvent.DELETE_RECORD, Target.VIOLATION, id, row.status().name(), AuditEvent.Marker.DELETED.name());
    }

    @Transactional
    public BatchResult review(Viewer viewer, Review input) {
        if (!viewer.allRecords() && viewer.scopes(DEPARTMENT_HEAD).isEmpty()) throw BusinessException.forbidden();
        String reason = input.reason() == null ? null : input.reason().strip();
        if (input.action() == ReviewAction.REJECT && (reason == null || reason.isEmpty())) {
            throw BusinessException.bad(REJECTION_REASON_REQUIRED);
        }
        ViolationStatus next = input.action() == ReviewAction.APPROVE ? ViolationStatus.APPROVED : ViolationStatus.PENDING;
        List<Long> changed = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        SortedMap<Long, Long> versions = new TreeMap<>();
        for (Item item : input.items()) {
            if (versions.putIfAbsent(item.id(), item.version()) != null) throw BusinessException.bad(DUPLICATE_REVIEW_ITEMS);
        }
        for (var item : versions.entrySet()) {
            long id = item.getKey();
            var row = records.byId(id);
            if (row == null || !canReview(viewer, row, today)
                    || records.changeState(id, item.getValue(), ViolationStatus.PENDING, next,
                    next == ViolationStatus.PENDING ? reason : null, null, null) != 1) {
                skipped.add(id);
                continue;
            }
            changed.add(id);
            audit.record(viewer.id(), input.action() == ReviewAction.APPROVE ? AuditEvent.APPROVE : AuditEvent.REJECT, Target.VIOLATION, id, ViolationStatus.PENDING.name(), next.name());
            if (next == ViolationStatus.APPROVED) {
                notifications.student(row.studentId(), VIOLATION_APPROVED, id, item.getValue() + 1);
            } else {
                notifications.user(row.createdBy(), VIOLATION_REJECTED, id, item.getValue() + 1);
            }
        }
        return new BatchResult(changed, skipped);
    }

    @Transactional
    public void resolve(Viewer viewer, long id, Resolve input) {
        if (!viewer.office()) throw BusinessException.forbidden();
        var row = records.byId(id);
        if (row == null || row.status() != ViolationStatus.APPROVED) throw BusinessException.missing();
        ViolationStatus next = input.resolution().status();
        String appealLabel = workflow.appealLabel(input.resolution(), input.appealCode());
        requireUpdated(records.changeState(id, input.version(), ViolationStatus.APPROVED, next, null,
                next == ViolationStatus.HANDED_BACK ? clock.millis() : null, appealLabel));
        audit.record(viewer.id(), AuditEvent.RESOLVE, Target.VIOLATION, id, ViolationStatus.APPROVED.name(), next.name(), appealLabel);
        notifications.student(row.studentId(), next == ViolationStatus.HANDED_BACK ? VIOLATION_HANDED_BACK : VIOLATION_APPEALED,
                id, input.version() + 1);
    }

    private ViolationMapper.Row visible(Viewer viewer, long id, LocalDate today) {
        var row = records.byId(id);
        if (row == null || (row.status() == ViolationStatus.PENDING && row.occurredOn().isBefore(policy.oldestPending(today)))
                || retention.window(today).excludes(row.occurredOn(), row.status())) {
            throw BusinessException.missing();
        }
        if (!viewer.allRecords() && !Objects.equals(viewer.studentId(), row.studentId())
                && !Objects.equals(viewer.committeeClass(), row.classId())
                && !viewer.scopes(OFFICER, DEPARTMENT_HEAD).contains(row.departmentCode())
                && !(viewer.office() && row.status() == ViolationStatus.APPROVED)) throw BusinessException.missing();
        return row;
    }

    private Entry entry(Viewer viewer, ViolationMapper.Row row, LocalDate today) {
        boolean reviewer = canReview(viewer, row, today);
        boolean editable = canEdit(viewer, row, today);
        boolean showRejection = reviewer || editable || viewer.id() == row.createdBy();
        return new Entry(row.id(), row.studentNo(), row.studentName(), row.classId(), row.className(), row.counselor(),
                row.occurredOn(), row.departmentCode(), row.typeCode(), row.reason(), row.disposition(),
                row.inspectionScope(), row.remarks(), row.status(), showRejection ? row.rejectionReason() : null,
                row.version(), editable, reviewer, viewer.office() && row.status() == ViolationStatus.APPROVED,
                viewer.coreAdmin() && row.status() != RECORDED,
                InspectionLabels.display(row.inspectionScope(), row.inspectionLabel()),
                viewer.office() || viewer.allRecords() ? row.appealLabel() : null);
    }

    private boolean canReview(Viewer viewer, ViolationMapper.Row row, LocalDate today) {
        return row.status() == ViolationStatus.PENDING && !row.occurredOn().isBefore(policy.oldestPending(today))
                && (viewer.allRecords() || viewer.scopes(DEPARTMENT_HEAD).contains(row.departmentCode()));
    }

    private boolean canEdit(Viewer viewer, ViolationMapper.Row row, LocalDate today) {
        return viewer.allRecords() || (policy.withinEditWindow(row.occurredOn(), today)
                && viewer.scopes(OFFICER, DEPARTMENT_HEAD).contains(row.departmentCode()));
    }

    static ViolationMapper.Scope scopeFor(Viewer viewer) {
        return new ViolationMapper.Scope(viewer.allRecords(), viewer.studentId(), viewer.committeeClass(),
                viewer.scopes(OFFICER, DEPARTMENT_HEAD), viewer.office());
    }

    static void requireDateRange(Filter query) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw BusinessException.bad(INVALID_DATE_RANGE);
        }
    }

    private static void requireUpdated(int changed) {
        if (changed != 1) throw BusinessException.conflict(STALE_RECORD);
    }
}
