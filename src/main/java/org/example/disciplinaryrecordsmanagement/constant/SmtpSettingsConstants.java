package org.example.disciplinaryrecordsmanagement.constant;

public final class SmtpSettingsConstants {
    public static final long SMTP_UNLOCK_MILLIS = 10 * 60_000L;
    public static final int SMTP_SETTINGS_ID = 1;
    public static final int MAX_HOST_LENGTH = 253;
    public static final int MAX_SMTP_PASSWORD_LENGTH = 512;
    public static final int MAX_PORT = 65535;
    public static final String SMTP_SETTINGS_PATH = "/admin/smtp-settings";
    public static final String SMTP_UNLOCK_HEADER = "X-SMTP-Unlock";
    public static final String SMTP_HOST_PATTERN = "[A-Za-z0-9_.:\\[\\]-]*";

    private SmtpSettingsConstants() {}
}
