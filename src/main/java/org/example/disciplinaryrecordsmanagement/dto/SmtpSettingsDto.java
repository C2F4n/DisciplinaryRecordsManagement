package org.example.disciplinaryrecordsmanagement.dto;

import jakarta.validation.constraints.*;

import static org.example.disciplinaryrecordsmanagement.constant.SmtpSettingsConstants.*;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

public final class SmtpSettingsDto {
    private SmtpSettingsDto() {}

    public enum Transport { STARTTLS, SSL, NONE }

    public record Unlock(@NotBlank @Size(max = MAX_PASSWORD_LENGTH) String password) {}
    public record Unlocked(String token, long expiresInMillis, Settings settings) {}
    public record PasswordChange(@NotBlank @Size(max = MAX_PASSWORD_LENGTH) String currentPassword,
                                 @NotBlank @Size(max = MAX_PASSWORD_LENGTH) String newPassword) {}

    public record ChangeSettings(@NotNull Boolean enabled, @NotNull @Size(max = MAX_HOST_LENGTH)
                                 @Pattern(regexp = SMTP_HOST_PATTERN) String host,
                                 @Min(1) @Max(MAX_PORT) int port,
                                 @NotNull @Size(max = MAX_EMAIL_LENGTH) String username,
                                 @NotNull @Size(max = MAX_EMAIL_LENGTH) @Email String senderAddress,
                                 @NotNull Boolean authentication, @NotNull Transport transport,
                                 @Size(max = MAX_SMTP_PASSWORD_LENGTH) String password,
                                 @PositiveOrZero long version) {}

    public record Settings(boolean enabled, String host, int port, String username, String senderAddress,
                           boolean authentication, Transport transport, boolean passwordConfigured,
                           long version, boolean restartRequired, boolean active, boolean registrationEmailReady) {}
}
