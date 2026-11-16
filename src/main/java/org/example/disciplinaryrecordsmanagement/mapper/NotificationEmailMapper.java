package org.example.disciplinaryrecordsmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.disciplinaryrecordsmanagement.model.NotificationType;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface NotificationEmailMapper {
    enum Status { SENT, FAILED, SKIPPED }

    record Due(long notificationId, int attempts) {}

    @Insert("""
            INSERT INTO notification_email(notification_id, next_attempt_at)
            SELECT n.id, #{now}
            FROM app_notification n JOIN app_user u ON u.id = n.recipient_id
            WHERE u.student_id = #{student} AND u.email IS NOT NULL
              AND n.event_type = #{type} AND n.subject_id = #{record} AND n.revision = #{revision}
            """)
    void enqueue(@Param("student") long student, @Param("type") NotificationType type, @Param("record") long record,
                 @Param("revision") long revision, @Param("now") long now);

    @Select("""
            SELECT notification_id, attempts FROM notification_email
            WHERE status = 'PENDING' AND next_attempt_at <= #{now}
            ORDER BY next_attempt_at, notification_id LIMIT #{limit}
            """)
    List<Due> due(@Param("now") long now, @Param("limit") int limit);

    @Update("""
            UPDATE notification_email SET lease_id = #{lease}, next_attempt_at = #{leaseUntil}
            WHERE notification_id = #{id} AND status = 'PENDING' AND next_attempt_at <= #{now}
            """)
    int claim(@Param("id") long id, @Param("lease") String lease, @Param("now") long now, @Param("leaseUntil") long leaseUntil);

    @Select("""
            SELECT u.email FROM app_notification n
            JOIN app_user u ON u.id = n.recipient_id AND u.enabled = TRUE
            JOIN student s ON s.id = u.student_id AND s.deleted_at IS NULL AND s.valid_until >= #{today}
            JOIN violation_record v ON v.id = n.subject_id AND v.student_id = s.id AND v.deleted_at IS NULL
            WHERE n.id = #{id}
            """)
    String eligibleAddress(@Param("id") long id, @Param("today") LocalDate today);

    @Update("""
            UPDATE notification_email SET status = #{status}, attempts = attempts + 1, finished_at = #{now}, lease_id = NULL
            WHERE notification_id = #{id} AND lease_id = #{lease}
            """)
    int finish(@Param("id") long id, @Param("lease") String lease, @Param("status") Status status, @Param("now") long now);

    @Update("""
            UPDATE notification_email SET attempts = attempts + 1, next_attempt_at = #{retryAt}, lease_id = NULL
            WHERE notification_id = #{id} AND lease_id = #{lease}
            """)
    int retry(@Param("id") long id, @Param("lease") String lease, @Param("retryAt") long retryAt);
}
