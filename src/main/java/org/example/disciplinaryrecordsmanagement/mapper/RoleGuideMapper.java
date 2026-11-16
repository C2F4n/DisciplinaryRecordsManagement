package org.example.disciplinaryrecordsmanagement.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.disciplinaryrecordsmanagement.model.Role;

@Mapper
public interface RoleGuideMapper {
    record BaseCounts(long coreAdminCount, long studentCount, long unassignedCount) {}
    record GrantCount(Role roleCode, boolean disciplineHead, long accountCount) {}

    @Select("""
            SELECT COUNT(CASE WHEN u.core_admin_slot IS NOT NULL THEN 1 END) AS core_admin_count,
                   COUNT(CASE WHEN u.student_id IS NOT NULL THEN 1 END) AS student_count,
                   COUNT(CASE WHEN u.core_admin_slot IS NULL AND u.student_id IS NULL
                       AND NOT EXISTS (SELECT 1 FROM user_grant g WHERE g.user_id = u.id) THEN 1 END) AS unassigned_count
            FROM app_user u LEFT JOIN student s ON s.id = u.student_id
            WHERE s.deleted_at IS NULL
            """)
    BaseCounts baseCounts();

    @Select("""
            SELECT g.role_code,
                   CASE WHEN g.role_code = #{headRole} AND g.scope_key = #{department} THEN TRUE ELSE FALSE END AS discipline_head,
                   COUNT(DISTINCT u.id) AS account_count
            FROM user_grant g JOIN app_user u ON u.id = g.user_id
            LEFT JOIN student s ON s.id = u.student_id
            WHERE s.deleted_at IS NULL
            GROUP BY g.role_code, discipline_head
            """)
    List<GrantCount> grantCounts(@Param("headRole") Role headRole, @Param("department") String department);
}
