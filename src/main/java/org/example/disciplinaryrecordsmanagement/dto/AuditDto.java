package org.example.disciplinaryrecordsmanagement.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;

import java.time.LocalDate;

import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_USERNAME_LENGTH;

public final class AuditDto {
    private AuditDto() {}

    public record Filter(LocalDate from, LocalDate to, @Size(max = MAX_USERNAME_LENGTH) String actor,
                         AuditEvent action, Target target, @Positive Long targetId) {}

    public record Entry(long id, String actor, AuditEvent action, Target target, long targetId,
                        String previousState, String newState, String details, String requestId, long createdAt) {}
}
