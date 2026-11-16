package org.example.disciplinaryrecordsmanagement.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.SortedSet;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.disciplinaryrecordsmanagement.dto.StatisticsDto.Bucket;
import org.example.disciplinaryrecordsmanagement.dto.StatisticsDto.Daily;
import org.example.disciplinaryrecordsmanagement.dto.StatisticsDto.Dimension;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.Correction;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.Create;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.Edit;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.Filter;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.Type;
import org.example.disciplinaryrecordsmanagement.dto.WorkbookRow;
import org.example.disciplinaryrecordsmanagement.model.RetentionWindow;
import org.example.disciplinaryrecordsmanagement.model.ViolationStatus;

@Mapper
public interface ViolationMapper {
    record Scope(boolean all, Long studentId, Long classId, SortedSet<String> departments, boolean office) {
        public ViolationStatus pending() { return ViolationStatus.PENDING; }
        public ViolationStatus approved() { return ViolationStatus.APPROVED; }
    }
    record Row(long id, long studentId, long classId, String studentNo, String studentName, String className,
               String counselor, LocalDate occurredOn, String departmentCode, String typeCode, String reason,
               String disposition, String inspectionScope, String remarks, ViolationStatus status,
               String rejectionReason, long version, long createdBy, Long handedBackAt,
               String inspectionLabel, String appealLabel) {}

    final class Draft {
        public Long id;
        public final AccountMapper.Student student;
        public final Create input;
        public final String department;
        public final String disposition;
        public final String inspectionLabel;
        public final long actor;
        public final long now;
        public final ViolationStatus status = ViolationStatus.PENDING;

        public Draft(AccountMapper.Student student, Create input, String department, String disposition,
                     String inspectionLabel, long actor, long now) {
            this.student = student;
            this.input = input;
            this.department = department;
            this.disposition = disposition;
            this.inspectionLabel = inspectionLabel;
            this.actor = actor;
            this.now = now;
        }
    }

    @Select("SELECT code, name, department_code FROM violation_type ORDER BY name")
    List<Type> types();

    @Select("SELECT code, name, department_code FROM violation_type WHERE code = #{code}")
    Type type(String code);

    Row byId(@Param("id") long id);

    List<Row> list(@Param("scope") Scope scope, @Param("q") Filter query, @Param("oldestPending") LocalDate oldestPending,
                   @Param("retention") RetentionWindow retention, @Param("limit") int limit, @Param("offset") long offset);

    long count(@Param("scope") Scope scope, @Param("q") Filter query, @Param("oldestPending") LocalDate oldestPending,
               @Param("retention") RetentionWindow retention);

    List<WorkbookRow> exportRows(@Param("q") Filter query, @Param("pending") ViolationStatus pending,
                                 @Param("excluded") ViolationStatus excluded,
                                 @Param("retention") RetentionWindow retention, @Param("limit") int limit);

    List<Bucket> statisticsBuckets(@Param("scope") Scope scope, @Param("q") Filter query,
                                  @Param("oldestPending") LocalDate oldestPending, @Param("retention") RetentionWindow retention,
                                  @Param("dimension") Dimension dimension);

    List<Daily> statisticsByDay(@Param("scope") Scope scope, @Param("q") Filter query,
                                @Param("oldestPending") LocalDate oldestPending, @Param("retention") RetentionWindow retention);

    @Insert("""
            INSERT INTO violation_record(student_id, class_id, student_no, student_name, class_name, counselor,
              occurred_on, department_code, type_code, reason, disposition, inspection_scope, inspection_label, remarks,
              status, created_by, created_at, submission_id)
            VALUES (#{student.id}, #{student.classId}, #{student.studentNo}, #{student.name}, #{student.className},
              #{student.counselor}, #{input.occurredOn}, #{department}, #{input.typeCode}, #{input.reason},
              #{disposition}, #{input.inspectionScope}, #{inspectionLabel}, #{input.remarks}, #{status}, #{actor}, #{now}, #{input.submissionId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Draft draft);

    @Update("""
            UPDATE violation_record SET reason = #{input.reason}, disposition = #{input.disposition},
                inspection_scope = #{input.inspectionScope}, inspection_label = #{inspectionLabel,jdbcType=VARCHAR},
                remarks = #{input.remarks}, version = version + 1
            WHERE id = #{id} AND version = #{input.version} AND deleted_at IS NULL
            """)
    int edit(@Param("id") long id, @Param("input") Edit input, @Param("inspectionLabel") String inspectionLabel);

    int correct(@Param("id") long id, @Param("input") Correction input, @Param("department") String department,
                @Param("student") AccountMapper.Student student, @Param("handedBackAt") Long handedBackAt,
                @Param("appealLabel") String appealLabel);

    @Update("""
            UPDATE violation_record SET deleted_at = #{now}, version = version + 1
            WHERE id = #{id} AND version = #{version} AND deleted_at IS NULL
            """)
    int delete(@Param("id") long id, @Param("version") long version, @Param("now") long now);

    @Update("""
            UPDATE violation_record SET status = #{next}, rejection_reason = #{rejection}, version = version + 1,
                handed_back_at = #{handedBack}, appeal_label = #{appealLabel,jdbcType=VARCHAR}
            WHERE id = #{id} AND version = #{version} AND status = #{previous} AND deleted_at IS NULL
            """)
    int changeState(@Param("id") long id, @Param("version") long version, @Param("previous") ViolationStatus previous,
                    @Param("next") ViolationStatus next, @Param("rejection") String rejection,
                    @Param("handedBack") Long handedBack, @Param("appealLabel") String appealLabel);

    @Update("""
            UPDATE violation_record SET deleted_at = #{now}, version = version + 1
            WHERE status = #{status} AND occurred_on < #{oldest} AND deleted_at IS NULL
            """)
    int expirePending(@Param("oldest") LocalDate oldest, @Param("now") long now, @Param("status") ViolationStatus status);
}
