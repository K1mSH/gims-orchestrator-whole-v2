package com.sync.orchestrator.common;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Jasypt를 사용한 자격증명 암호화/복호화 유틸리티
 * datasource username, password 등 민감정보 암호화에 사용
 */
@Component
public class CredentialEncryptor {

    @Value("${jasypt.encryptor.password}")
    private String secretKey;

    private StandardPBEStringEncryptor encryptor;

    @PostConstruct
    public void init() {
        this.encryptor = new StandardPBEStringEncryptor();
        this.encryptor.setPassword(secretKey);
        this.encryptor.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        this.encryptor.setIvGenerator(new RandomIvGenerator());
    }

    /**
     * 문자열 암호화
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        return encryptor.encrypt(plainText);
    }

    /**
     * 문자열 복호화
     * ENC(...) wrapper가 있으면 제거 후 복호화
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        // 암호화되지 않은 평문인 경우 그대로 반환 (마이그레이션 지원)
        if (!isEncrypted(encryptedText)) {
            return encryptedText;
        }

        String toDecrypt = encryptedText;
        // ENC(...) wrapper 제거
        if (encryptedText.startsWith("ENC(") && encryptedText.endsWith(")")) {
            toDecrypt = encryptedText.substring(4, encryptedText.length() - 1);
        }

        return encryptor.decrypt(toDecrypt);
    }

    /**
     * 암호화된 문자열인지 확인
     * 단순히 ENC() wrapper나 Base64 패턴으로 판단
     */
    public boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        // ENC() wrapper가 있거나, Base64 인코딩된 것처럼 보이면 암호화된 것으로 간주
        return value.startsWith("ENC(") ||
               (value.length() > 20 && value.matches("^[A-Za-z0-9+/=]+$"));
    }
}
