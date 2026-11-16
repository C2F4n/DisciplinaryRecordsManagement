package org.example.disciplinaryrecordsmanagement.service;

import org.example.disciplinaryrecordsmanagement.config.BusinessPolicy;
import org.example.disciplinaryrecordsmanagement.dto.AccountDto.*;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.AccountMapper;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;
import org.example.disciplinaryrecordsmanagement.model.Role;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.example.disciplinaryrecordsmanagement.security.SessionUser;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.*;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;
import static org.example.disciplinaryrecordsmanagement.model.AuditEvent.*;
import static org.springframework.util.StringUtils.hasText;

@Service
public class AccountService {
    private static final String TEMPORARY_PASSWORD_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_@";
    private static final Pattern NEW_PASSWORD = Pattern.compile(PASSWORD_PATTERN);
    private static final Duration TEMPORARY_PASSWORD_VALIDITY = Duration.ofHours(24);
    private static final int CORE_ADMIN_SLOT = 1;
    private static final Set<String> LOOPBACK_ADDRESSES = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountMapper mapper;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final BusinessPolicy policy;
    private final RosterMaintenanceService roster;
    private final CounselorService counselors;
    private final RegistrationEmailService registrationEmails;
    private final Clock clock;

    AccountService(AccountMapper mapper, PasswordEncoder passwords, AuditService audit, BusinessPolicy policy,
                   RosterMaintenanceService roster, CounselorService counselors, RegistrationEmailService registrationEmails,
                   Clock clock) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.audit = audit;
        this.policy = policy;
        this.roster = roster;
        this.counselors = counselors;
        this.registrationEmails = registrationEmails;
        this.clock = clock;
    }

    public record Credential(String username, String passwordHash, long passwordVersion) {}

    public Optional<Credential> credential(String username) {
        return activeAccount(username).map(a -> new Credential(a.username(), a.passwordHash(), a.passwordVersion()));
    }

    public Viewer viewer(Principal principal) {
        var account = sessionAccount(principal);
        if (account.temporaryPasswordUntil() != null) throw BusinessException.passwordChangeRequired();
        return viewer(account);
    }

    private Viewer viewer(AccountMapper.Account a) {
        return new Viewer(a.id(), a.username(), a.displayName(), a.studentId(), a.classId(),
                a.coreAdminSlot() != null, mapper.grants(a.id()));
    }

    private Optional<AccountMapper.Account> activeAccount(String username) {
        return Optional.ofNullable(mapper.account(username)).filter(a -> a.active(LocalDate.now(clock))
                && (a.temporaryPasswordUntil() == null || a.temporaryPasswordUntil() > clock.millis()));
    }

    private AccountMapper.Account sessionAccount(Principal principal) {
        if (!(principal instanceof Authentication auth) || !(auth.getPrincipal() instanceof SessionUser session)) {
            throw BusinessException.unauthorized();
        }
        return activeAccount(session.getUsername()).filter(a -> a.passwordVersion() == session.passwordVersion())
                .orElseThrow(BusinessException::unauthorized);
    }

    public CurrentUser currentUser(Principal principal) {
        var account = sessionAccount(principal);
        var viewer = viewer(account);
        return new CurrentUser(viewer.id(), viewer.username(), viewer.displayName(), viewer.coreAdmin(),
                viewer.grants(), viewer.studentId() != null, viewer.canReviewPermissionRequests(), viewer.allRecords(), viewer.canExportReports(), viewer.scopes(Role.OFFICER),
                viewer.canViewRecords(), viewer.canViewStatistics(), account.temporaryPasswordUntil() != null);
    }

    @Transactional
    public void changePassword(Principal principal, PasswordChange input) {
        var account = sessionAccount(principal);
        if (input.currentPassword().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES
                || !passwords.matches(input.currentPassword(), account.passwordHash())) {
            throw BusinessException.bad(CURRENT_PASSWORD_INVALID);
        }
        if (input.currentPassword().equals(input.newPassword())) throw BusinessException.bad(NEW_PASSWORD_REUSED);
        if (mapper.updatePassword(account.id(), account.passwordVersion(), hashPassword(input.newPassword()), null) != 1) {
            throw BusinessException.conflict(STALE_RECORD);
        }
        audit.record(account.id(), CHANGE_PASSWORD, Target.ACCOUNT, account.id(), null, null);
    }

    @Transactional
    public TemporaryPassword resetPassword(Viewer admin, long id) {
        admin.requireAdmin();
        String username = mapper.username(id);
        if (username == null) throw BusinessException.missing();
        var target = mapper.account(username);
        if (target.coreAdminSlot() != null) throw BusinessException.forbidden();
        String temporary = temporaryPassword();
        long expires = clock.instant().plus(TEMPORARY_PASSWORD_VALIDITY).toEpochMilli();
        if (mapper.updatePassword(id, target.passwordVersion(), hashPassword(temporary), expires) != 1) {
            throw BusinessException.conflict(STALE_RECORD);
        }
        audit.record(admin.id(), RESET_PASSWORD, Target.ACCOUNT, id, null, null);
        return new TemporaryPassword(temporary, expires);
    }

    public List<PublicClass> bindingClasses() {
        return mapper.groups().stream().map(g -> new PublicClass(g.id(), g.name())).toList();
    }

    public List<Group> groups(Viewer admin) {
        admin.requireAdmin();
        return mapper.groups();
    }

    public List<UserSummary> findUsers(Viewer admin, String username) {
        admin.requireAdmin();
        return mapper.findUsers(username.strip());
    }

    public List<Grant> grants(Viewer admin, long userId) {
        admin.requireAdmin();
        return mapper.grants(userId);
    }

    @Transactional
    public boolean bind(Bind input) {
        var student = registrationEmails.requireEligible(input.identity());
        if (!registrationEmails.consume(student.id(), input.email(), input.emailCode())) return false;
        mapper.createAccount(student.studentNo(), hashPassword(input.password()), student.id(), null, null, input.email());
        var account = mapper.account(student.studentNo());
        audit.record(account.id(), BIND, Target.ACCOUNT, account.id(), null, null);
        return true;
    }

    public EmailSettings notificationEmail(Viewer viewer) {
        if (viewer.studentId() == null) throw BusinessException.forbidden();
        return new EmailSettings(mapper.email(viewer.id()));
    }

    @Transactional
    public void updateNotificationEmail(Viewer viewer, EmailAddress input) {
        if (viewer.studentId() == null) throw BusinessException.forbidden();
        mapper.updateEmail(viewer.id(), input.email());
        audit.record(viewer.id(), UPDATE_NOTIFICATION_EMAIL, Target.ACCOUNT, viewer.id(), null, null);
    }

    @Transactional
    public Group createGroup(Viewer admin, NewGroup input) {
        admin.requireAdmin();
        var selected = counselors.registeredCounselors(input.counselors(), input.counselor());
        String name = input.name().strip();
        mapper.createGroup(name, CounselorService.displayNames(selected));
        var group = mapper.groupByName(name);
        counselors.assignToClass(group.id(), selected);
        audit.record(admin.id(), CREATE_CLASS, Target.CLASS, group.id(), null, null);
        return group;
    }

    @Transactional
    public void createStudent(Viewer admin, NewStudent input) {
        admin.requireAdmin();
        var rule = roster.studentNumberRule();
        if (!rule.accepts(input.studentNo())) throw BusinessException.bad(STUDENT_NUMBER_RULE_MISMATCH.formatted(rule.requirement()));
        if (mapper.lockGroup(input.classId()) == null) throw BusinessException.bad(CLASS_NOT_FOUND);
        if (input.enrolledOn().isAfter(LocalDate.now(clock))) throw BusinessException.bad(ENROLLMENT_IN_FUTURE);
        LocalDate validUntil = input.enrolledOn().plusYears(policy.enrollmentYears()).minusDays(1);
        if (validUntil.isBefore(LocalDate.now(clock))) throw BusinessException.bad(ENROLLMENT_EXPIRED);
        String phone = hasText(input.phone()) ? input.phone() : null;
        mapper.createStudent(input.studentNo(), input.name().strip(), input.classId(), phone, validUntil);
        audit.record(admin.id(), CREATE_STUDENT, Target.STUDENT, mapper.student(input.studentNo()).id(), null, null);
    }

    @Transactional
    public UserSummary createStaff(Viewer admin, NewStaff input) {
        admin.requireAdmin();
        mapper.createAccount(input.username(), hashPassword(input.password()), null, null, null, null);
        var account = mapper.account(input.username());
        audit.record(admin.id(), CREATE_STAFF, Target.ACCOUNT, account.id(), null, null);
        return new UserSummary(account.id(), account.username(), account.displayName(), null, true);
    }

    @Transactional
    public void changeGrant(Viewer admin, long userId, GrantChange input, boolean add) {
        admin.requireAdmin();
        String username = mapper.username(userId);
        if (username == null) throw BusinessException.missing();
        var target = mapper.account(username);
        if (target.coreAdminSlot() != null) throw BusinessException.bad(CORE_ADMIN_ALREADY_PRIVILEGED);
        String scope = input.scope() == null ? "" : input.scope().strip();
        if (input.role().departmentScoped()) {
            if (mapper.departmentExists(scope) == 0) throw BusinessException.bad(DEPARTMENT_NOT_FOUND);
        } else if (input.role() == Role.CLASS_COMMITTEE) {
            if (add) {
                Long classId = target.studentId() == null ? null : mapper.lockStudentClass(target.studentId());
                if (classId == null || !scope.equals(classId.toString())) throw BusinessException.bad(CLASS_COMMITTEE_SCOPE);
            }
        } else if (!scope.isEmpty()) throw BusinessException.bad(ROLE_WITHOUT_SCOPE);

        mapper.lockAccountEnabled(userId);
        if (add) {
            grantRole(admin.id(), userId, input.role(), scope);
        } else {
            if (mapper.removeGrant(userId, input.role(), scope) == 0) throw BusinessException.missing();
            audit.record(admin.id(), REVOKE_ROLE, Target.ACCOUNT, userId, grantState(input.role(), scope), null);
        }
    }

    void grantRole(long actorId, long userId, Role role, String scope) {
        mapper.addGrant(userId, role, scope);
        audit.record(actorId, GRANT_ROLE, Target.ACCOUNT, userId, null, grantState(role, scope));
    }

    private static String grantState(Role role, String scope) {
        return role.name() + (scope.isEmpty() ? "" : ":" + scope);
    }

    @Transactional
    public void setEnabled(Viewer admin, long userId, boolean enabled) {
        admin.requireAdmin();
        if (mapper.setAccountEnabled(userId, enabled) != 1) throw BusinessException.missing();
        audit.record(admin.id(), enabled ? ENABLE_ACCOUNT : DISABLE_ACCOUNT, Target.ACCOUNT, userId, null, null);
    }

    @Transactional
    public void bootstrap(String username, String password, String bindAddress) {
        if (password.isBlank() || mapper.countCoreAdmins() != 0) return;
        if (!username.matches(STAFF_NAME)) throw new IllegalStateException("APP_ADMIN_USERNAME must be a valid staff login");
        if (!password.matches(LOCAL_BOOTSTRAP_PIN_PATTERN)) {
            mapper.createAccount(username, hashPassword(password), null, CORE_ADMIN_SLOT, null, null);
            return;
        }
        if (!LOOPBACK_ADDRESSES.contains(bindAddress)) {
            throw new IllegalStateException("A bootstrap PIN requires a loopback server address");
        }
        long expires = clock.instant().plus(TEMPORARY_PASSWORD_VALIDITY).toEpochMilli();
        mapper.createAccount(username, passwords.encode(password), null, CORE_ADMIN_SLOT, expires, null);
    }

    private String hashPassword(String password) {
        if (!NEW_PASSWORD.matcher(password).matches()) {
            throw BusinessException.bad(PASSWORD_RULE.formatted(MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH));
        }
        return passwords.encode(password);
    }

    private static String temporaryPassword() {
        char[] characters = new char[MAX_PASSWORD_LENGTH];
        String candidate;
        do {
            for (int i = 0; i < characters.length; i++) {
                characters[i] = TEMPORARY_PASSWORD_CHARACTERS.charAt(RANDOM.nextInt(TEMPORARY_PASSWORD_CHARACTERS.length()));
            }
            candidate = new String(characters);
        } while (!NEW_PASSWORD.matcher(candidate).matches());
        return candidate;
    }

}
