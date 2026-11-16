package org.example.disciplinaryrecordsmanagement.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface RegistrationEmailMapper {
    record Challenge(long studentId, String challengeId, String email, String codeHash,
                     long issuedAt, long expiresAt, int failedAttempts) {}

    @Select("""
            SELECT student_id, challenge_id, email, code_hash, issued_at, expires_at, failed_attempts
            FROM registration_email_code WHERE student_id = #{studentId} FOR UPDATE
            """)
    Challenge lock(long studentId);

    @Insert("""
            INSERT INTO registration_email_code(student_id, challenge_id, email, code_hash, issued_at, expires_at, failed_attempts)
            VALUES(#{studentId}, #{challengeId}, #{email}, #{codeHash}, #{issuedAt}, #{expiresAt}, #{failedAttempts})
            """)
    void insert(Challenge challenge);

    @Update("""
            UPDATE registration_email_code SET challenge_id = #{challengeId}, email = #{email}, code_hash = #{codeHash},
                issued_at = #{issuedAt}, expires_at = #{expiresAt}, failed_attempts = #{failedAttempts}
            WHERE student_id = #{studentId}
            """)
    void replace(Challenge challenge);

    @Update("UPDATE registration_email_code SET failed_attempts = failed_attempts + 1 WHERE student_id = #{studentId}")
    void recordFailure(long studentId);

    @Delete("DELETE FROM registration_email_code WHERE student_id = #{studentId} AND challenge_id = #{challengeId}")
    void remove(@Param("studentId") long studentId, @Param("challengeId") String challengeId);

    @Delete("DELETE FROM registration_email_code WHERE expires_at <= #{now}")
    void removeExpired(long now);
}
