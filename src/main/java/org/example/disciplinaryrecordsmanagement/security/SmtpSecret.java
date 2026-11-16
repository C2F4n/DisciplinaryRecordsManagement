package org.example.disciplinaryrecordsmanagement.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SmtpSecret {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final Path keyFile;

    SmtpSecret(@Value("${app.notice.email.key-file}") String keyFile) {
        this.keyFile = Path.of(keyFile).toAbsolutePath().normalize();
    }

    public String encrypt(String password) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            byte[] ciphertext = cipher(Cipher.ENCRYPT_MODE, nonce, true).doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException | IOException error) {
            throw new IllegalStateException("SMTP encryption key is unavailable");
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length <= NONCE_BYTES) throw new GeneralSecurityException();
            var buffer = ByteBuffer.wrap(bytes);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce, false).doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException error) {
            throw new IllegalStateException("SMTP encryption key is unavailable or does not match");
        }
    }

    private Cipher cipher(int mode, byte[] nonce, boolean createKey) throws GeneralSecurityException, IOException {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key(createKey), new GCMParameterSpec(TAG_BITS, nonce));
        return cipher;
    }

    private synchronized SecretKey key(boolean create) throws IOException {
        if (create && Files.notExists(keyFile)) {
            byte[] key = new byte[KEY_BYTES];
            RANDOM.nextBytes(key);
            try {
                if (Files.getFileStore(keyFile.getParent()).supportsFileAttributeView("posix")) {
                    Files.createFile(keyFile, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                } else {
                    Files.createFile(keyFile);
                }
                Files.write(keyFile, key);
            } catch (FileAlreadyExistsException ignored) {
                // Another process created the key.
            }
        }
        byte[] key = Files.readAllBytes(keyFile);
        if (key.length != KEY_BYTES) throw new IOException("Invalid SMTP key length");
        return new SecretKeySpec(key, "AES");
    }
}
