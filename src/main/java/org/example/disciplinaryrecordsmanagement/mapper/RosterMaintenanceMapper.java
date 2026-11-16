package org.example.disciplinaryrecordsmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.disciplinaryrecordsmanagement.dto.RosterMaintenanceDto.*;
import org.example.disciplinaryrecordsmanagement.model.Role;

import java.util.List;

@Mapper
public interface RosterMaintenanceMapper {
    String PROFILE = """
            SELECT s.id, s.student_no, s.name, s.class_id, c.name AS class_name, c.counselor, s.phone,
                   s.valid_until, CASE WHEN u.id IS NULL THEN FALSE ELSE TRUE END AS bound, s.version
            FROM student s JOIN class_group c ON c.id = s.class_id
            LEFT JOIN app_user u ON u.student_id = s.id
            """;
    String FILTER = """
            <where>
                s.deleted_at IS NULL
                <if test="classId != null">AND s.class_id = #{classId}</if>
                <if test="pattern != null">
                    AND (s.student_no LIKE #{pattern} ESCAPE '!' OR s.name LIKE #{pattern} ESCAPE '!')
                </if>
            </where>
            """;

    String CLASS_COLUMNS = """
            SELECT c.id, c.name, c.counselor, c.version,
                   (SELECT COUNT(*) FROM student s WHERE s.class_id = c.id AND s.deleted_at IS NULL) AS student_count
            FROM class_group c WHERE c.deleted_at IS NULL
            """;

    @Select(CLASS_COLUMNS + " ORDER BY c.name, c.id")
    List<ClassRow> classes();

    @Select(CLASS_COLUMNS + " AND c.id = #{id}")
    ClassRow managedClass(long id);

    @Update("""
            UPDATE class_group SET name = #{name}, counselor = #{counselor}, version = version + 1
            WHERE id = #{id} AND version = #{version} AND deleted_at IS NULL
            """)
    int updateClass(@Param("id") long id, @Param("name") String name, @Param("counselor") String counselor,
                    @Param("version") long version);

    @Select(PROFILE + " WHERE s.id = #{id} AND s.deleted_at IS NULL")
    StudentProfile student(long id);

    @Select("<script>" + PROFILE + FILTER + " ORDER BY s.student_no, s.id LIMIT #{limit} OFFSET #{offset}</script>")
    List<StudentProfile> students(@Param("classId") Long classId, @Param("pattern") String pattern,
                                  @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM student s " + FILTER + "</script>")
    long studentCount(@Param("classId") Long classId, @Param("pattern") String pattern);

    @Update("""
            UPDATE student SET name = #{change.name}, class_id = #{change.classId}, phone = #{change.phone,jdbcType=VARCHAR},
                valid_until = #{change.validUntil}, version = version + 1
            WHERE id = #{id} AND version = #{change.version} AND deleted_at IS NULL
            """)
    int updateStudent(@Param("id") long id, @Param("change") ChangeStudent change);

    @Select("SELECT version FROM student WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Long lockStudent(long id);

    @Select("SELECT id, version FROM student WHERE class_id = #{id} AND deleted_at IS NULL ORDER BY id FOR UPDATE")
    List<StudentRevision> lockClassStudents(long id);

    @Delete("""
            DELETE FROM student WHERE id = #{id}
                AND NOT EXISTS (SELECT 1 FROM app_user WHERE student_id = #{id})
                AND NOT EXISTS (SELECT 1 FROM violation_record WHERE student_id = #{id})
            """)
    int deleteUnreferencedStudent(long id);

    @Update("""
            UPDATE student SET deleted_at = #{now}, version = version + 1
            WHERE id = #{id}
            """)
    int archiveStudent(@Param("id") long id, @Param("now") long now);

    @Update("""
            UPDATE app_user SET enabled = FALSE, password_version = password_version + 1, temporary_password_until = NULL
            WHERE student_id = #{id}
            """)
    void disableStudentAccount(long id);

    @Select("SELECT COUNT(*) FROM student WHERE class_id = #{id} AND deleted_at IS NULL")
    int activeStudentsInClass(long id);

    @Delete("""
            DELETE FROM class_group WHERE id = #{id}
                AND NOT EXISTS (SELECT 1 FROM student WHERE class_id = #{id})
                AND NOT EXISTS (SELECT 1 FROM violation_record WHERE class_id = #{id})
            """)
    int deleteUnreferencedClass(long id);

    @Update("UPDATE class_group SET deleted_at = #{now}, version = version + 1 WHERE id = #{id}")
    int archiveClass(@Param("id") long id, @Param("now") long now);

    @Delete("""
            DELETE FROM user_grant WHERE role_code = #{role}
            AND user_id IN (SELECT id FROM app_user WHERE student_id = #{studentId})
            """)
    int revokeStudentGrants(@Param("studentId") long studentId, @Param("role") Role role);

    @Select("""
            SELECT min_length, max_length, number_prefix AS prefix, version
            FROM student_number_rule WHERE id = #{id}
            """)
    StudentNumberRule studentNumberRule(int id);

    @Update("""
            UPDATE student_number_rule SET min_length = #{rule.minLength}, max_length = #{rule.maxLength},
                number_prefix = #{rule.prefix}, version = version + 1
            WHERE id = #{id} AND version = #{rule.version}
            """)
    int updateStudentNumberRule(@Param("id") int id, @Param("rule") StudentNumberRule rule);
}
