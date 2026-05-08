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

## 2. 8 문서 구성 (실행 순서 = 파일 번호)

| # | 파일 | 체계 | 종류 | 비고 |
|:-:|---|:-:|:-:|---|
| 00 | `00-test-protocol.md` | 공통 | 신규 | 분기점 + 데이터 클린 + 0건 룰 + 추적 3단계 + 사용자 직접 확인 |
| 01 | `01-security-test.md` | 7 (보안) | 신규 | Auth(8096) JWT + cookie + ApiKeyFilter strict/soft + 사용자 관리 |
| 02 | `02-datasource-test.md` | 1 | 신규 | 연결 테스트 + Proxy 패스스루 + Zone 분기 + 테이블/컬럼 검색 |
| 03 | `03-bojo-test.md` | 2 | 보완 | 보조관측망 5단계 파이프라인 + conditions + 추적 + Schedule + Retention |
| 04 | `04-others-test.md` | 2 | 보완 | Others SND (제주/이용량) |
| 05 | `05-api-collect-test.md` | 3 | 신규 | 외부 API 수집 + LOOKUP + 커스텀 executor + Schedule |
| 06 | `06-api-provide-test.md` | 5 | 신규 | API 제공 (B4 회귀 + META + CUSTOM) + Provide API Key |
| 07 | `07-monitoring-test.md` | 4 | 신규 | 실행이력 + 추적 + Retention + Schedule + 대시보드 (cross-cutting) |
| 08 | `08-first-deployment-e2e.md` | cross | 신규 | 1차 반입 통합 시나리오 — 운영자 페르소나 10 Step |

## 3. 실행 순서 = 파일 번호 (의존성 기반)

```
00. 00-test-protocol         (모든 사이클의 공통 룰 — 사전 학습)
01. 01-security-test         (Auth — 다른 endpoint 호출 전제)
02. 02-datasource-test       (Proxy 패스스루 — 모든 Agent 의 의존)
03. 03-bojo-test             (핵심 파이프라인)
04. 04-others-test           (Others SND)
05. 05-api-collect-test      (외부 API 수집)
06. 06-api-provide-test      (외부 API 제공 — B4 회귀 포함)
07. 07-monitoring-test       (cross-cutting — 위 모든 실행의 후속 검증)
08. 08-first-deployment-e2e  (통합 — 위 모두 통과 후)
```

> 작성 단계와 실행 단계 분리: 먼저 8 문서 다 작성 → 사용자 검토 → 작성된 문서 기반 실제 테스트 수행

## 4. 각 문서 공통 규약

### 헤더
```markdown
# {기능명} 기능 테스트 문서

> 작성일: 2026-05-07
```

> baseline tag / 신규 stable tag 항목은 문서에 박지 않음 (사용자 결정 5/7 — 사이클 마무리 시점에 사용자가 직접 언급).

### 본문 구조 (모듈별 자유)
1. 시스템 구성 (관련 서비스/포트/DB)
2. 사전 준비 (데이터, 환경 — **유의미한 데이터 N건 사전 INSERT 보장**)
3. 검증 항목 (체크박스 list — **분기점별로 분리**, **0건 = 테스트 안 함 룰**)
4. 주의사항 / 알려진 허점

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
| 1 | `01-security-test.md` 신규 (Auth Phase 1~5 + ApiKeyFilter soft-mode) | 45분 |
| 2 | `02-datasource-test.md` 신규 (등록/암호화/Proxy 패스스루) | 30분 |
| 3 | `03-bojo-test.md` 보완 (5/7 리네이밍 반영) | 15분 |
| 4 | `04-others-test.md` 보완 | 15분 |
| 5 | `05-api-collect-test.md` 신규 (Collector DMZ/Internal) | 45분 |
| 6 | `06-api-provide-test.md` 신규 (B4 + META operations) | 45분 |
| 7 | `07-monitoring-test.md` 신규 (Retention/Schedule/추적 cross-cutting) | 30분 |
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

## 9. ⭐ 본 사이클 범위 결정 (5/7 사용자 결정)

본 통합 테스트 = **실행쪽 기능 위주 (데이터를 다루는 부분)**. 등록(POST) 흐름은 별 사이클.

### 포함 (집중 영역)
- **실행** — 파이프라인 (RCV/Loader/SND), API 호출 (외부 사용자/운영자), CUSTOM 핸들러 (B4 등)
- **데이터 흐름** — 외부 → DMZ → Internal 데이터 흐르기, source_refs 생성/매칭
- **추적** — 3단계 자동 분기 (PK / source_refs / SND), Forward / Backward
- **운영 작업** — Schedule 자동 실행, Retention 보존기간 적용
- **모니터링** — 실행 이력, 통계, 대시보드, 호출 이력
- **인증** — JWT 로그인/refresh/logout/me, ApiKeyFilter strict/soft, cookie 흐름
- **회귀** — 5/6 B4 핸들러 등

### 제외 — 별 사이클 (등록 부담 큼 — 운영 환경에서 직접 검증)
- ❌ **datasource 등록** (POST) — 외부 10 + 내부 5+ 이미 등록됨, 신규 등록은 운영 시점
- ❌ **agent 등록** — auto-discover + 새 YAML 추가 + 재기동 필요, 부담 큼
- ❌ **api-collector endpoint 등록** — 12+ 이미 등록
- ❌ **api-provider operation 등록 (META)** — 16+ 이미 등록
- ❌ **api-provider CUSTOM 핸들러 등록** — 코드 변경 필요. B4 (id=36) 는 5/6 등록 끝
- ⚠️ 위 4 영역의 **수정 / 삭제** — 등록과 같은 사이클. 본 사이클 SKIP

### 포함 — 자유 등록/수정/삭제 (사이드 이펙트 작음)
- ✅ **Schedule (cron)** — Agent 메타 정책. 등록/수정/토글/삭제 자유. 자동 실행 검증과 같이.
- ✅ **Retention (보존기간)** — 동일. 정책 등록 + cleanup 호출로 적용 검증.
- ✅ **사용자 (auth) peer multi** — 등록(alice 추가) → 비번 변경 → 탈퇴 사이클 자연 검증.

→ 위 3 영역은 **메타 데이터 / 정책** — 외래키 의존 X + 데이터 흐름 자체에 영향 X. 본 사이클 자유롭게 검증.

### 각 문서 적용
- 01 security: §6 사용자 관리 (peer multi 등록 자연 사이클), 나머지 인증 흐름
- 02 datasource: §3 CRUD (datasource 등록) **SKIP**, §4 (연결 테스트) ~ §7 (테이블/컬럼) 핵심
- 03 bojo: §1~3 인프라 검증, **§4 E2E 파이프라인 + §5 conditions + §6 추적 + §7 Schedule (등록/자동 실행) + §8 Retention (등록/cleanup)** 이 본 사이클 핵심
- 04 others: 동일
- 05 api-collect: §3 endpoint CRUD **SKIP**, §4 (실행기 호출) + §5 LOOKUP + **§7 Schedule (등록/자동 실행)** 핵심
- 06 api-provide: §3 operation CRUD **SKIP**, §4 (META 호출) + §5 (CUSTOM B4) + §6 (외부 사용자) 핵심
- 07 monitoring: cross-cutting 전부 실행 영역 — **Schedule + Retention 자유 / 추적 / 이력**
- 08 통합: 운영자 흐름 그대로 — Step 8 Schedule + Retention 자유 등록/실행/삭제 사이클 검증

→ 별 사이클 등록 검증 (datasource / agent / endpoint / operation) 은 1차 반입 후 운영 환경에서 신규 자산 등록 시 별도 사이클로.

---

## 10. 참고 — 기존 bojo-test 의 좋은 패턴 (다른 문서에도 적용)

- ✅ "공통 테스트 규칙" 섹션 (3단계 추적 검증)
- ✅ 시스템 구성 표 (포트/DB)
- ✅ Step 별 추적 검증 테이블 (Agent / target / source / 매칭모드)
- ✅ Oracle 특이 케이스 / 알려진 허점 별도 섹션
- ✅ 부록 — 빌드 명령어
