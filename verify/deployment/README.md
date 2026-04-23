# Deployment — 실배포 게이트 체크리스트

개발 환경 → 실배포 전환 시 반드시 통과해야 하는 게이트.
상시 검증(`verify/_invariants/`)과 달리 **배포 D-day 한 번 거치는 절차 모음**.

## 1. 언제 쓰나
- **D-7 ~ D-1**: 사전 준비 (사전 점검 / 값 확정 / 발급 및 교체)
- **D-day**: 환경 전환 / 치환 실행 / 순차 배포 / 헬스 확인
- **배포 후**: 스모크 / 모니터링 / (문제 시) 롤백

## 2. 누가 쓰나
- **배포 담당자** — 실제 값 치환 / 서버 조작 / 체크박스 서명
- **verifier 세션** — 체크리스트 갱신 / `config-replacement.md` 전수표 누적 / `post-deploy-smoke.md` 시나리오 유지

## 3. 문서 지도

| 문서 | 역할 | 사용 타이밍 |
|------|------|-----------|
| `production-checklist.md` | **마스터 체크리스트** (D-day 순서) | 전 단계 |
| `config-replacement.md` | **치환되어야 할 고정값 전수표** | D-1 확정 / D-day 치환 |
| `credentials-rotation.md` | 계정 / API 키 / secret 교체 | D-7 발급 / D-day 적용 |
| `environment-setup.md` | 서버 / 네트워크 / 방화벽 / TZ / 인코딩 | D-7 ~ D-day |
| `monitoring-setup.md` | 로그 / 알람 / 헬스체크 / 대시보드 | D-7 ~ post |
| `post-deploy-smoke.md` | 배포 후 스모크 / E2E 검증 | post |
| `rollback.md` | 롤백 절차 / 시나리오 | 장애 시 |

## 4. 원칙
- 체크리스트는 **재실행 가능한 절차** 로 작성 (다음 배포 때도 그대로 사용 가능)
- 항목마다 **확인자 / 확인일 서명란** 필수
- 실패 시 진행 중단 + 사유 기록 (다음 반복에서 재발 방지)

## 5. 갱신 규칙
- **새 서비스 / 새 DB / 새 외부 의존** 추가 시마다 `config-replacement.md` 표 갱신 — verifier 책임
- 배포 경험이 쌓이면 `post-deploy-smoke.md` 에 실제 발견 케이스 축적
- 장애 경험이 쌓이면 `rollback.md` 에 실제 시나리오 축적

## 6. `verify/_invariants/` 와의 관계
- invariant = 규약 (예: "credentials 는 외부화되어야 한다")
- deployment = 인벤토리 / 절차 (예: "Orchestrator DB 암호를 환경변수 X 로 치환")
- invariant 위반이 있으면 deployment 에 앞서 해소 필요 (배포 블로커)
