# 트러블슈팅 기록

## 빌드/기동

### Jasypt 암호화 키
- Agent application.yml의 `jasypt.encryptor.password`가 v1과 다르면 기동 실패
- 키: `sync-pipeline-secret-key-2024`

### snakeyaml 의존성
- `org.yaml.snakeyaml:snakeyaml` (X) → `org.yaml:snakeyaml` (O)
- groupId 주의

### 프로퍼티 누락
- DaejeonLoadStep이 `${loader.step.id}` 등 플레이스홀더 사용
- v1 Loader의 loader-variables.yml에서 가져온 값들 → application.yml에 명시 필요

## DB/SQL

### PG vs MySQL 방언 분기
- 식별자 인용: PG `"col"` / MySQL `` `col` `` → `qi()` 헬퍼 사용
- 검색: PG `ILIKE` / MySQL `LIKE`
- 캐스팅: PG `CAST(x AS TEXT)` / MySQL `CAST(x AS CHAR)`
- 스키마: PG `table_schema = 'public'` / MySQL `TABLE_SCHEMA = DATABASE()`
- UPSERT: PG `ON CONFLICT DO UPDATE` / MySQL `ON DUPLICATE KEY UPDATE`
- INSERT 스킵: PG `ON CONFLICT DO NOTHING` / MySQL `INSERT IGNORE`
- 날짜: PG `TO_DATE(?, 'YYYYMMDD')` / MySQL `STR_TO_DATE(?, '%Y%m%d')`

### 복합 PK 처리
- source_refs의 PK 파싱: `split(":", 4)` (limit 필수, 시간값의 `:` 때문)
- 복합 PK 구분자: `|` (obsv_code|obsv_date|obsv_time)
- PK 감지: JDBC `DatabaseMetaData.getPrimaryKeys()` 사용
- SND는 PK 없을 수 있음 → `getIndexInfo()`로 UniqueKey fallback

### typedValue 변환
- obsv_date → `java.sql.Date` (PG date 타입 매칭)
- obsv_time → `java.sql.Time` (PG time 타입 매칭)
- 숫자 → `Long`
- 안하면 `operator does not exist: date = character varying` 에러

### link_ngwis 위치
- Agent IF DB(29001/dev)에만 존재해야 함
- 외부 업체 DB에는 없어야 함 (한번 잘못 넣은 적 있음)

## Orchestrator

### Agent PK 구조
- v1: `Long id` (auto-increment PK) + `String agentCode` (unique 비즈니스 키)
- v2 초기에 `String agentId` 단일 PK로 잘못 만들어서 스키마 충돌 → v1 기준으로 정렬

### agentCode 라우팅
- Orchestrator 자동생성 agentCode와 Agent YML의 agent-code가 불일치하면 런타임 에러
- 해결: discover 방식 (Agent /health 조회 → 사용 가능 agentCode 목록에서 선택)

### execution 트리거 시 agentCode 전달
- `request.put("agentCode", agentCode)` 필수
- 빠지면 Agent PipelineService에서 라우팅 실패

### sourceTableIds 비어있을 때
- Agent API `GET /api/pipeline/{agentCode}/tables` 호출하여 자동 등록
- 안하면 source_refs에 tbId=0 기록됨

## 프론트엔드

### AgentType 열거형
- v1: RELAY, LOADER_STANDARD, LOADER_CUSTOM
- v2: RCV, SND, LOADER
- 백엔드/프론트 양쪽 다 맞춰야 함

### Datasource POST 500 에러 (미해결 - 02-13)
- 컬럼 길이 증가(username 512, password 1024) 후에도 지속
- 서버 로그 확인 필요
