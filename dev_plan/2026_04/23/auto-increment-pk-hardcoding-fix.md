# SourceToTargetStep auto-increment PK 하드코딩 개선 계획

**작성일**: 2026-04-23
**대상 파일**: `sync-agent-common/src/main/java/com/sync/agent/common/step/SourceToTargetStep.java`

---

## 1. 수정 목적

`SourceToTargetStep`의 `AUTO_INCREMENT_PK_COLUMNS` 하드코딩 제거.
"이름이 `id` 또는 `sn` 이면 INSERT에서 제외"하는 현재 로직을 **의도에 맞는 방식**(진짜 auto-increment 제외)으로 바꾸되, 기존 동작에 회귀(regression)를 유발하지 않는다.

## 2. 현재 문제 정리

```java
// SourceToTargetStep.java:77
private static final List<String> AUTO_INCREMENT_PK_COLUMNS = List.of("id", "sn");

// :212-214
List<String> columns = selectColumns.stream()
        .filter(c -> AUTO_INCREMENT_PK_COLUMNS.stream().noneMatch(pk -> pk.equalsIgnoreCase(c)))
        .toList();
```

### 문제점
1. **이름 기반 블랙리스트** — PK 여부, auto-increment 여부를 검증하지 않음
2. **소스 컬럼에 적용** — 소스에 `id`/`sn` 이름의 **비즈니스 컬럼**이 있으면 조용히 누락
3. **상수 이름이 거짓말** — `AUTO_INCREMENT_PK_COLUMNS` 라는 이름과 실제 동작(단순 이름 매칭) 불일치
4. **경고 없음** — 컬럼 누락 시 로그에 카운트만 찍히고 어떤 컬럼이 빠졌는지 안 보임
5. **새 네이밍 추가 시 common 수정** — `seq`, `no` 등 다른 auto-increment 네이밍 쓰는 Agent 추가 시 common 수정 필요 → 전체 모듈 영향

### 현재 동작이 안전한 근거
- bojo/bojo-int IF 테이블은 모두 `id` PK (IDENTITY)
- provide 타겟 8개 엔티티는 모두 `sn` PK (IDENTITY, `@Comment("일련번호")`)
- 소스 테이블 중 `id`/`sn`이라는 이름의 비즈니스 컬럼을 가진 케이스 **현재 없음**

즉 지금까지는 관례가 깨지지 않아서 문제가 안 터진 것.

## 3. 영향 범위

### 이 로직을 통과하는 Agent (factory-key=source-to-if)
- **sync-agent-bojo** (11 YAML): `dmz-bojo-rcv-*` 10종 + `dmz-bojo-snd`
- **sync-agent-bojo-int** (6 YAML): `internal-*-rcv`, `internal-saeol-loader`
- **sync-agent-others** (3 YAML): `dmz-others-snd-*`
- **sync-agent-provide** (6 YAML): `provide-tm-gd*`
- 총 **26개 YAML** (약 97개 step 설정)

### 타겟 테이블별 auto-increment PK 이름
| 모듈 | 타겟 | PK 컬럼 | PK 타입 |
|------|------|--------|--------|
| bojo | `if_rsv_*`, `if_snd_*` (PG) | `id` | BIGSERIAL |
| bojo-int | `if_rsv_*` (PG) | `id` | BIGSERIAL |
| bojo-int | `IF_RSV_*` (Oracle) | `ID` | IDENTITY |
| others | saeol `IF_SND_*` (Oracle) | `ID` | IDENTITY |
| provide | `api_prv_*` (PG) | `sn` | BIGSERIAL |
| provide | `TM_GD*` (Oracle 레거시) | `SN` | IDENTITY |

## 4. 수정 방안 비교

### 방안 A: JDBC 메타데이터로 auto-increment 자동 감지 (정석)

**핵심 로직**
```java
// 타겟 테이블 메타 읽어서 IS_AUTOINCREMENT=YES 컬럼 추출
ResultSet rs = metaData.getColumns(catalog, schema, targetTable, null);
Set<String> autoIncCols = new HashSet<>();
while (rs.next()) {
    if ("YES".equals(rs.getString("IS_AUTOINCREMENT"))) {
        autoIncCols.add(rs.getString("COLUMN_NAME"));
    }
}
// INSERT 컬럼에서 제외
```

- **장점**: 이름 무관, 진짜 auto-increment만 제외. 추후 Agent 추가 시 코드 수정 불필요
- **단점**:
  - Oracle IDENTITY의 `IS_AUTOINCREMENT` 응답이 드라이버·버전별로 불안정 (오래된 Oracle JDBC는 "NO" 반환 이슈 존재)
  - 매 Step 실행 시 메타데이터 조회 1회 추가 (캐싱으로 완화 가능)
  - 타겟 테이블명 케이스 이슈(Oracle은 대문자 자동 처리) — 이미 `JdbcTableNameResolver`로 처리하는 로직 재사용 가능

### 방안 B: YAML 명시적 설정

**ExtractStepConfig**에 필드 추가:
```java
private final List<String> excludeInsertColumns;

@Builder.Default
private final List<String> defaultExcludeInsertColumns = List.of("id", "sn");

public List<String> getExcludeInsertColumnList() {
    return excludeInsertColumns != null ? excludeInsertColumns : defaultExcludeInsertColumns;
}
```

**YAML**:
```yaml
# 기본 동작 (미지정 시): ["id", "sn"] 제외 — 기존과 동일
- id: xxx-extract
  factory-key: source-to-if
  # ...

# 커스텀이 필요한 경우만 명시
- id: yyy-extract
  factory-key: source-to-if
  exclude-insert-columns: [seq]   # 새 네이밍 대응
  # 또는
  exclude-insert-columns: []      # 제외 안 함
```

- **장점**:
  - 기본값 `[id, sn]`으로 두면 기존 26개 YAML 수정 불필요 → **0 회귀**
  - 명시적으로 설정 가능 (추적·리뷰 용이)
  - Oracle 드라이버 이슈 회피
- **단점**:
  - "이름 기반 블랙리스트"라는 본질 문제는 그대로 남음 (단, 사용자가 인지하고 쓰는 것이 됨)
  - 소스에 `id` 비즈니스 컬럼 케이스 생기면 여전히 수동 개입 필요

### 방안 C: 방안 A + 방안 B 혼합 (하이브리드)

1. **기본 동작**: 타겟 JDBC 메타데이터에서 auto-increment 자동 감지
2. **오버라이드**: YAML `exclude-insert-columns` 지정 시 메타데이터 무시하고 YAML 값 사용
3. **안전장치**: 메타데이터 조회 실패 시 fallback으로 `["id", "sn"]` 사용 (현 동작과 동일)
4. **로그 개선**: 제외된 컬럼 목록을 INFO 레벨로 찍음
5. **캐싱**: 타겟 테이블별로 Step 인스턴스 life-cycle 동안 1회만 조회

- **장점**: 정석 + 안전장치. 회귀 없음 + 향후 확장성 확보
- **단점**: 구현 복잡도 가장 높음

## 5. 권장안 — **방안 C (하이브리드)**

### 이유
- MEMORY `feedback_no_regression_organic`: 유기적 구조라 땜질 금지, 전체 Agent 영향. → 메타데이터 자동 감지가 근본 해결.
- Oracle 드라이버 이슈는 **fallback + YAML override**로 보완.
- provide 모듈은 Oracle 타겟도 있으므로 단순 A안만 쓰면 리스크.
- 기본값 fallback이 현재 동작과 **완전히 동일**하므로 점진 마이그레이션 가능.

### 구현 순서
1. `ExtractStepConfig`에 `excludeInsertColumns` 필드 + getter 추가 (null 허용)
2. `SourceToTargetStep`에
   - `detectTargetAutoIncrementColumns(String datasourceId, String tableName)` 메서드 신설 (`detectSourcePrimaryKey` 패턴 참고)
   - 결과 캐싱용 필드 추가 (`volatile List<String> cachedTargetAutoIncCols`)
   - 제외 컬럼 결정 로직:
     ```
     1. config.excludeInsertColumns 있으면 → 그거 사용
     2. 없으면 → detectTargetAutoIncrementColumns() 호출
     3. 메타데이터 결과 비어있거나 실패 → fallback ["id", "sn"]
     ```
3. `SourceToTargetStepFactory`, `LinkSourceToIfStepFactory`에 `exclude-insert-columns` YAML 파싱 추가
4. 하드코딩 상수 `AUTO_INCREMENT_PK_COLUMNS` 는 **fallback용 상수로 유지** (이름은 `AUTO_INCREMENT_PK_FALLBACK`으로 변경)
5. 로그 개선:
   ```
   [xxx-extract] Exclude-from-insert columns: [id] (source: target-metadata)
   [xxx-extract] Exclude-from-insert columns: [sn] (source: fallback)
   [xxx-extract] Exclude-from-insert columns: [seq] (source: yaml)
   ```

### 수정 파일 목록
1. `sync-agent-common/.../step/ExtractStepConfig.java` (필드 추가)
2. `sync-agent-common/.../step/SourceToTargetStep.java` (로직 교체)
3. `sync-agent-common/.../pipeline/SourceToTargetStepFactory.java` (YAML 파싱)
4. `sync-agent-bojo/.../rcv/factory/LinkSourceToIfStepFactory.java` (YAML 파싱)
5. `sync-agent-common/build.gradle` → `mvn clean install` → bojo/bojo-int/others/provide `libs/` JAR 복사

### YAML 수정 여부
- **기본 26개 YAML: 수정 불필요** (메타데이터 자동 감지가 동일 결과 내므로)
- 향후 새 Agent에서 특수 네이밍 필요 시에만 `exclude-insert-columns` 명시

## 6. 테스트 계획

### 빌드 테스트
```bash
cd sync-agent-common && ./gradlew clean build -x test
cp build/libs/sync-agent-common-*.jar ../sync-agent-bojo/libs/
cp build/libs/sync-agent-common-*.jar ../sync-agent-bojo-int/libs/
cp build/libs/sync-agent-common-*.jar ../sync-agent-others/libs/
cp build/libs/sync-agent-common-*.jar ../sync-agent-provide/libs/
cd ../sync-agent-bojo && ./gradlew clean build -x test
cd ../sync-agent-bojo-int && ./gradlew clean build -x test
cd ../sync-agent-others && ./gradlew clean build -x test
cd ../sync-agent-provide && ./gradlew clean build -x test
```

### 기동 + 파이프라인 회귀 테스트 (각 모듈별 대표 Agent 1개씩)
1. **bojo RCV**: `daejeon` — IF_RSV_OBSVDATA 에 `id`가 INSERT 되지 않는지 확인
2. **bojo SND**: `dmz-bojo-snd` — IF_SND_* 에 `id`가 INSERT 되지 않는지 확인
3. **bojo-int RCV (Oracle)**: `internal-saeol-rcv` — Oracle `ID` IDENTITY 컬럼이 메타데이터에서 감지되는지 **특히 주의**
4. **others SND**: `dmz-others-snd-saeol` — Oracle 타겟 검증
5. **provide**: `provide-tm-gd000203` — `sn` 감지 확인

### 검증 포인트
- 실행 후 타겟 테이블 `id`/`sn` 컬럼에 null 또는 연속된 auto-increment 값이 들어갔는지 (DB 직접 조회)
- 로그에 제외 컬럼 메시지 정상 출력 여부
- 건수/추적 정상 (기존 SyncLog 비교)

### Oracle IS_AUTOINCREMENT 이슈 검증
기동 후 초기 로그에서 `detectTargetAutoIncrementColumns` 결과 확인.
- 만약 Oracle에서 "NO" 반환되어 fallback으로 빠지면 → 현재 동작과 동일 (안전)
- 정상 "YES" 반환되면 → 진짜 의도대로 동작

## 7. 롤백 계획

문제 발생 시:
1. `sync-agent-common` JAR을 직전 버전으로 복원
2. 각 모듈 `libs/` 폴더에 이전 JAR 재배포
3. 재기동

YAML 변경이 없으므로 설정 롤백은 불필요.

## 8. 열린 이슈 / 사용자 결정 필요

- [ ] 권장안(C) 진행 승인
- [ ] 아니면 방안 B (YAML 기반, 구현 가장 단순)만 먼저 적용
- [ ] 상수 이름 변경(`AUTO_INCREMENT_PK_COLUMNS` → `AUTO_INCREMENT_PK_FALLBACK`) 동의 여부
- [ ] 테스트 시 실환경 DB 접근 가능한 범위 확인 필요
- [ ] `verify/deployment/config-replacement.md` 갱신 필요 여부 (규칙: `feedback_config_replacement_sync`) — 이 수정은 외부 의존 추가 아니므로 **불필요 판단**, 확인 요망
