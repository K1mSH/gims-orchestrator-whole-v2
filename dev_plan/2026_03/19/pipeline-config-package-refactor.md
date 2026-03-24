# 파이프라인 Config 클래스 패키지 분리

## 목적
파이프라인 설정/조립/등록 클래스들이 일반 config 클래스와 섞여있어 역할 구분이 안 됨.
`config.pipeline` 서브패키지로 분리하여 파이프라인 구성 코드임을 명확히 한다.

## 대상 모듈
- sync-agent-bojo (DMZ)
- sync-agent-bojo-int (Internal)

## 변경 내용

### 이동 대상 클래스

**bojo** (`com.sync.agent.bojo.config` → `com.sync.agent.bojo.config.pipeline`):
| 파일 | 역할 |
|------|------|
| AgentConfigLoader.java | YAML 파싱 |
| AgentDefinition.java | YAML 모델 |
| PipelineRegistry.java | 라우팅 테이블 |
| RcvPipelineConfig.java | RCV Step 조립 |
| LoaderPipelineConfig.java | Loader Step 조립 |
| SndPipelineConfig.java | SND Step 조립 |

**bojo-int** (`com.sync.agent.bojoint.config` → `com.sync.agent.bojoint.config.pipeline`):
| 파일 | 역할 |
|------|------|
| AgentConfigLoader.java | YAML 파싱 |
| AgentDefinition.java | YAML 모델 |
| PipelineRegistry.java | 라우팅 테이블 |
| RcvPipelineConfig.java | RCV Step 조립 |
| LoaderPipelineConfig.java | Loader Step 조립 |

### config에 남는 클래스
- AsyncConfig.java — 일반 Spring 비동기 설정
- CaseAwareNamingStrategy.java — JPA 네이밍 (bojo만)
- DynamicEntityManagerService.java — EntityManager (bojo만)
- SyncDataSourceService.java — DataSource 관리

### import 수정 대상

**bojo**:
- PipelineController.java (AgentConfigLoader, AgentDefinition, PipelineRegistry)
- HealthController.java (PipelineRegistry)
- PipelineService.java (PipelineRegistry)

**bojo-int**:
- PipelineController.java (AgentConfigLoader, AgentDefinition, PipelineRegistry)
- HealthController.java (PipelineRegistry)
- PipelineService.java (PipelineRegistry)

### 영향 범위
- 패키지(디렉토리) 이동 + import 경로 변경만
- 로직 변경 없음
- 빌드 테스트로 검증
