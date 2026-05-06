---
name: 표준화 자료에 매핑 없는 테이블 = 레거시 이름 그대로
description: standardized_detail.tsv 에 매핑 없는 테이블/컬럼은 v3 원본 그대로 보존. 임의 추측해서 표준화 후 이름 만들지 말 것
type: feedback
originSessionId: 6a53f35d-a928-4fc2-a0fa-6199aa746193
---
표준화 작업 시 `docs/Standardizedtable/_converted/standardized_detail.tsv` 매핑 없는 테이블/컬럼은 **레거시 v3 이름 그대로 사용.**

**Why**: 자료에 없는 항목은 표준화 대상이 아니거나 자료 누락. 어느 쪽이든 임의로 11자리 이름 (예: `RGETNPMMS01` → `RGETN0PMMS01` 같은 추측) 만들면 운영 환경과 어긋남. 5/6 작업 중 `RGETNPMMS01`, `RGETSTGMS01_2024YB` 등 자료 매핑 없는 테이블 발견 — 사용자 결정 = "자료 없는 건 레거시 그대로".

**How to apply**:
1. 표준화 작업 전 `awk -F'\t' '$2 ~ /^TC_/ {print $2"\t"$4}' standardized_detail.tsv | sort -u` 로 매핑 전수 추출
2. `awk -F'\t' '$2=="<table>" {...}' ` 식으로 컬럼별 매핑 ($7=v3, $8=표준화후) 정확히 확인
3. 매핑 0건 = 레거시 그대로
4. **TSV 컬럼 의미**: $2=표준화 전 테이블, $3=중간 형식 (자기참조), $4=표준화 후 11자리, $7=표준화 전 컬럼, $8=표준화 후 컬럼, $9/$10=한글명, $16=도메인, $17=분류, $18=참조

**관련 사례** (5/6):
- `TC_GD00100` → `TC_GD000100` (자료 매핑 있음, RENAME 적용)
- `TC_GD00002` → `TC_GD000002` (자료 매핑 있음, RENAME 적용)
- `RGETNPMMS01` → 매핑 없음 → 그대로 (B4 핸들러 SQL 도 `FROM RGETNPMMS01` 그대로)
