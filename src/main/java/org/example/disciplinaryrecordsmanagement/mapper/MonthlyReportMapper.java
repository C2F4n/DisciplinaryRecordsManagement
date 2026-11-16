package org.example.disciplinaryrecordsmanagement.mapper;

import org.example.disciplinaryrecordsmanagement.dto.WorkbookDownload;
import org.example.disciplinaryrecordsmanagement.dto.MonthlyReportDto.Item;
import org.example.disciplinaryrecordsmanagement.dto.MonthlyReportDto.Summary;
import org.example.disciplinaryrecordsmanagement.model.ViolationStatus;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MonthlyReportMapper {
    @Select("SELECT id FROM monthly_report_lock WHERE id = #{lockId} FOR UPDATE")
    Integer lockGeneration(int lockId);

    @Select("SELECT MAX(month_start) FROM monthly_report")
    LocalDate latestMonth();

    @Select("""
            SELECT MIN(handed_back_at) FROM violation_record
            WHERE status = #{status} AND deleted_at IS NULL
            """)
    Long earliestUnreported(ViolationStatus status);

    @Select("""
            SELECT id, month_start, generated_at, record_count FROM monthly_report
            WHERE month_start = #{monthStart}
            """)
    Summary byMonth(LocalDate monthStart);

    @Select("SELECT id, month_start, generated_at, record_count FROM monthly_report WHERE id = #{id}")
    Summary byId(long id);

    @Select("""
            SELECT id, month_start, generated_at, record_count FROM monthly_report
            ORDER BY month_start DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Summary> list(@Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM monthly_report")
    long count();

    @Select("""
            SELECT source_record_id, occurred_on, student_no, student_name, class_name, counselor,
                   department_name, type_name, reason, disposition, inspection_scope, remarks, inspection_label
            FROM monthly_report_item WHERE report_id = #{id}
            ORDER BY occurred_on, source_record_id LIMIT #{limit} OFFSET #{offset}
            """)
    List<Item> items(@Param("id") long id, @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
            SELECT v.id AS source_record_id, v.occurred_on, v.student_no, v.student_name, v.class_name, v.counselor,
                   d.name AS department_name, t.name AS type_name, v.reason, v.disposition, v.inspection_scope, v.remarks,
                   v.inspection_label
            FROM violation_record v JOIN department d ON d.code = v.department_code
            JOIN violation_type t ON t.code = v.type_code
            WHERE v.status = #{status} AND v.deleted_at IS NULL AND v.handed_back_at < #{cutoff}
            ORDER BY v.id
            FOR UPDATE
            """)
    List<Item> lockCandidates(@Param("status") ViolationStatus status, @Param("cutoff") long cutoff);

    @Insert("""
            INSERT INTO monthly_report(month_start, generated_at, record_count)
            VALUES (#{monthStart}, #{generatedAt}, #{count})
            """)
    int insertReport(@Param("monthStart") LocalDate monthStart, @Param("generatedAt") long generatedAt, @Param("count") int count);

    void insertItems(@Param("reportId") long reportId, @Param("items") List<Item> items);

    @Insert("INSERT INTO monthly_report_file(report_id, file_name, file_content) VALUES (#{id}, #{name}, #{content})")
    void storeFile(@Param("id") long id, @Param("name") String name, @Param("content") byte[] content);

    @Select("SELECT file_name, file_content FROM monthly_report_file WHERE report_id = #{id}")
    WorkbookDownload file(long id);

    @Update("""
            UPDATE violation_record SET status = #{next}, version = version + 1
            WHERE status = #{previous} AND deleted_at IS NULL AND id IN (
                SELECT source_record_id FROM monthly_report_item WHERE report_id = #{reportId}
            )
            """)
    int markRecorded(@Param("reportId") long reportId, @Param("previous") ViolationStatus previous,
                     @Param("next") ViolationStatus next);
}
