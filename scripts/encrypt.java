///usr/bin/env java --source 17 "$0" "$@"; exit $?
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import java.util.Scanner;

/**
 * Jasypt 암/복호화 도구
 *
 * 사용법:
 *   java -cp <jasypt-jar> encrypt.java
 *   java -cp <jasypt-jar> encrypt.java encrypt "평문"
 *   java -cp <jasypt-jar> encrypt.java decrypt "암호문"
 *
 * jasypt JAR 위치 (gradle cache):
 *   ~/.gradle/caches/modules-2/files-2.1/org.jasypt/jasypt/1.9.3/.../jasypt-1.9.3.jar
 */
public class encrypt {

    static final String DEFAULT_KEY = "sync-pipeline-secret-key-2024";

    public static void main(String[] args) {
        StandardPBEStringEncryptor enc = new StandardPBEStringEncryptor();
        enc.setPassword(DEFAULT_KEY);
        enc.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        enc.setIvGenerator(new RandomIvGenerator());

        // 인자가 있으면 바로 실행
        if (args.length >= 2) {
            String mode = args[0].toLowerCase();
            String value = args[1];
            if ("encrypt".equals(mode) || "e".equals(mode)) {
                System.out.println("ENC(" + enc.encrypt(value) + ")");
            } else if ("decrypt".equals(mode) || "d".equals(mode)) {
                String raw = value;
                if (raw.startsWith("ENC(") && raw.endsWith(")")) {
                    raw = raw.substring(4, raw.length() - 1);
                }
                System.out.println(enc.decrypt(raw));
            } else {
                System.out.println("Usage: encrypt|decrypt \"value\"");
            }
            return;
        }

        // 대화형 모드
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Jasypt Encrypt/Decrypt (key: " + DEFAULT_KEY + ") ===");
        System.out.println("  e <text>  : encrypt");
        System.out.println("  d <text>  : decrypt");
        System.out.println("  q         : quit");
        System.out.println();

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            if ("q".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) break;

            int sp = line.indexOf(' ');
            if (sp < 0) { System.out.println("  e <text> 또는 d <text>"); continue; }

            String cmd = line.substring(0, sp).toLowerCase();
            String val = line.substring(sp + 1).trim();

            try {
                if ("e".equals(cmd) || "encrypt".equals(cmd)) {
                    System.out.println("  ENC(" + enc.encrypt(val) + ")");
                } else if ("d".equals(cmd) || "decrypt".equals(cmd)) {
                    String raw = val;
                    if (raw.startsWith("ENC(") && raw.endsWith(")")) {
                        raw = raw.substring(4, raw.length() - 1);
                    }
                    System.out.println("  " + enc.decrypt(raw));
                } else {
                    System.out.println("  e <text> 또는 d <text>");
                }
            } catch (Exception ex) {
                System.out.println("  ERROR: " + ex.getMessage());
            }
        }
    }
}
