package com.sync.agent.common.auth;

/**
 * 검증자 모듈의 인증 실패 코드.
 *
 * AUTH_DESIGN.md §13.8 응답 매트릭스 정합:
 * <ul>
 *   <li>401 — 자격 문제 (사용자 측)</li>
 *   <li>503 — 시스템 문제 (auth 모듈 다운)</li>
 * </ul>
 */
public enum AuthErrorCode {

    /** cookie 없음 */
    AUTH_REQUIRED(401, "인증이 필요합니다"),

    /** 토큰 만료 — Frontend 가 자동 refresh 시도 */
    TOKEN_EXPIRED(401, "토큰이 만료되었습니다"),

    /** 서명 검증 실패 (위조) */
    INVALID_SIGNATURE(401, "토큰 서명이 유효하지 않습니다"),

    /** iss/aud/format 등 기타 토큰 문제 */
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),

    /** JWKS 캐시에 매칭 kid 없음 — 회전 직후 5min 갭 또는 위조 */
    UNKNOWN_KEY_ID(401, "알 수 없는 키 ID — 회전 직후일 수 있음"),

    /** auth 모듈 다운 / JWKS fetch 실패 (retry 2회 후) */
    AUTH_SERVICE_UNAVAILABLE(503, "인증 서버 응답 없음 — 잠시 후 다시 시도하세요");

    private final int httpStatus;
    private final String message;

    AuthErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int httpStatus() { return httpStatus; }
    public String message() { return message; }
}
