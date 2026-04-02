# 새올 내부망 파이프라인 계획

## 목적
새올 DMZ IF_SND(Oracle 29005) → 내부망 GIMS(Oracle 29004) 적재
단순 카피 (데이터 변환 없음)

## 흐름

```
[DMZ Oracle 29005]              [Internal Oracle 29004]
IF_SND_RGETSTGMS01              IF_RSV_RGETSTGMS01 (신규)
IF_SND_RGETNPMMS01              IF_RSV_RGETNPMMS01 (신규)
... (16개)                       ... (16개)
    ↓ Proxy DMZ(8083)               ↓
    → Proxy Internal(8093)       Internal Loader
    → Internal RCV (8092)            ↓
                                 GIMS 타겟 테이블 (신규, 16개)
```

## 구성 요소

### 1. Internal RCV — 새올 전용
- bojo-int(8092)에 YAML 추가: `internal-saeol-rcv`
- factory-key: `source-to-if` (기존 SourceToIfStep 그대로)
- source: DMZ IF_SND 16개 (Oracle 29005, Proxy 경유)
- target: Internal IF_RSV 16개 (Oracle 29004)
- 증분: link_status 기반 (IF_SND에 link_status 있음)
- skip-source-status-update: true? → 소스(IF_SND)의 link_status를 SUCCESS로 바꿔야 하므로 false

### 2. Internal Loader — 새올 전용
- bojo-int(8092)에 YAML 추가: `internal-saeol-loader`
- factory-key: `source-to-if` (단순 카피이므로 같은 Step 사용)
- source: Internal IF_RSV 16개 (Oracle 29004)
- target: GIMS 타겟 16개 (Oracle 29004, 같은 DB, 원본과 동일 이름)
- conflict-key: `source_refs` (MERGE ON 조건)
- MERGE INTO ... ON (source_refs) — 중복 방지

### 3. DDL 필요 (Oracle 29004)
- IF_RSV 16개 테이블 (IF_SND와 동일 구조)
- GIMS 타겟 16개 테이블 (소스 원본과 동일 구조 + 메타 컬럼)

> **확정**: GIMS 타겟 = 원본과 같은 이름 (RGETSTGMS01 등) → Oracle 29004에 동일 이름 생성

### 4. Orchestrator 등록
- Agent 2개: internal-saeol-rcv, internal-saeol-loader
- Datasource: internal (29004) — 이미 있음
- 테이블: IF_RSV 16개 + GIMS 타겟 16개 = 32개

## 단순 카피 구조의 장점
- 커스텀 Step 불필요 (SourceToIfStep으로 충분)
- bojo처럼 EAV 변환/spot_id 매핑 없음
- YAML 작성만으로 완성

## 작업 순서
1. Oracle 29004에 IF_RSV + GIMS 타겟 DDL 생성
2. bojo-int에 YAML 2개 추가
3. Orchestrator Agent/테이블 등록
4. 테스트: DMZ SND 실행 → Internal RCV → Internal Loader → GIMS 확인

## 고려사항
- IF_SND가 Oracle(29005)이므로 Proxy DMZ가 ojdbc 필요 — 오늘 이미 추가 완료
- Internal RCV의 source datasource = saeol-oracle(29005)? → Proxy 경유로 접근
- bojo-int에 이미 Oracle 드라이버 있음 (ojdbc11)
- 16개 테이블 × 90컬럼 DDL → 오늘 새올 DDL 자동 생성 스크립트 재활용
