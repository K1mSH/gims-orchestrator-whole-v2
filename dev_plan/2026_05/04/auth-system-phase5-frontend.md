# Auth 시스템 — Phase 5: Frontend (3000)

> 작성일: 2026-05-04
> 범위: `sync-orchestrator/frontend` 에 로그인 화면 + 글로벌 헤더 + 사용자 관리 화면 추가
> 선행: [Phase 1](auth-system-phase1.md), [Phase 2 Backend](auth-system-phase2-backend.md), [Phase 3 api-provider](auth-system-phase3-api-provider.md), [Phase 4 api-collector](auth-system-phase4-api-collector.md)
> 동반 문서: [AUTH_DESIGN.md §10](../../../docs/AUTH_DESIGN.md), [AUTH_FLOW.md](../../../docs/AUTH_FLOW.md)

---

## 1. 목적

운영자 인증 UI 도입:
1. **로그인 화면** (`/login`)
2. **글로벌 슬림 헤더** — 로그인/로그아웃 버튼 + 사용자 아이콘 + 사용자 이름
3. **사용자 관리 화면** (`/users`) — 목록 + 새 사용자 추가 모달 (peer multiplication)
4. **본인 정보 변경 화면** (`/users/me`) — 비번 변경 + 탈퇴
5. **미인증 가드** — middleware (1차) + axios interceptor (2차)

## 2. 작업 범위

### 포함
- 로그인 페이지 (form + 401 메시지 / 423 잠김 / 503 인증서버 다운)
- 글로벌 헤더 (`AppHeader.tsx` + `app/layout.tsx` 수정)
- `/users` 목록 + 새 사용자 추가 모달
- `/users/me` 비번 변경 + 탈퇴
- `useCurrentUser` SWR hook (GET /api/auth/me)
- middleware (cookie 존재 여부 — 1차 가드)
- axios interceptor (401 → /refresh / refresh 실패 → /login redirect, 503 → toast)
- next.config rewrites (`/auth/*` → 8096)

### 미포함
- 비밀번호 강도 시각 피드백 (정책 강화는 후속)
- MFA / 2FA
- 사용자 활동 로그 화면

## 3. 변경 파일

```
sync-orchestrator/frontend/
├── next.config.mjs                                ← 수정 (rewrites 에 /auth/* 추가)
├── middleware.ts                                  ← 신규
├── lib/
│   ├── axios.ts                                   ← 수정 (interceptor 401/503)
│   └── useCurrentUser.ts                          ← 신규 (SWR hook)
├── components/
│   └── AppHeader.tsx                              ← 신규 (글로벌 헤더)
└── app/
    ├── layout.tsx                                 ← 수정 (AppHeader 박음)
    ├── login/page.tsx                             ← 신규
    └── users/
        ├── page.tsx                               ← 신규 (목록 + 추가 모달)
        └── me/page.tsx                            ← 신규 (비번 변경 + 탈퇴)
```

## 4. 단계별 Step

### Step 1. next.config rewrites (~10분)
```js
// next.config.mjs
async rewrites() {
  return [
    { source: '/auth/:path*', destination: 'http://localhost:8096/api/auth/:path*' },
    { source: '/orch-api/:path*', destination: 'http://localhost:8080/api/:path*' },
    { source: '/provider-api/:path*', destination: 'http://localhost:8095/api/:path*' },
    { source: '/collector-api/:path*', destination: 'http://localhost:8084/api/:path*' },
  ]
}
```

> 같은 origin 으로 cookie 자동 전송되도록 proxy 통일.

### Step 2. axios interceptor (~30분)
```typescript
// lib/axios.ts
import axios from 'axios';
import { toast } from 'react-hot-toast';   // or similar

const client = axios.create({ withCredentials: true });

let refreshing: Promise<void> | null = null;

client.interceptors.response.use(
  res => res,
  async err => {
    const original = err.config;
    const status = err.response?.status;
    const errorCode = err.response?.data?.error;

    // 401 — 토큰 문제: refresh 시도
    if (status === 401 && !original._retry) {
      original._retry = true;
      if (!refreshing) {
        refreshing = axios.post('/auth/refresh', null, { withCredentials: true })
          .then(() => { refreshing = null; })
          .catch(() => {
            refreshing = null;
            window.location.href = '/login';
            throw err;
          });
      }
      await refreshing;
      return client(original);
    }

    // 503 — auth 서버 다운
    if (status === 503 && errorCode === 'AUTH_SERVICE_UNAVAILABLE') {
      toast.error('인증 서버가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해주세요.');
    }

    return Promise.reject(err);
  }
);

export default client;
```

### Step 3. useCurrentUser SWR hook (~15분)
```typescript
// lib/useCurrentUser.ts
import useSWR from 'swr';
import client from './axios';

export function useCurrentUser() {
  const { data, error, mutate } = useSWR('/auth/me', url =>
    client.get(url).then(res => res.data));
  return {
    user: data,
    isLoading: !error && !data,
    error,
    mutate,
  };
}
```

### Step 4. 로그인 페이지 (~30분)
- `app/login/page.tsx`
- form (username / password) + submit → POST `/auth/login`
- 응답:
  - 200 → `router.push('/')` (또는 next 쿼리 파라미터로 원래 path)
  - 401 INVALID_CREDENTIALS → "ID/비밀번호가 일치하지 않습니다"
  - 423 ACCOUNT_LOCKED → "계정이 잠겼습니다. 30분 후 다시 시도해주세요"
  - 503 → toast (interceptor 처리)

### Step 5. 글로벌 슬림 헤더 (~30분)
- `components/AppHeader.tsx` — height ~40px
- `useCurrentUser` 로 상태 분기
  - 미로그인: `[로그인]` 버튼 → `/login`
  - 로그인: 사용자 이름 + `[👥 사용자 목록]` 아이콘 (→ `/users`) + `[로그아웃]`
- `app/layout.tsx` 수정 — `<AppHeader />` 추가

### Step 6. 미인증 middleware (~20분)
```typescript
// middleware.ts
import { NextRequest, NextResponse } from 'next/server';

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;
  const accessCookie = req.cookies.get('accessToken');
  const isLoginPath = pathname === '/login';

  if (!accessCookie && !isLoginPath) {
    const url = req.nextUrl.clone();
    url.pathname = '/login';
    url.searchParams.set('next', pathname);
    return NextResponse.redirect(url);
  }
  if (accessCookie && isLoginPath) {
    const url = req.nextUrl.clone();
    url.pathname = '/';
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|auth/|orch-api/|provider-api/|collector-api/).*)',
  ],
};
```

### Step 7. 사용자 관리 화면 — `/users` (~1시간)
- `app/users/page.tsx`
- GET `/auth/users` → 테이블 (id / username / name / createdAt)
- 본인 row 옆 "내 정보 변경" 버튼 → `/users/me`
- 우측 상단 **새 사용자 추가** 버튼 → 모달
  - Form: username / password / name
  - POST `/auth/users` → 성공 시 alert ("ID/PW 를 별도 채널로 전달하세요") + 목록 mutate
  - 409 USERNAME_DUPLICATE → "이미 사용 중인 ID 입니다"
  - 400 PASSWORD_TOO_SHORT → "비밀번호는 8자 이상이어야 합니다"

### Step 8. 본인 정보 변경 — `/users/me` (~45분)
- `app/users/me/page.tsx`
- 비번 변경 폼 (currentPassword / newPassword / confirmPassword)
  - PATCH `/auth/users/me/password`
  - 성공 → alert ("비밀번호가 변경되었습니다. 다시 로그인해주세요") + `/login` redirect
  - 400 CURRENT_PASSWORD_MISMATCH → "현재 비밀번호가 일치하지 않습니다"
- 탈퇴 버튼 (확인 모달)
  - DELETE `/auth/users/me`
  - 성공 → cookie 만료 + `/login` redirect
  - 409 LAST_USER_CANNOT_DELETE → "마지막 사용자는 탈퇴할 수 없습니다"

### Step 9. 통합 검증 (~30분)
- [ ] 미로그인 진입 → `/login` redirect
- [ ] 로그인 → `/` 진입 + 헤더에 사용자 이름 표시
- [ ] `/users` → 목록 + 새 사용자 추가
- [ ] alice 로 추가 → 별 브라우저(incognito)에서 alice 로그인 → 본인 비번 변경 → 다시 로그인
- [ ] admin 으로 본인 탈퇴 시도 (alice 빠진 후) → "마지막 사용자는 탈퇴할 수 없습니다" 메시지
- [ ] 로그아웃 → `/login` redirect
- [ ] auth 모듈 다운 (`docker stop` 또는 kill) → 운영 화면 호출 → toast "인증 서버가 응답하지 않습니다"

## 5. 검증 시나리오 (§12.6 매트릭스)
- ✅ #11 access exp → axios interceptor refresh 자동 (E2E)
- ✅ middleware 1차 가드 동작
- ✅ 사용자 관리 UI E2E (peer multiplication / 비번 변경 / 탈퇴 / 마지막 1명 차단)

## 6. 영향 범위 / 회귀

### 영향 받는 흐름
- 모든 운영 화면 — 미로그인 시 `/login` redirect
- API 호출 — cookie 자동 첨부 (withCredentials: true)

### 회귀 위험
- **기존 운영자 흐름 모두 바뀜** — Phase 5 적용 시점에 운영자 첫 로그인 필요
- 운영자 사전 안내 + auth CLI 로 사용자 1명 발급 후 배포 권장

## 7. 추정 시간
- Step 1~9 합계 = **~5~6시간** (UI 가 가장 큰 파트)

## 8. 참고
- AUTH_DESIGN.md §10 — Frontend 통합
- AUTH_FLOW.md §5~§12 — 토큰 라이프사이클 (UI 가 어떻게 cookie 활용하는지)
- 메모리: `feedback_form_consistency_register_edit.md` — 등록/수정 화면 양식 일관성
