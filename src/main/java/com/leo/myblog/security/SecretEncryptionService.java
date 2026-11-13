package com.leo.myblog.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts long-lived authentication material before it is stored in Redis.
 */
@Slf4j
@Service
public class SecretEncryptionService {

    private static final String PREFIX = "v1:";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Environment environment;
    private final SecretKeySpec key;
    private final boolean envProvided;

    public SecretEncryptionService(@Value("${totp.encryption-key}") String encodedKey,
                                   Environment environment) {
        this.environment = environment;
        this.envProvided = System.getenv("TOTP_ENCRYPTION_KEY") != null
                && !System.getenv("TOTP_ENCRYPTION_KEY").isBlank();
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("TOTP encryption key must be exactly 32 bytes");
            }
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY must be a Base64-encoded 32-byte key", e);
        }
    }

    @PostConstruct
    void validateProductionKey() {
        if (environment.acceptsProfiles(Profiles.of("prod")) && !envProvided) {
            throw new IllegalStateException(
                    "Production must provide TOTP_ENCRYPTION_KEY as a Base64-encoded 32-byte key");
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + ":"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null || !isEncrypted(storedValue)) {
            return storedValue;
        }
        try {
            String[] parts = storedValue.substring(PREFIX.length()).split(":", 2);
            byte[] iv = Base64.getUrlDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
