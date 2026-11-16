package org.example.disciplinaryrecordsmanagement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.disciplinaryrecordsmanagement.dto.CounselorDto.Option;

import java.util.List;

@Mapper
public interface CounselorMapper {
    record ClassCounselor(long classId, String name) {}
    @Select("SELECT id, name FROM counselor ORDER BY id")
    List<Option> list();

    @Select("SELECT id, name FROM counselor WHERE name = #{name}")
    Option byName(String name);

    @Insert("INSERT INTO counselor(name) VALUES (#{name})")
    void create(String name);

    @Select("""
            SELECT l.class_id, a.name
            FROM class_counselor l JOIN counselor a ON a.id = l.counselor_id
            JOIN class_group c ON c.id = l.class_id
            WHERE c.deleted_at IS NULL ORDER BY l.class_id, l.position
            """)
    List<ClassCounselor> classCounselors();

    @Delete("DELETE FROM class_counselor WHERE class_id = #{id}")
    void removeClassCounselors(long id);

    @Insert("INSERT INTO class_counselor(class_id, counselor_id, position) VALUES (#{id}, #{counselorId}, #{position})")
    void addClassCounselor(@Param("id") long id, @Param("counselorId") long counselorId, @Param("position") int position);
}
