package org.example.disciplinaryrecordsmanagement.dto;

import jakarta.validation.constraints.*;
import org.example.disciplinaryrecordsmanagement.dto.AccountDto.Department;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.Resolution;

import java.util.List;

import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.*;

public final class WorkflowDto {
    private WorkflowDto() {}

    public enum Kind { INSPECTION, APPEAL }

    public record Option(long id, Kind kind, String code, String label, Resolution resolution, boolean enabled, long version,
                         String category) {}
    public record Choice(String code, String label, Resolution resolution, String category) {}

    public record TypeAssignment(String code, String name, String departmentCode, long version) {}
    public record Settings(List<Option> options, List<TypeAssignment> types, List<Department> departments) {}

    public record NewInspection(@NotBlank @Size(max = MAX_WORKFLOW_LABEL_LENGTH) String label,
                                @Size(max = MAX_WORKFLOW_LABEL_LENGTH) String category) {}
    public record ChangeOption(@Size(max = MAX_WORKFLOW_LABEL_LENGTH) String label,
                               @NotNull Boolean enabled, @NotNull @PositiveOrZero Long version,
                               @Size(max = MAX_WORKFLOW_LABEL_LENGTH) String category) {}
    public record ChangeDepartment(@NotBlank @Size(max = MAX_DEPARTMENT_CODE_LENGTH) String departmentCode,
                                   @NotNull @PositiveOrZero Long version) {}
}
