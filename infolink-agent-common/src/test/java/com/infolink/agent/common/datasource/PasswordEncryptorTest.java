package com.infolink.agent.common.datasource;

import org.junit.jupiter.api.Test;

public class PasswordEncryptorTest {

    @Test
    void encryptCredentials() {
        String secretKey = "sync-pipeline-secret-key-2024";
        String username = "k1m";
        String password = "1111";

        PasswordEncryptor encryptor = new PasswordEncryptor(secretKey);

        String encryptedUsername = encryptor.encrypt(username);
        String encryptedPassword = encryptor.encrypt(password);

        System.out.println("========================================");
        System.out.println("Username: " + username + " -> " + encryptedUsername);
        System.out.println("Password: " + password + " -> " + encryptedPassword);
        System.out.println("========================================");

        // 복호화 확인
        System.out.println("Decrypted Username: " + encryptor.decrypt(encryptedUsername));
        System.out.println("Decrypted Password: " + encryptor.decrypt(encryptedPassword));
    }
}
