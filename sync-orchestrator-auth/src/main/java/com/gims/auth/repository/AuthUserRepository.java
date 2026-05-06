package com.gims.auth.repository;

import com.gims.auth.entity.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {

    Optional<AuthUser> findByAuthUsersId(String authUsersId);

    boolean existsByAuthUsersId(String authUsersId);

    /** 마지막 1명 탈퇴 차단 체크용 — count >= 2 일 때만 본인 삭제 허용 */
    long count();
}
