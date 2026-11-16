package org.example.disciplinaryrecordsmanagement.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.disciplinaryrecordsmanagement.dto.AuditDto.Entry;
import org.example.disciplinaryrecordsmanagement.dto.AuditDto.Filter;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;

@Mapper
public interface AuditMapper {
    String FROM_FILTER = """
            FROM audit_log a LEFT JOIN app_user u ON u.id = a.actor_id
            <where>
                <if test="from != null">a.created_at &gt;= #{from}</if>
                <if test="until != null">AND a.created_at &lt; #{until}</if>
                <if test="filter.actor != null and !filter.actor.isBlank()">AND u.username = #{filter.actor}</if>
                <if test="filter.action != null">AND a.action = #{filter.action}</if>
                <if test="filter.target != null">AND a.target_type = #{filter.target}</if>
                <if test="filter.targetId != null">AND a.target_id = #{filter.targetId}</if>
            </where>
            """;

    @Select("""
            <script>
            SELECT a.id, u.username AS actor, a.action, a.target_type AS target, a.target_id,
                   a.previous_state, a.new_state, a.details, a.request_id, a.created_at
            """ + FROM_FILTER + " ORDER BY a.created_at DESC, a.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Entry> list(@Param("filter") Filter filter, @Param("from") Long from, @Param("until") Long until,
                     @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) " + FROM_FILTER + "</script>")
    long count(@Param("filter") Filter filter, @Param("from") Long from, @Param("until") Long until);

    @Insert("""
            INSERT INTO audit_log(actor_id, action, target_type, target_id, previous_state, new_state, request_id, created_at, details)
            VALUES(#{actor}, #{action}, #{target}, #{id}, #{previous}, #{next}, #{requestId}, #{now}, #{details,jdbcType=VARCHAR})
            """)
    void append(@Param("actor") Long actor, @Param("action") AuditEvent action, @Param("target") Target target,
                @Param("id") long id, @Param("previous") String previous, @Param("next") String next,
                @Param("requestId") String requestId, @Param("now") long now, @Param("details") String details);
}
