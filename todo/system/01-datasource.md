# 1. Datasource 관리

> **요구사항**: 여러 기관/업체의 DB 연결 정보를 중앙에서 등록·관리하고,
> 연결 상태를 확인하며, 인증 정보를 안전하게 암호화하여 보관한다.

## 상태: 개발완료

---

## Datasource CRUD [Backend]
- [x] 등록 API (DB타입, 호스트, 포트, 인증정보, Zone 설정)
- [x] 수정 API
- [x] 삭제 API
- [x] 목록 조회 / 활성 목록 / 간단 조회 (Agent 등록용)
- [x] 단건 조회 + 연결 정보 조회 (자격증명 포함)
- [x] Multi-DB 타입 지원 (PostgreSQL / MySQL / Oracle / MariaDB / SQL Server) ※ 배포환경 Tibero는 Oracle로 대체 구현
- [x] Jasypt 암호화 (ENC 암호문 저장, 복호화는 Agent에서)

## 연결 테스트 [Backend]
- [x] 저장된 Datasource 연결 테스트 (Proxy 경유 또는 직접)
- [x] 저장 전 연결 테스트 (등록 폼에서 바로 테스트)
- [x] Zone 기반 분기 — zone 있으면 Proxy 경유, 없으면 직접 연결

## 테이블/컬럼 관리 [Backend]
- [x] 실제 DB에서 테이블 검색 (Proxy 경유)
- [x] 실제 DB에서 컬럼 검색
- [x] 테이블 등록 (컬럼 포함)
- [x] 컬럼 갱신
- [x] 테이블 삭제
- [x] 테이블 alias 전역 조회
- [x] sourceRef lookup 데이터 조회
- [x] Oracle remarksReporting 활성화 (comment 수집)

## DB 등록/수정 화면 [Frontend]
- [x] DB 타입 선택 (5종 드롭다운)
- [x] 연결 정보 입력 (호스트, 포트, DB명, 사용자, 비밀번호)
- [x] Zone 선택 (EXTERNAL / DMZ / INTERNAL_COMMON / INTERNAL_SERVICE)
- [x] 등록 전 연결 테스트 버튼
- [x] 수정 모드: 기존 정보 로드 (비밀번호는 보안상 비움)

## DB 목록 화면 [Frontend]
- [x] 목록 테이블 (ID, 이름, DB타입 배지, Zone, 호스트, 포트, 활성 상태)
- [x] 작업 버튼: 테이블관리, 연결테스트, 수정, 삭제

## 테이블 관리 모달 [Frontend]
- [x] 선택된 Datasource의 테이블 목록 (실제 DB에서 검색)
- [x] 테이블별 컬럼 정보 (이름, 데이터타입, comment/alias)
- [x] 컬럼 재수집 기능
- [x] 미등록 테이블 상단 정렬
- [x] description 표시 통일 (tableAlias → description)

## DB 연결 프록시 [Proxy]
- [x] infolink-proxy-dmz (8083) — 외부 DB 연결 프록시
- [x] infolink-proxy-internal (8093) — 내부 DB 연결 프록시
- [x] ProxyDataSourceService (커넥션 풀 캐시 + Orchestrator 폴백)
- [x] HikariCP 풀 하드닝 (maxPool=10, timeout=10s, leak=60s)
- [x] API Key 인증
- [x] Oracle 지원 (ojdbc8)

---

## 등록 현황

### 외부 업체 DB (DMZ, 10개)

| # | 업체 | DB 타입 | 포트 | DB명 |
|---|------|---------|------|------|
| 1 | 대전 | PostgreSQL | 29000 | daejeon |
| 2 | 바이텍 | PostgreSQL | 29000 | bytek |
| 3 | 충남 | PostgreSQL | 29000 | chungnam |
| 4 | 근산 | PostgreSQL | 29000 | keunsan (대문자 테이블/컬럼) |
| 5 | 인포월드(로컬) | MySQL | 29010 | infoworld_local |
| 6 | 인포월드(서울) | MySQL | 29010 | infoworld_seoul |
| 7 | 하이드로넷(아라) | MySQL | 29010 | hydronet_ara |
| 8 | 하이드로넷(IDC) | MySQL | 29010 | hydronet_idc |
| 9 | 하이드로넷(경남) | MySQL | 29010 | hydronet_kyungnam |
| 10 | 하이드로넷(원주) | MySQL | 29010 | hydronet_wonju |

### 내부 시스템 DB

| # | 용도 | DB 타입 | 포트 | DB명 | 비고 |
|---|------|---------|------|------|------|
| 1 | Orchestrator | PostgreSQL | 29001 | orchestrator | 중앙 관리 |
| 2 | Agent IF (DMZ) | PostgreSQL | 29001 | dev | IF_RSV/IF_SND, DMZ Target |
| 3 | API Collector | PostgreSQL | 29001 | api_collector | 독립 DB |
| 4 | Internal Oracle | Oracle XE | 29004 | XEPDB1 | 내부망 GIMS 대체 |
| 5 | 새올 Oracle | Oracle XE | 29005 | XEPDB1 | 새올 Tibero 대체 |
