package com.leo.myblog.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 加密算法信息响应
 * 返回当前 WebSocket 连接使用的加密算法信息
 */
@Data
public class CipherInfoResponse {

    /**
     * 密钥交换算法信息
     */
    private KeyExchangeInfo keyExchange;

    /**
     * 数据加密算法信息
     */
    private DataEncryptionInfo dataEncryption;

    /**
     * 可用的加密算法列表
     */
    private List<AlgorithmInfo> availableAlgorithms;

    /**
     * 设备首选的加密算法
     */
    private String deviceCipherPref;

    @Data
    public static class KeyExchangeInfo {
        /** 算法名称 */
        private String algorithm;
        /** 密钥长度 */
        private int keySize;
        /** DH 群组 */
        private String group;
        /** 哈希算法 */
        private String hash;
    }

    @Data
    public static class DataEncryptionInfo {
        /** 算法名称 */
        private String algorithm;
        /** 密钥长度 */
        private int keySize;
        /** IV 长度 */
        private int ivLength;
        /** GCM Tag 长度 */
        private int tagLength;
        /** 加密模式 */
        private String mode;
        /** 填充方式 */
        private String padding;
    }

    @Data
    public static class AlgorithmInfo {
        /** 算法名称 */
        private String name;
        /** 算法类型: key_exchange / data_encryption */
        private String type;
        /** 密钥长度 */
        private int keySize;
        /** 描述 */
        private String description;
    }
}
