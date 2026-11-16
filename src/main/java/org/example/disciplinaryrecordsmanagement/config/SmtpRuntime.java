package org.example.disciplinaryrecordsmanagement.config;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.example.disciplinaryrecordsmanagement.dto.SmtpSettingsDto.Transport;
import org.example.disciplinaryrecordsmanagement.mapper.SmtpSettingsMapper;
import org.example.disciplinaryrecordsmanagement.mapper.SmtpSettingsMapper.Saved;
import org.example.disciplinaryrecordsmanagement.security.SmtpSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static org.example.disciplinaryrecordsmanagement.constant.SmtpSettingsConstants.SMTP_SETTINGS_ID;

@Component
public class SmtpRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(SmtpRuntime.class);
    private final Saved environmentSettings;
    private final String environmentPassword;
    private final long activeVersion;
    private final Delivery delivery;
    private final boolean violationEmailEnabled;

    SmtpRuntime(SmtpSettingsMapper mapper, SmtpSecret secrets, Environment environment) {
        var databaseSettings = mapper.saved(SMTP_SETTINGS_ID);
        boolean ssl = Boolean.parseBoolean(mailProperty(environment, "mail.smtp.ssl.enable"));
        boolean startTls = Boolean.parseBoolean(mailProperty(environment, "mail.smtp.starttls.enable"));
        environmentSettings = new Saved(0, environment.getRequiredProperty("app.notice.email.enabled", Boolean.class),
                environment.getRequiredProperty("spring.mail.host"), environment.getRequiredProperty("spring.mail.port", Integer.class),
                environment.getRequiredProperty("spring.mail.username"), environment.getRequiredProperty("app.notice.email.from"),
                Boolean.parseBoolean(mailProperty(environment, "mail.smtp.auth")),
                ssl ? Transport.SSL : startTls ? Transport.STARTTLS : Transport.NONE, null);
        environmentPassword = environment.getRequiredProperty("spring.mail.password");
        var initial = effective(databaseSettings);
        activeVersion = initial.configVersion();
        violationEmailEnabled = initial.enabled();
        Delivery configured = null;
        if (initial.enabled() || !initial.host().isBlank() || !initial.senderAddress().isBlank()) {
            try {
                String password = !initial.authentication() ? null : initial.encryptedPassword() == null
                        ? environmentPassword : secrets.decrypt(initial.encryptedPassword());
                configured = configure(initial, password, environment);
            } catch (AddressException | IllegalStateException error) {
                LOG.error("SMTP notifications are unavailable; verify the settings and the SMTP key file before restarting");
            }
        }
        delivery = configured;
    }

    static Delivery configure(Saved settings, String password, Environment environment) throws AddressException {
        if (settings.host().isBlank() || settings.senderAddress().isBlank()
                || (settings.authentication() && (settings.username().isBlank() || password == null || password.isBlank()))) {
            throw new IllegalStateException("Incomplete SMTP settings");
        }
        var address = new InternetAddress(settings.senderAddress(), true);
        address.validate();
        var sender = new JavaMailSenderImpl();
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        if (settings.authentication()) {
            sender.setUsername(settings.username());
            sender.setPassword(password);
        }
        var props = sender.getJavaMailProperties();
        props.setProperty("mail.smtp.auth", Boolean.toString(settings.authentication()));
        props.setProperty("mail.smtp.starttls.enable", Boolean.toString(settings.transport() == Transport.STARTTLS));
        props.setProperty("mail.smtp.starttls.required", Boolean.toString(settings.transport() == Transport.STARTTLS));
        props.setProperty("mail.smtp.ssl.enable", Boolean.toString(settings.transport() == Transport.SSL));
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        for (String timeout : new String[]{"connectiontimeout", "timeout", "writetimeout"}) {
            String key = "mail.smtp." + timeout;
            props.setProperty(key, mailProperty(environment, key));
        }
        return new Delivery(sender, address);
    }

    private static String mailProperty(Environment environment, String name) {
        String direct = environment.getProperty("spring.mail.properties." + name);
        return direct == null ? environment.getRequiredProperty("spring.mail.properties[" + name + "]") : direct;
    }

    public Saved effective(Saved saved) {
        return saved.configVersion() == 0 ? environmentSettings : saved;
    }

    public boolean passwordConfigured(Saved saved) {
        return saved.encryptedPassword() != null || !environmentPassword.isBlank();
    }

    public boolean active() { return violationEmailEnabled && delivery != null; }
    public boolean canSendMail() { return delivery != null; }
    public long activeVersion() { return activeVersion; }
    public Delivery delivery() { return delivery; }

    public record Delivery(JavaMailSender sender, InternetAddress from) {
        public void sendText(String address, String subject, String text) throws MessagingException {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setValidateAddresses(true);
            helper.setFrom(from);
            helper.setTo(new InternetAddress(address, true));
            helper.setSubject(subject);
            helper.setText(text);
            sender.send(message);
        }
    }
}
