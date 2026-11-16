package org.example.disciplinaryrecordsmanagement.service;

import jakarta.mail.MessagingException;

import org.example.disciplinaryrecordsmanagement.config.SmtpRuntime;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

@Component
public class RegistrationMailSender {
    private static final Logger LOG = LoggerFactory.getLogger(RegistrationMailSender.class);
    private static final String SUBJECT = "自律会工作台注册验证码";
    private static final String BODY = "你的注册验证码是 %s。请在 %d 分钟内完成注册。请勿将验证码告知他人。";
    private final SmtpRuntime smtp;

    RegistrationMailSender(SmtpRuntime smtp) {
        this.smtp = smtp;
    }

    void requireAvailable() {
        if (!smtp.canSendMail()) throw BusinessException.registrationEmailUnavailable();
    }

    public void send(String address, String code, long validMinutes) {
        requireAvailable();
        try {
            smtp.delivery().sendText(address, SUBJECT, BODY.formatted(code, validMinutes));
        } catch (MailException | MessagingException error) {
            LOG.warn("Registration email could not be sent: type={}", error.getClass().getSimpleName());
            throw BusinessException.registrationEmailUnavailable();
        }
    }
}
