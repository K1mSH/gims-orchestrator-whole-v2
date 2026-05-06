# auth_users.username → auth_users_id (모든 layer 통일)

> 작성일: 2026-05-06 오후 (B4 후)
> 의도: `auth_refresh_tokens.user_id` (FK→PK) 와 명명 충돌 회피. PK `id` 와도 구분 명확.

## 정책 — 모든 layer 통일

| Layer | 명 |
|---|---|
| DB 컬럼 | `auth_users_id` |
| JPA 필드 | `authUsersId` |
| DTO 필드 | `authUsersId` |
| JSON 응답 키 (외부 노출) | `authUsersId` |
| Frontend 변수 | `authUsersId` |
| Frontend display 텍스트 | "ID" (사용자 영향 0) |

## 영향 파일 (14 + DB)

### DB
- `auth_users.username` 컬럼 RENAME → `auth_users_id`
- `uk_auth_users_username` UNIQUE INDEX RENAME → `uk_auth_users_auth_users_id`

### Backend (sync-orchestrator-auth)
- `entity/AuthUser.java` — 필드 + `@Column(name="auth_users_id")`
- `repository/AuthUserRepository.java` — `findByUsername` / `existsByUsername` → `findByAuthUsersId` / `existsByAuthUsersId`
- `dto/{LoginRequest, AddUserRequest, UserDto}.java` — 필드명
- `service/{UserService, AuthService}.java` — 매개변수 + 로직
- `controller/{AuthController, UserController}.java` — RequestBody 매핑 (DTO 가 처리)
- `tools/UserGeneratorCli.java` — args 처리
- 단위 테스트 — Token/User/AuthServiceTest 안의 username 참조

### Frontend
- `lib/authApi.ts` — login/addUser body + AuthUser interface
- `app/login/page.tsx` — form state 변수
- `app/users/page.tsx` — 목록 컬럼 표시 + 추가 모달
- `app/users/me/page.tsx` — 프로필 표시

## 데이터 보존
- admin 사용자 (id=27) 그대로 — `RENAME COLUMN` 데이터 보존
- 실 DB ALTER 후 ddl-auto=update 가 자동 매핑 (`@Column(name="auth_users_id")` 박힌 후)

## 검증
- auth 모듈 단위 테스트 (Token/Auth) 재실행
- E2E: 로그인 (admin/pass1234) → /me → 사용자 추가 → 로그아웃 → 재로그인
- Frontend 화면: /login form 동작 + /users 목록 + AppHeader 표시
- UserGeneratorCli 새 사용자 발급 검증

## 추정
- ~30~40분
