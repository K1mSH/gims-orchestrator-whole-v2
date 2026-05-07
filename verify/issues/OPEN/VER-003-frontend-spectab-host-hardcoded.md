---
id: VER-003
title: Frontend SpecTab.tsx API host 코드 내 평문 하드코딩
status: OPEN
created: 2026-04-23
parts: [P11-frontend]
parallel_safe: true
assignee: forward
related: [VER-001, VER-002]
---

## 증상 요약

`infolink-orchestrator-frontend/components/api-provide/SpecTab.tsx` 내에 API host 가 **코드 내 평문 상수** 로 선언되어 있음.

다른 프론트엔드 호출부는 Next.js 프록시(`/collector-api/*`, `/provider-api/*`) 경유로 설정되어 있는데 이 한 곳만 예외 — **일관성 위반**.
또한 `process.env.*` / `NEXT_PUBLIC_*` 치환 지점 없어 실배포 시 **코드 수정 없이 전환 불가**.

## 재현 절차
1. `infolink-orchestrator-frontend/components/api-provide/SpecTab.tsx` 열기
2. line 48 확인

## 기대 vs 실제

### 기대 (두 방향 중 택일)

**방향 A — Next.js 프록시 경유 (권장, 기존 패턴과 일관)**
```tsx
const host = '/provider-api';   // next.config.js 의 rewrite destination 경유
```

**방향 B — 환경변수 치환 패턴**
```tsx
const host = process.env.NEXT_PUBLIC_PROVIDER_BASE ?? 'http://localhost:8095';
```

### 실제
```tsx
// infolink-orchestrator-frontend/components/api-provide/SpecTab.tsx:48
const host = 'http://localhost:8095';   // ⚠️ 코드 내 평문
```

## 증거

2026-04-23 grep 스캔 (`rg localhost --glob "*.tsx"`):
- Frontend 내 `localhost` 매치 총 5 건 중 **컴포넌트 코드 본문 내 하드코딩은 SpecTab 이 유일**.
- 나머지 4 건: `next.config.js` 의 rewrite destination 3 건 (빌드 설정 레이어 — G3~G5) + `app/agents/page.tsx:490` HTML `placeholder` 속성 (무해).

```
infolink-orchestrator-frontend/components/api-provide/SpecTab.tsx:48:  const host = 'http://localhost:8095';
```

## 수정 범위 제안
- `SpecTab.tsx:48` — 방향 A (프록시 경유) 권장
  - 이유: `next.config.js` 에 이미 `/provider-api/:path*` → `http://localhost:8095/api/:path*` rewrite 가 등록되어 있어 일관성 유지 가능 (`G4` 해결 시 자동으로 전환)
  - 기존 다른 호출부 (`lib/api.ts` 등) 패턴과 동일
- 대안 — 방향 B (환경변수) 는 SSR / ISR 컨텍스트 등 프록시가 적용되지 않는 경우에만

## 회귀 확인 방법
- 프론트엔드 타입체크: `cd infolink-orchestrator-frontend && npx tsc --noEmit` 통과
- SpecTab 화면 접근 → API 호출 정상 응답
- dev 에서 기본값 유지, 실배포 빌드 시 프록시 경로 또는 환경변수로 치환되는지 확인

## 관련 문서
- `verify/_invariants/00-overview.md` § 11A (환경 분리 / 외부화)
- `verify/deployment/config-replacement.md § G6`
- `verify/runs/2026-04-23.md` 오후 추가 스캔 결과
- 관련 이슈: VER-001 (api-provider 외부화 누락), VER-002 (api-collector lookup URL 외부화 누락)
- MEMORY: `feedback_config_vs_registration`, `feedback_config_replacement_sync`
