package org.example.disciplinaryrecordsmanagement.mapper;

import org.example.disciplinaryrecordsmanagement.dto.AccountDto.*;
import org.example.disciplinaryrecordsmanagement.model.Role;

import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AccountMapper {
    record Account(long id, String username, String passwordHash, Long studentId, Integer coreAdminSlot,
                   boolean enabled, Long classId, String displayName, LocalDate validUntil,
                   long passwordVersion, Long temporaryPasswordUntil) {
        public boolean active(LocalDate today) {
            return enabled && (studentId == null || (validUntil != null && !validUntil.isBefore(today)));
        }
    }
    record Student(long id, String studentNo, String name, long classId, String className, String counselor,
                   String phone, LocalDate validUntil) {}
    record Enrollment(long id, String studentNo, String name, long classId, String phone, LocalDate validUntil) {}

    @Select("""
            SELECT u.id, u.username, u.password_hash, u.student_id, u.core_admin_slot, u.enabled,
                   s.class_id, COALESCE(s.name, u.username) AS display_name, s.valid_until,
                   u.password_version, u.temporary_password_until
            FROM app_user u LEFT JOIN student s ON s.id = u.student_id AND s.deleted_at IS NULL
            WHERE u.username = #{username}
            """)
    Account account(String username);

    @Select("SELECT username FROM app_user WHERE id = #{id}")
    String username(long id);

    @Select("SELECT COUNT(*) FROM app_user WHERE core_admin_slot = 1")
    int countCoreAdmins();

    @Insert("""
            INSERT INTO app_user(username, password_hash, student_id, core_admin_slot, temporary_password_until, email)
            VALUES(#{username}, #{hash}, #{studentId}, #{adminSlot}, #{expires,jdbcType=BIGINT}, #{email,jdbcType=VARCHAR})
            """)
    void createAccount(@Param("username") String username, @Param("hash") String hash,
                       @Param("studentId") Long studentId, @Param("adminSlot") Integer adminSlot,
                       @Param("expires") Long expires, @Param("email") String email);

    @Select("SELECT email FROM app_user WHERE id = #{id}")
    String email(long id);

    @Update("UPDATE app_user SET email = #{email} WHERE id = #{id}")
    void updateEmail(@Param("id") long id, @Param("email") String email);

    @Update("""
            UPDATE app_user SET enabled = #{enabled}
            WHERE id = #{id} AND core_admin_slot IS NULL
                AND (student_id IS NULL OR EXISTS (SELECT 1 FROM student s WHERE s.id = app_user.student_id AND s.deleted_at IS NULL))
            """)
    int setAccountEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    @Update("""
            UPDATE app_user SET password_hash = #{hash}, password_version = password_version + 1,
                temporary_password_until = #{expires,jdbcType=BIGINT}
            WHERE id = #{id} AND password_version = #{version}
            """)
    int updatePassword(@Param("id") long id, @Param("version") long version, @Param("hash") String hash,
                       @Param("expires") Long expires);

    @Select("SELECT role_code, scope_key FROM user_grant WHERE user_id = #{id} ORDER BY role_code, scope_key")
    List<Grant> grants(long id);

    @Select("SELECT class_id FROM student WHERE id = #{studentId} AND deleted_at IS NULL FOR UPDATE")
    Long lockStudentClass(long studentId);

    @Insert("INSERT INTO user_grant(user_id, role_code, scope_key) VALUES(#{id}, #{role}, #{scope})")
    void addGrant(@Param("id") long id, @Param("role") Role role, @Param("scope") String scope);

    @Delete("DELETE FROM user_grant WHERE user_id = #{id} AND role_code = #{role} AND scope_key = #{scope}")
    int removeGrant(@Param("id") long id, @Param("role") Role role, @Param("scope") String scope);

    @Select("SELECT id, name, counselor FROM class_group WHERE deleted_at IS NULL ORDER BY name")
    List<Group> groups();

    @Select("SELECT name FROM class_group")
    List<String> classNames();

    @Select("SELECT version FROM class_group WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Long lockGroup(long id);

    @Select("SELECT id, name, counselor FROM class_group WHERE name = #{name} AND deleted_at IS NULL")
    Group groupByName(String name);

    @Insert("INSERT INTO class_group(name, counselor) VALUES(#{name}, #{counselor})")
    void createGroup(@Param("name") String name, @Param("counselor") String counselor);

    @Select("SELECT code, name FROM department ORDER BY code")
    List<Department> departments();

    @Select("SELECT COUNT(*) FROM department WHERE code = #{code}")
    int departmentExists(String code);

    @Select("""
            SELECT s.id, s.student_no, s.name, s.class_id, c.name AS class_name, c.counselor,
                   s.phone, s.valid_until
            FROM student s JOIN class_group c ON c.id = s.class_id
            WHERE s.student_no = #{studentNo} AND s.deleted_at IS NULL
            """)
    Student student(String studentNo);

    @Select("""
            SELECT id, student_no, name, class_id, phone, valid_until
            FROM student WHERE student_no = #{number} AND deleted_at IS NULL FOR UPDATE
            """)
    Enrollment lockEnrollment(String number);

    @Select("SELECT enabled FROM app_user WHERE id = #{id} FOR UPDATE")
    Boolean lockAccountEnabled(long id);

    @Select("SELECT scope_key FROM user_grant WHERE user_id = #{id} AND role_code = #{role} AND scope_key = #{scope}")
    String existingGrant(@Param("id") long id, @Param("role") Role role, @Param("scope") String scope);

    @Select("SELECT id FROM student WHERE student_no = #{number} AND deleted_at IS NULL FOR UPDATE")
    Long lockStudentByNumber(String number);

    @Select("""
            SELECT s.id, s.student_no, s.name, c.name AS class_name, c.counselor
            FROM student s JOIN class_group c ON c.id = s.class_id
            JOIN app_user u ON u.student_id = s.id AND u.enabled = TRUE
            WHERE s.deleted_at IS NULL AND s.valid_until >= #{today} AND (s.student_no = #{search} OR s.name = #{search})
            ORDER BY s.student_no LIMIT 15
            """)
    List<Candidate> findStudents(@Param("search") String search, @Param("today") LocalDate today);

    @Select("SELECT COUNT(*) FROM app_user WHERE student_id = #{id} AND enabled = TRUE")
    int hasEnabledBinding(long id);

    @Select("""
            <script>
            SELECT student_no FROM student WHERE student_no IN
            <foreach collection="numbers" item="number" open="(" separator="," close=")">#{number}</foreach>
            </script>
            """)
    List<String> existingStudentNumbers(@Param("numbers") List<String> numbers);

    @Insert("""
            INSERT INTO student(student_no, name, class_id, phone, valid_until)
            VALUES(#{number}, #{name}, #{classId}, #{phone,jdbcType=VARCHAR}, #{validUntil})
            """)
    void createStudent(@Param("number") String number, @Param("name") String name, @Param("classId") long classId,
                       @Param("phone") String phone, @Param("validUntil") LocalDate validUntil);

    @Select("""
            SELECT u.id, u.username, COALESCE(s.name, u.username) AS display_name, u.student_id, u.enabled
            FROM app_user u LEFT JOIN student s ON s.id = u.student_id
            WHERE u.username = #{query} AND (u.student_id IS NULL OR s.deleted_at IS NULL)
            ORDER BY u.id LIMIT 20
            """)
    List<UserSummary> findUsers(String query);
}
