# 문서 구조 개편 계획서

## 목적
프로젝트 성장에 따라 ARCHITECTURE.md 단일 파일(800줄) → 전체 개요 + 모듈별 상세 문서로 분리

## 변경 전 구조
```
docs/
├── ARCHITECTURE.md              # 800줄, 모든 내용 혼재
├── ARCHITECTURE_INTERNAL.md     # 내부망 Agent
├── UI_GUIDE.md                  # 화면 정의서 (오래됨)
└── useIncludeJeju/              # 이관 설계
```

## 변경 후 구조
```
docs/
├── OVERVIEW.md                  # 신규 — 전체 개요
├── modules/
│   ├── orchestrator.md          # 신규 — Orchestrator 상세
│   ├── agent-bojo.md            # 기존 ARCHITECTURE.md에서 분리
│   ├── agent-internal.md        # 기존 ARCHITECTURE_INTERNAL.md 이동
│   ├── agent-common.md          # 신규 — 공통 모듈 상세
│   └── api-collector.md         # 신규 — API 수집 모듈 상세
├── UI_GUIDE.md                  # 갱신 — API 수집 화면 추가
└── useIncludeJeju/              # 유지
```

## 각 문서 내용 설계

### 1. OVERVIEW.md (신규)
- 프로젝트 목적/배경 (GIMS 지하수정보관리시스템 데이터 연계)
- 모듈 구성 요약표 (6개 모듈: 역할, 포트, 기술스택)
- 전체 데이터 흐름도 (Source→RCV→IF→Loader→Target→SND→Internal)
- 서버 포트 표 (8080~8094, 29000~29010)
- DB 환경 표 (PG/MySQL/Oracle, 용도별)
- 공통 규칙 (패키지 구조 레이어 기반, 로그 한글, Lombok 스타일 등)
- 세부 문서 링크 (modules/*.md)

### 2. modules/agent-bojo.md (기존 ARCHITECTURE.md에서 분리)
기존 ARCHITECTURE.md의 핵심 내용 이관:
- 통합 Agent 개요 (12개 논리 Agent, YAML 설정)
- RCV 파이프라인 상세
- Loader 파이프라인 상세 (DefaultLoadStep으로 업데이트)
- SND 파이프라인 상세
- 테이블 설계 (IF/Target/Link/이력)
- 컬럼 설계 (execution_id/link_status/source_refs)
- 실행 모드 (증분/재동기화/RESYNC)
- JPA vs JDBCTemplate 전략

### 3. modules/orchestrator.md (신규)
기존 ARCHITECTURE.md 1.2.4 확장:
- 역할/제공 기능
- 패키지 구조 (레이어 기반: controller/dto/entity/repository/scheduler/service)
- 핵심 API 목록 (/api/agents, /api/executions, /api/datasources, /api/schedules, /api/callback)
- 스케줄러/헬스체크/Retention 동작
- 실행 트리거 흐름 (Orchestrator→Agent 프록시)

### 4. modules/agent-internal.md (기존 이동)
- 기존 ARCHITECTURE_INTERNAL.md → modules/ 하위로 이동
- 내용은 현행 유지 (보수적)

### 5. modules/agent-common.md (신규)
기존 ARCHITECTURE.md 1.2.5 확장:
- 공통 모듈 역할
- 핵심 컴포넌트 (SourceToIfStep, IfTableService, ExecutionDataController 등)
- ExtractStepConfig 옵션
- Multi-DB 지원 (PG/MySQL/Oracle 방언 분기)
- JAR 배포 방식

### 6. modules/api-collector.md (신규)
MEMORY 내용 + 실제 코드 기반:
- 설계 원칙 (코드 변경 없이 UI 등록으로 새 API 수집)
- 범용 실행 엔진 플로우 (등록→테스트→매핑→실행)
- 커스텀 실행기 (D1~D5: 제주 제원/수위/이용시설/수질, 안양 이용량)
- 동적 파라미터 (TODAY/NOW/YEAR)
- LOOKUP 파생 컬럼
- TransformType 목록
- API키 참조 방식

### 7. UI_GUIDE.md (갱신)
- 메뉴 구성에 `/api-collect` 추가
- 사이드바 레이아웃 업데이트
- API 수집 화면 섹션 추가:
  - 엔드포인트 목록/등록/수정
  - 테스트 호출
  - 필드 매핑
  - 스케줄 관리
  - 실행 이력
  - 커스텀/범용 폼 통합 구조

### 8. 기존 ARCHITECTURE.md 처리
- 내용이 modules/로 분산되므로 **삭제** 후 OVERVIEW.md가 대체
- 또는 OVERVIEW.md로 리네이밍하여 git 히스토리 유지

## 작업 순서
1. `docs/modules/` 디렉토리 생성
2. OVERVIEW.md 작성
3. modules/agent-bojo.md — ARCHITECTURE.md에서 파이프라인 내용 분리
4. modules/orchestrator.md — 신규 작성
5. modules/agent-common.md — 신규 작성
6. modules/api-collector.md — 신규 작성
7. modules/agent-internal.md — ARCHITECTURE_INTERNAL.md 이동
8. UI_GUIDE.md 갱신
9. 기존 ARCHITECTURE.md, ARCHITECTURE_INTERNAL.md 삭제
10. CLAUDE.md 참고 문서 경로 업데이트

## 영향 범위
- docs/ 디렉토리만 수정 (코드 변경 없음)
- CLAUDE.md 참고 문서 테이블 경로 업데이트 필요
- MEMORY.md 참고 문서 경로 업데이트 필요
