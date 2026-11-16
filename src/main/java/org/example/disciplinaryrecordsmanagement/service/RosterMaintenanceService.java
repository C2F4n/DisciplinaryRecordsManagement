package org.example.disciplinaryrecordsmanagement.service;

import org.example.disciplinaryrecordsmanagement.dto.RosterMaintenanceDto.*;
import org.example.disciplinaryrecordsmanagement.dto.CounselorDto.Option;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.AccountMapper;
import org.example.disciplinaryrecordsmanagement.mapper.RosterMaintenanceMapper;
import org.example.disciplinaryrecordsmanagement.model.Role;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Clock;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.*;
import static org.example.disciplinaryrecordsmanagement.model.AuditEvent.*;
import static org.springframework.util.StringUtils.hasText;

@Service
public class RosterMaintenanceService {
    private static final int STUDENT_NUMBER_RULE_ID = 1;
    private static final LocalDate MIN_ENROLLMENT_VALIDITY = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_ENROLLMENT_VALIDITY = LocalDate.of(9999, 12, 31);

    private final RosterMaintenanceMapper mapper;
    private final AccountMapper accounts;
    private final CounselorService counselors;
    private final AuditService audit;
    private final Clock clock;

    RosterMaintenanceService(RosterMaintenanceMapper mapper, AccountMapper accounts, CounselorService counselors,
                             AuditService audit, Clock clock) {
        this.mapper = mapper;
        this.accounts = accounts;
        this.counselors = counselors;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ManagedClass> classes(Viewer admin) {
        admin.requireAdmin();
        var selected = counselors.classCounselors();
        return mapper.classes().stream()
                .map(row -> managedClass(row, selected.getOrDefault(row.id(), List.of(row.counselor()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<StudentProfile> students(Viewer admin, Long classId, String query, int page, int size) {
        admin.requireAdmin();
        String pattern = query.isBlank() ? null
                : "%" + query.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new PageResult<>(mapper.students(classId, pattern, size, (page - 1) * size),
                mapper.studentCount(classId, pattern));
    }

    @Transactional
    public ManagedClass updateClass(Viewer admin, long id, ChangeClass input) {
        admin.requireAdmin();
        requireCurrentVersion(accounts.lockGroup(id), input.version());
        var selected = counselors.registeredCounselors(input.counselors(), input.counselor());
        requireUpdated(mapper.updateClass(id, input.name().strip(), CounselorService.displayNames(selected), input.version()));
        counselors.assignToClass(id, selected);
        var saved = mapper.managedClass(id);
        audit.record(admin.id(), UPDATE_CLASS, Target.CLASS, id, revision(input.version()), revision(saved.version()));
        return managedClass(saved, selected.stream().map(Option::name).toList());
    }

    @Transactional
    public StudentProfile updateStudent(Viewer admin, long id, ChangeStudent input) {
        admin.requireAdmin();
        var old = mapper.student(id);
        if (old == null) throw BusinessException.missing();
        if (input.validUntil().isBefore(MIN_ENROLLMENT_VALIDITY) || input.validUntil().isAfter(MAX_ENROLLMENT_VALIDITY)) {
            throw BusinessException.bad(ENROLLMENT_VALIDITY_INVALID);
        }
        String phone = hasText(input.phone()) ? input.phone() : null;
        var change = new ChangeStudent(input.name().strip(), input.classId(), phone, input.validUntil(), input.version());
        boolean moved = change.classId() != old.classId();
        if (moved && accounts.lockGroup(change.classId()) == null) throw BusinessException.bad(CLASS_NOT_FOUND);
        requireUpdated(mapper.updateStudent(id, change));
        int revoked = moved ? mapper.revokeStudentGrants(id, Role.CLASS_COMMITTEE) : 0;
        var saved = mapper.student(id);
        audit.record(admin.id(), UPDATE_STUDENT_PROFILE, Target.STUDENT, id,
                studentState(old), studentState(saved));
        if (revoked > 0) {
            audit.record(admin.id(), REVOKE_STUDENT_COMMITTEE, Target.STUDENT, id, Long.toString(old.classId()), null);
        }
        return saved;
    }

    @Transactional
    public void deleteStudent(Viewer admin, long id, long version) {
        admin.requireAdmin();
        requireCurrentVersion(mapper.lockStudent(id), version);
        removeStudent(admin, id, version);
    }

    @Transactional
    public int deleteClassStudents(Viewer admin, long id, long version, int expectedCount) {
        admin.requireAdmin();
        requireCurrentVersion(accounts.lockGroup(id), version);
        var students = mapper.lockClassStudents(id);
        if (students.size() != expectedCount) throw BusinessException.conflict(CLASS_ROSTER_CHANGED);
        for (var student : students) {
            removeStudent(admin, student.id(), student.version());
        }
        return students.size();
    }

    private void removeStudent(Viewer admin, long id, long version) {
        if (mapper.deleteUnreferencedStudent(id) == 0) {
            requireUpdated(mapper.archiveStudent(id, clock.millis()));
            mapper.disableStudentAccount(id);
        }
        audit.record(admin.id(), DELETE_STUDENT, Target.STUDENT, id, revision(version), Marker.DELETED.name());
    }

    @Transactional
    public void deleteClass(Viewer admin, long id, long version) {
        admin.requireAdmin();
        requireCurrentVersion(accounts.lockGroup(id), version);
        if (mapper.activeStudentsInClass(id) > 0) throw BusinessException.conflict(CLASS_HAS_STUDENTS);
        if (mapper.deleteUnreferencedClass(id) == 0) requireUpdated(mapper.archiveClass(id, clock.millis()));
        audit.record(admin.id(), DELETE_CLASS, Target.CLASS, id, revision(version), Marker.DELETED.name());
    }

    public StudentNumberRule studentNumberRule(Viewer admin) {
        admin.requireAdmin();
        return studentNumberRule();
    }

    StudentNumberRule studentNumberRule() {
        return mapper.studentNumberRule(STUDENT_NUMBER_RULE_ID);
    }

    @Transactional
    public StudentNumberRule updateStudentNumberRule(Viewer admin, StudentNumberRule input) {
        admin.requireAdmin();
        if (input.minLength() > input.maxLength() || input.prefix().length() > input.maxLength()) {
            throw BusinessException.bad(STUDENT_NUMBER_RULE_INVALID);
        }
        requireUpdated(mapper.updateStudentNumberRule(STUDENT_NUMBER_RULE_ID, input));
        var saved = studentNumberRule();
        audit.record(admin.id(), UPDATE_STUDENT_NUMBER_RULE, Target.STUDENT_NUMBER_RULE, STUDENT_NUMBER_RULE_ID,
                revision(input.version()), revision(saved.version()));
        return saved;
    }

    private static void requireUpdated(int count) {
        if (count != 1) throw BusinessException.conflict(STALE_RECORD);
    }

    private static ManagedClass managedClass(ClassRow row, List<String> selected) {
        return new ManagedClass(row.id(), row.name(), row.counselor(), row.version(), row.studentCount(), selected);
    }

    private static void requireCurrentVersion(Long current, long expected) {
        if (current == null) throw BusinessException.missing();
        if (current != expected) throw BusinessException.conflict(STALE_RECORD);
    }

    private static String studentState(StudentProfile student) {
        return student.classId() + "/" + student.validUntil();
    }

    private static String revision(long version) {
        return Long.toString(version);
    }
}
