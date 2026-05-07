# 01 — Datasource 관리 기능 테스트 문서

> 검증 baseline: `stable-2026-05-07-rename` (commit: dad8a1b)
> 통과 시: `stable-2026-05-07` 신규 tag 박음 (이름 보류)
> 작성일: 2026-05-07

---

## 공통 검증 규칙

- claude API 호출 → 1차 확인. **사용자가 직접 프론트(`localhost:3000/datasources`)에서 같은 흐름 확인** 후에만 통과.
- 단계마다 사용자 OK 후 다음 진입.

---

## 목차
1. [시스템 구성](#1-시스템-구성)
2. [사전 준비](#2-사전-준비)
3. [Datasource CRUD](#3-datasource-crud)
4. [연결 테스트 (저장 전 / 저장 후)](#4-연결-테스트-저장-전--저장-후)
5. [Proxy 패스스루 / 암호문 통신](#5-proxy-패스스루--암호문-통신)
6. [Zone 기반 분기](#6-zone-기반-분기)
7. [테이블/컬럼 관리](#7-테이블컬럼-관리)
8. [Multi-DB 타입 지원 (5종)](#8-multi-db-타입-지원-5종)
9. [프론트엔드 UI](#9-프론트엔드-ui)
10. [알려진 허점 / 주의사항](#10-알려진-허점--주의사항)

---

## 1. 시스템 구성

### 1-1. 모듈 구성
| 역할 | 모듈 | 포트 | 비고 |
|---|---|:-:|---|
| Datasource CRUD | infolink-orchestrator-backend | 8080 | DB 등록/수정/삭제, ENC 저장 |
| 자격증명 패스스루 (DMZ) | infolink-proxy-dmz | 8083 | Agent 가 경유해서 connection-info 받음 |
| 자격증명 패스스루 (Internal) | infolink-proxy-internal | 8093 | 동일 — Internal Agent 용 |
| 자격증명 소비 | infolink-agent-* | 8082 / 8085 / 8092 | Proxy 경유로 받은 ENC 자체 복호화 후 JdbcTemplate 생성 |
| 운영자 UI | infolink-orchestrator-frontend | 3000 | `/datasources` 화면 |

### 1-2. 자격증명 흐름
```
[운영자] Frontend /datasources → Backend `/api/datasources` (CRUD, ENC 저장)
                                            ↓
                                     PG `datasource` 테이블 (ENC 암호문)

[Agent 실행] Agent → Proxy `/api/datasources/{id}/connection-info`
              ↓
       Proxy 가 Backend `/api/datasources/{id}/connection-info` 호출
              ↓
       Backend 응답 = ENC 암호문 그대로 (복호화 안 함, 패스스루)
              ↓
       Agent 가 로컬에서 jasypt 복호화 → HikariCP DataSource 생성
```

> **핵심**: Backend 외 다른 모듈에서 평문 자격증명 안 다룸. Backend 만 ENC 암호문 보관 + Agent 만 메모리 복호화.

### 1-3. DB 자체 (Backend 가 관리하는 DB)
- Orchestrator PG (29001 / orchestrator)
- 테이블: `datasource` (id / name / type / host / port / db_name / username_enc / password_enc / zone / is_active / ...)

### 1-4. Zone 정의
| Zone | 의미 | 호출 경로 |
|---|---|---|
| `EXTERNAL` | 외부 사용자 시스템 (10 업체) | DMZ Agent (Proxy 경유) |
| `DMZ` | DMZ 자체 PG (29001 / dev) | DMZ Agent / Proxy |
| `INTERNAL_COMMON` | 내부망 공용 인프라 (Oracle 29004 등) | Internal Agent (Proxy 경유) |
| `INTERNAL_SERVICE` | 내부 서비스 (새올 Oracle 29005 등) | Internal Agent (Proxy 경유) |

---

## 2. 사전 준비

### 2-1. 서비스 기동
- [ ] backend (8080)
- [ ] proxy-dmz (8083)
- [ ] proxy-internal (8093)
- [ ] frontend (3000)
- [ ] (07 security 통과 후) JWT cookie 보관 — 본 테스트 모든 호출에 동반

### 2-2. DB 컨테이너 가동 확인
| 컨테이너 | 포트 | 비고 |
|---|:-:|---|
| Orchestrator PG | 29001 | `orchestrator` + `dev` DB |
| 외부 PG | 29000 | daejeon, bytek, chungnam, keunsan |
| 외부 MySQL | 29010 | infoworld_*, hydronet_* (6개) |
| Internal Oracle | 29004 | XEPDB1 (k1m/1111) |
| 새올 Oracle | 29005 | XEPDB1 (k1m/1111) |
| API Provider PG | 29006 | api_provider (별 모듈 의존) |

확인:
```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "29(000|001|004|005|006|010)"
```

### 2-3. 등록된 Datasource 목록 (사전 데이터 — 5/6 시점)
- [ ] `GET /api/datasources` 호출 시 외부 10 + 내부 5+ 모두 표시 정합

---

## 3. Datasource CRUD

### 3-1. 목록 조회
```bash
curl -s -b /tmp/cookies.txt http://localhost:8080/api/datasources | jq | head -40
```
- [ ] HTTP 200, 배열 응답
- [ ] 각 항목 `password_enc` 포함 X (또는 마스킹) — 외부 노출 방지
- [ ] 사용자 검증: 프론트 `/datasources` → 같은 목록 표시 (DB 타입 배지 / Zone / 호스트:포트 / 활성 상태)

### 3-2. 단건 조회
```bash
curl -s -b /tmp/cookies.txt http://localhost:8080/api/datasources/dmz | jq
```
- [ ] HTTP 200, `{id, name, type, host, port, dbName, username, zone, isActive, ...}`
- [ ] 사용자 검증: 프론트 행 클릭 → 수정 모달 → 같은 정보 표시 (비번 빈 칸)

### 3-3. 등록 (`POST /api/datasources`)
```bash
curl -s -b /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X POST http://localhost:8080/api/datasources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-ds",
    "type": "POSTGRESQL",
    "host": "localhost",
    "port": 29000,
    "dbName": "daejeon",
    "username": "k1m",
    "password": "1111",
    "zone": "EXTERNAL",
    "isActive": true
  }'
```
- [ ] HTTP 201
- [ ] DB `datasource` 테이블에 새 row + `password_enc` 박혀있음 (평문 X — `ENC(...)` 형식 또는 jasypt encoded)
- [ ] 사용자 검증: 프론트 "+ 새 DB" → 같은 입력 → 목록에 즉시 추가됨

### 3-4. 수정 (`PUT /api/datasources/{id}`)
- [ ] 비번 빈 값 시 → 기존 비번 보존
- [ ] 비번 입력 시 → 새 ENC 로 교체 + DB 갱신
- [ ] 사용자 검증: 프론트 수정 모달 → 비번 빈 채로 저장 → 연결 테스트 통과
- [ ] 비번 새로 입력 → 저장 → 연결 테스트 통과

### 3-5. 삭제 (`DELETE /api/datasources/{id}`)
- [ ] HTTP 204
- [ ] `is_active=false` (soft delete) 또는 row 자체 제거 — 어느 정책인지 확인
- [ ] 참조 중인 Datasource 삭제 시도 (Agent.targetDatasourceId 가 가리키는 경우) → 차단 또는 경고

### 3-6. 활성 목록 (`GET /api/datasources/active`)
- [ ] `is_active=true` 만 반환 — Agent 등록 화면의 드롭다운에 사용

### 3-7. 간단 조회 (`GET /api/datasources/simple`)
- [ ] 자격증명 / 호스트 등 민감 정보 제외, id+name+type+zone 만 반환

---

## 4. 연결 테스트 (저장 전 / 저장 후)

### 4-1. 저장된 Datasource 연결 테스트 (`POST /api/datasources/{id}/test-connection`)
```bash
curl -s -b /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X POST http://localhost:8080/api/datasources/dmz/test-connection
```
- [ ] HTTP 200 + `{success: true, message: "OK"}` 또는 `{success: false, error: "..."}`
- [ ] zone 기반 라우팅:
  - DMZ / EXTERNAL → proxy-dmz 경유
  - INTERNAL_* → proxy-internal 경유
- [ ] zone 없으면 backend 직접 연결
- [ ] 사용자 검증: 프론트 "연결테스트" 버튼 → 토스트 또는 모달로 결과 표시

### 4-2. 저장 전 연결 테스트 (`POST /api/datasources/test-connection`)
```bash
curl -s -b /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X POST http://localhost:8080/api/datasources/test-connection \
  -H "Content-Type: application/json" \
  -d '{
    "type": "POSTGRESQL",
    "host": "localhost",
    "port": 29000,
    "dbName": "daejeon",
    "username": "k1m",
    "password": "1111",
    "zone": "EXTERNAL"
  }'
```
- [ ] HTTP 200 + 연결 성공 또는 실패 메시지
- [ ] 사용자 검증: 등록 폼에서 "연결 테스트" 버튼 → 입력값 그대로 검증

### 4-3. 잘못된 자격증명
- [ ] password 틀림 → `{success: false, error: "..."}`
- [ ] host 도달 불가 → `{success: false, error: "Connection refused..."}`
- [ ] db명 없음 → 적절한 에러

### 4-4. Oracle 특이
- [ ] type=ORACLE / 29004 / XEPDB1 / k1m / 1111 → 성공
- [ ] JDBC URL 형식 = `jdbc:oracle:thin:@//host:port/service`

---

## 5. Proxy 패스스루 / 암호문 통신

### 5-1. Proxy → Backend 패스스루 (Proxy 자체 호출)
```bash
# proxy-dmz → backend connection-info 조회
curl -s -H "X-API-Key: <proxy-api-key>" \
  http://localhost:8083/api/datasources/dmz/connection-info | jq
```
- [ ] HTTP 200
- [ ] 응답에 `password` 필드가 ENC 암호문 (평문 X)
- [ ] Proxy 로그: `[Proxy] Passthrough connection-info request: dmz`
- [ ] Backend 로그: `connection-info served: dmz` (X-API-Key soft-mode 통과)

### 5-2. Agent → Proxy 호출 (실제 파이프라인 시나리오)
- [ ] DMZ Agent (8082) 가 파이프라인 실행 시 Proxy 8083 의 `/api/datasources/{id}/connection-info` 호출
- [ ] Agent 로그: `Fetched datasource from Proxy: dmz (localhost:29001)` (호스트:포트가 보임 = 복호화 성공)
- [ ] Internal Agent (8092) 가 Proxy 8093 경유 동일 흐름

### 5-3. Backend 직접 호출 fallback **제거됨** (3/17~3/18)
- [ ] Agent yml 또는 코드에서 backend `/api/datasources` 직접 호출 경로 없음
- [ ] Proxy 미기동 시 Agent 즉시 실패 (재시도 X)

### 5-4. Proxy 미기동 시 실패
- [ ] proxy-dmz 정지 → bojo-dmz 파이프라인 실행 → "Proxy 에서 datasource 해석 실패: dmz" 즉시 예외
- [ ] proxy-internal 정지 → bojo-internal 파이프라인 실행 → 동일 패턴

### 5-5. URL 통일 (3/17 commit 정합)
- [ ] Proxy → Backend 경로: `GET /api/datasources/{id}/connection-info` ✅
- [ ] 옛 경로 `/api/datasource/connection-info/{id}` → 404 ✅

---

## 6. Zone 기반 분기

### 6-1. zone=EXTERNAL → proxy-dmz
- [ ] daejeon (EXTERNAL) 연결 테스트 → proxy-dmz 경유 로그
- [ ] proxy-dmz 정지 시 실패

### 6-2. zone=DMZ → proxy-dmz
- [ ] dmz (DMZ PG 29001) 연결 테스트 → proxy-dmz 경유

### 6-3. zone=INTERNAL_COMMON → proxy-internal
- [ ] internal Oracle (INTERNAL_COMMON) 연결 테스트 → proxy-internal 경유 로그

### 6-4. zone=INTERNAL_SERVICE → proxy-internal
- [ ] saeol Oracle 등 (INTERNAL_SERVICE) → proxy-internal 경유

### 6-5. zone 없음 → backend 직접
- [ ] backend 자체 PG 같은 지역 연결은 직접 (proxy 경유 없음)

---

## 7. 테이블/컬럼 관리

### 7-1. 실제 DB 에서 테이블 검색 (`GET /api/datasources/{id}/tables`)
```bash
curl -s -b /tmp/cookies.txt http://localhost:8080/api/datasources/dmz/tables | jq | head -20
```
- [ ] HTTP 200, 배열
- [ ] 각 항목: `{tableName, alias?, registered}` — 등록된 항목과 미등록 구분
- [ ] 미등록 테이블 상단 정렬 (UI 정합)
- [ ] 사용자 검증: 프론트 "테이블관리" 모달 → 같은 목록 표시

### 7-2. 테이블 등록 (`POST /api/datasources/{id}/tables`)
- [ ] 컬럼 자동 수집 (실 DB 에서 information_schema 또는 user_tab_columns)
- [ ] 등록 후 응답 = 컬럼 리스트 포함
- [ ] 사용자 검증: 프론트 "테이블 추가" → 컬럼 자동 채워짐

### 7-3. 컬럼 재수집 (`POST /api/datasources/{id}/tables/{tableName}/columns/refresh`)
- [ ] 실 DB 의 컬럼 변경 후 재수집 → 신규 컬럼 추가 / 삭제 컬럼 제거
- [ ] 사용자 검증: 프론트 "컬럼 재수집" 버튼 → 결과 즉시 표시

### 7-4. Oracle remarksReporting (comment 수집)
- [ ] type=ORACLE 의 컬럼 수집 시 `COMMENT ON COLUMN ...` 데이터까지 받아옴
- [ ] alias 또는 description 으로 표시
- [ ] **TC_GD000100/000002** (5/6 표준화) 의 한글명 + 도메인 표기 정합

### 7-5. 테이블 alias 전역 조회 (`GET /api/table-aliases`)
- [ ] 전 Datasource 의 alias 한 번에 조회 — 운영자 메뉴/UI 에서 사용
- [ ] 사용자 검증: 프론트 어디서 alias 보여주는지 (모니터링/실행 상세) → 같은 정보

### 7-6. sourceRef lookup (`GET /api/source-refs/lookup?...`)
- [ ] `[D:1018:103:145]` 형식의 source_refs 디코딩 — Datasource ID + Table ID + PK
- [ ] 결과 = `{datasourceName, tableName, pkValue}` 같은 매핑
- [ ] 사용자 검증: 추적 화면에서 "원본 보기" 클릭 시 활용

---

## 8. Multi-DB 타입 지원 (5종)

| DB 타입 | 드라이버 | 테스트 대상 |
|---|---|---|
| PostgreSQL | postgresql | daejeon (EXTERNAL) / dmz (DMZ) |
| MySQL | mysql-connector-j | infoworld-local (EXTERNAL) |
| Oracle | ojdbc8 19.3.0.0 | internal (INTERNAL_COMMON) / saeol (INTERNAL_SERVICE) |
| MariaDB | mysql-connector-j (호환) | (해당 등록 없음 — 드라이버만 검증) |
| SQL Server | mssql-jdbc | (해당 등록 없음 — 드라이버만 검증) |

### 8-1. PostgreSQL
- [ ] 등록 + 연결 테스트 + 테이블 검색 + 컬럼 수집 모두 정상

### 8-2. MySQL
- [ ] 등록 + 연결 테스트
- [ ] 백틱 인용 처리 (테이블/컬럼명에 예약어 포함 시)
- [ ] AUTO_INCREMENT 컬럼 인식

### 8-3. Oracle
- [ ] type=ORACLE 등록 시 JDBC URL 자동 생성: `jdbc:oracle:thin:@//host:port/service`
- [ ] HikariCP `connectionTestQuery = SELECT 1 FROM DUAL`
- [ ] 대문자 테이블/컬럼 인식 (keunsan 패턴)
- [ ] proxy-internal 의 ojdbc8 의존 정합 (추적 조회 시 직접 연결)

### 8-4. MariaDB / SQL Server (드라이버만)
- [ ] build.gradle 의존성 존재 확인 (mssql-jdbc, mariadb-java-client 또는 mysql-connector-j 호환)
- [ ] 본 테스트는 driver 로드만 검증 (실 DB 미준비)

---

## 9. 프론트엔드 UI

### 9-1. `/datasources` 목록 화면
- [ ] 테이블 = ID / 이름 / DB타입 배지 / Zone / 호스트:포트 / 활성 / 작업
- [ ] DB 타입 배지 색상: PG(파랑) / MySQL(주황) / Oracle(빨강) / SQL Server(녹색) / MariaDB(보라) — 디자인 정합 확인
- [ ] 작업 버튼: 테이블관리 / 연결테스트 / 수정 / 삭제

### 9-2. 등록/수정 모달
- [ ] DB 타입 드롭다운 (5종)
- [ ] 호스트 / 포트 / DB명 / 사용자 / 비밀번호 입력
- [ ] Zone 드롭다운 (4종)
- [ ] **저장 전 연결 테스트** 버튼 — 모달 안에서 즉시 검증
- [ ] **수정 모드: 비밀번호 빈 칸** (보안 — 기존 ENC 보존)

### 9-3. 테이블 관리 모달
- [ ] Datasource 선택 시 자동으로 실 DB 의 테이블 검색
- [ ] 미등록 테이블 상단 정렬
- [ ] 테이블별 컬럼 토글 (확장/축소)
- [ ] 컬럼 = 이름 / 데이터타입 / comment(alias) / description
- [ ] "컬럼 재수집" 버튼 정상 동작

### 9-4. 메뉴 구조
- [ ] sidebar → "DB 관리" 메뉴 → `/datasources`
- [ ] 페이지 진입 시 권한 가드 통과 (cookie 정상)

---

## 10. 알려진 허점 / 주의사항

### 10-1. ENC 키 회전 (별 사이클)
- 현재 jasypt password = `${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}` (개발 default). 운영 시 환경변수 주입 + dev default 제거 (5/7 dev_plan deploy-package §2.2 참조)
- 주의: 현재 ENC 값 = 위 default key 로 암호화됨. 운영 시 새 key 로 일괄 재암호화 필요

### 10-2. password_enc 노출 위험
- 목록/단건 조회 응답에 `password_enc` 평문 포함 시 보안 위험. 마스킹 또는 미포함 정합 확인
- 본 테스트 §3-1, 3-2 에서 점검

### 10-3. Proxy 미기동 = Agent 즉시 실패
- Backend 직접 fallback 제거됨 (3/17). Proxy 우선 기동 절차 운영 매뉴얼에 명시 필요

### 10-4. 외부 DB 의 컬럼 추가/삭제 시 자동 동기화 X
- 등록 시점의 컬럼만 보관. 외부 DB 변경 시 "컬럼 재수집" 수동 호출 필요

### 10-5. Zone 누락 시 직접 연결
- zone null/empty → backend 직접 jdbc connection. 운영 시 zone 누락 검증 강화 (현재 등록 폼 default = EXTERNAL)

### 10-6. Oracle 한글 comment 인코딩
- TC_GD000100/000002 의 한글 comment 가 일부 환경에서 깨질 수 있음 (NLS_LANG 설정 의존). proxy-internal yml 의 oracle properties 점검

---

## 11. Baseline 태그 갱신

```
실행 시작 baseline: stable-2026-05-07-rename (commit dad8a1b)
검증 통과 일시: 2026-05-XX
신규 stable tag: stable-2026-05-XX (이름 보류)
신규 tag commit: ?????? (실행 시점 main HEAD)
```
