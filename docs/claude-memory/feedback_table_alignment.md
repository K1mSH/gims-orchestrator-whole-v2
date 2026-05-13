---
name: feedback-table-alignment
description: 마크다운 표는 raw 줄맞춤 필수 + 셀 컴팩트 + 디테일은 footnote (CJK=2칸 폭 계산)
metadata: 
  node_type: memory
  type: feedback
  originSessionId: be73226d-e944-4630-9c9d-52d96ea9900e
---

마크다운 표 작성 시 **raw 텍스트도 줄맞춤** 해서 컬럼이 시각적으로 정렬되게.

**규칙**:
1. **CJK(한글/한자) = 2칸 폭** 으로 계산해서 padding. ASCII = 1칸.
2. 컬럼별 최대 width 맞춰 trailing space 로 padding. 모든 행의 같은 컬럼이 같은 위치에 시작·종료.
3. **셀 내용 컴팩트하게**:
   - col1 (식별자/제목) = 짧은 라벨, 풀명/경로는 별도 컬럼으로
   - col2~ = 한 줄 요약. 자세한 사유/예외는 표 밖 footnote
   - 동일 컬럼 내 형식 통일 (예: 모든 행이 `Step.method` (`yml`) 패턴 식)
4. **너무 긴 셀** 들어가면 cell 안에 다 욱여넣지 말고 — 마커(`※`/`*1`) 표시 후 **표 아래 footnote 에서** 디테일.
5. **너무 넓은 표**(>100 cells) 는 컬럼 분할보다 **컬럼 통합 + 줄바꿈/footnote** 로 가는 게 raw 가독성 좋음.

**Why**: raw markdown 을 IDE/터미널 에서 직접 읽는 빈도 높음. 줄맞춤 안 되면 같은 항목(같은 컬럼)의 위치가 행마다 들쭉날쭉이라 스캔 못 함. 렌더링은 어차피 잘 되니까 raw 가독성이 작성자 의도.

**How to apply**:
- 표 쓰기 전에 각 셀 display width(CJK=2) 가늠 → 최대값으로 padding 양 결정.
- 셀 내용이 길어지면 줄이거나 footnote 로 분리. "한 줄에 다 욱여넣기" 금지.
- 같은 컬럼 내 항목들은 같은 패턴/구조 (parallel structure) — 일부만 다른 형식이면 위화감.

**예 (좋은 패턴)**:
```
| 테이블          | 이유         | 어디서 적재                                              |
|-----------------|--------------|----------------------------------------------------------|
| `link_ngwis`    | Link/스냅샷  | `LinkTableUpdateStep` (`dmz-bojo-rcv-*.yml`)             |
| `tm_gd980002`   | Link         | `InternalBojoLoadStep` (`internal-bojo-loader.yml`)      |
| `PM_GD111022` ※ | 파생 집계    | `UseLoadStep.updateDailyAggregation`                     |

※ 자세한 설명 표 아래에 ...
```

**예 (피할 패턴)**:
```
| 테이블 | 이유 | 어디서 적재 |
|---|---|---|
| `link_ngwis` | Link/스냅샷 — 자연키별 최신 1행만 유지 | DMZ Loader (`bojo-dmz` 의 `LinkTableUpdateStep` UPSERT) — `dmz-bojo-rcv-*` yml `link-table:` |
| `tm_gd980002` | Link — 보조 internal loader 상태 테이블 | Internal Loader (`bojo-internal InternalBojoLoadStep`) — `internal-bojo-loader.yml` target |
```
(셀 길이 제각각, 같은 항목 위치 행마다 다름, 한 셀에 정보 욱여넣음)
