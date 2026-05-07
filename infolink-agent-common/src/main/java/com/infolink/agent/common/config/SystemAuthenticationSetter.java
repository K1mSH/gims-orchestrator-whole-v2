package com.infolink.agent.common.config;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Spring Security {@code SecurityContext} 에 시스템 호출 인증 박는 헬퍼.
 *
 * <p>{@link ApiKeyFilter} 의 soft-mode + X-API-Key 매치 시점에서만 호출됨.
 * 별 클래스로 분리한 이유 = ApiKeyFilter 가 Spring Security 클래스를 import 안 하게 하여
 * strict 모듈 (Spring Security 의존성 미포함) 에서 NoClassDefFoundError 회피.
 * JVM lazy class resolution 덕에 본 클래스는 softMode=true 시점에만 실제 로드됨.
 */
public final class SystemAuthenticationSetter {

    private static final String SYSTEM_PRINCIPAL = "SYSTEM";
    private static final String SYSTEM_ROLE = "ROLE_SYSTEM";

    private SystemAuthenticationSetter() {}

    /** 현 요청의 SecurityContext 에 ROLE_SYSTEM 인증 박음 (시스템 간 호출 식별). */
    public static void setSystemAuthentication() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                SYSTEM_PRINCIPAL,
                null,
                List.of(new SimpleGrantedAuthority(SYSTEM_ROLE))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
