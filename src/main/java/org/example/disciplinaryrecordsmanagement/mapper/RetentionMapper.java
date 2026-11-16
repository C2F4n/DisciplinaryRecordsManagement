package org.example.disciplinaryrecordsmanagement.mapper;

import org.example.disciplinaryrecordsmanagement.dto.RetentionDto.Day;
import org.example.disciplinaryrecordsmanagement.dto.RetentionDto.Run;
import org.example.disciplinaryrecordsmanagement.model.ViolationStatus;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RetentionMapper {
    @Select("""
            <script>
            SELECT occurred_on, COUNT(*) AS total,
                   SUM(CASE WHEN status = #{handedBack} AND deleted_at IS NULL THEN 1 ELSE 0 END) AS deferred
            FROM violation_record
            WHERE occurred_on &lt;= #{cutoff} AND status IN
            <foreach collection="states" item="state" open="(" separator="," close=")">#{state}</foreach>
            GROUP BY occurred_on ORDER BY occurred_on
            </script>
            """)
    List<Day> previewDays(@Param("cutoff") LocalDate cutoff, @Param("states") List<ViolationStatus> states,
                          @Param("handedBack") ViolationStatus handedBack);

    @Delete("""
            <script>
            DELETE FROM violation_record
            WHERE occurred_on BETWEEN #{from} AND #{until} AND status IN
            <foreach collection="states" item="state" open="(" separator="," close=")">#{state}</foreach>
            AND (status != #{handedBack} OR deleted_at IS NOT NULL)
            </script>
            """)
    int removeWeek(@Param("from") LocalDate from, @Param("until") LocalDate until,
                   @Param("states") List<ViolationStatus> states, @Param("handedBack") ViolationStatus handedBack);

    @Insert("""
            INSERT INTO retention_run(week_start, week_end, executed_at, removed_records)
            VALUES(#{from}, #{until}, #{now}, #{count})
            """)
    void recordRun(@Param("from") LocalDate from, @Param("until") LocalDate until,
                   @Param("now") long now, @Param("count") int count);

    @Select("""
            SELECT id, week_start, week_end, executed_at, removed_records
            FROM retention_run ORDER BY executed_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Run> history(@Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM retention_run")
    long countRuns();
}
