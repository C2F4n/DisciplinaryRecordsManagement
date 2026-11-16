package org.example.disciplinaryrecordsmanagement.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.*;

public final class RosterMaintenanceDto {
    private RosterMaintenanceDto() {}

    public record ClassRow(long id, String name, String counselor, long version, int studentCount) {}
    public record ManagedClass(long id, String name, String counselor, long version, int studentCount,
                               List<String> counselors) {}
    public record StudentRevision(long id, long version) {}

    public record StudentProfile(long id, String studentNo, String name, long classId, String className,
                                 String counselor, String phone, LocalDate validUntil, boolean bound, long version) {}

    public record ChangeClass(@NotBlank @Size(max = MAX_CLASS_NAME_LENGTH) String name,
                              @Size(max = MAX_COUNSELOR_NAME_LENGTH) String counselor,
                              @Size(min = 1, max = MAX_CLASS_COUNSELORS)
                              List<@NotBlank @Size(max = MAX_COUNSELOR_NAME_LENGTH) String> counselors,
                              @NotNull @PositiveOrZero Long version) {}

    public record ChangeStudent(@NotBlank @Size(max = MAX_STUDENT_NAME_LENGTH) String name,
                                @Positive long classId, @Pattern(regexp = OPTIONAL_PHONE) String phone,
                                @NotNull LocalDate validUntil, @NotNull @PositiveOrZero Long version) {}

    public record StudentNumberRule(@Min(1) @Max(MAX_STUDENT_NUMBER_LENGTH) int minLength,
                                    @Min(1) @Max(MAX_STUDENT_NUMBER_LENGTH) int maxLength,
                                    @NotNull @Size(max = MAX_STUDENT_NUMBER_LENGTH) @Pattern(regexp = STUDENT_NUMBER_PREFIX) String prefix,
                                    @NotNull @PositiveOrZero Long version) {
        public boolean accepts(String studentNo) {
            return studentNo.length() >= minLength && studentNo.length() <= maxLength
                    && studentNo.startsWith(prefix) && studentNo.matches(STUDENT_NUMBER);
        }

        public String requirement() {
            String length = minLength == maxLength ? STUDENT_NUMBER_FIXED_LENGTH.formatted(minLength)
                    : STUDENT_NUMBER_LENGTH_RANGE.formatted(minLength, maxLength);
            return prefix.isEmpty() ? length : length + STUDENT_NUMBER_PREFIX_REQUIREMENT.formatted(prefix);
        }
    }
}
