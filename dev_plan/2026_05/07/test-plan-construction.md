# 1차 반입 전 통합 테스트 — test_plan 8 문서 구성 계획

> 작성일: 2026-05-07
> 출발점: "1차 반입 전 전체 기능 테스트 한 번 돌려서 큰 틀의 오류 잡기" (사용자 5/7)
> Baseline tag: `stable-2026-05-07-rename` (= dad8a1b)
> 통과 시: `stable-2026-05-07` (또는 합의된 신규 tag) + (사용자 결정에 따라 GitHub Release 생략)

---

## 1. 결정 요약 (사용자 5/7 오전)

- **구성**: 모듈별 N개 + 통합 시나리오 1개 = **8 문서**
- **범위**: 6 체계 (1 datasource / 2 agent / 3 api-collect / 4 monitoring / 5 api-provide / 7 security)
- **baseline tag**: 현재 main HEAD 그대로 (`stable-2026-05-07-rename` = dad8a1b)
- **양식**: 기능별 그때그때 (메모리 룰 `feedback_test_plan_baseline_tag` — "공통 템플릿 미리 안 박음, baseline tag 헤더 + 신규 tag 결과 끝 패턴만 일관")

## 2. 8 문서 구성

| # | 파일 | 체계 | 신규/보완 | 출처 |
|:-:|---|:-:|:-:|---|
| 1 | `01-datasource-test.md` | 1 | **신규** | bojo-test §3 (Proxy 패스스루) 보강 + 등록/암호화/연결 테스트 |
| 2 | `02-bojo-test.md` (현 `bojo-test.md` 갱신) | 2 | **보완** | 현 749 라인 — 5/7 리네이밍 반영 + 누락 항목 점검 |
| 3 | `03-others-test.md` (현 `others-test.md` 갱신) | 2 | **보완** | 현 383 라인 — 동일 |
| 4 | `04-api-collect-test.md` | 3 | **신규** | API Collector DMZ/Internal 흐름. LOOKUP / endpoint 등록 / Mock 자기호출 / 페이징 |
| 5 | `05-api-provide-test.md` | 5 | **신규** | API Provider (B4 핸들러 36 + 다른 META operation). Provide API Key 흐름 + 운영자 cookie 흐름 |
| 6 | `06-monitoring-test.md` | 4 | **신규** | execution history 조회 + Retention + Schedule + 추적 (3단계) — 다른 모듈 cross-cutting |
| 7 | `07-security-test.md` | 7 | **신규** | Auth(8096) JWT 발급/검증 + cookie 흐름 + ApiKeyFilter (strict/soft) + 사용자 관리 |
| 8 | `08-first-deployment-e2e.md` | (cross) | **신규** | 1차 반입 시점 통합 시나리오 — 시간 흐름 기반: auth 로그인 → datasource 등록 → bojo 실행 → provide 호출 → 모니터링 |

## 3. 작성 순서 (의존성 기반)

```
1. 07-security-test         (Auth — 다른 endpoint 호출 전제)
2. 01-datasource-test       (Proxy 패스스루 — 모든 Agent 의 의존)
3. 02-bojo-test (보완)       (핵심 파이프라인)
4. 03-others-test (보완)     (Others SND)
5. 04-api-collect-test      (외부 API 수집)
6. 05-api-provide-test      (외부 API 제공 — B4 회귀 포함)
7. 06-monitoring-test       (cross-cutting — 위 모든 실행의 후속 검증)
8. 08-first-deployment-e2e  (통합 — 위 모두 통과 후)
```

> 작성 단계와 실행 단계 분리: 먼저 8 문서 다 작성 → 사용자 검토 → 작성된 문서 기반 실제 테스트 수행

## 4. 각 문서 공통 규약

### 헤더
```markdown
# {기능명} 기능 테스트 문서

> 검증 baseline: `stable-2026-05-07-rename` (commit: dad8a1b)
> 통과 시: `stable-2026-05-07` 신규 tag 박음
> 작성일: 2026-05-07
```

### 본문 구조 (모듈별 자유)
1. 시스템 구성 (관련 서비스/포트/DB)
2. 사전 준비 (데이터, 환경)
3. 검증 항목 (체크박스 list)
4. 주의사항 / 알려진 허점

### 끝부분
```markdown
## Baseline 태그 갱신
- 검증 통과: 2026-05-XX
- 신규 stable tag: `stable-2026-05-07` (commit: ?????)
```

## 5. 5/7 리네이밍 반영 항목 (기존 bojo-test / others-test 보완)

| 변경 | 영향 |
|---|---|
| 모듈 이름 `sync-*` → `infolink-*` | 빌드/실행 명령 (`cd infolink-agent-bojo-dmz && ./gradlew bootRun` 등) |
| `bojo-int` → `bojo-internal` 약칭 | 문서 표기 일관성 (이미 sed 적용됨) |
| `gims-api-provider` → `infolink-api-provider` | provide 관련 표기 |
| Java 패키지 `com.sync.*` → `com.infolink.*` | 거의 영향 없음 (테스트는 endpoint 호출 위주) |
| ApiKeyFilter soft-mode (5/6 추가) | security-test 에 명시 |
| Auth 시스템 (5/4~5/6) | security-test 신규 문서로 분리 |
| B4 핸들러 id=36 (5/6) | api-provide-test 에 회귀 포함 |
| frontend route group `(main)/` (5/6) | bojo/others-test 의 §9 프론트엔드 UI 보완 |

## 6. 작성 작업 단위 (시간 추정)

| Step | 내용 | 시간 |
|---|---|:--:|
| 1 | `07-security-test.md` 신규 (Auth Phase 1~5 + ApiKeyFilter soft-mode) | 45분 |
| 2 | `01-datasource-test.md` 신규 (등록/암호화/Proxy 패스스루) | 30분 |
| 3 | `02-bojo-test.md` 보완 (5/7 리네이밍 반영) | 15분 |
| 4 | `03-others-test.md` 보완 | 15분 |
| 5 | `04-api-collect-test.md` 신규 (Collector DMZ/Internal) | 45분 |
| 6 | `05-api-provide-test.md` 신규 (B4 + META operations) | 45분 |
| 7 | `06-monitoring-test.md` 신규 (Retention/Schedule/추적 cross-cutting) | 30분 |
| 8 | `08-first-deployment-e2e.md` 신규 (통합 시나리오) | 45분 |
| 9 | 사용자 검토 게이트 | — |
| 10 | 실제 테스트 수행 (8 문서 기반) | 별 사이클 |
| 합계 (작성만) | — | **~4.5h** |

## 7. 검증 흐름

### 작성 단계 (현재)
1. dev_plan 사용자 승인
2. 8 문서 순차 작성 (의존성 순서대로)
3. 각 문서 작성 후 사용자 즉시 검토 (한 번에 다 작성보다 단계별)
4. 8 문서 완성 → 사용자 최종 승인

### 실행 단계 (별 세션 또는 즉시)
1. 7 서비스 + frontend 기동
2. `07-security` → `01-datasource` → ... → `08-first-deployment-e2e` 순서로 검증
3. 각 단계 통과 시 체크박스 ✅
4. 미통과 시 → issue 등록 (verify/issues/) + fix → 재검증
5. 모든 8 문서 통과 시 → 신규 stable tag 박기

## 8. 결정 (사용자 5/7)

- [x] **8 문서 분리** — 04 api-collect 와 05 api-provide 분리 유지
- [x] **02/03 bojo/others 보완 깊이** — 단순 리네이밍 반영 + 5/7 변경 신규 항목 추가 (Auth cookie 인증 흐름, ApiKeyFilter soft-mode 등)
- [x] **작성 진입 방식** — **단계별** (07부터 1개씩 작성 → 사용자 검토 → 다음 문서)
- [ ] 08 통합 시나리오 깊이 — 작성 시점 결정 (보류)
- [ ] 신규 stable tag 이름 — 실행 통과 후 결정 (보류)

## 8-1. ⭐ 사용자 룰 — 실행 단계 검증 패턴 (5/7)

**테스트 검증은 claude OK 만으로는 통과 X.**
- claude 가 curl 결과 보고 "정상" 이라고 해도 그건 1차 확인.
- **사용자가 직접 프론트 UI 로 결과물 보고 같이 확인**해야 다음 단계 진입.
- 메모리 룰 `feedback_trace_definition` ("추적 검증 = 건수 + 단건 역추적 / trace-source") + `feedback_test_scenario` ("단위테스트 결과서: 원본 양식 엄수") 정수에 정합 — UI 검증 미실행 시 "정상" 으로 판단 X (bojo-test §공통 테스트 규칙 §3단계와 동일).

**적용 절차** (각 문서 실행 시점):
1. claude 가 API 호출 / 데이터 확인 1차 검증
2. claude 가 사용자에게 "이 단계 결과 OK 인지 프론트에서 직접 확인 부탁" 요청
3. 사용자가 프론트 (`localhost:3000`) 에서 해당 화면 직접 클릭/조회/검증
4. 사용자 "OK" → 다음 단계 진입
5. 사용자 "이상 발견" → issue 등록 + fix → 재검증

→ 작업 메모리 후속 등록 권장 (`feedback_test_validation_user_confirms`)

## 9. 참고 — 기존 bojo-test 의 좋은 패턴 (다른 문서에도 적용)

- ✅ "공통 테스트 규칙" 섹션 (3단계 추적 검증)
- ✅ 시스템 구성 표 (포트/DB)
- ✅ Step 별 추적 검증 테이블 (Agent / target / source / 매칭모드)
- ✅ Oracle 특이 케이스 / 알려진 허점 별도 섹션
- ✅ 부록 — 빌드 명령어
