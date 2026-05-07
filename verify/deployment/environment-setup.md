# Environment Setup — 서버 / 네트워크 / 시스템 환경

실배포 서버의 인프라 수준 준비사항. **`config-replacement.md` 보다 아래 계층** — 운영체제/네트워크 수준.

## 1. 서버 / 호스트

### 1.1 호스트 할당 (TODO — 실배포 인프라 확정 시 채움)
| 역할 | 호스트명 | 포트 | 망 |
|------|---------|-----|-----|
| Orchestrator backend | | 8080 | 내부망 |
| Frontend | | 3000 | 내부망 (운영자 접근) |
| Proxy DMZ | | 8083 | DMZ |
| Proxy Internal | | 8093 | 내부망 |
| Agent DMZ (bojo) | | 8082 | DMZ |
| Agent Others (DMZ) | | 8085 | DMZ |
| Agent Internal (bojo-internal) | | 8092 | 내부망 |
| Agent Provide | | 8096 | 내부망 |
| API Collector DMZ | | 8084 | DMZ |
| API Collector Internal | | 8094 | 내부망 |
| API Provider | | 8095 | **외부 공개** (API 소비자용) |

### 1.2 JVM / OS 설정
- [ ] Java 버전 (Agent / Orchestrator 각각 확인 — Java 17 기준 추정)
- [ ] Node 버전 (Frontend — Next.js 빌드 기준)
- [ ] 시스템 타임존: `Asia/Seoul`
- [ ] 시스템 로케일: `ko_KR.UTF-8`
- [ ] 파일 인코딩: UTF-8
- [ ] JVM heap 사이즈 (각 서비스별 권장치 확정 필요)
- [ ] JVM GC 옵션

## 2. 네트워크 / 방화벽

### 2.1 망 분리 기본 구조
```
   인터넷 ──▶ API Provider (8095, 외부 공개)
                    │
   외부 원본DB ─▶ DMZ ─▶ Proxy Internal ─▶ 내부망 Agent/Orchestrator ─▶ GIMS
                (Agent DMZ + Collector)            (bojo-internal / provide / Orchestrator)
```

### 2.2 방화벽 규칙 (필요 개방)
- [ ] 외부 → API Provider (HTTPS)
- [ ] 외부 원본 DB → DMZ Agent (해당 DB 포트)
- [ ] DMZ ↔ Internal 단방향 / Proxy 경유만 허용
- [ ] Internal → GIMS (OneLogin / Oracle 등)
- [ ] 관리자 → Orchestrator UI (내부망 접근)
- [ ] 모니터링 수집 서버 → 각 서비스 (헬스체크 / 메트릭)

### 2.3 HTTPS / 인증
- [ ] API Provider HTTPS 인증서
- [ ] 내부망 Orchestrator HTTPS (내부 CA 기반)
- [ ] 인증서 만료 알람 등록

## 3. DB 인프라

### 3.1 DB 인스턴스 준비
- [ ] 운영 Oracle (GIMS 본 DB 접근 경로)
- [ ] 운영 Tibero (새올)
- [ ] 운영 PostgreSQL (Orchestrator / Agent IF / API Collector / API Provider 용)
  - 단일 인스턴스 다중 DB 로 갈지 / 분리 인스턴스로 갈지 **확정 필요**

### 3.2 스키마 / 초기 데이터
- [ ] DDL 배포 (`scripts/ddl/saeol-tibero/` / `internal-oracle/` / `dmz-pg/`)
- [ ] Orchestrator DataSource 등록 초기 데이터 — 운영 DB 주소/계정 주입
- [ ] Agent 설정 초기 데이터 (필요 시)
- [ ] 인덱스 확인 (특히 executionId 인덱스 6 개)

### 3.3 백업 / 복구
- [ ] 백업 주기 / 보관 정책
- [ ] PITR 가능 여부
- [ ] 복구 드릴 일정

## 4. 배포 / 실행 환경

### 4.1 배포 방식 (확정 필요)
- [ ] 수동 배포 (systemd / init 스크립트)
- [ ] 컨테이너 (Docker / Kubernetes)
- [ ] CI/CD 파이프라인 (GitHub Actions 등)

### 4.2 프로세스 관리
- [ ] 자동 재시작 설정
- [ ] 로그 로테이션
- [ ] 메모리 / CPU 리소스 제한

## 5. 확정 필요 항목 (TODO)

- [ ] 각 호스트 IP / DNS 확정
- [ ] 배포 방식 확정 (수동 / 컨테이너)
- [ ] DB 인스턴스 전략 확정 (단일 vs 분리)
- [ ] 백업 정책 확정
- [ ] 모니터링 / 로그 수집 스택 확정 (→ `monitoring-setup.md`)
