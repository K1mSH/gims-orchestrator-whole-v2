# GIMS Orchestrator v2 - 작업 메모리
<!-- 최종 동기화: 2026-02-20 09:05 -->

## 작업 규칙
- 한글 사용 (주석, 터미널 설명, 변수명 혼용 OK)
- 작업 범위: `orchestrator_v2/` 내에서만 진행
- 빌드/테스트: **Gradle** 사용 (`./gradlew clean build -x test`)
- lib(sync-agent-common) 수정 시 → 참조 프로젝트(sync-agent-bojo)에 JAR 복사 필수
- dev_logs 작성: 코드 수정+테스트 완료 후, 수정 목적 기술
- 구조/UI 문서(docs/)는 개발방향 문서 → 보수적 수정, 애매하면 질문
- "테스트 해봐" = 앱 실행 + 로그 모니터링 + 피드백 루프
- MEMORY 관리: 임의 수정 안함, 필요시 건의 → 사용자 승인 후 반영
- 코드 수정은 반드시 dev_plan에 날짜별 디렉토리 안에서 [작업주제].md 로 플랜을 짜고, 검수 이후 허가가 떨어지면 수정을 할 수 있도록 한다
- 테이블 생성시에는 comment도 포함해서 작성토록 한다

## 프로젝트 구조
```
orchestrator_v2/
├── sync-agent-common/    # 공통 모듈 (JAR 라이브러리)
├── sync-agent-bojo/      # 통합 Agent (12개 논리적 Agent)
├── sync-orchestrator/
│   ├── backend/          # Spring Boot (port 8080)
│   └── frontend/         # Next.js (port 3000)
├── docs/                 # ARCHITECTURE.md, UI_GUIDE.md, 클로드 작업 메뉴얼.txt
│   └── claude-memory/    # troubleshooting.md (git 추적)
├── dev_logs/             # 루트 작업 일지
├── orchestrator/dev_logs/ # orchestrator 작업 일지
└── scripts/              # testdata.sh 등
```

## 빌드 명령어
```bash
# common (수정 시 bojo에 JAR 복사 필요)
cd sync-agent-common && ./gradlew clean build -x test
cp build/libs/sync-agent-common-*.jar ../sync-agent-bojo/libs/

# agent
cd sync-agent-bojo && ./gradlew clean build -x test

# orchestrator backend
cd sync-orchestrator/backend && ./gradlew clean build -x test

# frontend 타입체크
cd sync-orchestrator/frontend && npx tsc --noEmit
```

## DB 환경
| 용도 | 타입 | 포트 | DB명 | 비고 |
|------|------|------|------|------|
| Orchestrator | PostgreSQL | 29001 | orchestrator | 중앙 관리 |
| Agent IF | PostgreSQL | 29001 | dev | IF 테이블, link_ngwis |
| 외부 PG | PostgreSQL | 29000 | daejeon,bytek,chungnam,keunsan | 4개 |
| 외부 MySQL | MySQL | 29010 | infoworld_*,hydronet_* | 6개 (Docker) |
- 전체 계정: `k1m` / `1111` (테스트용)
- keunsan만 대문자 테이블/컬럼, 나머지 소문자

## 서버 포트
| 서비스 | 포트 |
|--------|------|
| Orchestrator Backend | 8080 |
| Agent (sync-agent-bojo) | 8082 |
| Frontend (Next.js) | 3000 |
| 외부 PG | 29000 |
| 내부 PG | 29001 |
| 외부 MySQL | 29010 |

## 핵심 아키텍처
- 12개 논리적 Agent = RCV 10(업체별) + Loader 1 + SND 1
- 하나의 물리적 앱(sync-agent-bojo:8082)에서 agentCode로 라우팅
- 파이프라인: Source(외부) →[RCV]→ IF_RSV →[Loader]→ Target →[SND]→ IF_SND
- Agent 설정: `config/agents/*.yml` (파일 기반, 업체명 네이밍)
- Multi-DB: PostgreSQL + MySQL 자동 분기 (isMysql()/qi() 헬퍼)
- **데이터 접근**: 읽기=JPA, 쓰기=JDBCTemplate batch (IDENTITY+대량UPSERT 성능 문제)
- **제원 UK**: `obsv_code` 아닌 `source_refs` (외부 DB에 obsv_code 중복 존재)

## 주요 파일 위치
- Agent YAML: `sync-agent-bojo/src/main/resources/config/agents/`
- 파이프라인 라우팅: `sync-agent-bojo/.../config/PipelineRegistry.java`
- RCV 핵심: `LinkTableObsvDataFetcher.java`, `LinkTableUpdateStep.java`
- Loader 핵심: `DaejeonLoadStep.java`, `TargetRepositoryService.java`
- 공통 추출: `sync-agent-common/.../step/SourceToIfStep.java`
- Tracing: `sync-agent-common/.../controller/ExecutionDataController.java`
- Orchestrator 실행: `sync-orchestrator/backend/.../execution/ExecutionService.java`

## 참고 문서 (필요시 읽기)
| 문서 | 언제 읽나 | 경로 |
|------|----------|------|
| ARCHITECTURE.md | 아키텍처/파이프라인/테이블 설계 작업 시 | `docs/ARCHITECTURE.md` |
| UI_GUIDE.md | 프론트엔드/화면 작업 시 | `docs/UI_GUIDE.md` |
| 클로드 작업 메뉴얼 | 작업 규칙 재확인 시 | `docs/클로드 작업 메뉴얼.txt` |
| troubleshooting.md | 이슈 해결 중 기존 해결법 참고 시 | `docs/claude-memory/troubleshooting.md` |
| dev_logs/*.md | 이전 작업 이어서 할 때 | `dev_logs/`, `orchestrator/dev_logs/` |
