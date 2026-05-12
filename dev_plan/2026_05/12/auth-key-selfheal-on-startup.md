# auth 모듈 — 기동 시 만료 active 키 자동 회전 (계획)

작성일: 2026-05-12
발견 경위: 04-others 테스트 진입 중 프론트 "인증 서버가 일시적으로 응답하지 않습니다" → backend `JwksClient` 가 `JWKS response has no usable RSA keys` → auth `GET /.well-known/jwks.json` 이 `{"keys":[]}` 반환.

## 원인

- auth 모듈은 5/4 신규. 유일한 키 `K-2026-05-04-...` 의 `expires_at` = 생성 + `auth.rsa.retention-days(8)` = **2026-05-12 14:51** → 오늘 만료됨.
- `KeyRotationJob` 은 매일 자정(`0 0 0 * * ?`) 회전하는데, dev 환경에서 auth 모듈이 자정에 켜져 있던 적이 없어 한 번도 회전 안 됨 → 8일 윈도우가 닫히는 순간 키가 그냥 만료.
- `JwksController.jwks()` = `keyService.findAllValidPublicKeys()` = `findByExpiresAtAfter(now)` — 만료 안 된 키만 → 0개 → JWKS 빈 응답 → 쿠키 인증 전부 다운.
- **`InitialKeyLoader.onReady()` 가 `findByActiveTrue()` 로만 보고 만료 여부를 안 봄** → 만료된 active 키가 있어도 "이미 있음" 하고 새로 안 만듦 → 재기동해도 자가 복구 안 됨. (← 사실상 버그)

> 임시 복구(5/12): 만료 키의 `expires_at` 을 `2026-06-30` 으로 연장(band-aid). 본 작업 후 원복하여 D 로 테스트.

## 수정

### `KeyService` — `ensureValidActiveKey()` 추가 (`@Transactional`)
```java
public AuthRsaKey ensureValidActiveKey() {
    Optional<AuthRsaKey> active = keyRepository.findByActiveTrue();
    if (active.isEmpty()) {
        log.info("[KeyService] no active RSA key — generating");
        return generateAndSave(true);
    }
    if (active.get().isExpired()) {
        log.warn("[KeyService] active RSA key expired (kid={}, expiresAt={}) — rotating",
            active.get().getKid(), active.get().getExpiresAt());
        return rotate();   // deactivateAll + generateAndSave(true) + deleteExpired
    }
    return active.get();   // 정상 — 유효한 active 키 존재
}
```

### `InitialKeyLoader.onReady()` — 위 메서드 호출로 단순화
```java
@EventListener(ApplicationReadyEvent.class)
public void onReady() {
    AuthRsaKey active = keyService.ensureValidActiveKey();
    log.info("[InitialKeyLoader] active RSA key ready: kid={}, expiresAt={}", active.getKid(), active.getExpiresAt());
}
```

### (선택, 본 작업 범위 밖) 더 proactive 한 보강
- 시간당 1회 스케줄: active 키가 `expires_at` 까지 N일 이내면 미리 회전. 24/7 인데 자정 cron 이 DST/hiccup 으로 미스파이어하는 케이스까지 커버. 이번 실패 모드(다운 중 만료)엔 기동 체크면 충분 → 일단 미포함.

## 운영 영향 / 회귀

- 운영(auth 24/7 + 자정 회전): 평상 재기동 시 active 키 유효 → `ensureValidActiveKey()` 가 그냥 반환, 동작 무변화.
- 발동 케이스 = "모듈이 키 만료 시점을 넘겨서 다운돼 있었을 때" → 자동 회전으로 복구 (현재는 그대로 멈춤). **다운사이드 없음, 회귀 없음.**
- 만료 키의 kid 참조하는 JWT 는 D 유무와 무관하게 검증 실패(`findPublicKeyByKid` 가 `!isExpired()` 필터) — 변화 없음.
- `infolink-auth` 는 `infolink-agent-common` 의존 없음 → common JAR 복사 불필요. auth 모듈만 재빌드.

## 테스트

1. 코드 수정 → `infolink-auth` clean build OK
2. **만료 시뮬레이션**: `UPDATE auth_rsa_keys SET expires_at = now() - interval '1 hour' WHERE active=true;` (active 키를 만료 상태로) — 이때 `GET /.well-known/jwks.json` → `{"keys":[]}` 확인 (D 적용 전 상태 재현)
3. auth 재기동 → 로그 `[KeyService] active RSA key expired ... — rotating` + `[KeyService] rotated: ... newKid=...` + `[InitialKeyLoader] active RSA key ready: kid=K-2026-05-12-...`
4. `GET /.well-known/jwks.json` → 새 키 1개 노출 (`kid=K-2026-05-12-...`, expiresAt = now+8일). 만료된 옛 키 row 는 `deleteExpired` 로 제거됨
5. 재로그인 → `GET /api/agents` (쿠키 경로) → 200. 프론트 정상
6. (회귀) 다시 auth 재기동 (이번엔 키 유효) → 로그 `[InitialKeyLoader] active RSA key ready` (회전 안 함), JWKS 동일

## 작업 순서

`KeyService.ensureValidActiveKey()` 추가 → `InitialKeyLoader` 수정 → `infolink-auth` 빌드 → 만료 시뮬레이션 → auth 재기동 → 검증 → (band-aid expires_at 연장은 자동으로 무의미해짐 — 새 키가 생기므로) → dev_log/커밋
