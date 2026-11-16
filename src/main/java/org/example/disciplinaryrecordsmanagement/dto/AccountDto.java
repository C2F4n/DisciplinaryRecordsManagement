package org.example.disciplinaryrecordsmanagement.dto;

import org.example.disciplinaryrecordsmanagement.model.Role;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.SortedSet;

import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

public final class AccountDto {
    private AccountDto() {}

    public record Bind(@NotBlank @Pattern(regexp = STUDENT_NUMBER) String studentNo,
                       @NotBlank @Size(max = MAX_STUDENT_NAME_LENGTH) String name, @Positive long classId,
                       @Pattern(regexp = OPTIONAL_PHONE) String phone,
                       @NotBlank @Email @Size(max = MAX_EMAIL_LENGTH) @Pattern(regexp = SINGLE_EMAIL) String email,
                       @NotBlank @Pattern(regexp = EMAIL_CODE_PATTERN) String emailCode,
                       @NotBlank @Size(max = MAX_PASSWORD_BYTES) String password) {
        public RegistrationIdentity identity() {
            return new RegistrationIdentity(studentNo, name, classId, phone, email);
        }
    }
    public record RegistrationIdentity(@NotBlank @Pattern(regexp = STUDENT_NUMBER) String studentNo,
                                       @NotBlank @Size(max = MAX_STUDENT_NAME_LENGTH) String name, @Positive long classId,
                                       @Pattern(regexp = OPTIONAL_PHONE) String phone,
                                       @NotBlank @Email @Size(max = MAX_EMAIL_LENGTH) @Pattern(regexp = SINGLE_EMAIL) String email) {}
    public record EmailCodeSent(long expiresInSeconds, long resendAfterSeconds) {}

    public record EmailAddress(@NotBlank @Email @Size(max = MAX_EMAIL_LENGTH)
                               @Pattern(regexp = SINGLE_EMAIL) String email) {}
    public record EmailSettings(String email) {}

    public record NewGroup(@NotBlank @Size(max = MAX_CLASS_NAME_LENGTH) String name,
                           @Size(max = MAX_COUNSELOR_NAME_LENGTH) String counselor,
                           @Size(min = 1, max = MAX_CLASS_COUNSELORS)
                           List<@NotBlank @Size(max = MAX_COUNSELOR_NAME_LENGTH) String> counselors) {}
    public record NewStudent(@NotBlank @Pattern(regexp = STUDENT_NUMBER) String studentNo,
                             @NotBlank @Size(max = MAX_STUDENT_NAME_LENGTH) String name, @Positive long classId,
                             @Pattern(regexp = OPTIONAL_PHONE) String phone, @NotNull LocalDate enrolledOn) {}
    public record NewStaff(@NotBlank @Pattern(regexp = STAFF_NAME) String username,
                           @NotBlank @Size(max = MAX_PASSWORD_BYTES) String password) {}
    public record GrantChange(@NotNull Role role, @Size(max = MAX_GRANT_SCOPE_LENGTH) String scope) {}
    public record Enabled(@NotNull Boolean enabled) {}
    public record PasswordChange(@NotBlank @Size(max = MAX_PASSWORD_BYTES) String currentPassword,
                                 @NotBlank @Size(max = MAX_PASSWORD_BYTES) String newPassword) {}
    public record TemporaryPassword(String password, long expiresAt) {}

    public record PublicClass(long id, String name) {}
    public record Group(long id, String name, String counselor) {}
    public record Department(String code, String name) {}
    public record Candidate(long id, String studentNo, String name, String className, String counselor) {}
    public record UserSummary(long id, String username, String displayName, Long studentId, boolean enabled) {}
    public record Grant(Role roleCode, String scopeKey) {}
    public record CurrentUser(long id, String username, String displayName, boolean coreAdmin, List<Grant> grants,
                              boolean student, boolean canReviewPermissionRequests, boolean allRecords, boolean canExportReports, SortedSet<String> officerDepartments,
                              boolean canViewRecords, boolean canViewStatistics, boolean passwordChangeRequired) {}
    public record Csrf(String headerName, String token) {}
}
