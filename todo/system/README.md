# 체계별 TODO (Jira 연동용)

> 목적: 산재된 프로세스를 가시화/관리하는 플랫폼의 전체 기능 현황
> 관점: 요구사항 → 서비스별 구현 → 등록 현황

## 전체 체계

| # | 체계 | 설명 | 상태 |
|---|------|------|------|
| 1 | [Datasource 관리](01-datasource.md) | DB 연결 등록/테스트/암호화/컬럼 수집 | 개발완료 |
| 2 | [Agent 파이프라인](02-agent-pipeline.md) | 데이터 동기화 자동화 (RCV→Loader→SND) | 개발완료 |
| 3 | [API 수집](03-api-collect.md) | 외부 공공 API 데이터 수집/적재 | 개발완료 |
| 4 | [모니터링/운영](04-monitoring.md) | 실행이력, 데이터 추적, 보존기간, 대시보드 | 개발완료 |
| 5 | [API 제공](05-api-provide.md) | GIMS 데이터를 외부에 API로 제공 | 미개발 |
| 6 | [프로시저 관리](06-procedure-mgmt.md) | DB 프로시저 암호화 게시판 | 미개발 |
| 7 | [보안](07-security.md) | 암호화, API Key 인증, 로그인 | 부분 완료 |

## 서비스 구성

| 서비스 | 포트 | 역할 | 관련 체계 |
|--------|------|------|-----------|
| infolink-orchestrator-backend | 8080 | 중앙 관리 API | 1, 4, 7 |
| infolink-orchestrator-frontend | 3000 | 관리 화면 (Next.js) | 1, 2, 3, 4 |
| infolink-agent-bojo-dmz | 8082 | DMZ 수집 Agent (10업체) | 2 |
| infolink-agent-others-dmz | 8085 | DMZ 전송 Agent (SND) | 2 |
| infolink-agent-bojo-internal | 8092 | 내부망 적재 Agent | 2 |
| infolink-proxy-dmz | 8083 | DMZ DB 연결 프록시 | 1, 2, 7 |
| infolink-proxy-internal | 8093 | 내부망 DB 연결 프록시 | 1, 2, 7 |
| infolink-api-collector | 8084 | 외부 API 수집 모듈 | 3 |
| infolink-agent-common | - | 공통 라이브러리 (JAR) | 2, 7 |

## Jira 파싱 규칙

```
# 체계명                    → Epic
## 제목 [서비스태그]          → Task (라벨: 개발)
## 제목 [등록]               → Task (라벨: 등록)
## 제목 [확정필요]            → Task (라벨: 확정필요)
## 제목 [참고]               → Jira 미등록
## 상태: xxx                → Jira 미등록
- [x] / [ ] 항목            → Sub-task (Done / To Do)
```

> 최종 업데이트: 2026-04-14
