package org.example.disciplinaryrecordsmanagement.service;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.example.disciplinaryrecordsmanagement.config.SmtpRuntime;
import org.example.disciplinaryrecordsmanagement.dto.SmtpSettingsDto.*;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.SmtpSettingsMapper;
import org.example.disciplinaryrecordsmanagement.mapper.SmtpSettingsMapper.Saved;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.example.disciplinaryrecordsmanagement.security.SmtpSecret;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.*;
import static org.example.disciplinaryrecordsmanagement.constant.SmtpSettingsConstants.*;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;
import static org.example.disciplinaryrecordsmanagement.model.AuditEvent.*;

@Service
public class SmtpSettingsService {
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(5);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Pattern NEW_PASSWORD = Pattern.compile(PASSWORD_PATTERN);

    private final SmtpSettingsMapper mapper;
    private final SmtpRuntime runtime;
    private final SmtpSecret secrets;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final Clock clock;
    private long windowEnd;
    private int failedAttempts;

    SmtpSettingsService(SmtpSettingsMapper mapper, SmtpRuntime runtime, SmtpSecret secrets,
                        PasswordEncoder passwords, AuditService audit, Clock clock) {
        this.mapper = mapper;
        this.runtime = runtime;
        this.secrets = secrets;
        this.passwords = passwords;
        this.audit = audit;
        this.clock = clock;
    }

    public record Access(String token, long accountId, long gateVersion, long expiresAt) {}

    public Access unlock(Viewer admin, Unlock input) {
        admin.requireAdmin();
        var gate = mapper.gate(SMTP_SETTINGS_ID);
        checkUnlockPassword(input.password(), gate.gateHash());
        return new Access(UUID.randomUUID().toString(), admin.id(), gate.gateVersion(),
                clock.millis() + SMTP_UNLOCK_MILLIS);
    }

    @Transactional(readOnly = true)
    public Settings settings(Viewer admin, Access access, String token) {
        admin.requireAdmin();
        requireUnlocked(admin, access, token, mapper.gate(SMTP_SETTINGS_ID).gateVersion());
        return view(mapper.saved(SMTP_SETTINGS_ID));
    }

    @Transactional
    public Settings save(Viewer admin, Access access, String token, ChangeSettings change) {
        admin.requireAdmin();
        requireUnlocked(admin, access, token, mapper.lockGate(SMTP_SETTINGS_ID).gateVersion());
        var saved = mapper.saved(SMTP_SETTINGS_ID);
        if (saved.configVersion() != change.version()) throw BusinessException.conflict(STALE_RECORD);
        boolean replacePassword = change.password() != null && !change.password().isBlank();
        validateSmtp(change, replacePassword || runtime.passwordConfigured(saved));
        String encrypted = saved.encryptedPassword();
        if (replacePassword) {
            try {
                encrypted = secrets.encrypt(change.password());
            } catch (IllegalStateException error) {
                throw BusinessException.bad(SMTP_SETTINGS_SECRET_ERROR);
            }
        }
        if (mapper.save(SMTP_SETTINGS_ID, change, encrypted) != 1) throw BusinessException.conflict(STALE_RECORD);
        audit.record(admin.id(), UPDATE_SMTP_SETTINGS, Target.SMTP_SETTINGS, SMTP_SETTINGS_ID, null, null);
        return view(mapper.saved(SMTP_SETTINGS_ID));
    }

    @Transactional
    public void changePassword(Viewer admin, Access access, String token, PasswordChange input) {
        admin.requireAdmin();
        var gate = mapper.lockGate(SMTP_SETTINGS_ID);
        requireUnlocked(admin, access, token, gate.gateVersion());
        checkUnlockPassword(input.currentPassword(), gate.gateHash());
        if (input.currentPassword().equals(input.newPassword())) throw BusinessException.bad(NEW_PASSWORD_REUSED);
        if (!NEW_PASSWORD.matcher(input.newPassword()).matches()) {
            throw BusinessException.bad(PASSWORD_RULE.formatted(MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH));
        }
        mapper.replaceGate(SMTP_SETTINGS_ID, passwords.encode(input.newPassword()));
        audit.record(admin.id(), CHANGE_SMTP_SETTINGS_PASSWORD, Target.SMTP_SETTINGS, SMTP_SETTINGS_ID, null, null);
    }

    private Settings view(Saved saved) {
        var settings = runtime.effective(saved);
        return new Settings(settings.enabled(), settings.host(), settings.port(), settings.username(),
                settings.senderAddress(), settings.authentication(), settings.transport(),
                runtime.passwordConfigured(saved), saved.configVersion(),
                saved.configVersion() != runtime.activeVersion(), runtime.active(), runtime.canSendMail());
    }

    private void requireUnlocked(Viewer viewer, Access access, String token, long gateVersion) {
        if (access == null || !access.token().equals(token) || access.accountId() != viewer.id()
                || access.gateVersion() != gateVersion || access.expiresAt() <= clock.millis()) {
            throw BusinessException.smtpLocked();
        }
    }

    private synchronized void checkUnlockPassword(String password, String hash) {
        long now = clock.millis();
        if (now >= windowEnd) {
            failedAttempts = 0;
            windowEnd = now + ATTEMPT_WINDOW.toMillis();
        }
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) throw BusinessException.rateLimited();
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES || !passwords.matches(password, hash)) {
            failedAttempts++;
            throw BusinessException.bad(SMTP_SETTINGS_PASSWORD_INVALID);
        }
        failedAttempts = 0;
    }

    private static void validateSmtp(ChangeSettings change, boolean hasPassword) {
        if (change.enabled() && (change.host().isBlank() || change.senderAddress().isBlank()
                || (change.authentication() && (change.username().isBlank() || !hasPassword)))) {
            throw BusinessException.bad(SMTP_SETTINGS_REQUIRED);
        }
        if (!change.senderAddress().isBlank()) {
            try {
                var address = new InternetAddress(change.senderAddress(), true);
                address.validate();
                if (address.isGroup() || !change.senderAddress().equals(address.getAddress())) {
                    throw BusinessException.bad(SMTP_SETTINGS_REQUIRED);
                }
            } catch (AddressException error) {
                throw BusinessException.bad(SMTP_SETTINGS_REQUIRED);
            }
        }
    }
}
