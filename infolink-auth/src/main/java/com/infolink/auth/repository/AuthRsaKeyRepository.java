package com.infolink.auth.repository;

import com.infolink.auth.entity.AuthRsaKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthRsaKeyRepository extends JpaRepository<AuthRsaKey, String> {

    /** 현재 서명용 키 (active=true) — 1쌍만 존재해야 함 */
    Optional<AuthRsaKey> findByActiveTrue();

    /** JWKS endpoint 응답용 — 만료 안 된 모든 키 (active 1 + 비활성 ~7) */
    List<AuthRsaKey> findByExpiresAtAfter(LocalDateTime now);

    /** 자정 회전 시 — 기존 active 키들을 비활성으로 전환 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AuthRsaKey k SET k.active = false WHERE k.active = true")
    int deactivateAll();

    /** 만료된 키 cleanup */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AuthRsaKey k WHERE k.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
