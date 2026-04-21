# sync-agent-provide 테스트 계획

> 작성일: 2026-04-21
> 대상 모듈: sync-agent-provide (8096)
> 연관 모듈: api-provider (8095), Orchestrator (8080), Proxy Internal (8093)

---

## Phase 1: 모듈 기동 테스트

### 1-1. 빌드
| 항목 | 확인 |
|------|------|
| `./gradlew clean build -x test` 성공 | |
| common JAR 의존성 정상 로드 | |

### 1-2. 기동
| 항목 | 확인 |
|------|------|
| `./gradlew bootRun` → 8096 포트 리스닝 | |
| PG 29006 연결 성공 (ddl-auto로 테이블 생성) | |
| `execution`, `sync_log` 테이블 PG 29006에 생성 확인 | |
| YAML 로드 로그: `Agent 설정 로드: provide-rgetnpmms01 (type=LOADER)` | |
| `/actuator/health` → 200 OK | |

### 1-3. Orchestrator 연동
| 항목 | 확인 |
|------|------|
| Orchestrator 프론트 → Agent 관리 → URL `http://localhost:8096` 등록 | |
| auto-discover → provide 파이프라인 목록 표시 | |

---

## Phase 2: Type A 단순 복사 — RGETNPMMS01 (개발 DB에 존재)

> 소스: Oracle 29004 `RGETNPMMS01`
> 타겟: PG 29006 `api_prv_rgetnpmms01`

### 2-0. 사전 준비
| 항목 | 확인 |
|------|------|
| Oracle 29004에 RGETNPMMS01 데이터 존재 확인 (`SELECT COUNT(*)`) | |
| RGETNPMMS01에 추적 컬럼 추가 (ALTER TABLE — LINK_STATUS DEFAULT 'PENDING' 등) | |
| PG 29006에 `api_prv_rgetnpmms01` 테이블 자동 생성 확인 (ddl-auto) | |

### 2-1. 실행
| 항목 | 확인 |
|------|------|
| Orchestrator에서 `provide-rgetnpmms01` 실행 트리거 | |
| 로그: Oracle 조회 건수 출력 | |
| 로그: PG UPSERT 건수 출력 | |
| 로그: SyncLog 저장 완료 | |

### 2-2. 데이터 검증
| 항목 | 확인 |
|------|------|
| PG 29006 `api_prv_rgetnpmms01` 데이터 건수 = Oracle 소스 건수 | |
| `execution_id` 컬럼에 실행 ID 기록 확인 | |
| `source_refs` 컬럼에 원본 참조 기록 확인 | |
| `updated_at` 컬럼에 타임스탬프 기록 확인 | |
| Oracle `RGETNPMMS01`의 `LINK_STATUS` → `SUCCESS` 갱신 확인 | |

### 2-3. 재실행 (증분/멱등성)
| 항목 | 확인 |
|------|------|
| 동일 데이터로 재실행 → PG 건수 변동 없음 (UPSERT 멱등) | |
| Oracle LINK_STATUS 이미 SUCCESS인 건은 스킵 또는 재처리 | |
| SyncLog에 2번째 실행 이력 기록 | |

### 2-4. Orchestrator 이력 확인
| 항목 | 확인 |
|------|------|
| Orchestrator 대시보드에서 실행 이력 표시 | |
| readCount / writeCount 정상 표시 | |
| 데이터 추적 (trace) 동작 여부 | |

---

## Phase 3: api-provider 연동 — E2E 관통 테스트

> 제공 테이블에 데이터가 있는 상태에서 api-provider로 외부 API 노출

### 3-1. 오퍼레이션 등록
| 항목 | 확인 |
|------|------|
| 프론트 `/api-provide/new` → Datasource 선택 (PG 29006) | |
| 테이블 목록에 `api_prv_rgetnpmms01` 표시 | |
| 컬럼 자동 로드 + 체크박스 선택 | |
| WHERE 파라미터 설정 (예: 지역코드 등) | |
| 저장 성공 | |

### 3-2. 테스트 호출
| 항목 | 확인 |
|------|------|
| 상세 → 테스트 탭 → 파라미터 입력 → 실행 | |
| 결과 데이터 표시 (JSON) | |
| 생성된 SQL 미리보기 확인 | |
| 페이징 동작 (page=1, pageSize=10) | |

### 3-3. 외부 API 호출
| 항목 | 확인 |
|------|------|
| 오퍼레이션 활성 (게시) | |
| `GET /api/provide/{operationId}?apiKey=...` 호출 → 200 + 데이터 | |
| 비활성 오퍼레이션 → 404 | |
| 잘못된 API Key → 401/403 | |
| 호출 이력 탭에 기록 표시 | |

---

## Phase 4: Type A 추가 테이블 — DDL 기반 (개발 DB에 없는 소스)

> 소스: Oracle 29004에 DDL로 생성한 샘플 테이블
> 대상: TM_GD30301 (A2), TM_GD00203 (A3) 등

### 4-0. 사전 준비
| 항목 | 확인 |
|------|------|
| DDL 스크립트로 Oracle 29004에 소스 테이블 생성 | |
| 샘플 데이터 INSERT (최소 10건) | |
| LINK_STATUS 등 추적 컬럼 포함 확인 | |

### 4-1. 실행 + 검증 (Phase 2와 동일 패턴)
| 항목 | 확인 |
|------|------|
| YAML 추가 → 기동 시 자동 로드 | |
| 실행 → Oracle→PG 복사 성공 | |
| PG 건수 = Oracle 건수 | |
| api-provider에서 오퍼레이션 등록 → 외부 API 호출 성공 | |

---

## Phase 5: Type B 전처리 — DDL 기반

> 소스: Oracle 29004에 DDL로 생성한 복수 테이블
> 대상: TM_GD30302 PIVOT (B1/B2) 우선

### 5-0. 사전 준비
| 항목 | 확인 |
|------|------|
| TM_GD30302 + TM_GD30301 + TM_GD10001 DDL + 샘플 데이터 | |
| PIVOT SQL이 Oracle에서 정상 실행되는지 수동 확인 | |

### 5-1. 전처리 Step 실행
| 항목 | 확인 |
|------|------|
| 실행 → Oracle PIVOT SQL 조회 성공 | |
| PG `api_prv_tm_gd30302`에 flat 결과 UPSERT | |
| 컬럼 수 = PIVOT 결과 컬럼 수 | |
| SyncLog에 sourceTables/targetTables 정확히 기록 | |

### 5-2. api-provider 연동
| 항목 | 확인 |
|------|------|
| 전처리 결과 테이블로 오퍼레이션 등록 | |
| 외부 API 호출 → 정제된 데이터 응답 | |

---

## 에러 케이스

| 케이스 | 기대 결과 |
|--------|---------|
| Oracle 소스 테이블 없음 | Step FAILED + 에러 메시지 로그 + SyncLog 기록 |
| Oracle 연결 실패 (Proxy 다운) | Step FAILED + 연결 오류 메시지 |
| PG 29006 연결 실패 | Step FAILED + 연결 오류 메시지 |
| 소스 데이터 0건 | Step SKIPPED + SyncLog readCount=0 |
| UPSERT 중 일부 실패 | 부분 성공 처리 (failedCount 기록, 나머지 계속) |
| 동일 실행 재시도 | 멱등 — 데이터 중복 없음 |

---

## 전체 관통 시나리오 요약

```
[1] sync-agent-provide 기동 → PG 29006에 테이블 자동 생성
[2] Orchestrator에서 Agent 등록 (8096)
[3] provide-rgetnpmms01 실행 트리거
[4] Oracle RGETNPMMS01 → PG api_prv_rgetnpmms01 복사
[5] api-provider 프론트에서 api_prv_rgetnpmms01 오퍼레이션 등록
[6] 외부 API 호출 → 데이터 응답 확인
[7] 호출 이력 확인
```
