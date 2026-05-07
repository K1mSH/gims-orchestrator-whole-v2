package com.infolink.agent.common.datasource;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;

/**
 * Jasypt를 사용한 비밀번호 암호화/복호화 유틸리티
 */
public class PasswordEncryptor {

    private final StandardPBEStringEncryptor encryptor;

    public PasswordEncryptor(String secretKey) {
        this.encryptor = new StandardPBEStringEncryptor();
        this.encryptor.setPassword(secretKey);
        this.encryptor.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        this.encryptor.setIvGenerator(new RandomIvGenerator());
    }

    /**
     * 비밀번호 암호화
     */
    public String encrypt(String plainPassword) {
        return encryptor.encrypt(plainPassword);
    }

    /**
     * 비밀번호 복호화
     * ENC(...) wrapper가 있으면 제거 후 복호화
     */
    public String decrypt(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) {
            return encryptedPassword;
        }

        String toDecrypt = encryptedPassword;
        // ENC(...) wrapper 제거
        if (encryptedPassword.startsWith("ENC(") && encryptedPassword.endsWith(")")) {
            toDecrypt = encryptedPassword.substring(4, encryptedPassword.length() - 1);
        }

        return encryptor.decrypt(toDecrypt);
    }
}
