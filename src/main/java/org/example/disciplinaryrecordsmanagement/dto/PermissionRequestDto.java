package org.example.disciplinaryrecordsmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.example.disciplinaryrecordsmanagement.model.PermissionRequestStatus;
import org.example.disciplinaryrecordsmanagement.model.Role;

import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_GRANT_SCOPE_LENGTH;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_PERMISSION_NOTE_LENGTH;

public final class PermissionRequestDto {
    private PermissionRequestDto() {}

    public record Submit(@NotNull Role role, @Size(max = MAX_GRANT_SCOPE_LENGTH) String scope,
                         @NotBlank @Size(max = MAX_PERMISSION_NOTE_LENGTH) String reason) {}

    public record Decision(@NotNull Boolean approve, @Size(max = MAX_PERMISSION_NOTE_LENGTH) String note) {}

    public record Entry(long id, long requesterId, String requesterName, String requesterNumber, Role roleCode,
                        String scopeKey, String scopeLabel, String reason, PermissionRequestStatus status,
                        long submittedAt, Long decidedBy, String reviewerName, Long decidedAt, String decisionNote) {}
}
