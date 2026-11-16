package org.example.disciplinaryrecordsmanagement.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.disciplinaryrecordsmanagement.dto.NotificationDto.Row;
import org.example.disciplinaryrecordsmanagement.model.NotificationType;
import org.example.disciplinaryrecordsmanagement.model.Role;
import org.example.disciplinaryrecordsmanagement.model.ViolationStatus;

@Mapper
public interface NotificationMapper {
    @Select("""
            SELECT id, event_type, subject_id, item_count, created_at, read_at
            FROM app_notification WHERE recipient_id = #{recipient}
            ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Row> list(@Param("recipient") long recipient, @Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM app_notification WHERE recipient_id = #{recipient}")
    long count(long recipient);

    @Select("SELECT COUNT(*) FROM app_notification WHERE recipient_id = #{recipient} AND read_at IS NULL")
    long unread(long recipient);

    @Select("SELECT COUNT(*) FROM app_notification WHERE recipient_id = #{recipient} AND id = #{id}")
    int owns(@Param("recipient") long recipient, @Param("id") long id);

    @Update("""
            UPDATE app_notification SET read_at = #{now}
            WHERE recipient_id = #{recipient} AND id = #{id} AND read_at IS NULL
            """)
    int markRead(@Param("recipient") long recipient, @Param("id") long id, @Param("now") long now);

    @Update("""
            UPDATE app_notification SET read_at = #{now}
            WHERE recipient_id = #{recipient} AND read_at IS NULL
            """)
    int markAllRead(@Param("recipient") long recipient, @Param("now") long now);

    @Insert("""
            INSERT INTO app_notification(recipient_id, event_type, subject_id, revision, created_at)
            SELECT u.id, #{type}, #{subject}, #{revision}, #{now} FROM app_user u
            WHERE u.student_id = #{student} AND u.enabled = TRUE
            """)
    void notifyStudent(@Param("student") long student, @Param("type") NotificationType type,
                       @Param("subject") long subject, @Param("revision") long revision, @Param("now") long now);

    @Delete("""
            <script>
            DELETE FROM app_notification
            WHERE recipient_id = (SELECT id FROM app_user WHERE student_id = #{student})
                AND subject_id = #{subject} AND event_type IN
                <foreach collection="types" item="type" open="(" separator="," close=")">#{type}</foreach>
            </script>
            """)
    void removeStudentNotices(@Param("student") long student, @Param("subject") long subject,
                              @Param("types") List<NotificationType> types);

    @Insert("""
            INSERT INTO app_notification(recipient_id, event_type, subject_id, revision, created_at)
            SELECT u.id, #{type}, #{subject}, #{revision}, #{now} FROM app_user u
            WHERE u.id = #{recipient} AND u.enabled = TRUE
            """)
    void notifyUser(@Param("recipient") long recipient, @Param("type") NotificationType type,
                    @Param("subject") long subject, @Param("revision") long revision, @Param("now") long now);

    @Insert("""
            INSERT INTO app_notification(recipient_id, event_type, subject_id, revision, created_at)
            SELECT u.id, #{type}, v.id, 0, #{now}
            FROM violation_record v
            JOIN user_grant g ON g.scope_key = v.department_code AND g.role_code = #{role}
            JOIN app_user u ON u.id = g.user_id AND u.enabled = TRUE
            WHERE v.status = #{status} AND v.deleted_at IS NULL AND v.created_at < #{cutoff}
              AND v.occurred_on >= #{oldestPending}
              AND NOT EXISTS (
                SELECT 1 FROM app_notification n
                WHERE n.recipient_id = u.id AND n.event_type = #{type} AND n.subject_id = v.id AND n.revision = 0
              )
            ON DUPLICATE KEY UPDATE read_at = app_notification.read_at
            """)
    int remindOverdue(@Param("type") NotificationType type, @Param("role") Role role,
                      @Param("status") ViolationStatus status, @Param("cutoff") long cutoff,
                      @Param("oldestPending") LocalDate oldestPending, @Param("now") long now);

    @Select("""
            SELECT COUNT(*) FROM violation_record
            WHERE status = #{status} AND deleted_at IS NULL AND occurred_on >= #{from} AND occurred_on < #{until}
            """)
    int pendingForMonth(@Param("status") ViolationStatus status, @Param("from") LocalDate from,
                        @Param("until") LocalDate until);

    @Insert("""
            INSERT INTO app_notification(recipient_id, event_type, subject_id, revision, item_count, created_at)
            SELECT u.id, #{type}, #{subject}, 0, #{count}, #{now}
            FROM user_grant g JOIN app_user u ON u.id = g.user_id AND u.enabled = TRUE
            WHERE g.role_code = #{role} AND g.scope_key = #{department}
            """)
    void notifyReportPending(@Param("role") Role role, @Param("department") String department,
                             @Param("type") NotificationType type, @Param("subject") long subject,
                             @Param("count") int count, @Param("now") long now);
}
