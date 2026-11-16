package org.example.disciplinaryrecordsmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.disciplinaryrecordsmanagement.dto.SmtpSettingsDto.ChangeSettings;
import org.example.disciplinaryrecordsmanagement.dto.SmtpSettingsDto.Transport;

@Mapper
public interface SmtpSettingsMapper {
    record Gate(String gateHash, long gateVersion) {}
    record Saved(long configVersion, boolean enabled, String host, int port, String username, String senderAddress,
                 boolean authentication, Transport transport, String encryptedPassword) {}

    String COLUMNS = """
            SELECT config_version, enabled, host, port, username, sender_address, authentication, transport,
                encrypted_password FROM smtp_settings WHERE id = #{id}
            """;

    @Select("SELECT gate_hash, gate_version FROM smtp_settings WHERE id = #{id}")
    Gate gate(int id);

    @Select("SELECT gate_hash, gate_version FROM smtp_settings WHERE id = #{id} FOR UPDATE")
    Gate lockGate(int id);

    @Select(COLUMNS)
    Saved saved(int id);

    @Update("""
            UPDATE smtp_settings SET enabled = #{change.enabled}, host = #{change.host}, port = #{change.port},
                username = #{change.username}, sender_address = #{change.senderAddress},
                authentication = #{change.authentication}, transport = #{change.transport},
                encrypted_password = #{ciphertext,jdbcType=VARCHAR}, config_version = config_version + 1
            WHERE id = #{id} AND config_version = #{change.version}
            """)
    int save(@Param("id") int id, @Param("change") ChangeSettings change, @Param("ciphertext") String ciphertext);

    @Update("""
            UPDATE smtp_settings SET gate_hash = #{hash}, gate_version = gate_version + 1 WHERE id = #{id}
            """)
    void replaceGate(@Param("id") int id, @Param("hash") String hash);
}
