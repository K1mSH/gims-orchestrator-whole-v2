# TODO 진행도 관리

## 에픽 (Epic) 정의

| 키 | 에픽명 | 상태 |
|----|--------|------|
| E1 | 동기화 파이프라인 핵심 (DMZ/Internal) | 검증중 |
| E2 | 보안 체계 (암호화/인증) | 검증중 |
| E3 | 외부 API 수집기 (api-collector) | 검증중 |
| E4 | 내부망 Oracle 전환 | 완료 |
| E5 | 새올 SND 파이프라인 | 완료 |
| E6 | 제주/이용량 파이프라인 (Internal) | 진행중 |
| E7 | 환경부 표준 컬럼명 전환 | 완료 |
| E8 | Orchestrator UI/기능 | 검증중 |
| E9 | bojo-int Entity 전환 | 진행중 |
| E10 | 인프라/리팩토링 | 검증�� |
| E11 | Jira 연동 | 진행중 |

## 파트별 진행도 요약

| 파트 | 파일 | 완료/전체 | 상태 |
|------|------|-----------|------|
| 1. sync-agent-common | [01-common.md](01-common.md) | 17/17 | 검증중 |
| 2. sync-agent-bojo (DMZ) | [02-bojo.md](02-bojo.md) | 23/23 | 검증중 |
| 3. sync-agent-bojo-int (Internal) | [03-bojo-int.md](03-bojo-int.md) | 30/36 | 진행중 (83%) |
| 4. sync-proxy (DMZ/Internal) | [04-proxy.md](04-proxy.md) | 10/10 | 검��중 |
| 5. sync-orchestrator/backend | [05-orchestrator-backend.md](05-orchestrator-backend.md) | 19/19 | 검���중 |
| 6. sync-orchestrator/frontend | [06-orchestrator-frontend.md](06-orchestrator-frontend.md) | 18/19 | 검증중 |
| 7. infolink-api-collector | [07-api-collector.md](07-api-collector.md) | 76/82 | 검증중 (93%) |
| 8. 인프라/보안 | [08-infra-security.md](08-infra-security.md) | 9/10 | 검증중 |
| 9. DMZ Agent 2호기 (others) | [09-dmz-agent-2.md](09-dmz-agent-2.md) | 10/13 | 검증중 (보류 3건) |

### 상태 기준
| 상태 | 의미 |
|------|------|
| 진행중 (%) | 개발 진행 ��� |
| 검증중 | 개발 완료, 테스트/안정화 단계 (실서버 배포 전) |
| 운영중 | 실서버 배포 완료 |

### Jira 연동 규칙
- `## [E키]` 헤딩 → Jira **Task** (에픽 링크 포함)
- `- [x]` / `- [ ]` → Jira **Sub-task** (상태: Done / To Do)
- 스크립트가 파싱하여 자동 등록/상태 동기화

> 최종 업데이트: 2026-04-14
