package com.leo.myblog.utils;

import org.springframework.util.StringUtils;

/**
 * 数据脱敏工具类
 * 用于对用户敏感信息进行脱敏处理，保护用户隐私
 */
public class DataMaskingUtil {
    
    /**
     * 手机号脱敏
     * 规则：保留前3位和后4位，中间用****替换
     * 示例：13812345678 -> 138****5678
     * 
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return phone;
        }
        
        // 去除空格和特殊字符
        phone = phone.replaceAll("[\\s-]", "");
        
        // 验证手机号格式（中国大陆11位手机号）
        if (phone.length() != 11 || !phone.matches("^1[3-9]\\d{9}$")) {
            return phone; // 格式不正确，返回原值
        }
        
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
    
    /**
     * 邮箱脱敏
     * 规则：保留前2个字符和@后面的域名，中间用***替换
     * 示例：zhangsan@gmail.com -> zh***@gmail.com
     * 示例：a@gmail.com -> a***@gmail.com
     * 
     * @param email 邮箱地址
     * @return 脱敏后的邮箱
     */
    public static String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return email;
        }
        
        // 验证邮箱格式
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return email; // 格式不正确，返回原值
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        
        // 如果用户名部分长度<=2，只显示第一个字符
        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***" + domainPart;
        }
        
        // 显示前2个字符
        return localPart.substring(0, 2) + "***" + domainPart;
    }
    
    /**
     * 身份证号脱敏
     * 规则：保留前6位和后4位，中间用********替换
     * 示例：110101199001011234 -> 110101********1234
     * 
     * @param idCard 身份证号
     * @return 脱敏后的身份证号
     */
    public static String maskIdCard(String idCard) {
        if (!StringUtils.hasText(idCard)) {
            return idCard;
        }
        
        // 去除空格
        idCard = idCard.replaceAll("\\s", "");
        
        // 验证身份证号格式（18位或15位）
        if (idCard.length() != 18 && idCard.length() != 15) {
            return idCard;
        }
        
        if (idCard.length() == 18) {
            return idCard.substring(0, 6) + "********" + idCard.substring(14);
        } else {
            // 15位身份证
            return idCard.substring(0, 6) + "*****" + idCard.substring(11);
        }
    }
    
    /**
     * 姓名脱敏
     * 规则：只显示姓，名用*替换
     * 示例：张三 -> 张*
     * 示例：欧阳修 -> 欧阳**
     * 示例：张三丰 -> 张**
     * 
     * @param name 姓名
     * @return 脱敏后的姓名
     */
    public static String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        
        name = name.trim();
        
        if (name.length() == 1) {
            return name; // 单字姓名不脱敏
        }
        
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        
        // 3个字及以上，只显示姓，名全部用*替换
        StringBuilder masked = new StringBuilder();
        masked.append(name.charAt(0));
        for (int i = 1; i < name.length(); i++) {
            masked.append("*");
        }
        
        return masked.toString();
    }
    
    /**
     * 银行卡号脱敏
     * 规则：保留前6位和后4位，中间用****替换
     * 示例：6222021234567890123 -> 622202****0123
     * 
     * @param bankCard 银行卡号
     * @return 脱敏后的银行卡号
     */
    public static String maskBankCard(String bankCard) {
        if (!StringUtils.hasText(bankCard)) {
            return bankCard;
        }
        
        // 去除空格和横杠
        bankCard = bankCard.replaceAll("[\\s-]", "");
        
        // 银行卡号通常16-19位
        if (bankCard.length() < 10) {
            return bankCard;
        }
        
        return bankCard.substring(0, 6) + "****" + bankCard.substring(bankCard.length() - 4);
    }
    
    /**
     * IP地址脱敏
     * 规则：保留第一段和最后一段，中间用.*.替换
     * 示例：192.168.1.100 -> 192.*.*.100
     * 
     * @param ip IP地址
     * @return 脱敏后的IP地址
     */
    public static String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return ip;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return ip;
        }
        
        return parts[0] + ".*.*." + parts[3];
    }
    
    /**
     * 自定义脱敏
     * 规则：保留前n位和后m位，中间用指定字符替换
     * 
     * @param text 原始文本
     * @param prefixLen 保留的前缀长度
     * @param suffixLen 保留的后缀长度
     * @param maskChar 脱敏字符
     * @return 脱敏后的文本
     */
    public static String maskCustom(String text, int prefixLen, int suffixLen, char maskChar) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        
        if (text.length() <= prefixLen + suffixLen) {
            return text; // 文本太短，不脱敏
        }
        
        StringBuilder masked = new StringBuilder();
        masked.append(text.substring(0, prefixLen));
        
        int maskLen = text.length() - prefixLen - suffixLen;
        for (int i = 0; i < maskLen; i++) {
            masked.append(maskChar);
        }
        
        masked.append(text.substring(text.length() - suffixLen));
        
        return masked.toString();
    }
    
    /**
     * 完全隐藏（用固定长度的星号替换）
     * 
     * @param text 原始文本
     * @param length 星号长度
     * @return 脱敏后的文本
     */
    public static String maskFull(String text, int length) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < length; i++) {
            masked.append("*");
        }
        
        return masked.toString();
    }
}
