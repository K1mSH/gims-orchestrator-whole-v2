package com.infolink.auth.service;

import com.infolink.auth.entity.AuthRsaKey;
import com.infolink.auth.repository.AuthRsaKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RSA 키 페어 관리.
 *
 * <ul>
 *   <li>active=true 1쌍 (서명용) + 만료 안 된 비활성 ~7쌍 (검증용) = 약 8쌍 동시 보관</li>
 *   <li>매일 자정 회전 — 새 페어 INSERT, 기존 active 비활성화, 만료 키 cleanup</li>
 *   <li>private_pem 은 jasypt ENC 적용 후 저장 (마스터 PW 노출 시에만 복호화 가능)</li>
 *   <li>public_pem 은 평문 (JWKS endpoint 노출 OK)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeyService {

    private static final String PUBLIC_PEM_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_PEM_FOOTER = "-----END PUBLIC KEY-----";
    private static final String PRIVATE_PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_PEM_FOOTER = "-----END PRIVATE KEY-----";

    private final AuthRsaKeyRepository keyRepository;

    /**
     * Jasypt 자동 설정의 default StringEncryptor bean.
     * jasypt-spring-boot-starter 가 "jasyptStringEncryptor" 이름으로 등록.
     */
    @Qualifier("jasyptStringEncryptor")
    private final StringEncryptor encryptor;

    @Value("${auth.rsa.key-size:2048}")
    private int keySize;

    @Value("${auth.rsa.retention-days:8}")
    private int retentionDays;

    // ============================================================
    // 조회
    // ============================================================

    /** 현재 서명용 키 1쌍 — DB 조회 (없으면 InitialKeyLoader 가 생성) */
    public Optional<ActiveKey> findActiveKey() {
        return keyRepository.findByActiveTrue()
            .map(this::toActiveKey);
    }

    /** JWKS 응답용 — 만료 안 된 모든 키 (active 1 + 비활성 ~7) */
    public List<PublicKeyInfo> findAllValidPublicKeys() {
        return keyRepository.findByExpiresAtAfter(LocalDateTime.now()).stream()
            .map(k -> new PublicKeyInfo(k.getKid(), parsePublicKey(k.getPublicPem())))
            .toList();
    }

    /** kid 매칭 공개키 — 회전 후 이전 키 검증용 */
    public Optional<PublicKey> findPublicKeyByKid(String kid) {
        return keyRepository.findById(kid)
            .filter(k -> !k.isExpired())
            .map(k -> parsePublicKey(k.getPublicPem()));
    }

    // ============================================================
    // 생성 / 회전
    // ============================================================

    /** 새 RSA 페어 생성 + 저장 (active 플래그는 호출자가 결정) */
    @Transactional
    public AuthRsaKey generateAndSave(boolean active) {
        KeyPair pair = generateKeyPair();
        String kid = "K-" + LocalDateTime.now().toLocalDate() + "-" + UUID.randomUUID();
        String publicPem = toPem(pair.getPublic().getEncoded(), PUBLIC_PEM_HEADER, PUBLIC_PEM_FOOTER);
        String privatePem = toPem(pair.getPrivate().getEncoded(), PRIVATE_PEM_HEADER, PRIVATE_PEM_FOOTER);
        String privatePemEnc = encryptor.encrypt(privatePem);

        AuthRsaKey entity = AuthRsaKey.builder()
            .kid(kid)
            .publicPem(publicPem)
            .privatePemEnc(privatePemEnc)
            .active(active)
            .expiresAt(LocalDateTime.now().plusDays(retentionDays))
            .build();

        AuthRsaKey saved = keyRepository.save(entity);
        log.info("[KeyService] new RSA key generated: kid={}, active={}, expiresAt={}",
            saved.getKid(), saved.isActive(), saved.getExpiresAt());
        return saved;
    }

    /**
     * 자정 회전 — 기존 active 모두 비활성 + 새 페어 INSERT (active=true) + 만료 키 cleanup.
     * 트랜잭션 1개 안에서 처리.
     */
    @Transactional
    public AuthRsaKey rotate() {
        int deactivated = keyRepository.deactivateAll();
        AuthRsaKey newKey = generateAndSave(true);
        int deleted = keyRepository.deleteExpired(LocalDateTime.now());
        log.info("[KeyService] rotated: deactivated={}, newKid={}, deletedExpired={}",
            deactivated, newKey.getKid(), deleted);
        return newKey;
    }

    /**
     * 기동 시 호출 — 유효한(만료 안 된) active 키가 있도록 보장한다.
     * <ul>
     *   <li>active 키 없음 → 새로 생성 (첫 부팅)</li>
     *   <li>active 키 있지만 만료됨 → 회전 (모듈이 만료 시점 넘겨서 다운돼 있던 경우 자가 복구)</li>
     *   <li>유효한 active 키 있음 → 그대로 반환 (정상)</li>
     * </ul>
     */
    @Transactional
    public AuthRsaKey ensureValidActiveKey() {
        Optional<AuthRsaKey> active = keyRepository.findByActiveTrue();
        if (active.isEmpty()) {
            log.info("[KeyService] no active RSA key — generating");
            return generateAndSave(true);
        }
        if (active.get().isExpired()) {
            log.warn("[KeyService] active RSA key expired (kid={}, expiresAt={}) — rotating",
                active.get().getKid(), active.get().getExpiresAt());
            return rotate();
        }
        return active.get();
    }

    // ============================================================
    // 내부 유틸
    // ============================================================

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(keySize);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm unavailable", e);
        }
    }

    private ActiveKey toActiveKey(AuthRsaKey entity) {
        String privatePem = encryptor.decrypt(entity.getPrivatePemEnc());
        PrivateKey privateKey = parsePrivateKey(privatePem);
        PublicKey publicKey = parsePublicKey(entity.getPublicPem());
        return new ActiveKey(entity.getKid(), publicKey, privateKey);
    }

    private static String toPem(byte[] encoded, String header, String footer) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
        return header + "\n" + b64 + "\n" + footer;
    }

    private static byte[] fromPem(String pem, String header, String footer) {
        String body = pem.replace(header, "").replace(footer, "").replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }

    public static PublicKey parsePublicKey(String pem) {
        try {
            byte[] der = fromPem(pem, PUBLIC_PEM_HEADER, PUBLIC_PEM_FOOTER);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to parse RSA public key", e);
        }
    }

    public static PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = fromPem(pem, PRIVATE_PEM_HEADER, PRIVATE_PEM_FOOTER);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to parse RSA private key", e);
        }
    }

    // ============================================================
    // value object
    // ============================================================

    /** 서명용 active 키 — 메모리에 들고 다닐 단위 */
    public record ActiveKey(String kid, PublicKey publicKey, PrivateKey privateKey) {}

    /** JWKS 응답용 — 검증자가 받을 공개키 묶음 단위 */
    public record PublicKeyInfo(String kid, PublicKey publicKey) {}
}
