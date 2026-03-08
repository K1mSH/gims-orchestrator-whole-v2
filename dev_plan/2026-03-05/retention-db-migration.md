# Retention 설정 DB 이관 — YAML → Orchestrator DB

## 목적
Retention 설정을 YAML 고정값에서 Orchestrator DB로 이관하여 프론트에서 수정 가능하게 함.

## 설계

### DB: Agent 테이블에 JSON 컬럼 추가
```sql
ALTER TABLE agent ADD COLUMN retention_config TEXT;
-- JSON 형식: {"enabled":true,"targets":[{"table":"pm_gd970201","dateColumn":"obsrvn_dt","retentionDays":1}]}
```
JPA `ddl-auto: update` 설정이므로 컬럼 자동 생성됨.

### 흐름 변경
```
[기존] Agent YAML → Agent가 설정 읽고 삭제 실행
[변경] Orchestrator DB → 스케줄러가 설정 포함해서 Agent에 POST → Agent가 삭제 실행
```

## 수정 대상

### 1. Orchestrator backend
| 파일 | 변경 |
|------|------|
| `Agent.java` | `retentionConfig` (TEXT) 컬럼 추가 |
| `AgentDto.java` | Response/UpdateRequest에 `retentionConfig` 필드 추가 |
| `AgentService.java` | `getRetentionConfig()` → DB에서 읽기, `updateRetentionConfig()` 추가 |
| `AgentController.java` | `GET/PUT /{id}/retention` → DB CRUD |
| `DataRetentionScheduler.java` | DB에서 설정 읽어서 Agent에 POST body로 전달 |

### 2. Agent common
| 파일 | 변경 |
|------|------|
| `DataRetentionController.java` | POST body에서 RetentionConfig 수신하도록 변경 |

### 3. Frontend
| 파일 | 변경 |
|------|------|
| `api.ts` | `updateRetentionConfig()` 추가 |
| `InfoTab.tsx` | Retention 테이블을 수정 가능하게 변경 (추가/삭제/수정) |

### 4. Agent YAML
- `internal-bojo-loader.yml`에서 retention 섹션 제거 (DB가 source of truth)

## 영향 범위
- Agent 프로젝트 코드(AgentDefinition, AgentConfigLoader)의 retention 파싱은 그대로 둠 (fallback)
- DB에 설정이 있으면 DB 우선, 없으면 Agent YAML fallback
