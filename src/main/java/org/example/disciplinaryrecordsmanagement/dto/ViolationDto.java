package org.example.disciplinaryrecordsmanagement.dto;

import org.example.disciplinaryrecordsmanagement.model.ViolationStatus;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.Choice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

public final class ViolationDto {
    private ViolationDto() {}

    public enum ReviewAction { APPROVE, REJECT }
    public enum Resolution {
        HANDED_BACK(ViolationStatus.HANDED_BACK), APPEALED(ViolationStatus.APPEALED);

        private final ViolationStatus status;
        Resolution(ViolationStatus status) { this.status = status; }
        public ViolationStatus status() { return status; }
    }
    public record Create(@NotBlank @Pattern(regexp = STUDENT_NUMBER) String studentNo,
                         @NotNull LocalDate occurredOn, @NotBlank @Size(max = MAX_VIOLATION_TYPE_LENGTH) String typeCode,
                         @NotBlank @Size(max = MAX_REASON_LENGTH) String reason, @Size(max = MAX_VIOLATION_NOTE_LENGTH) String disposition,
                         @NotBlank @Size(max = MAX_WORKFLOW_CODE_LENGTH) String inspectionScope, @Size(max = MAX_VIOLATION_NOTE_LENGTH) String remarks,
                         @NotBlank @Pattern(regexp = SUBMISSION_ID) String submissionId) {}
    public record Edit(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = MAX_REASON_LENGTH) String reason,
                       @NotBlank @Size(max = MAX_VIOLATION_NOTE_LENGTH) String disposition,
                       @NotBlank @Size(max = MAX_WORKFLOW_CODE_LENGTH) String inspectionScope,
                       @Size(max = MAX_VIOLATION_NOTE_LENGTH) String remarks) {}
    public record Item(@Positive long id, @NotNull @PositiveOrZero Long version) {}
    public record Review(@NotEmpty @Size(max = MAX_REVIEW_ITEMS) List<@NotNull @Valid Item> items,
                         @NotNull ReviewAction action, @Size(max = MAX_VIOLATION_NOTE_LENGTH) String reason) {}
    public record Resolve(@NotNull @PositiveOrZero Long version, @NotNull Resolution resolution,
                          @Size(max = MAX_WORKFLOW_CODE_LENGTH) String appealCode) {}
    public record Correction(@NotNull @PositiveOrZero Long version,
                             @NotBlank @Pattern(regexp = STUDENT_NUMBER) String studentNo,
                             @NotNull LocalDate occurredOn, @NotBlank @Size(max = MAX_VIOLATION_TYPE_LENGTH) String typeCode,
                             @NotNull ViolationStatus status,
                             @NotBlank @Size(max = MAX_VIOLATION_NOTE_LENGTH) String correctionReason) {}
    public record Filter(LocalDate from, LocalDate to, Long classId, String department, String type, ViolationStatus status,
                         Boolean formalOnly) {}

    public record Type(String code, String name, String departmentCode) {}
    public record Metadata(List<AccountDto.Department> departments, List<Type> types, LocalDate today, String defaultDisposition,
                           List<Choice> inspectionOptions, List<Choice> appealOptions) {}
    public record Entry(long id, String studentNo, String studentName, long classId, String className, String counselor,
                        LocalDate occurredOn, String departmentCode, String typeCode, String reason, String disposition,
                        String inspectionScope, String remarks, ViolationStatus status, String rejectionReason, long version,
                        boolean editable, boolean reviewable, boolean resolvable, boolean correctable,
                        String inspectionLabel, String appealLabel) {}
    public record BatchResult(List<Long> changed, List<Long> skipped) {}
}
