package org.example.disciplinaryrecordsmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.disciplinaryrecordsmanagement.dto.ViolationDto.Resolution;
import org.example.disciplinaryrecordsmanagement.dto.WorkflowDto.*;

import java.util.List;

@Mapper
public interface WorkflowMapper {
    String OPTIONS = "SELECT id, kind, code, label, resolution, enabled, version, category FROM workflow_option";

    @Select(OPTIONS + " ORDER BY kind, id")
    List<Option> options();

    @Select(OPTIONS + " WHERE kind = #{kind} ORDER BY id FOR UPDATE")
    List<Option> lockOptions(Kind kind);

    @Select(OPTIONS + " WHERE code = #{code}")
    Option option(String code);

    @Select("SELECT code, label, resolution, category FROM workflow_option WHERE kind = #{kind} AND enabled = TRUE ORDER BY id")
    List<Choice> choices(Kind kind);

    @Select("""
            SELECT COUNT(*) FROM workflow_option
            WHERE kind = #{kind} AND resolution = #{resolution} AND enabled = TRUE
            """)
    int activeAppeals(@Param("kind") Kind kind, @Param("resolution") Resolution resolution);

    @Insert("INSERT INTO workflow_option (kind, code, label, category) VALUES (#{kind}, #{code}, #{label}, #{category,jdbcType=VARCHAR})")
    void insertOption(@Param("kind") Kind kind, @Param("code") String code, @Param("label") String label,
                      @Param("category") String category);

    @Update("""
            UPDATE workflow_option SET label = #{label,jdbcType=VARCHAR}, enabled = #{enabled},
                category = #{category,jdbcType=VARCHAR}, version = version + 1
            WHERE code = #{code} AND version = #{version}
            """)
    int updateOption(@Param("code") String code, @Param("label") String label, @Param("enabled") boolean enabled,
                     @Param("version") long version, @Param("category") String category);

    @Delete("DELETE FROM workflow_option WHERE kind = #{kind} AND code = #{code} AND version = #{version}")
    int deleteOption(@Param("kind") Kind kind, @Param("code") String code, @Param("version") long version);

    @Select("SELECT code, name, department_code, version FROM violation_type ORDER BY name")
    List<TypeAssignment> typeAssignments();

    @Select("SELECT code, name, department_code, version FROM violation_type WHERE code = #{code}")
    TypeAssignment typeAssignment(String code);

    @Update("""
            UPDATE violation_type SET department_code = #{department}, version = version + 1
            WHERE code = #{code} AND version = #{version}
            """)
    int updateType(@Param("code") String code, @Param("department") String department, @Param("version") long version);
}
