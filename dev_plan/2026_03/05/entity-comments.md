# JPA 엔티티 @Comment 추가

## 목적
모든 엔티티의 테이블/컬럼에 `@Comment` 어노테이션 추가.
PostgreSQL에 `COMMENT ON TABLE/COLUMN` 으로 반영되어 DB 문서화 역할.

## 사용 어노테이션
- `org.hibernate.annotations.Comment`
- Hibernate 5.6.x (Spring Boot 2.7.12) 지원 확인

## 대상 엔티티 (14개)

### 1. Agent — 동기화 에이전트
| 컬럼 | 코멘트 |
|-------|--------|
| id | 에이전트 PK |
| agent_code | 에이전트 고유 코드 |
| agent_name | 에이전트 표시명 |
| endpoint_url | 에이전트 REST 엔드포인트 URL |
| zone | 네트워크 존 (DMZ/INTERNAL) |
| is_active | 활성화 여부 |
| agent_type | 에이전트 유형 (RCV/LOADER/SND/DB_CON_PROXY) |
| datasource_tag | 데이터소스 태그 |
| source_datasource_id | 소스 데이터소스 ID |
| target_datasource_id | 타겟 데이터소스 ID |
| description | 설명 |
| status | 상태 (ONLINE/OFFLINE/ERROR) |
| last_executed_at | 마지막 실행 시각 |
| last_execution_status | 마지막 실행 결과 |
| created_at | 생성 시각 |
| retention_config | 데이터 보존(Retention) 설정 JSON |

### 2. AgentExecutionMode — 에이전트 실행 모드
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| agent_id | 에이전트 FK |
| mode_id | 실행 모드 ID |
| mode_name | 실행 모드명 |
| description | 설명 |
| display_order | 표시 순서 |
| is_default | 기본 모드 여부 |
| created_at | 생성 시각 |

### 3. AgentExecutionParam — 에이전트 실행 파라미터
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| agent_id | 에이전트 FK |
| param_id | 파라미터 ID |
| label | 표시 라벨 |
| description | 설명 |
| data_type | 데이터 타입 (STRING/DATETIME 등) |
| default_value | 기본값 |
| is_enabled | 활성화 여부 |
| display_order | 표시 순서 |
| created_at | 생성 시각 |

### 4. AgentStepDefinition — 에이전트 스텝 정의
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| agent_id | 에이전트 FK |
| step_id | 스텝 ID |
| step_name | 스텝명 |
| description | 설명 |
| display_order | 표시 순서 |
| enabled_by_default | 기본 활성화 여부 |
| created_at | 생성 시각 |

### 5. AgentTable — 에이전트-테이블 매핑
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| agent_id | 에이전트 FK |
| datasource_table_id | 데이터소스 테이블 FK |
| table_type | 테이블 유형 (SOURCE/TARGET) |

### 6. AgentChain — 에이전트 체인
| 컬럼 | 코멘트 |
|-------|--------|
| chain_id | 체인 ID (PK) |
| chain_name | 체인명 |
| description | 설명 |
| trigger_type | 트리거 유형 (SEQUENTIAL/PARALLEL) |
| created_at | 생성 시각 |

### 7. AgentChainMember — 체인 구성원
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| chain_id | 체인 FK |
| agent_id | 에이전트 FK |
| seq_order | 실행 순서 |

### 8. Datasource — 데이터소스 연결정보
| 컬럼 | 코멘트 |
|-------|--------|
| id | 자동생성 시퀀스 |
| datasource_id | 데이터소스 고유 ID (PK) |
| datasource_name | 데이터소스명 |
| db_type | DB 유형 (POSTGRESQL/MYSQL/ORACLE) |
| host | 호스트 주소 |
| port | 포트 번호 |
| database_name | 데이터베이스명 |
| username | 접속 계정 (암호화) |
| password | 접속 비밀번호 (암호화) |
| description | 설명 |
| zone | 네트워크 존 |
| is_active | 활성화 여부 |
| created_at | 생성 시각 |
| updated_at | 수정 시각 |

### 9. DatasourceTable — 데이터소스 등록 테이블
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| datasource_id | 데이터소스 ID |
| table_name | 테이블명 |
| table_alias | 테이블 별칭 |
| description | 설명 |
| created_at | 생성 시각 |

### 10. DatasourceColumn — 데이터소스 테이블 컬럼
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| datasource_table_id | 테이블 FK |
| column_name | 컬럼명 |
| column_alias | 컬럼 별칭 |
| data_type | 데이터 타입 |
| is_primary_key | PK 여부 |
| is_nullable | NULL 허용 여부 |
| description | 설명 |

### 11. ExecutionHistory — 실행 이력
| 컬럼 | 코멘트 |
|-------|--------|
| execution_id | 실행 ID (PK) |
| agent_code | 에이전트 코드 |
| agent_name | 에이전트명 |
| status | 실행 상태 (RUNNING/SUCCESS/FAILED) |
| total_read_count | 읽기 건수 |
| total_write_count | 쓰기 건수 |
| total_skip_count | 스킵 건수 |
| duration_ms | 소요 시간 (ms) |
| error_message | 오류 메시지 |
| started_at | 실행 시작 시각 |
| finished_at | 실행 종료 시각 |
| triggered_by | 실행 주체 (MANUAL/SCHEDULE/CHAIN) |
| agent_type | 에이전트 유형 |

### 12. ExecutionStepHistory — 실행 스텝 이력
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| execution_id | 실행 이력 FK |
| step_id | 스텝 ID |
| status | 스텝 상태 (SUCCESS/FAILED/SKIPPED) |
| read_count | 읽기 건수 |
| write_count | 쓰기 건수 |
| skip_count | 스킵 건수 |
| duration_ms | 소요 시간 (ms) |
| error_message | 오류 메시지 |
| step_order | 스텝 순서 |

### 13. Schedule — 스케줄 설정
| 컬럼 | 코멘트 |
|-------|--------|
| schedule_id | PK |
| agent_id | 에이전트 FK |
| cron_expression | Cron 표현식 |
| is_enabled | 활성화 여부 |
| execution_options | 실행 옵션 JSON |
| created_at | 생성 시각 |

### 14. ZoneConfig — 네트워크 존 설정
| 컬럼 | 코멘트 |
|-------|--------|
| zone | 존 이름 (PK) |
| short_code | 존 약어 (E/D/IC/IS) |
| proxy_agent_url | 프록시 에이전트 URL |
| description | 설명 |
| is_active | 활성화 여부 |
| created_at | 생성 시각 |
| updated_at | 수정 시각 |

---

## B. Agent 프로젝트 엔티티 (11개)

### 15. Execution (common) — 파이프라인 실행 이력
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| execution_id | 실행 고유 ID (UUID) |
| parent_execution_id | 부모 실행 ID (Orchestrator 발행) |
| agent_id | 에이전트 코드 |
| status | 실행 상태 (RUNNING/SUCCESS/FAILED) |
| total_read_count | 총 읽기 건수 |
| total_write_count | 총 쓰기 건수 |
| total_skip_count | 총 스킵 건수 |
| duration_ms | 소요 시간 (ms) |
| error_message | 오류 메시지 |
| started_at | 실행 시작 시각 |
| finished_at | 실행 종료 시각 |
| source_datasource_id | 소스 데이터소스 ID |
| target_datasource_id | 타겟 데이터소스 ID |

### 16. SyncLog (common) — 동기화 처리 로그
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| execution_id | 실행 ID (execution 참조) |
| step_id | Step 식별자 |
| table_name | 처리 대상 테이블명 |
| table_type | 테이블 유형 (SOURCE/IF/TARGET) |
| success_count | 성공 건수 |
| failed_count | 실패 건수 |
| skip_count | 스킵 건수 |
| failed_keys | 실패 키 목록 |
| error_summary | 오류 요약 |
| source_pk_column | 소스 PK 컬럼명 |
| created_at | 로그 생성 시각 |

### 17. SecJewonView (bojo/source) — 외부 소스 제원 뷰
| 컬럼 | 코멘트 |
|-------|--------|
| obsv_code | 관측소 코드 (PK) |
| obsv_name | 관측소 명칭 |
| well | 관정 번호 |
| sido | 시도 |
| sigungu | 시군구 |
| upmyundo | 읍면동 |
| bunji | 번지 |
| ri | 리 |
| x | 경도 (X좌표) |
| y | 위도 (Y좌표) |
| pyogo | 표고 (지반고, m) |
| insdate | 설치일 |
| guldep | 굴착 깊이 (m) |
| guldia | 굴착 지름 (mm) |
| regdate | 등록일 |
| casing_height | 케이싱 높이 (m) |

### 18. SecObsvdataView (bojo/source) — 외부 소스 관측데이터 뷰
| 컬럼 | 코멘트 |
|-------|--------|
| obsv_code | 관측소 코드 (복합PK) |
| obsv_date | 관측 일자 (복합PK) |
| obsv_time | 관측 시각 (복합PK) |
| gwdep | 지하수위 (m) |
| gwtemp | 지하수온도 (°C) |
| ec | 전기전도도 (μS/cm) |
| remark | 비고 |

### 19. IfRsvSecJewon (bojo/if_rsv) — IF_RSV 제원
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| obsv_code | 관측소 코드 |
| obsv_name | 관측소 명칭 |
| well | 관정 번호 |
| sido~ri | (17과 동일) |
| x, y | 경도, 위도 |
| pyogo | 표고 (m) |
| insdate | 설치일 |
| guldep | 굴착 깊이 (m) |
| guldia | 굴착 지름 (mm) |
| regdate | 등록일 |
| casing_height | 케이싱 높이 (m) |
| source_refs | 원본 참조키 (UK, E:dsId:tableId:pk) |
| link_status | 처리 상태 (PENDING/SUCCESS/FAILED) |
| extracted_at | RCV 추출 시각 |
| updated_at | 최종 수정 시각 |
| execution_id | 처리 실행 ID |

### 20. IfRsvSecObsvdata (bojo/if_rsv) — IF_RSV 관측데이터
| 컬럼 | 코멘트 |
|-------|--------|
| id | PK |
| obsv_code | 관측소 코드 |
| obsv_date | 관측 일자 |
| obsv_time | 관측 시각 |
| gwdep | 지하수위 (m) |
| gwtemp | 지하수온도 (°C) |
| ec | 전기전도도 (μS/cm) |
| remark | 비고 |
| source_refs | 원본 참조키 (UK) |
| link_status | 처리 상태 (PENDING/SUCCESS/FAILED) |
| extracted_at | RCV 추출 시각 |
| updated_at | 최종 수정 시각 |
| execution_id | 처리 실행 ID |

### 21. IfSndSecJewon (bojo/if_snd) — IF_SND 송신 제원
(19와 동일 컬럼 구조)

### 22. IfSndSecObsvdata (bojo/if_snd) — IF_SND 송신 관측데이터
(20과 동일 컬럼 구조)

### 23. SecJewon (bojo/target) — Target 제원
(19와 동일 컬럼 + id가 Long IDENTITY)

### 24. SecObsvdata (bojo/target) — Target 관측데이터
(20과 동일 컬럼 + id가 Integer IDENTITY)

### 25. LinkNgwis (bojo/target) — 동기화 시점 추적
| 컬럼 | 코멘트 |
|-------|--------|
| obsv_code | 관측소 코드 (PK) |
| obsv_date | 관측 일자 |
| obsv_time | 관측 시각 |
| update_time | 업데이트 시각 |

---

## 수정 대상 파일

### Orchestrator Backend (14개)
- `domain/agent/Agent.java`
- `domain/agent/AgentExecutionMode.java`
- `domain/agent/AgentExecutionParam.java`
- `domain/agent/AgentStepDefinition.java`
- `domain/agent/AgentTable.java`
- `domain/chain/AgentChain.java`
- `domain/chain/AgentChainMember.java`
- `domain/datasource/Datasource.java`
- `domain/datasource/DatasourceTable.java`
- `domain/datasource/DatasourceColumn.java`
- `domain/execution/ExecutionHistory.java`
- `domain/execution/ExecutionStepHistory.java`
- `domain/schedule/Schedule.java`
- `domain/zone/ZoneConfig.java`

### Agent Common (2개)
- `entity/Execution.java`
- `entity/SyncLog.java`

### Agent Bojo (9개)
- `entity/source/SecJewonView.java`
- `entity/source/SecObsvdataView.java`
- `entity/iftable/rsv/IfRsvSecJewon.java`
- `entity/iftable/rsv/IfRsvSecObsvdata.java`
- `entity/iftable/snd/IfSndSecJewon.java`
- `entity/iftable/snd/IfSndSecObsvdata.java`
- `entity/target/SecJewon.java`
- `entity/target/SecObsvdata.java`
- `entity/target/LinkNgwis.java`

## 영향 범위
- ddl-auto: update → 앱 재시작 시 PostgreSQL COMMENT 자동 반영
- 코드 로직 변경 없음 (어노테이션만 추가)
- common JAR 수정 시 → bojo, bojo-int 등에 JAR 복사 필요
