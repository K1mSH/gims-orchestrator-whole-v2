# 타겟 테이블 자동삭제(Retention) 기능 구현 계획

## 목적
에이전트별 타겟 테이블에 데이터가 계속 쌓여 무거워지는 문제 방지.
날짜 컬럼이 있는 테이블만 대상으로 보존 기간을 설정하여 오래된 데이터를 자동 삭제.

### 대상 선정 기준
- **대상**: 날짜 기준 컬럼(date-column)이 존재하는 테이블만
- **제외**: 날짜 컬럼이 없는 테이블(제원 테이블 등)은 retention 대상에서 제외
- **예시**: pm_gd970201(obsrvn_dt 기준), tm_gd980002(change_dt 기준) → 5년 이전 데이터 삭제

---

## 설계 요약

### 실행 흐름
```
Agent YAML → retention 설정 로드
  ↓
Agent: POST /api/cleanup/{agentCode} 엔드포인트 노출
  ↓
Orchestrator: 스케줄러가 주기적으로 해당 엔드포인트 호출
  ↓
Agent: target datasource에 DELETE 실행 → 결과 반환
```

---

## 수정 대상 파일 (총 10개)

### 1. sync-agent-common (공통 모듈) — 신규 3개

#### 1-1. 신규: `RetentionConfig.java`
- **경로**: `sync-agent-common/src/main/java/com/sync/agent/common/config/RetentionConfig.java`
- **역할**: retention 설정 POJO
```java
@Getter @Setter
public class RetentionConfig {
    private boolean enabled;
    private List<TableRetention> targets = new ArrayList<>();

    @Getter @Setter
    public static class TableRetention {
        private String table;
        private String dateColumn;
        private int retentionDays;
    }
}
```

#### 1-2. 신규: `DataRetentionService.java`
- **경로**: `sync-agent-common/src/main/java/com/sync/agent/common/service/DataRetentionService.java`
- **역할**: 삭제 실행 로직
- **핵심 로직**:
  - `RetentionConfigProvider` 인터페이스로 agent 프로젝트에서 설정 주입받음
  - `DataSourceProvider.getJdbcTemplate(targetDatasourceId)`로 target DB 접속
  - 테이블별 `DELETE FROM {table} WHERE {dateColumn} < ?` 실행
  - cutoffDate = `LocalDate.now().minusDays(retentionDays)`
  - 삭제 건수를 Map으로 반환
- **의존성**: `DataSourceProvider`, `RetentionConfigProvider`

#### 1-3. 신규: `DataRetentionController.java`
- **경로**: `sync-agent-common/src/main/java/com/sync/agent/common/controller/DataRetentionController.java`
- **역할**: REST 엔드포인트
- **엔드포인트**: `POST /api/cleanup/{agentCode}`
- **응답 예시**:
```json
{
  "agentCode": "internal-bojo-loader",
  "results": [
    { "table": "pm_gd970201", "deletedCount": 150, "cutoffDate": "2025-03-05" },
    { "table": "tm_gd980002", "deletedCount": 30, "cutoffDate": "2025-03-05" }
  ],
  "totalDeleted": 180
}
```
- **에러 처리**: retention 미설정 agent → 404, 삭제 실패 → 500 + 에러 메시지

#### 1-4. 신규: `RetentionConfigProvider.java` (인터페이스)
- **경로**: `sync-agent-common/src/main/java/com/sync/agent/common/config/RetentionConfigProvider.java`
- **역할**: agent 프로젝트가 구현하여 common에 설정 주입
```java
public interface RetentionConfigProvider {
    RetentionConfig getRetentionConfig(String agentCode);
    String getTargetDatasourceId(String agentCode);
}
```

### 2. sync-agent-bojo (DMZ Agent) — 수정 2개

#### 2-1. 수정: `AgentDefinition.java`
- **경로**: `sync-agent-bojo/src/main/java/com/sync/agent/bojo/config/AgentDefinition.java`
- **변경**: `RetentionConfig retention` 필드 추가 (getter/setter)

#### 2-2. 수정: `AgentConfigLoader.java`
- **경로**: `sync-agent-bojo/src/main/java/com/sync/agent/bojo/config/AgentConfigLoader.java`
- **변경**: YAML의 `retention:` 섹션 파싱 로직 추가
- `RetentionConfigProvider` 인터페이스 구현 (common 모듈에 설정 전달)

### 3. sync-agent-bojo-int (내부망 Agent) — 수정 2개 + YAML 1개

#### 3-1. 수정: `AgentDefinition.java`
- **경로**: `sync-agent-bojo-int/src/main/java/com/sync/agent/bojoint/config/AgentDefinition.java`
- **변경**: `RetentionConfig retention` 필드 추가

#### 3-2. 수정: `AgentConfigLoader.java`
- **경로**: `sync-agent-bojo-int/src/main/java/com/sync/agent/bojoint/config/AgentConfigLoader.java`
- **변경**: YAML `retention:` 파싱 + `RetentionConfigProvider` 구현

#### 3-3. 수정: `internal-bojo-loader.yml`
- **경로**: `sync-agent-bojo-int/src/main/resources/config/agents/internal-bojo-loader.yml`
- **변경**: retention 섹션 추가
```yaml
retention:
  enabled: true
  targets:
    - table: pm_gd970201
      date-column: obsrvn_dt
      retention-days: 1  # 테스트용 1일 (운영 시 1825=5년)
    - table: tm_gd980002
      date-column: change_dt
      retention-days: 1  # 테스트용 1일 (운영 시 1825=5년)
```

### 4. sync-orchestrator/backend — 신규 1개

#### 4-1. 신규: `DataRetentionScheduler.java`
- **경로**: `sync-orchestrator/backend/src/main/java/com/sync/orchestrator/scheduler/DataRetentionScheduler.java`
- **역할**: 주기적으로 모든 active Agent의 cleanup 엔드포인트 호출
- **구현 상세**:
  - `@Scheduled(cron = "0 0 2 * * *")` — 매일 새벽 2시 실행
  - `AgentRepository`에서 ONLINE/RUNNING 상태 Agent 조회
  - 각 Agent의 `endpointUrl + "/api/cleanup/" + agentCode`로 POST 호출
  - `RestTemplate` 사용 (기존 `ExecutionService`와 동일 패턴)
  - 실행 결과 로깅 (성공/실패, 삭제 건수)
  - 한 Agent 실패해도 나머지 계속 실행

---

## YAML 설정 예시

### DMZ Agent (필요한 경우만)
```yaml
# dmz-bojo-rcv-daejeon.yml — RCV Agent는 보통 retention 불필요
# retention 섹션 없으면 자동 스킵
```

### Internal Agent (Loader — 주 대상)
```yaml
# internal-bojo-loader.yml
agent-code: internal-bojo-loader
type: LOADER
# ... 기존 설정 ...
retention:
  enabled: true
  targets:
    - table: pm_gd970201
      date-column: obsrvn_dt
      retention-days: 1  # 테스트용 1일 (운영 시 1825=5년)
    - table: tm_gd980002
      date-column: change_dt
      retention-days: 1  # 테스트용 1일 (운영 시 1825=5년)
```

---

## 빌드 & 배포 순서

```bash
# 1. common 빌드 → JAR 복사
cd sync-agent-common && ./gradlew clean build -x test
cp build/libs/sync-agent-common-*.jar ../sync-agent-bojo/libs/
cp build/libs/sync-agent-common-*.jar ../sync-agent-bojo-int/libs/

# 2. bojo 빌드
cd sync-agent-bojo && ./gradlew clean build -x test

# 3. bojo-int 빌드
cd sync-agent-bojo-int && ./gradlew clean build -x test

# 4. orchestrator backend 빌드
cd sync-orchestrator/backend && ./gradlew clean build -x test
```

---

## 검증 방법

1. **수동 테스트**: bojo-int 기동 후 curl로 직접 호출
   ```bash
   curl -X POST http://localhost:8092/api/cleanup/internal-bojo-loader
   ```
2. **응답 확인**: 삭제 건수가 포함된 JSON 응답
3. **DB 확인**: pm_gd970201에서 1일 이전 데이터 삭제 확인
4. **스케줄러 테스트**: Orchestrator 기동 후 로그에서 retention 스케줄러 실행 확인

---

## 영향 범위

- **기존 기능**: 영향 없음 (신규 엔드포인트 + 신규 스케줄러만 추가)
- **데이터**: retention 설정된 테이블의 오래된 데이터만 삭제
- **성능**: DELETE 쿼리 실행 시 일시적 부하 (새벽 2시 실행으로 최소화)
- **롤백**: retention.enabled=false 또는 YAML에서 retention 섹션 제거로 즉시 비활성화
