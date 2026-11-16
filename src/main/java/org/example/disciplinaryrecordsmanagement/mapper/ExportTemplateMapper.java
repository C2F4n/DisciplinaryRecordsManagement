package org.example.disciplinaryrecordsmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.disciplinaryrecordsmanagement.dto.ExportTemplateDto.Kind;

@Mapper
public interface ExportTemplateMapper {
    record Settings(String title, String columnsCsv, long version) {}

    @Select("SELECT title, columns_csv, version FROM export_template WHERE kind_code = #{kind}")
    Settings settings(Kind kind);

    @Insert("""
            INSERT INTO export_template(kind_code, title, columns_csv, version)
            VALUES(#{kind}, #{settings.title}, #{settings.columnsCsv}, #{settings.version})
            """)
    void insert(@Param("kind") Kind kind, @Param("settings") Settings settings);

    @Update("""
            UPDATE export_template SET title = #{settings.title}, columns_csv = #{settings.columnsCsv},
                version = #{settings.version}
            WHERE kind_code = #{kind} AND version = #{expected}
            """)
    int update(@Param("kind") Kind kind, @Param("settings") Settings settings, @Param("expected") long expected);
}
