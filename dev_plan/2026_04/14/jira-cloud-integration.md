# Jira Cloud 연동 계획

> 작성일: 2026-04-13
> 상태: 계획
> 목적: 작업 문서(dev_plan, dev_logs)를 Jira로 연동하여 상급자 공유 용이하게

## 배경

- 현재 dev_plan/, dev_logs/는 git 저장소 안에만 존재
- 상급자가 진행상황을 보려면 git을 직접 확인해야 함
- 팀 전체 체계화를 위한 파일럿 — 본인 작업부터 먼저 적용 후 시연

## 선택: Jira Cloud 무료 플랜

| 항목 | 내용 |
|------|------|
| 플랜 | Jira Cloud Free (10명까지 무료) |
| URL | xxx.atlassian.net (가입 후 확정) |
| 이유 | 서버 관리 불필요, 즉시 사용, 브라우저로 접근 가능 |

### 왜 Self-hosted가 아닌가
- Data Center는 라이선스 비용 부담
- 파일럿 단계에서 서버 관리 오버헤드 불필요
- Cloud 무료로 충분히 검증 가능, 추후 팀 확대 시에도 10명 이내면 무료 유지

## 작업 순서

### Step 1: Jira 환경 준비
- [ ] Atlassian 계정 가입 (https://www.atlassian.com/software/jira/free)
- [ ] Jira 프로젝트 생성 (프로젝트 키: `GIMS`)
- [ ] 이슈 유형 정의: Task(작업), Bug(버그), Story(기능)
- [ ] API 토큰 발급 (Atlassian 설정 → API tokens)

### Step 2: 워크플로우 설계
- [ ] dev_plan → Jira 이슈 매핑 규칙 정의
  - 계획 문서 1개 = Jira Task 1개
  - 제목, 상태, 설명 매핑
- [ ] dev_logs → Jira 코멘트/워크로그 매핑 규칙 정의
  - 일지 항목 = 해당 이슈에 코멘트 추가
- [ ] 상태 흐름: 계획 → 진행중 → 완료

### Step 3: 연동 스크립트 개발
- [ ] Jira REST API 연동 스크립트 작성 (Python 또는 Shell)
  - dev_plan .md 파싱 → Jira 이슈 생성
  - dev_logs .md 파싱 → Jira 코멘트 추가
  - 상태 동기화 (문서 상태 → Jira 상태)
- [ ] 설정 파일 분리 (API URL, 토큰, 프로젝트 키)
- [ ] .gitignore에 토큰/인증 정보 제외

### Step 4: 테스트 및 시연
- [ ] 기존 dev_plan 몇 건 Jira에 등록 테스트
- [ ] 대시보드 구성 (진행상황 한눈에 보이도록)
- [ ] 상급자에게 시연

## 연동 범위 (초기)

```
dev_plan/*.md  ──→  Jira Task 생성
                     - 제목: 문서 제목 (# 헤더)
                     - 설명: 문서 내용 요약
                     - 상태: 계획/진행중/완료

dev_logs/*.md  ──→  Jira Comment 추가
                     - 해당 Task에 작업 일지 기록
                     - 완료 항목, 수정 파일, 이슈 등
```

## 필요 정보 (Step 1 완료 후 확인)

- Jira 사이트 URL
- API 토큰
- 프로젝트 키
- 사용자 이메일 (API 인증용)

## 향후 확장 (팀 전파 시)

- 팀원별 Jira 계정 추가
- 공통 워크플로우 템플릿 배포
- 자동화: git commit hook → Jira 상태 자동 업데이트
- 스프린트/칸반 보드 활용
