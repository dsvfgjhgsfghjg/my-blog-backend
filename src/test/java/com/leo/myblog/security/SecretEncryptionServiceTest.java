package com.leo.myblog.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecretEncryptionServiceTest {

    private static final String KEY = "ZGV2LW9ubHktMzItYnl0ZS10b3RwLWtleS0wMDAwMDA=";

    @Test
    void encryptsWithUniqueIvAndDecrypts() {
        SecretEncryptionService service = new SecretEncryptionService(KEY, mock(Environment.class));
        String first = service.encrypt("totp-secret");
        String second = service.encrypt("totp-secret");

        assertTrue(service.isEncrypted(first));
        assertNotEquals(first, second);
        assertEquals("totp-secret", service.decrypt(first));
        assertEquals("totp-secret", service.decrypt(second));
    }

    @Test
    void legacyPlaintextCanBeReadBeforeMigration() {
        SecretEncryptionService service = new SecretEncryptionService(KEY, mock(Environment.class));
        assertEquals("legacy", service.decrypt("legacy"));
    }
}
