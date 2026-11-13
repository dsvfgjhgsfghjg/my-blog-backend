package com.leo.myblog.util;

import com.leo.myblog.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsLongNonNumericPasswordWithoutUsername() {
        assertDoesNotThrow(() -> policy.validate("alice", "correct horse battery staple"));
    }

    @Test
    void rejectsShortNumericAndUsernameContainingPasswords() {
        assertThrows(BusinessException.class, () -> policy.validate("alice", "short"));
        assertThrows(BusinessException.class, () -> policy.validate("alice", "1234567890"));
        assertThrows(BusinessException.class, () -> policy.validate("alice", "my-alice-password"));
    }
}
