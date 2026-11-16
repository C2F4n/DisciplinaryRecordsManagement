package org.example.disciplinaryrecordsmanagement.dto;

import org.example.disciplinaryrecordsmanagement.model.NotificationType;
import org.example.disciplinaryrecordsmanagement.model.NotificationType.Target;

public final class NotificationDto {
    private NotificationDto() {}

    public record Row(long id, NotificationType eventType, long subjectId, Integer itemCount,
                      long createdAt, Long readAt) {}

    public record Notice(long id, String title, String message, Target target, long subjectId,
                         long createdAt, Long readAt) {
        public static Notice from(Row row) {
            var type = row.eventType();
            return new Notice(row.id(), type.title(), type.message(row.itemCount()), type.target(),
                    row.subjectId(), row.createdAt(), row.readAt());
        }
    }

    public record Unread(long count) {}
}
