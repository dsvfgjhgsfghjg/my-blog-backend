package com.leo.myblog;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class keygener {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        // 生成 admin123 的哈希
        String password = "admin123";
        String hash = encoder.encode(password);

        System.out.println("密码: " + password);
        System.out.println("BCrypt哈希: " + hash);
    }
}
