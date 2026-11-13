package com.leo.myblog.util;

import com.leo.myblog.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PasswordPolicy {

    public void validate(String username, String password) {
        if (password == null || password.length() < 10) {
            throw new BusinessException("密码长度不能少于10位");
        }
        if (password.length() > 128) {
            throw new BusinessException("密码长度不能超过128位");
        }
        if (password.chars().allMatch(Character::isDigit)) {
            throw new BusinessException("密码不能全部为数字");
        }

        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String normalizedPassword = password.toLowerCase(Locale.ROOT);
        if (!normalizedUsername.isEmpty()
                && (normalizedPassword.equals(normalizedUsername)
                || (normalizedUsername.length() >= 3 && normalizedPassword.contains(normalizedUsername)))) {
            throw new BusinessException("密码不能包含用户名");
        }
    }
}
