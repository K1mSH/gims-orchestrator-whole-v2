package com.infolink.auth.repository;

import com.infolink.auth.entity.AuthRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, String> {

    Optional<AuthRefreshToken> findByJti(String jti);

    /**
     * 본인 탈퇴 / 비번 변경 / 보안 사고 시 — 사용자의 모든 refresh token 일괄 무효화.
     * (다른 디바이스 강제 로그아웃 효과)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AuthRefreshToken t SET t.revoked = true WHERE t.userId = :userId AND t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /** 만료된 refresh token cleanup (회전 job 또는 별 cron) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AuthRefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
