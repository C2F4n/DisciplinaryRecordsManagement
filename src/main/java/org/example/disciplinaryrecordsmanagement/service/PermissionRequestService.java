package org.example.disciplinaryrecordsmanagement.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

import org.example.disciplinaryrecordsmanagement.dto.AccountDto.Department;
import org.example.disciplinaryrecordsmanagement.dto.PermissionRequestDto.Decision;
import org.example.disciplinaryrecordsmanagement.dto.PermissionRequestDto.Entry;
import org.example.disciplinaryrecordsmanagement.dto.PermissionRequestDto.Submit;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.AccountMapper;
import org.example.disciplinaryrecordsmanagement.mapper.PermissionRequestMapper;
import org.example.disciplinaryrecordsmanagement.mapper.PermissionRequestMapper.ReviewScope;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;
import org.example.disciplinaryrecordsmanagement.model.PermissionRequestStatus;
import org.example.disciplinaryrecordsmanagement.model.Role;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.DEPARTMENT_NOT_FOUND;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.PERMISSION_ALREADY_GRANTED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.PERMISSION_CLASS_CHANGED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.PERMISSION_PENDING;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.PERMISSION_PROCESSED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.PERMISSION_ROLE_UNAVAILABLE;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.PERMISSION_STUDENT_INACTIVE;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.ROLE_WITHOUT_SCOPE;
import static org.example.disciplinaryrecordsmanagement.model.AuditEvent.APPROVE_PERMISSION;
import static org.example.disciplinaryrecordsmanagement.model.AuditEvent.REJECT_PERMISSION;
import static org.example.disciplinaryrecordsmanagement.model.AuditEvent.REQUEST_PERMISSION;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.PERMISSION_APPROVED;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.PERMISSION_REJECTED;
import static org.example.disciplinaryrecordsmanagement.model.NotificationType.PERMISSION_REQUESTED;
import static org.example.disciplinaryrecordsmanagement.model.PermissionRequestStatus.APPROVED;
import static org.example.disciplinaryrecordsmanagement.model.PermissionRequestStatus.PENDING;
import static org.example.disciplinaryrecordsmanagement.model.PermissionRequestStatus.REJECTED;
import static org.example.disciplinaryrecordsmanagement.model.Role.CLASS_COMMITTEE;
import static org.example.disciplinaryrecordsmanagement.model.Role.DEPARTMENT_HEAD;
import static org.example.disciplinaryrecordsmanagement.model.Role.OFFICER;
import static org.example.disciplinaryrecordsmanagement.model.Role.PRESIDENT;
import static org.example.disciplinaryrecordsmanagement.model.Role.TEACHER;

@Service
public class PermissionRequestService {
    private final PermissionRequestMapper requests;
    private final AccountMapper accounts;
    private final AccountService accountService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    PermissionRequestService(PermissionRequestMapper requests, AccountMapper accounts, AccountService accountService,
                             NotificationService notifications, AuditService audit, Clock clock) {
        this.requests = requests;
        this.accounts = accounts;
        this.accountService = accountService;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<Entry> mine(Viewer viewer, int page, int size) {
        requireStudent(viewer);
        return new PageResult<>(requests.mine(viewer.id(), size, (page - 1L) * size), requests.myCount(viewer.id()));
    }

    @Transactional(readOnly = true)
    public PageResult<Entry> queue(Viewer viewer, PermissionRequestStatus status, int page, int size) {
        requireReviewer(viewer);
        var scope = new ReviewScope(viewer.id(), viewer.coreAdmin(), collegeReviewer(viewer), viewer.scopes(DEPARTMENT_HEAD));
        return new PageResult<>(requests.queue(scope, status, size, (page - 1L) * size), requests.queueCount(scope, status));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void submit(Viewer viewer, Submit input) {
        requireStudent(viewer);
        Role role = input.role();
        if (role == TEACHER) throw BusinessException.bad(PERMISSION_ROLE_UNAVAILABLE);
        var student = lockActiveStudent(viewer.id(), viewer.studentId(), viewer.username());
        String scope = role == CLASS_COMMITTEE ? Long.toString(student.classId()) : clean(input.scope());
        String label = "";
        if (role == CLASS_COMMITTEE) {
            label = accounts.student(student.studentNo()).className();
        } else if (role.departmentScoped()) {
            label = accounts.departments().stream().filter(department -> department.code().equals(scope))
                    .map(Department::name).findFirst().orElseThrow(() -> BusinessException.bad(DEPARTMENT_NOT_FOUND));
        } else if (!scope.isEmpty()) {
            throw BusinessException.bad(ROLE_WITHOUT_SCOPE);
        }
        if (accounts.existingGrant(viewer.id(), role, scope) != null) {
            throw BusinessException.conflict(PERMISSION_ALREADY_GRANTED);
        }
        if (requests.pendingId(viewer.id(), role, scope) != null) {
            throw BusinessException.conflict(PERMISSION_PENDING);
        }
        long now = clock.millis();
        requests.submit(viewer.id(), student.name(), role, scope, label, input.reason().strip(), PENDING, now);
        long id = requests.pendingId(viewer.id(), role, scope);
        audit.record(viewer.id(), REQUEST_PERMISSION, Target.PERMISSION_REQUEST, id, null, PENDING.name());
        for (long reviewer : requests.reviewers(viewer.id(), role, scope, LocalDate.now(clock), now)) {
            notifications.user(reviewer, PERMISSION_REQUESTED, id, 0);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void decide(Viewer viewer, long id, Decision input) {
        requireReviewer(viewer);
        var target = requests.target(id);
        if (target == null) throw BusinessException.missing();
        if (target.requesterId() == viewer.id() || !canReview(viewer, target.roleCode(), target.scopeKey())) {
            throw BusinessException.forbidden();
        }
        if (target.status() != PENDING) throw BusinessException.conflict(PERMISSION_PROCESSED);

        // Lock roster and account before the request on approval, matching the order used by submission.
        if (input.approve()) {
            var student = lockActiveStudent(target.requesterId(), target.studentId(), target.requesterNumber());
            if (target.roleCode() == CLASS_COMMITTEE && !target.scopeKey().equals(Long.toString(student.classId()))) {
                throw BusinessException.conflict(PERMISSION_CLASS_CHANGED);
            }
        } else {
            accounts.lockAccountEnabled(target.requesterId());
        }
        if (requests.lockStatus(id) != PENDING) throw BusinessException.conflict(PERMISSION_PROCESSED);
        var status = input.approve() ? APPROVED : REJECTED;
        if (input.approve() && accounts.existingGrant(target.requesterId(), target.roleCode(), target.scopeKey()) == null) {
            accountService.grantRole(viewer.id(), target.requesterId(), target.roleCode(), target.scopeKey());
        }
        if (requests.decide(id, status, viewer.id(), viewer.displayName(), clock.millis(), clean(input.note())) != 1) {
            throw BusinessException.conflict(PERMISSION_PROCESSED);
        }
        audit.record(viewer.id(), input.approve() ? APPROVE_PERMISSION : REJECT_PERMISSION,
                Target.PERMISSION_REQUEST, id, PENDING.name(), status.name());
        notifications.user(target.requesterId(), input.approve() ? PERMISSION_APPROVED : PERMISSION_REJECTED, id, 0);
    }

    private AccountMapper.Enrollment lockActiveStudent(long userId, Long studentId, String number) {
        var student = accounts.lockEnrollment(number);
        if (student == null || !Objects.equals(student.id(), studentId) || student.validUntil().isBefore(LocalDate.now(clock))
                || !Boolean.TRUE.equals(accounts.lockAccountEnabled(userId))) {
            throw BusinessException.conflict(PERMISSION_STUDENT_INACTIVE);
        }
        return student;
    }

    private static boolean collegeReviewer(Viewer viewer) {
        return viewer.grants().stream().anyMatch(g -> g.roleCode() == PRESIDENT || g.roleCode() == TEACHER);
    }

    private static boolean canReview(Viewer viewer, Role role, String scope) {
        return viewer.coreAdmin() || (collegeReviewer(viewer) && (role == OFFICER || role == CLASS_COMMITTEE))
                || (role == OFFICER && viewer.scopes(DEPARTMENT_HEAD).contains(scope));
    }

    private static void requireReviewer(Viewer viewer) {
        if (!viewer.canReviewPermissionRequests()) throw BusinessException.forbidden();
    }

    private static void requireStudent(Viewer viewer) {
        if (viewer.studentId() == null) throw BusinessException.forbidden();
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
