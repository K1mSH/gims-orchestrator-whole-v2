# Source PK 매칭 로직 수정 계획

## 문제
내부망 RCV 실행 시, 테이블 상세조회에서 SOURCE 타입 테이블(`if_snd_sec_obsvdata`)의 건수가 요약(9,940건)과 다르게 전체 건수(13,981건)로 표시됨.

## 원인
`ExecutionDataController.getSourcePksFromIfTable()` 메서드의 소스 테이블↔IF 테이블 매칭 로직 결함:

1. 소스 테이블명: `if_snd_sec_obsvdata`
2. `_view` suffix 제거 후: `if_snd_sec_obsvdata` (변화 없음)
3. IF 테이블 후보(SyncLog TARGET_IF): `if_rsv_sec_obsvdata`
4. 매칭 조건: `ifTable.contains(sourceBase)` → `"if_rsv_sec_obsvdata".contains("if_snd_sec_obsvdata")` → **false**
5. 매칭 실패 → null 반환 → fallback으로 전체 데이터 표시

## 수정 대상
- `sync-agent-common/src/main/java/com/sync/agent/common/controller/ExecutionDataController.java`
  - `getSourcePksFromIfTable()` 메서드 (라인 270-278)

## 수정 내용
sourceBase 추출 시 `_view` suffix뿐 아니라 IF prefix(`if_rsv_`, `if_snd_`, `if_loader_` 등)도 제거하여 코어 테이블명으로 매칭:

```
기존: sourceBase = sourceTableName.replaceAll("(?i)_view$", "")
변경: sourceBase = stripIfPrefix(sourceTableName.replaceAll("(?i)_view$", ""))

stripIfPrefix: "if_rsv_xxx" → "xxx", "if_snd_xxx" → "xxx", 일반 테이블은 그대로
```

매칭 시에도 양쪽 모두 IF prefix를 제거한 후 비교:
- `stripIfPrefix("if_snd_sec_obsvdata")` → `sec_obsvdata`
- `stripIfPrefix("if_rsv_sec_obsvdata")` → `sec_obsvdata`
- `"sec_obsvdata".contains("sec_obsvdata")` → **true**

## 영향 범위
- 기존 외부망 RCV (소스: `sec_obsvdata_view`, IF: `if_rsv_sec_obsvdata`):
  - sourceBase → `sec_obsvdata` (view 제거) → IF 테이블에 포함됨 → 정상 동작 유지
- 내부망 RCV (소스: `if_snd_sec_obsvdata`, IF: `if_rsv_sec_obsvdata`):
  - sourceBase → `sec_obsvdata` (IF prefix + view 제거) → IF 테이블에 포함됨 → **수정됨**

## 빌드/배포
- sync-agent-common 빌드 → sync-agent-bojo libs/ 복사
