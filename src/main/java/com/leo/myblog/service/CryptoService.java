package com.leo.myblog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 加密服务
 * 提供 DH 密钥交换、AES-GCM 和 SM4-GCM 加解密功能
 */
@Slf4j
@Service
public class CryptoService {

    // DH 参数：2048 位 modp 安全素数（RFC 3526 第2048位模数群）
    // 使用 IKE 标准的 2048-bit MODP Group
    private static final String DH_PARAM_P_STR =
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E08" +
            "8A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B" +
            "302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9" +
            "A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE6" +
            "49286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8" +
            "FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
            "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C" +
            "180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
            "3995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D" +
            "04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7D" +
            "B3970F85A6E1E4C7ABF5AE8CDB0933D71E8C94E04A25619DCEE3D226" +
            "1AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
            "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFC" +
            "E0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF";

    private static final String DH_PARAM_G = "2";

    /**
     * 生成 DH 密钥对
     * <p>
     * 使用固定的 RFC 3526 2048-bit MODP Group 参数（素数 p 和生成元 g），
     * 确保服务端和客户端使用相同的 DH 参数组。
     *
     * @return 包含公钥（Base64）和私钥的 DH 密钥对
     */
    public DhKeyPair generateDhKeyPair() {
        try {
            // 使用固定的 RFC 3526 2048-bit MODP Group 参数
            // 避免使用 AlgorithmParameterGenerator 动态生成（某些 JDK 版本可能不兼容）
            java.math.BigInteger p = new java.math.BigInteger(DH_PARAM_P_STR, 16);
            java.math.BigInteger g = new java.math.BigInteger(DH_PARAM_G);
            javax.crypto.spec.DHParameterSpec dhParamSpec = new javax.crypto.spec.DHParameterSpec(p, g);

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
            keyPairGenerator.initialize(dhParamSpec, new SecureRandom());

            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

            log.debug("DH 密钥对生成成功: publicKey长度={}", publicKeyBase64.length());

            return new DhKeyPair(publicKeyBase64, privateKeyBase64, keyPair);
        } catch (Exception e) {
            log.error("生成 DH 密钥对失败", e);
            throw new RuntimeException("生成 DH 密钥对失败", e);
        }
    }

    /**
     * 计算共享密钥
     * @param privateKeyBase64 服务端私钥（Base64）
     * @param clientPublicKeyBase64 客户端公钥（Base64）
     * @return 共享密钥的字节数组
     */
    public byte[] computeSharedSecret(String privateKeyBase64, String clientPublicKeyBase64) {
        try {
            // 解码私钥
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("DH");
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            // 解码客户端公钥
            byte[] clientPublicKeyBytes = Base64.getDecoder().decode(clientPublicKeyBase64);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(clientPublicKeyBytes);
            PublicKey clientPublicKey = keyFactory.generatePublic(publicKeySpec);

            // 计算共享密钥
            KeyAgreement keyAgreement = KeyAgreement.getInstance("DH");
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(clientPublicKey, true);

            return keyAgreement.generateSecret();
        } catch (Exception e) {
            log.error("计算 DH 共享密钥失败", e);
            throw new RuntimeException("计算 DH 共享密钥失败", e);
        }
    }

    /**
     * 从共享密钥派生 AES 密钥
     * @return AES-128(16字节) / AES-192(24字节) / AES-256(32字节) 密钥
     */
    public byte[] deriveAesKey(byte[] sharedSecret, int keySize) {
        // 使用 SHA-256 从共享密钥中派生密钥
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sharedSecret);
            byte[] key = new byte[keySize / 8];
            System.arraycopy(hash, 0, key, 0, Math.min(key.length, hash.length));
            return key;
        } catch (Exception e) {
            log.error("派生 AES 密钥失败", e);
            throw new RuntimeException("派生 AES 密钥失败", e);
        }
    }

    /**
     * AES-GCM 加密
     * @param plaintext 明文
     * @param key AES 密钥
     * @return 加密结果（IV + 密文 + GCM Tag）
     */
    public AesGcmEncryptResult aesGcmEncrypt(byte[] plaintext, byte[] key) {
        try {
            // 生成随机 IV（12 字节）
            byte[] iv = new byte[12];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);
            // GCM 模式下，密文末尾 16 字节是 GCM Tag
            return new AesGcmEncryptResult(
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext)
            );
        } catch (Exception e) {
            log.error("AES-GCM 加密失败", e);
            throw new RuntimeException("AES-GCM 加密失败", e);
        }
    }

    /**
     * AES-GCM 解密
     * @param ivBase64 IV（Base64）
     * @param ciphertextBase64 密文（Base64，包含 GCM Tag）
     * @param key AES 密钥
     * @return 解密后的明文
     */
    public byte[] aesGcmDecrypt(String ivBase64, String ciphertextBase64, byte[] key) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            log.error("AES-GCM 解密失败", e);
            throw new RuntimeException("AES-GCM 解密失败", e);
        }
    }

    /**
     * SM4-GCM 加密（使用 Bouncy Castle 实现）
     * @param plaintext 明文
     * @param key SM4 密钥（16 字节）
     * @return 加密结果（IV + 密文 + GCM Tag）
     */
    public AesGcmEncryptResult sm4GcmEncrypt(byte[] plaintext, byte[] key) {
        try {
            byte[] iv = new byte[12];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            org.bouncycastle.jce.provider.BouncyCastleProvider provider = new org.bouncycastle.jce.provider.BouncyCastleProvider();
            java.security.Security.addProvider(provider);

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("SM4/GCM/NoPadding", provider);
            SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);
            return new AesGcmEncryptResult(
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext)
            );
        } catch (Exception e) {
            log.error("SM4-GCM 加密失败", e);
            throw new RuntimeException("SM4-GCM 加密失败", e);
        }
    }

    /**
     * SM4-GCM 解密
     */
    public byte[] sm4GcmDecrypt(String ivBase64, String ciphertextBase64, byte[] key) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);

            org.bouncycastle.jce.provider.BouncyCastleProvider provider = new org.bouncycastle.jce.provider.BouncyCastleProvider();
            java.security.Security.addProvider(provider);

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("SM4/GCM/NoPadding", provider);
            SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            log.error("SM4-GCM 解密失败", e);
            throw new RuntimeException("SM4-GCM 解密失败", e);
        }
    }

    // ==================== 内部类 ====================

    /**
     * DH 密钥对
     */
    public static class DhKeyPair {
        private final String publicKeyBase64;
        private final String privateKeyBase64;
        private final KeyPair keyPair;

        public DhKeyPair(String publicKeyBase64, String privateKeyBase64, KeyPair keyPair) {
            this.publicKeyBase64 = publicKeyBase64;
            this.privateKeyBase64 = privateKeyBase64;
            this.keyPair = keyPair;
        }

        public String getPublicKeyBase64() {
            return publicKeyBase64;
        }

        public String getPrivateKeyBase64() {
            return privateKeyBase64;
        }

        public KeyPair getKeyPair() {
            return keyPair;
        }
    }

    /**
     * AES-GCM 加密结果
     */
    public static class AesGcmEncryptResult {
        private final String iv;
        private final String ciphertext;

        public AesGcmEncryptResult(String iv, String ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }

        public String getIv() {
            return iv;
        }

        public String getCiphertext() {
            return ciphertext;
        }
    }
}
