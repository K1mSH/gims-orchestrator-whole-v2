# Verify — 검증 세션 체계

여러 세션이 병렬로 개발을 진행하는 동안 **기존 기능의 확정성(회귀 방지)** 을 담당하는 전담 체계.
`test_plan/` 은 모든 세션이 공용으로 쓰는 **기능 테스트 계획**이고, 본 `verify/` 는 **검증 세션 ↔ 개발 세션 간 핸드오프와 회귀 관리**를 위한 전용 공간이다.

---

## 1. 세션 역할 분담

| 세션 | 역할 | 브랜치 |
|------|------|--------|
| **verifier** (본 디렉토리 사용) | 최신 코드 pull → 회귀 검증 → 이슈/작업 문서 생성 → 핸드오프 관리 | main (read-only) |
| **forward** | 메인 개발 진행 | main |
| **parallel** | `tasks/OPEN/` 중 파트 안 겹치는 작업을 병렬 처리 | main |

### verifier 세션 원칙 (본 세션)
- **코드 수정 금지 (read-only).** 수정은 forward/parallel 세션이 담당.
- 검증 전용 보조 스크립트/쿼리 작성은 예외 (사용자 확인 후).
- 발견한 문제는 즉시 수정하지 말고 **이슈 문서로 남겨 핸드오프**.

---

## 2. 디렉토리 구조

```
verify/
├── README.md                    ← 본 문서
├── map/
│   └── feature-dependency.md    ← 파트 의존 맵 (병렬 가능 판단 근거)
├── checklists/                  ← 재사용 — 기능/Agent별 회귀 체크리스트
│   └── {agent-or-feature}.md
├── runs/                        ← 일별 검증 실행 기록
│   └── YYYY-MM-DD.md
├── issues/                      ← 발견된 문제 → 수정 지시
│   ├── OPEN/
│   └── DONE/
└── tasks/                       ← 파생 작업 지시 (개선/신규 등)
    ├── OPEN/
    └── DONE/
```

---

## 3. 문서 명명 / 번호

| 종류 | 파일명 | 예시 |
|------|--------|------|
| 이슈 (회귀/버그) | `issues/OPEN/VER-NNN-{kebab-title}.md` | `VER-001-provide-sync-log-missing.md` |
| 작업 지시 (파생/개선) | `tasks/OPEN/TASK-NNN-{kebab-title}.md` | `TASK-001-refactor-trace-prefix-branch.md` |
| 검증 실행 기록 | `runs/YYYY-MM-DD.md` | `runs/2026-04-23.md` |
| 체크리스트 | `checklists/{name}.md` | `checklists/provide-agent.md` |
| 의존 맵 | `map/feature-dependency.md` | 단일 파일 |

- 번호는 유형별 단순 증가 (3자리 0-padded).
- 상태 관리는 **폴더 이동**으로. 완료 시 `OPEN/` → `DONE/` 이동.
- frontmatter 의 `status` 필드는 `OPEN | IN_PROGRESS | DONE` 로 세부 상태 표현.

---

## 4. 워크플로 (verifier 세션)

1. **Pull**: 작업 시작 시 `git pull` 로 최신 상태 확보
2. **대상 결정**: 오늘의 리스크 포인트 선정 (최근 커밋 / 사용자 요청 / 장기 미검증 영역)
3. **체크리스트 실행**: `checklists/*.md` 기반 앱 기동 + 로그 모니터링 + DB 조회 + trace 검증
4. **결과 기록**: `runs/YYYY-MM-DD.md` 에 통과/실패/발견사항 기록
5. **이슈 생성**: 문제 발견 → `issues/OPEN/VER-NNN-*.md`
6. **작업 지시 생성**: 파생 작업이 필요하면 → `tasks/OPEN/TASK-NNN-*.md`
7. **재검증**: forward/parallel 이 수정 완료했다는 신호 → 해당 이슈 재검증 → 통과 시 `DONE/` 이동
8. **체크리스트 갱신**: 재발 방지를 위해 발견된 케이스를 체크리스트에 추가

---

## 5. 문서 템플릿

### 5.1 이슈 (VER-NNN)

```yaml
---
id: VER-NNN
title: 간결한 제목
status: OPEN
created: YYYY-MM-DD
parts: [P1-common, P3-proxy-internal]   # map 참조
parallel_safe: false                    # 파트 겹침 있으면 false
assignee: any | forward | parallel
related: [TASK-NNN, VER-NNN]
---

## 증상 요약

## 재현 절차
1. ...

## 기대 vs 실제
- 기대:
- 실제:

## 증거 (로그 / 스크린샷 / 쿼리)

## 수정 범위 제안
- `path/to/file.java:123` — ...

## 회귀 확인 방법
- `checklists/XXX.md` 의 항목 N 로 확인
- 또는 별도 E2E 절차:
```

### 5.2 작업 지시 (TASK-NNN)

```yaml
---
id: TASK-NNN
title: ...
status: OPEN
created: YYYY-MM-DD
parts: [...]
parallel_safe: true | false
depends_on: [VER-NNN, TASK-NNN]
blocks: [...]
assignee: any | forward | parallel
---

## 목적

## 수정 대상 파일
- [ ] `path/to/file1`
- [ ] `path/to/file2`

## 변경 내용
- [ ] ...
- [ ] ...

## 완료 조건 (Definition of Done)
- [ ] 빌드 통과
- [ ] `checklists/XXX.md` 통과
- [ ] E2E 시나리오: ...

## 금지 사항 (scope creep 방지)
- 본 task 범위 밖 리팩토링 금지
- ...
```

### 5.3 검증 실행 기록 (`runs/YYYY-MM-DD.md`)

```markdown
# YYYY-MM-DD 검증 실행 기록

## 기준 커밋
`<git rev-parse --short HEAD>` — <제목>

## 대상 체크리스트
- [x] `checklists/provide-agent.md`
- [ ] `checklists/trace-lifecycle.md`

## 결과
| 항목 | 결과 | 비고 |
|------|:----:|------|
| ... | PASS / FAIL | ... |

## 발견 이슈
- VER-001 — ...
- VER-002 — ...

## 파생 작업
- TASK-001 — ...

## 내일 할 일
- ...
```

---

## 6. 파트 겹침 판단

`map/feature-dependency.md` 참조. 병렬 가능 여부는 이슈/작업 문서의 `parallel_safe` 필드로 명시.

**기본 룰**
- 같은 파일 수정 → **겹침**
- 같은 공유 의존성(common JAR / Orchestrator / Proxy / 관리 DB 규약) → **겹침 (광범위 회귀 필요)**
- 다른 Agent YAML 만 각자 수정 → **독립 (병렬 안전)**
- 백엔드 vs 프론트엔드 분리 작업 → 일반적으로 **독립**

---

## 7. 상태 전이

| 상태 | 의미 | 위치 / 전이 조건 |
|------|------|----------------|
| OPEN | 생성되었고 아직 착수 전 | `OPEN/` 폴더, frontmatter status=OPEN |
| IN_PROGRESS | 담당 세션이 작업 중 | `OPEN/` 폴더 유지, frontmatter status=IN_PROGRESS |
| DONE | 수정+회귀 통과 | `DONE/` 폴더로 이동, frontmatter status=DONE |

- 담당 세션이 착수 시: frontmatter `status` 만 IN_PROGRESS 로 변경.
- verifier 가 회귀 통과 확인 후 파일을 `DONE/` 으로 이동 (+ status=DONE).

---

## 8. Git 관리

- `verify/` 전체를 git 추적 (작업 증적).
- `issues/DONE/`, `tasks/DONE/` 은 감사 로그 역할 — 삭제하지 않음.
- 커밋 메시지 prefix 예: `verify:` (예: `verify: VER-001 provide 추적 컬럼 누락 재현`).
