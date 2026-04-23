# provide Agent 리팩토링 + Type A 나머지 이식 (TM_GD112002 / TM_GD120001)

> 작성일: 2026-04-23 (전면 개정)
> 선행: `dev_plan/2026_04/22/provide-source-table-strategy.md`, `dev_logs/2026_04/2026-04-22.md`
> 관련 메모리: `feedback_provide_layer_upsert.md`
> 목적: Type A 이식을 계기로 **provide 계층 UK 규약을 `source_refs`로 통일**하는 리팩토링 + 112002/120001 이식

---

## 0. 오늘 작업 전체 요약 (7 Phase)

| Phase | 내용 | 시간 |
|:---:|------|:---:|
| 0 | `ProvideLoadStep.java` conflict key를 자연키 → `source_refs` 고정 | 10분 |
| 1 | provide 엔티티 16개 전부 통일: `@Id=sn IDENTITY` + `@UniqueConstraint(source_refs)` + 자연키 일반 컬럼화 | 1~2시간 |
| 2 | 기존 PG `api_prv_*` 테이블 drop → ddl-auto 재생성 준비 | 20분 |
| 3 | provide Agent 재빌드 + 재기동 (common 영향 없음) | 10분 |
| 4 | 회귀 검증: 000203, 130001 — Oracle LINK_STATUS PENDING 전환 후 재실행 | 30분~1시간 |
| 5 | TM_GD112002 DDL + E2E (본래 오늘 목표) | 1시간 |
| 6 | TM_GD120001 DDL + E2E (본래 오늘 목표) | 1시간 |

총 예상 4~6시간. **A4 / A7은 기존 계획대로 별도 이슈 보류**.

---

## 1. 수정 목적

### 1.1 근본 문제

Type A 이식 진행 중 ProvideLoadStep 설계 검토에서 발견:

1. **UK 불일치** — bojo Loader는 `source_refs` UK 기반 UPSERT, provide Loader는 자연키 UK 기반 UPSERT
2. **엔티티 혼재** — 16개 provide 엔티티가 Case A(@Id=자연키) 11개 + Case B(@Id=IDENTITY) 5개로 섞여 있고, Case B 중 UK 누락(112002, 110301)도 존재
3. **복합 merge-key 확장성** — Type B 전처리에서 복합 자연키 가능성. 자연키 UK는 `@EmbeddedId`/`@IdClass` 복잡, source_refs UK는 문자열 하나로 일관

### 1.2 해결 방향

provide 계층 전체를 **bojo Loader 패턴(source_refs UK)** 으로 통일. Type A 이식을 "올바른 예시"로 정립한 상태에서 진행.

### 1.3 근거

- `feedback_provide_layer_upsert.md` 메모리: provide Loader는 항상 UPSERT + UK. 외부 제공 계층 안정성 우선
- source_refs는 이미 추적 시스템의 "데이터 정체성 식별자" 역할 → UK 제약으로 DB가 강제
- bojo와 일관 패턴 → 인지 부하 감소, 향후 유지보수 용이

---

## 2. 핵심 변경 명세

### 2.1 ProvideLoadStep (Phase 0)

**변경 전** (`ProvideLoadStep.java:223-225`):
```java
String conflictKeys = mergeKeys.stream()
        .map(String::toLowerCase)
        .collect(Collectors.joining(", "));
```

**변경 후**:
```java
String conflictKeys = "source_refs";   // source_refs UK 통일 (2026-04-23)
```

**UPDATE SET 조정** (`ProvideLoadStep.java:227-234`):
- 기존: merge-key 제외한 모든 비즈니스 컬럼 + execution_id + source_refs + updated_at
- 변경: **source_refs 제외한 모든 비즈니스 컬럼** + execution_id + updated_at
  - source_refs가 conflict key니까 UPDATE 대상에서 빠짐

**merge-key의 역할 변경**:
- 기존: (a) source PK 조회 (b) source_refs 생성 (c) ON CONFLICT 키 (d) SyncLog sourcePkColumn
- 변경: (a) source PK 조회 (b) source_refs 생성 (d) SyncLog sourcePkColumn만 유지. ON CONFLICT에서 제거

**YAML은 변경 없음** — merge-key는 여전히 자연키 지정.

### 2.2 엔티티 구조 통일 (Phase 1)

**공통 신(新) 구조**:
```java
@Entity
@Table(name = "api_prv_xxx",
       uniqueConstraints = @UniqueConstraint(columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_xxx", comment = "...")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiPrvXxx {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    // 자연키는 일반 컬럼 (UK 없음)
    @Column(name = "prmsn_dclr_no", length = 30)
    @Comment("허가신고번호")
    private String prmsnDclrNo;

    // ... 기타 비즈니스 컬럼

    // ── 추적 컬럼 ──
    @Column(name = "execution_id", length = 100)
    @Comment("실행 ID")
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    @Comment("소스 참조")
    private String sourceRefs;

    @Column(name = "updated_at")
    @Comment("갱신 시각")
    private LocalDateTime updatedAt;
}
```

### 2.3 엔티티 16개 케이스별 변경

#### Case A (@Id=자연키) — 11개, @Id 교체 필요

| 엔티티 | 기존 @Id | 변경 내용 |
|--------|---------|---------|
| ApiPrvTmGd120001 | `gwelNo` (Long) | @Id → sn 이동, gwelNo는 일반 컬럼 |
| ApiPrvTmGd130001 | `isvrNo` (String) | @Id → sn 이동, isvrNo는 일반 컬럼 |
| ApiPrvPermwell | `relTrsmSggCd` | @Id → sn 이동, relTrsmSggCd는 일반 컬럼 |
| ApiPrvInspection | `ugwtrExmnCd` | @Id → sn 이동 |
| ApiPrvGeneral105 | `gwelNo` | @Id → sn 이동 |
| ApiPrvLinkageChart | `gwelNo` | @Id → sn 이동 |
| ApiPrvWaterQuality | `gwelNo` | @Id → sn 이동 |
| ApiPrvNgw04 | `wqInspSn` | @Id → sn 이동 |
| ApiPrvWaterQualityMfds | `wqInspSn` | @Id → sn 이동 |
| ApiPrvUnregitsFcly | `ctpvNm` | @Id → sn 이동 |
| ApiPrvWqInputStatusDj | `ctpvNm` | @Id → sn 이동 |

#### Case B (@Id=sn IDENTITY) — 5개, UK 추가/조정

| 엔티티 | 기존 UK | 변경 내용 |
|--------|--------|---------|
| ApiPrvTmGd000203 | `(ctpv_nm+sgg_nm+emd_nm+li_nm)` 복합 + `@Id sn` | source_refs UK **추가**. 복합 UK는 **비즈니스 정합성 차원에서 유지** (행정구역 중복 방지) |
| ApiPrvTmGd110301 | UK 없음 | source_refs UK 추가 |
| ApiPrvTmGd110302 | UK 없음 | source_refs UK 추가 |
| ApiPrvTmGd112002 | UK 없음 (본래 오늘 버그 수정 대상) | source_refs UK 추가 |
| ApiPrvActualUseDj | UK 없음 | source_refs UK 추가 |

### 2.4 기존 PG 테이블 drop + 재생성 (Phase 2)

`ddl-auto=update`는 UK 구조 변경을 신뢰할 수 없음 (Hibernate 제약). **drop 후 JPA가 엔티티 기반 재생성하게 둠**.

```sql
-- api_provider PG (port 29006, database: api_provider)
DROP TABLE IF EXISTS api_prv_tm_gd000203 CASCADE;
DROP TABLE IF EXISTS api_prv_tm_gd110301 CASCADE;
DROP TABLE IF EXISTS api_prv_tm_gd110302 CASCADE;
DROP TABLE IF EXISTS api_prv_tm_gd112002 CASCADE;
DROP TABLE IF EXISTS api_prv_tm_gd120001 CASCADE;
DROP TABLE IF EXISTS api_prv_tm_gd130001 CASCADE;
DROP TABLE IF EXISTS api_prv_actual_use_dj CASCADE;
DROP TABLE IF EXISTS api_prv_general_105 CASCADE;
DROP TABLE IF EXISTS api_prv_inspection CASCADE;
DROP TABLE IF EXISTS api_prv_linkage_chart CASCADE;
DROP TABLE IF EXISTS api_prv_ngw04 CASCADE;
DROP TABLE IF EXISTS api_prv_permwell CASCADE;
DROP TABLE IF EXISTS api_prv_unregits_fcly CASCADE;
DROP TABLE IF EXISTS api_prv_water_quality CASCADE;
DROP TABLE IF EXISTS api_prv_water_quality_mfds CASCADE;
DROP TABLE IF EXISTS api_prv_wq_input_status_dj CASCADE;
```

---

## 3. 수정 대상 파일 전체

### 3.1 코드 수정 (17개 파일)

| 구분 | 파일 | 변경 |
|------|------|------|
| Step | `sync-agent-provide/.../loader/step/ProvideLoadStep.java` | conflict key `source_refs` 고정 |
| 엔티티 | `sync-agent-provide/.../entity/target/ApiPrv*.java` 16개 | @Id sn 통일 + source_refs UK + 자연키 일반 컬럼화 |

### 3.2 신규 DDL (2개)

| 파일 | 내용 |
|------|------|
| `scripts/ddl/internal-oracle/provide-source/TM_GD112002.sql` | 34컬럼 + 추적컬럼 + 주석 + 샘플 3~5건 |
| `scripts/ddl/internal-oracle/provide-source/TM_GD120001.sql` | 33컬럼 + 추적컬럼 + 주석 + 샘플 3~5건 |

### 3.3 영향 없음

- common JAR 재빌드 불필요 (provide 내부 수정만)
- bojo / bojo-int / others / api-collector 영향 없음
- Orchestrator / frontend 영향 없음
- YAML 수정 없음 (merge-key 자연키 그대로)

---

## 4. 작업 순서 (Phase별 상세)

### Phase 0 — ProvideLoadStep 수정 (10분)

1. `ProvideLoadStep.java:223-225` conflict key 하드코딩
2. UPDATE SET 대상 재조정 (source_refs 제외, 나머지 비즈니스 + execution_id + updated_at)
3. 주석 갱신: "source_refs UK 기반 UPSERT로 통일 (2026-04-23)"

### Phase 1 — 엔티티 16개 통일 (1~2시간)

Case A (11개) 작업 흐름:
1. 기존 @Id 필드를 일반 @Column으로 변경
2. 맨 위에 `@Id @GeneratedValue(IDENTITY) private Long sn` 추가
3. @Table에 `uniqueConstraints = @UniqueConstraint(columnNames = {"source_refs"})` 추가
4. 주석/Lombok 유지

Case B (5개) 작업 흐름:
1. @Table에 source_refs UK 추가 (000203은 기존 복합 UK와 병존)
2. 기타 변경 없음

파일당 1~3분 × 16개 = 30~50분 예상.

### Phase 2 — PG 테이블 drop (20분)

```bash
docker exec -i gims_api_provider_pg psql -U k1m -d api_provider < /tmp/drop_api_prv_tables.sql
```

drop 후 확인: `\dt api_prv_*` → 비어 있어야 함.

### Phase 3 — 재빌드 + 재기동 (10분)

```bash
cd sync-agent-provide
./gradlew clean build -x test
# 재기동 (기존 프로세스 종료 후 새로 띄움)
```

재기동 로그에서 **16개 엔티티 기반 테이블 재생성 확인**:
- `create table api_prv_* ...`
- `alter table api_prv_* add constraint ...`

### Phase 4 — 회귀 검증 (30분~1시간)

1. Oracle LINK_STATUS 되돌리기:
   ```sql
   UPDATE TM_GD000203 SET LINK_STATUS = 'PENDING';
   UPDATE TM_GD130001 SET LINK_STATUS = 'PENDING';
   COMMIT;
   ```
2. `provide-tm-gd000203` 실행 → 11건 적재 확인 → 정/역방향 추적 확인
3. `provide-tm-gd130001` 실행 → 6건 적재 확인 → 정/역방향 추적 확인

### Phase 5 — TM_GD112002 이식 (1시간)

1. `TM_GD112002.sql` 작성 (130001 패턴, 34컬럼, PRMSN_DCLR_NO NULLABLE 유지)
2. Oracle 실행: `docker exec -i gims_orchestrator_inner_oracle sqlplus k1m/1111@//localhost:1521/XEPDB1 < .../TM_GD112002.sql`
3. 샘플 3~5건 PENDING 상태 확인
4. `provide-tm-gd112002` 실행 → PG 적재 확인
5. 정/역방향 추적 확인

### Phase 6 — TM_GD120001 이식 (1시간)

Phase 5와 동일 패턴 (33컬럼, GWEL_NO NOT NULL PK 유지).

---

## 5. 검증 체크리스트

### Phase 0~3 (리팩토링)
- [ ] ProvideLoadStep 수정 확인 (`grep "source_refs" ProvideLoadStep.java`)
- [ ] provide 재빌드 성공 (`./gradlew clean build -x test`)
- [ ] 16개 엔티티 테이블 재생성 로그 확인
- [ ] PG에서 UK 제약 확인: `\d api_prv_tm_gd000203` → source_refs UK 존재

### Phase 4 (회귀)
- [ ] Oracle LINK_STATUS PENDING 전환 확인
- [ ] `provide-tm-gd000203` 실행 → Oracle 11건 → PG 11건 적재
- [ ] `provide-tm-gd130001` 실행 → Oracle 6건 → PG 6건 적재
- [ ] 양쪽 Agent에 대해 정방향(Source→Target) / 역방향(Target→Source) 추적 정상

### Phase 5~6 (신규 이식)
- [ ] 112002 Oracle DDL 실행 → 샘플 PENDING 상태 확인
- [ ] 112002 Agent 실행 → PG 적재 → 정/역방향 추적
- [ ] 120001 Oracle DDL 실행 → 샘플 PENDING 상태 확인
- [ ] 120001 Agent 실행 → PG 적재 → 정/역방향 추적

---

## 6. 리스크 / 주의사항

| 항목 | 대응 |
|------|------|
| @Id 교체로 기존 DB 마이그레이션 부담 | **drop 후 재생성**으로 간결 처리 (개발 환경) |
| ddl-auto update가 UK 추가/변경 누락 | drop-recreate로 강제 |
| ApiPrvTmGd000203 복합 UK(행정구역) 의미 | 비즈니스 정합성으로 **유지**. source_refs UK와 공존 |
| 기존 000203/130001 데이터 삭제 | 회귀에서 Oracle PENDING 전환 + 재실행으로 복원 |
| 16개 엔티티 수정 실수 | 기계적 변경이지만 기존 테스트 회귀로 확인 |
| provide 재기동 시 엔티티 매핑 에러 | 재기동 로그 주시 (Hibernate 스키마 검증) |

### 회귀가 깨진 경우 (상상 가능한 시나리오)

- 기존 데이터와 새 엔티티 매핑 오류 → Hibernate 로그에서 즉시 포착 가능
- source_refs UK 충돌 (기존 000203의 복합 UK와 상호 간섭) → 동시에 선언되지만 실제로는 source_refs가 우선. 복합 UK는 별도 인덱스로 존재
- 롤백: 기존 ProvideLoadStep 커밋으로 revert 후 PG 재drop

---

## 7. 완료 후 할 일

- `dev_logs/2026_04/2026-04-23.md` 작성 (오전: 리팩토링 / 오후: Type A 이식)
- A4 (SDE_NGWS) / A7 (TMP_MEGOKR_API)는 기존 방침대로 별도 이슈
- Type B 전처리 Step 구현 이슈 (PreprocessLoadStepFactory)는 이제 **source_refs UK 기반으로 시작 가능** — 구조적 이득
- 커밋 제안 (docs 동기화 이미 완료, 메모리 포함)

---

## 8. 확정 사항 (사용자 승인 완료)

1. ✅ 오늘 확정 범위 = TM_GD112002 + TM_GD120001 이식
2. ✅ A4 (SDE_NGWS) 별도 이슈 이관
3. ✅ A7 (TMP_MEGOKR_API) 담당자 확인 후 보류
4. ✅ TM_GD112002 DDL에 `PRMSN_DCLR_NO` PK 제약 없음 (NULLABLE)
5. ✅ ProvideLoadStep UPSERT 기본 설계 유지 → **단, UK 기준을 source_refs로 통일 (범위 확대)**
6. ✅ 엔티티 수정 범위 = **16개 전부**
7. ✅ Oracle LINK_STATUS UPDATE SQL 직접 실행 OK

---

## 9. 진행 승인 요청

위 Phase 0~6 순서로 바로 착수하시겠습니까? 승인 주시면 Phase 0 (ProvideLoadStep 수정)부터 시작합니다.
