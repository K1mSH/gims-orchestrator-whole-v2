# Post-deploy Smoke — 배포 직후 스모크 / E2E 검증

배포 완료 직후 **"정말 살아 있나"** 확인 시나리오.
통과 = 인수인계 가능 / 실패 = 즉시 `rollback.md` 검토.

## 1. Phase A. 기본 생존 (5 분 이내)

- [ ] 각 서비스 `/actuator/health` 모두 200
- [ ] Orchestrator UI 접근 가능 (관리자 로그인)
- [ ] Orchestrator 에서 Agent 목록 조회 (각 Agent 상태 정상)
- [ ] Proxy DMZ / Internal 헬스체크 OK
- [ ] API Provider 외부 공개 엔드포인트 응답

## 2. Phase B. 파이프라인 스모크 (30 분 이내)

### 2.1 DMZ 파이프
- [ ] 외부 원본 DB 1 개 접근 가능 (SELECT count 확인)
- [ ] bojo RCV 1 개 Agent 실행 — IF_RSV 적재 확인
- [ ] bojo Loader 실행 — Target 적재 확인
- [ ] bojo SND 실행 — IF_SND 적재 확인

### 2.2 Internal 파이프
- [ ] Proxy Internal 경유 bojo-internal RCV 실행 — IF_RSV 적재
- [ ] bojo-internal Loader 실행 — GIMS 연동 Target 적재 확인

### 2.3 Provide 파이프
- [ ] provide Agent 1 개 실행 (TM_GD000203 등 파일럿)
- [ ] 관리 DB 헤더 라우팅 정상 (API Provider PG 에 Execution/SyncLog 저장)
- [ ] api-provider 를 통한 외부 API 응답 OK

### 2.4 Collector 파이프
- [ ] DMZ Collector 실행 — 외부 API 수집 / 적재 확인
- [ ] Internal Collector 실행 — 적재 확인

## 3. Phase C. 추적 / 회귀 검증 (1 시간 이내)

- [ ] 정방향 추적 (Source → Target) 동작
- [ ] 역방향 추적 (Target → Source) 동작
- [ ] 실행 상세 화면 테이블별 처리현황 정상
- [ ] SyncLog per-mapping 기록 정상
- [ ] Retention 실행 (짧은 주기 테스트) — 감사 로그 미삭제 확인

## 4. Phase D. 24 시간 모니터링

- [ ] 첫 1 시간 알람 0 건
- [ ] 첫 6 시간 에러 로그 추세 안정
- [ ] 첫 24 시간 리소스 (CPU / heap / 연결 풀) 안정
- [ ] 외부 API 응답 시간 범위 내

## 5. 실패 시 대응

| 실패 지점 | 우선 조치 |
|---------|---------|
| Phase A 생존 실패 | 즉시 롤백 검토 (`rollback.md`) |
| Phase B 파이프 실패 | 해당 Agent 로그 확인 → `config-replacement.md` 치환 누락 점검 → invariant 위반 확인 |
| Phase C 추적 실패 | 특정 Agent 이슈 가능성 → VER 이슈 기록 후 해당 Agent 격리 |
| Phase D 안정성 실패 | 원인 분석 후 다음 배포 반영 (즉시 롤백까진 아님) |

## 6. 축적 규칙 (verifier 책임)

- 실제 배포 중 발견된 케이스는 본 문서에 **시나리오 추가**
- 반복 발생하는 실패는 해당 **invariant 로 승격** 또는 checklist 추가
