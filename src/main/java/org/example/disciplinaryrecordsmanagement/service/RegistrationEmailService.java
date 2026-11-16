package org.example.disciplinaryrecordsmanagement.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.example.disciplinaryrecordsmanagement.dto.AccountDto.EmailCodeSent;
import org.example.disciplinaryrecordsmanagement.dto.AccountDto.RegistrationIdentity;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.AccountMapper;
import org.example.disciplinaryrecordsmanagement.mapper.AccountMapper.Enrollment;
import org.example.disciplinaryrecordsmanagement.mapper.RegistrationEmailMapper;
import org.example.disciplinaryrecordsmanagement.mapper.RegistrationEmailMapper.Challenge;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.REGISTRATION_MAIL_COOLDOWN;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.REGISTRATION_MISMATCH;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.EMAIL_CODE_LENGTH;
import static org.springframework.util.StringUtils.hasText;

@Service
public class RegistrationEmailService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_SPACE = (int) Math.pow(10, EMAIL_CODE_LENGTH);
    private static final String CODE_FORMAT = "%0" + EMAIL_CODE_LENGTH + "d";
    private static final Duration VALIDITY = Duration.ofMinutes(10);
    private static final Duration RESEND_DELAY = Duration.ofMinutes(1);
    private static final int MAX_ATTEMPTS = 5;
    private final AccountMapper accounts;
    private final RegistrationEmailMapper codes;
    private final RegistrationMailSender mail;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final TransactionTemplate transactions;

    RegistrationEmailService(AccountMapper accounts, RegistrationEmailMapper codes, RegistrationMailSender mail,
                             PasswordEncoder encoder, Clock clock, PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.codes = codes;
        this.mail = mail;
        this.encoder = encoder;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public EmailCodeSent send(RegistrationIdentity input) {
        String code = String.format(Locale.ROOT, CODE_FORMAT, RANDOM.nextInt(CODE_SPACE));
        var challenge = transactions.execute(status -> reserve(input, code));
        try {
            mail.send(challenge.email(), code, VALIDITY.toMinutes());
        } catch (BusinessException error) {
            codes.remove(challenge.studentId(), challenge.challengeId());
            throw error;
        }
        return new EmailCodeSent(VALIDITY.toSeconds(), RESEND_DELAY.toSeconds());
    }

    Enrollment requireEligible(RegistrationIdentity input) {
        var student = accounts.lockEnrollment(input.studentNo());
        if (student == null || !student.name().equals(input.name().strip()) || student.classId() != input.classId()
                || (hasText(student.phone()) && !student.phone().equals(input.phone()))
                || student.validUntil().isBefore(LocalDate.now(clock)) || accounts.account(student.studentNo()) != null) {
            throw BusinessException.bad(REGISTRATION_MISMATCH);
        }
        return student;
    }

    boolean consume(long studentId, String email, String code) {
        var challenge = codes.lock(studentId);
        if (challenge == null || !challenge.email().equals(email) || challenge.expiresAt() <= clock.millis()
                || challenge.failedAttempts() >= MAX_ATTEMPTS) return false;
        if (!encoder.matches(code, challenge.codeHash())) {
            codes.recordFailure(studentId);
            return false;
        }
        codes.remove(studentId, challenge.challengeId());
        return true;
    }

    private Challenge reserve(RegistrationIdentity input, String code) {
        var student = requireEligible(input);
        mail.requireAvailable();
        var previous = codes.lock(student.id());
        long now = clock.millis();
        if (previous != null && previous.issuedAt() + RESEND_DELAY.toMillis() > now) {
            throw BusinessException.rateLimited(REGISTRATION_MAIL_COOLDOWN);
        }
        var challenge = new Challenge(student.id(), UUID.randomUUID().toString(), input.email(), encoder.encode(code),
                now, now + VALIDITY.toMillis(), 0);
        if (previous == null) codes.insert(challenge);
        else codes.replace(challenge);
        return challenge;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    void clearExpired() {
        codes.removeExpired(clock.millis());
    }
}
