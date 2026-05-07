package com.infolink.auth.tools;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * 첫 사용자 발급 도구 (별도 main, Spring context 안 띄움).
 *
 * <p>운영 배포 직후 1회 실행 — 운영자 한 명을 직접 INSERT. 그 후 사용자가 운영 화면에서
 * 새 사용자 추가 (peer multiplication). 사용자 0명 비상 진입로 역할도 겸함.</p>
 *
 * <h3>실행</h3>
 * <pre>
 *   ./gradlew :sync-orchestrator-auth:createUser --args="alice alicePw1 앨리스"
 * </pre>
 *
 * <h3>처리</h3>
 * <ol>
 *   <li>JASYPT_PASSWORD env (or default) 로 yml ENC 복호화</li>
 *   <li>application.yml 의 datasource url/user/pass 평문화</li>
 *   <li>BCrypt(12) 해시 생성</li>
 *   <li>JDBC INSERT INTO auth_users — UNIQUE 충돌 시 exit 2</li>
 * </ol>
 */
public class UserGeneratorCli {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int BCRYPT_ROUNDS = 12;
    private static final String DEFAULT_JASYPT_PASSWORD = "sync-pipeline-secret-key-2024";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: createUser <username> <password> <name>");
            System.exit(1);
        }
        String username = args[0];
        String password = args[1];
        String name = args[2];

        // 입력 검증 (UserService.validatePassword/validateName 와 동일 정책)
        if (username.isBlank() || username.length() > 50) {
            System.err.println("ERROR: AUTH_USERS_ID_INVALID (max 50, non-blank)");
            System.exit(3);
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            System.err.println("ERROR: PASSWORD_TOO_SHORT (min " + MIN_PASSWORD_LENGTH + ")");
            System.exit(3);
        }
        if (name.isBlank() || name.length() > 50) {
            System.err.println("ERROR: NAME_INVALID (max 50, non-blank)");
            System.exit(3);
        }

        // 1. Jasypt encryptor
        StandardPBEStringEncryptor encryptor = newEncryptor();

        // 2. application.yml 읽고 datasource 복호화
        DbCredentials creds = loadCredentials(encryptor);

        // 3. BCrypt
        String hash = new BCryptPasswordEncoder(BCRYPT_ROUNDS).encode(password);

        // 4. JDBC INSERT
        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(creds.url, creds.user, creds.password);
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO auth_users (auth_users_id, password_hash, name, role, fail_count, created_at) " +
                 "VALUES (?, ?, ?, 'user', 0, NOW()) RETURNING id")) {

            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, name);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    System.out.println("User created: id=" + id + ", username=" + username + ", name=" + name);
                }
            }
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("uk_auth_users_auth_users_id") || msg.toLowerCase().contains("duplicate key")) {
                System.err.println("ERROR: AUTH_USERS_ID_DUPLICATE — '" + username + "' already exists");
                System.exit(2);
            }
            System.err.println("ERROR: SQL — " + msg);
            throw e;
        }
    }

    private static StandardPBEStringEncryptor newEncryptor() {
        String pwd = System.getenv("JASYPT_PASSWORD");
        if (pwd == null || pwd.isBlank()) {
            pwd = DEFAULT_JASYPT_PASSWORD;
        }
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(pwd);
        encryptor.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        encryptor.setIvGenerator(new RandomIvGenerator());
        return encryptor;
    }

    @SuppressWarnings("unchecked")
    private static DbCredentials loadCredentials(StandardPBEStringEncryptor encryptor) throws Exception {
        try (InputStream in = UserGeneratorCli.class.getResourceAsStream("/application.yml")) {
            if (in == null) {
                throw new IllegalStateException("application.yml not found in classpath");
            }
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            Map<String, Object> ds = (Map<String, Object>) spring.get("datasource");

            return new DbCredentials(
                decryptIfEnc((String) ds.get("url"), encryptor),
                decryptIfEnc((String) ds.get("username"), encryptor),
                decryptIfEnc((String) ds.get("password"), encryptor)
            );
        }
    }

    private static String decryptIfEnc(String value, StandardPBEStringEncryptor encryptor) {
        if (value == null) return null;
        if (value.startsWith("ENC(") && value.endsWith(")")) {
            return encryptor.decrypt(value.substring(4, value.length() - 1));
        }
        return value;
    }

    private record DbCredentials(String url, String user, String password) {}
}
