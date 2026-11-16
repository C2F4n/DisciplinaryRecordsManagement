package org.example.disciplinaryrecordsmanagement.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.SortedSet;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.disciplinaryrecordsmanagement.dto.PermissionRequestDto.Entry;
import org.example.disciplinaryrecordsmanagement.model.PermissionRequestStatus;
import org.example.disciplinaryrecordsmanagement.model.Role;

@Mapper
public interface PermissionRequestMapper {
    record ReviewScope(long id, boolean coreAdmin, boolean collegeReviewer, SortedSet<String> departments) {}
    record ReviewTarget(long id, long requesterId, Long studentId, String requesterNumber, Role roleCode,
                        String scopeKey, PermissionRequestStatus status) {}

    String ENTRY = """
            SELECT p.id, p.requester_id, p.requester_name, u.username AS requester_number,
                p.role_code, p.scope_key, p.scope_label, p.reason, p.status,
                p.submitted_at, p.decided_by, p.reviewer_name, p.decided_at, p.decision_note
            FROM permission_request p JOIN app_user u ON u.id = p.requester_id
            """;
    String REVIEW_QUEUE = """
            WHERE p.requester_id &lt;&gt; #{viewer.id} AND p.status = #{status}
            AND (
                #{viewer.coreAdmin} = TRUE
                OR (#{viewer.collegeReviewer} = TRUE AND p.role_code IN ('CLASS_COMMITTEE', 'OFFICER'))
                <if test="!viewer.departments.isEmpty()">
                  OR (p.role_code = 'OFFICER' AND p.scope_key IN
                    <foreach collection="viewer.departments" item="dept" open="(" separator="," close=")">#{dept}</foreach>)
                </if>
            )
            """;

    @Select(ENTRY + """
            WHERE p.requester_id = #{id}
            ORDER BY p.submitted_at DESC, p.id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Entry> mine(@Param("id") long id, @Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM permission_request WHERE requester_id = #{id}")
    long myCount(long id);

    @Select("<script>" + ENTRY + REVIEW_QUEUE + """
            ORDER BY p.submitted_at DESC, p.id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Entry> queue(@Param("viewer") ReviewScope viewer, @Param("status") PermissionRequestStatus status,
                      @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM permission_request p " + REVIEW_QUEUE + "</script>")
    long queueCount(@Param("viewer") ReviewScope viewer, @Param("status") PermissionRequestStatus status);

    @Select("""
            SELECT p.id, p.requester_id, u.student_id, u.username AS requester_number, p.role_code, p.scope_key, p.status
            FROM permission_request p JOIN app_user u ON u.id = p.requester_id WHERE p.id = #{id}
            """)
    ReviewTarget target(long id);

    @Select("SELECT status FROM permission_request WHERE id = #{id} FOR UPDATE")
    PermissionRequestStatus lockStatus(long id);

    @Select("""
            SELECT id FROM permission_request WHERE requester_id = #{requester} AND role_code = #{role}
                AND scope_key = #{scope} AND pending_slot = 1
            """)
    Long pendingId(@Param("requester") long requester, @Param("role") Role role, @Param("scope") String scope);

    @Insert("""
            INSERT INTO permission_request(requester_id, requester_name, role_code, scope_key, scope_label, reason,
                status, pending_slot, submitted_at)
            VALUES(#{requester}, #{name}, #{role}, #{scope}, #{label}, #{reason}, #{status}, 1, #{now})
            """)
    void submit(@Param("requester") long requester, @Param("name") String name, @Param("role") Role role,
                @Param("scope") String scope, @Param("label") String label, @Param("reason") String reason,
                @Param("status") PermissionRequestStatus status, @Param("now") long now);

    @Update("""
            UPDATE permission_request SET status = #{status}, pending_slot = NULL, decided_by = #{reviewer},
                reviewer_name = #{reviewerName}, decided_at = #{now}, decision_note = #{note}
            WHERE id = #{id} AND pending_slot = 1
            """)
    int decide(@Param("id") long id, @Param("status") PermissionRequestStatus status, @Param("reviewer") long reviewer,
               @Param("reviewerName") String reviewerName, @Param("now") long now, @Param("note") String note);

    @Select("""
            SELECT u.id FROM app_user u LEFT JOIN student s ON s.id = u.student_id
            WHERE u.id != #{requester} AND u.enabled = TRUE
                AND (u.student_id IS NULL OR (s.deleted_at IS NULL AND s.valid_until >= #{today}))
                AND (u.temporary_password_until IS NULL OR u.temporary_password_until > #{now})
                AND (u.core_admin_slot IS NOT NULL OR EXISTS (
                    SELECT 1 FROM user_grant g WHERE g.user_id = u.id
                        AND ((g.role_code IN ('PRESIDENT', 'TEACHER') AND #{role} IN ('CLASS_COMMITTEE', 'OFFICER'))
                            OR (g.role_code = 'DEPARTMENT_HEAD' AND #{role} = 'OFFICER' AND g.scope_key = #{scope}))
                ))
            ORDER BY u.id
            """)
    List<Long> reviewers(@Param("requester") long requester, @Param("role") Role role, @Param("scope") String scope,
                         @Param("today") LocalDate today, @Param("now") long now);
}
