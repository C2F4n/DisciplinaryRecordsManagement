package org.example.disciplinaryrecordsmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_COUNSELOR_NAME_LENGTH;

public final class CounselorDto {
    private CounselorDto() {}

    public record Option(long id, String name) {}
    public record Create(@NotBlank @Size(max = MAX_COUNSELOR_NAME_LENGTH) String name) {}
}
