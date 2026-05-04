---
name: 기능 테스트 (test_plan/) baseline tag 동반
description: test_plan/ 기반 정식 기능 테스트는 baseline tag 명시 + 통과 시 신규 stable tag + GitHub Release
type: feedback
---

test_plan/ 기반의 "작정하고 하는 기능 테스트" 는 항상 baseline tag 동반. 문서 헤더에 검증 시점 baseline tag (commit hash) 명시 + 통과 시 신규 stable tag 박고 GitHub Release 도 같이.

**Why:** 시간 흐름에 따라 회귀 추적이 어려워짐. 안정화 시점마다 tag 박아두면 (1) 미래 회귀 의심 시 baseline 으로 즉시 비교 가능, (2) `git bisect` 로 회귀 commit 자동 추적, (3) "이전엔 정상이었음" 증거가 영구 보존. 사용자 결정 (2026-05-04 — "안정화 시점마다 태그로 보존, 그 이후 수정이 이전 코드에 영향 줬는지 검증").

**How to apply:**
- test_plan/ 폴더 안의 정식 기능 테스트 문서 작성 시 — 헤더에 baseline tag + commit hash 명시
- 검증 통과 후 — 신규 stable tag 박고 (`git tag -a {기능}-stable-{YYYY-MM-DD} ...` + `git push origin <tag>`) GitHub Release 도 같이
- 문서 끝 "Baseline 태그 갱신" 섹션에 신규 tag 박기
- **기능별 검증 양식은 그때그때 (사용자 결정 2026-05-04)** — 공통 템플릿 미리 안 박음. 단 baseline tag (헤더) + 신규 tag (결과 끝) 패턴만 일관
- 단위 테스트 결과서 (별 영역, `feedback_test_scenario.md` 룰) 와는 별개
