# bojo-int Entity 전환 계획

> 작성일: 2026-04-13
> 상태: 계획
> 선행 작업: I3 구현 전에 완료 필수

## 배포 시 DDL 관리 전략

| DB | 소유 | DDL 관리 방식 | 비고 |
|----|------|-------------|------|
| **새올 Tibero (실서버)** | 새올 시스템 | RGET* 원본 = 안 건드림 | 우리 소유 아님 |
| **새올 Tibero IF_SND** | 우리 | **DDL 스크립트 직접 관리** (`scripts/`) → 배포 시 수동 실행 | 실서버에 JPA 없음 |
| **DMZ PG** | 우리 | JPA `ddl-auto` 관리 가능 | bojo, others, api-collector |
| **내부 Oracle (실서버)** | 우리 | **엔티티 기반 DDL 스크립트 관리** (`scripts/`) → 배포 시 수동 실행 | bojo-int |

### 모듈별 테이블 소유 현황

```
DMZ PG (dev DB)
├── bojo 소유: sec_jewon, sec_obsvdata, link_ngwis, if_rsv_*, if_snd_sec_*
├── others 소유: if_snd_tb_jeju*, if_snd_use_*, if_snd_rgetstgms01
├── api-collector 소유: rget*(4개), anyang_*, tm_gd014000, tm_gd014001
└── 제주/이용량 source: tb_jeju*, use_*, rgetstgms01 (api-collector가 적재, others SND가 읽기)

새올 Oracle/Tibero (29005)
├── 새올 소유: RGET* 원본 16개 (우리가 읽기만)
└── 우리 소유: IF_SND_RGET* 16개 (DDL 스크립트로 관리)

내부 Oracle (29004)
├── IF_RSV 23개 (RCV가 적재, Loader가 읽기)
├── 새올 원본 RGET* 16개 (RCV가 적재)
├── Target TM/PM 13개 (Loader가 적재)
└── 시스템: EXECUTION, SYNC_LOG (common 엔티티)
```

### 새올 IF_SND DDL 스크립트 위치

새올 Tibero에 직접 파줘야 하는 테이블 (16개):
- `scripts/saeol-if-snd-ddl.sql` — IF_SND_RGET* 16개 CREATE TABLE
- 실서버 배포 시 DBA에게 전달하여 수동 실행
- 컬럼 구조: 원본 RGET* 컬럼 + IF 공통 6개 (id, source_refs, link_status, execution_id, extracted_at, updated_at)

## 문제

bojo(DMZ)는 `entity/source/`, `entity/iftable/`, `entity/target/` 구조로 JPA 엔티티를 관리하고,
**읽기=JPA, 쓰기=JDBCTemplate batch** 전략을 따르고 있다 (ARCHITECTURE.md 1.2.6절).

bojo-int(내부망)는 이 구조가 **전혀 없고**, 4개 Step 전부 raw SQL string + JdbcTemplate.queryForList()로 되어 있다.
엔티티 디렉토리 자체가 존재하지 않음.

## 내부망 Oracle (29004) 전체 테이블 현황

### IF_RSV 테이블 (21개)

| # | 테이블 | 출처 | 현재 Step | 비고 |
|---|--------|------|----------|------|
| 1 | IF_RSV_SEC_JEWON | DMZ bojo SND→내부 RCV | InternalBojoLoadStep | 관측 제원 |
| 2 | IF_RSV_SEC_OBSVDATA | DMZ bojo SND→내부 RCV | InternalBojoLoadStep | 관측 데이터 |
| 3 | IF_RSV_TB_JEJU_JEWON | DMZ 제주 SND→내부 RCV | I1 JejuJewonLoadStep | 제주 제원 |
| 4 | IF_RSV_TB_JEJU | DMZ 제주 SND→내부 RCV | I2 JejuObsvdataLoadStep | 제주 관측 |
| 5 | IF_RSV_USE_LEGACY_DATA | DMZ 이용량 SND→내부 RCV | I5 UseLoadStep | 이용량 레거시 |
| 6 | IF_RSV_USE_STATUS_DATA | DMZ 이용량 SND→내부 RCV | I5 UseLoadStep | 이용량 현황 |
| 7 | IF_RSV_USE_JEJU_DAY | DMZ 제주 이용량 | 미구현 | 제주 이용량 일자료 |
| 8 | IF_RSV_RGETHKMIR01 | 새올 SND→내부 RCV | 미구현 | 행정처분이력 |
| 9 | IF_RSV_RGETNJHMS01 | 새올 SND→내부 RCV | 미구현 | 시추업체 |
| 10 | IF_RSV_RGETNKCNO01 | 새올 SND→내부 RCV | 미구현 | 굴착신고 |
| 11 | IF_RSV_RGETNKMTB01 | 새올 SND→내부 RCV | 미구현 | 계량기이력 |
| 12 | IF_RSV_RGETNMNFE01 | 새올 SND→내부 RCV | 미구현 | 시설물관리 |
| 13 | IF_RSV_RGETNOPMS01 | 새올 SND→내부 RCV | 미구현 | 허가신고기본 |
| 14 | IF_RSV_RGETNPMMS01 | 새올 SND→내부 RCV | 미구현 | 양수시험 |
| 15 | IF_RSV_RGETNSCKT01 | 새올 SND→내부 RCV | 미구현 | 착정 |
| 16 | IF_RSV_RGETNSIMS01 | 새올 SND→내부 RCV | 미구현 | 영향조사 |
| 17 | IF_RSV_RGETNTGMS02 | 새올 SND→내부 RCV | 미구현 | 수질검사 |
| 18 | IF_RSV_RGETNWAMS01 | 새올 SND→내부 RCV | 미구현 | 원상복구 |
| 19 | IF_RSV_RGETNWAVI05 | 새올 SND→내부 RCV | 미구현 | 수위관측자료1 |
| 20 | IF_RSV_RGETNWAVI06 | 새올 SND→내부 RCV | 미구현 | 수위관측자료2 |
| 21 | IF_RSV_RGETNYCSG01 | 새올 SND→내부 RCV | 미구현 | 이용량계측 |
| 22 | IF_RSV_RGETNYYMS01 | 새올 SND→내부 RCV | 미구현 | 연차보고 |
| 23 | IF_RSV_RGETSTGMS01 | 새올 SND→내부 RCV (API수집) | I3 예정 | 이용실태 |

### 새올 원본 테이블 (16개)

| # | 테이블 | 설명 |
|---|--------|------|
| 1 | RGETHKMIR01 | 행정처분이력 |
| 2 | RGETNJHMS01 | 시추업체 |
| 3 | RGETNKCNO01 | 굴착신고 |
| 4 | RGETNKMTB01 | 계량기이력 |
| 5 | RGETNMNFE01 | 시설물관리 |
| 6 | RGETNOPMS01 | 허가신고기본 |
| 7 | RGETNPMMS01 | 양수시험 |
| 8 | RGETNSCKT01 | 착정 |
| 9 | RGETNSIMS01 | 영향조사 |
| 10 | RGETNTGMS02 | 수질검사 |
| 11 | RGETNWAMS01 | 원상복구 |
| 12 | RGETNWAVI05 | 수위관측자료1 |
| 13 | RGETNWAVI06 | 수위관측자료2 |
| 14 | RGETNYCSG01 | 이용량계측 |
| 15 | RGETNYYMS01 | 연차보고 |
| 16 | RGETSTGMS01 | 이용실태 |

### Target 테이블 (13개)

| # | 테이블 | 설명 | 사용 Step |
|---|--------|------|----------|
| 1 | TM_GD970001 | ODM관측소 | I1 MERGE, InternalBojo READ |
| 2 | TM_GD120001 | 관정 | I1 MERGE |
| 3 | TM_GD970130 | ODM관정사양 | I1 MERGE |
| 4 | TM_GD970002 | ODM관측소사양 | I1 MERGE |
| 5 | TM_GD970101 | ODM결과 | I1 MERGE, I2/InternalBojo READ+INSERT |
| 6 | TM_GD980002 | Link테이블 | InternalBojo batch UPSERT |
| 7 | PM_GD970201 | 관측자료(단일심도) | I2 INSERT, InternalBojo batch INSERT |
| 8 | PM_GD970202 | 관측자료(다심도) | I2 INSERT |
| 9 | TM_GD111010 | 이용량시설 | I5 READ, I3 예정 MERGE |
| 10 | PM_GD111021 | 이용량시간자료 | I5 MERGE |
| 11 | PM_GD111022 | 이용량일자료 | I5 MERGE, I3 예정 MERGE |
| 12 | TM_GD111024 | 최근수신현황 | I5 MERGE |
| 13 | TM_GD111025 | 관측데이터 | I5 INSERT |

### 시스템 테이블 (2개 — common 모듈에 이미 엔티티 있음)

| 테이블 | 엔티티 | 모듈 |
|--------|--------|------|
| EXECUTION | Execution.java | sync-agent-common |
| SYNC_LOG | SyncLog.java | sync-agent-common |

## 전환 범위 (총 52개 엔티티)

### Entity 디렉토리 구조

```
sync-agent-bojo-int/src/main/java/com/sync/agent/bojoint/entity/
├── iftable/                      (23개)
│   ├── IfRsvSecJewon.java         ← IF_RSV_SEC_JEWON
│   ├── IfRsvSecObsvdata.java      ← IF_RSV_SEC_OBSVDATA
│   ├── IfRsvJejuJewon.java        ← IF_RSV_TB_JEJU_JEWON
│   ├── IfRsvJeju.java             ← IF_RSV_TB_JEJU
│   ├── IfRsvUseLegacy.java        ← IF_RSV_USE_LEGACY_DATA
│   ├── IfRsvUseStatus.java        ← IF_RSV_USE_STATUS_DATA
│   ├── IfRsvUseJejuDay.java       ← IF_RSV_USE_JEJU_DAY
│   └── saeol/                     (16개)
│       ├── IfRsvRgethkmir01.java
│       ├── IfRsvRgetnjhms01.java
│       ├── IfRsvRgetnkcno01.java
│       ├── IfRsvRgetnkmtb01.java
│       ├── IfRsvRgetnmnfe01.java
│       ├── IfRsvRgetnopms01.java
│       ├── IfRsvRgetnpmms01.java
│       ├── IfRsvRgetnsckt01.java
│       ├── IfRsvRgetnsims01.java
│       ├── IfRsvRgetntgms02.java
│       ├── IfRsvRgetnwams01.java
│       ├── IfRsvRgetnwavi05.java
│       ├── IfRsvRgetnwavi06.java
│       ├── IfRsvRgetnycsg01.java
│       ├── IfRsvRgetnyyms01.java
│       └── IfRsvRgetstgms01.java
├── source/                        (16개 — 새올 원본)
│   ├── Rgethkmir01.java
│   ├── Rgetnjhms01.java
│   ├── Rgetnkcno01.java
│   ├── Rgetnkmtb01.java
│   ├── Rgetnmnfe01.java
│   ├── Rgetnopms01.java
│   ├── Rgetnpmms01.java
│   ├── Rgetnsckt01.java
│   ├── Rgetnsims01.java
│   ├── Rgetntgms02.java
│   ├── Rgetnwams01.java
│   ├── Rgetnwavi05.java
│   ├── Rgetnwavi06.java
│   ├── Rgetnycsg01.java
│   ├── Rgetnyyms01.java
│   └── Rgetstgms01.java
└── target/                        (13개)
    ├── TmGd970001.java
    ├── TmGd120001.java
    ├── TmGd970130.java
    ├── TmGd970002.java
    ├── TmGd970101.java
    ├── TmGd980002.java
    ├── PmGd970201.java
    ├── PmGd970202.java
    ├── TmGd111010.java
    ├── PmGd111021.java
    ├── PmGd111022.java
    ├── TmGd111024.java
    └── TmGd111025.java
```

## Entity 공통 패턴

### IF 엔티티 (bojo IfRsvSecJewon 참고)
```java
@Entity
@Table(name = "IF_RSV_XXX",
       indexes = @Index(name = "IDX_IF_RSV_XXX_EID", columnList = "execution_id"),
       uniqueConstraints = @UniqueConstraint(name = "UK_IF_RSV_XXX_SREFS", columnNames = "source_refs"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IfRsvXxx {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // 비즈니스 컬럼들...

    @Column(name = "source_refs", columnDefinition = "TEXT")
    private String sourceRefs;

    @Builder.Default
    @Column(name = "link_status", length = 20)
    private String linkStatus = "PENDING";

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

### Source 엔티티 (새올 원본 — Read Only)
```java
@Entity
@Table(name = "RGETXXXXX")
@Getter @Setter @NoArgsConstructor
public class Rgetxxxxx {
    @Id
    // 복합키인 경우 @IdClass 또는 @EmbeddedId
    // 컬럼 정의는 oracle_meta.txt 참조
}
```

### Target 엔티티
```java
@Entity
@Table(name = "TM_GD970001")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TmGd970001 {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BRNCH_ID")
    private Long brnchId;

    // 비즈니스 컬럼들...

    @Column(name = "EXECUTION_ID", length = 255)
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;
}
```

## 컬럼 정보 소스

| 엔티티 그룹 | 컬럼 정보 출처 |
|------------|--------------|
| IF_RSV (비새올 7개) | 기존 DDL 스크립트 (`scripts/oracle-init-if.sql` 등) |
| IF_RSV 새올 (16개) | 새올 원본 + 공통 IF 컬럼 (id, source_refs, link_status, execution_id, extracted_at, updated_at) |
| Source 새올 (16개) | `scripts/oracle_meta.txt` (전체 컬럼 정의) |
| Target (13개) | 기존 DDL 스크립트 + `표준화_컬럼매핑.md` |

## 인프라 추가

| 파일 | 변경 | 설명 |
|------|------|------|
| **DynamicEntityManagerService.java** | 신규 | bojo에서 복제, 패키지 스캔 경로 변경 |
| **CaseAwareNamingStrategy.java** | 신규 | bojo에서 복제 (Oracle 대소문자 처리) |

## Step 리팩터링 (4개)

**공통 변경사항:**
- IF 조회: `queryForList("SELECT * FROM " + ifTable)` → JPA EntityManager 조회
- 캐시 조회: `queryForObject("SELECT BRNCH_ID FROM ...")` → JPA 조회
- row 접근: `getString(row, "OBSRVT_ID")` → `entity.getObsvtId()`
- **쓰기(MERGE/INSERT/batch)는 JDBC 그대로 유지**

| Step | 읽기 변경 | 쓰기 | 비고 |
|------|----------|------|------|
| I1 JejuJewonLoadStep | IF→JPA, Target 5개 조회→JPA | MERGE 5개 유지 | |
| I2 JejuObsvdataLoadStep | IF→JPA, brnch/rslt 캐시→JPA | INSERT 유지 | |
| I5 UseLoadStep | IF 2개→JPA, brnch 캐시→JPA | MERGE/INSERT 유지 | |
| InternalBojoLoadStep | GimsTargetRepository→JPA | batch INSERT/UPSERT 유지 | |

## 작업 순서

### Phase 1: 원본 DDL 백업 (검증 기준선)

엔티티 작성 전, 현재 테이블 구조를 원본으로 남겨둔다.

```bash
# 내부 Oracle — 전체 테이블 DDL 덤프
docker exec gims_orchestrator_inner_oracle bash -c "
echo '
SET LONG 50000
SET PAGESIZE 0
SET LINESIZE 300
SET FEEDBACK OFF
SELECT DBMS_METADATA.GET_DDL(\"TABLE\", table_name) FROM user_tables ORDER BY table_name;
' | sqlplus -S k1m/1111@//localhost:1521/XEPDB1
" > scripts/ddl/internal-oracle/original-ddl-backup.sql

# 새올 Oracle — IF_SND 테이블 DDL 덤프
docker exec gims_dmz_saeol_oracle bash -c "
echo '
SET LONG 50000
SET PAGESIZE 0
SET LINESIZE 300
SET FEEDBACK OFF
SELECT DBMS_METADATA.GET_DDL(\"TABLE\", table_name) FROM user_tables WHERE table_name LIKE \"IF_SND%\" ORDER BY table_name;
' | sqlplus -S k1m/1111@//localhost:1521/XEPDB1
" > scripts/ddl/saeol-tibero/original-if-snd-backup.sql
```

### Phase 2: 인프라 (2개)
1. DynamicEntityManagerService 추가
2. CaseAwareNamingStrategy 추가

### Phase 3: Entity 생성 (52개)
3. Target 엔티티 13개 — DDL 기준으로 컬럼 매핑
4. IF 비새올 엔티티 7개 — 기존 DDL 기준
5. IF 새올 엔티티 16개 — oracle_meta.txt 기준 + IF 공통 컬럼
6. Source 새올 엔티티 16개 — oracle_meta.txt 기준

### Phase 4: 엔티티 ↔ 원본 DDL 비교 검증

엔티티가 원본과 정확히 일치하는지 확인하는 과정.

**검증 절차:**
1. 기존 테이블 DROP (백업 확인 후)
2. `ddl-auto: create`로 앱 기동 → JPA가 엔티티 기반 테이블 재생성
3. 재생성된 DDL 덤프
4. 원본 DDL과 diff 비교

```bash
# 1. 기존 테이블 DROP (주의: 데이터 소실됨 — 테스트 환경에서만)
# DROP 스크립트는 별도 생성

# 2. application.yml 임시 변경
#    ddl-auto: validate → ddl-auto: create
#    앱 기동 → 테이블 재생성

# 3. 재생성된 DDL 덤프
docker exec gims_orchestrator_inner_oracle bash -c "..." > scripts/ddl/internal-oracle/jpa-generated-ddl.sql

# 4. 비교
diff scripts/ddl/internal-oracle/original-ddl-backup.sql \
     scripts/ddl/internal-oracle/jpa-generated-ddl.sql
```

**비교 기준:**
| 항목 | 일치해야 함 | 차이 허용 |
|------|-----------|----------|
| 테이블명 | O | |
| 컬럼명 | O | |
| 컬럼 타입/길이 | O | |
| NOT NULL 제약 | O | |
| PK 제약 | O | |
| 인덱스 | O | 인덱스명 차이는 허용 |
| IDENTITY/SEQUENCE | O | |
| 컬럼 순서 | | O (JPA 순서와 다를 수 있음) |
| COMMENT | | O (JPA가 생성하지 않음 — DDL 스크립트에서 별도 관리) |
| 테이블스페이스/스토리지 | | O (환경별 설정) |

**불일치 발견 시:** 엔티티 수정 → DROP → 재생성 → 재비교 (일치할 때까지 반복)

### Phase 5: Step 리팩터링 (4개)
7. GimsTargetRepository JPA 전환
8. InternalBojoLoadStep 리팩터링
9. JejuJewonLoadStep (I1) 리팩터링
10. JejuObsvdataLoadStep (I2) 리팩터링
11. UseLoadStep (I5) 리팩터링

### Phase 6: E2E 검증
12. `ddl-auto: create` → `ddl-auto: validate`로 복원
13. 빌드 테스트 (`./gradlew clean build -x test`)
14. E2E 테스트 (internal-bojo-loader 실행 → 기존과 동일 결과)

## 주의사항

- **ddl-auto: validate** — 엔티티 컬럼 ↔ 실제 테이블 정확히 매칭 필수
- **Oracle 대소문자** — 테이블/컬럼명 대문자, CaseAwareNamingStrategy 적용
- **복합키 테이블** — 새올 원본 중 복합 PK는 `@IdClass` 또는 `@EmbeddedId` 사용
- **IF_RSV 새올의 컬럼** — 원본 컬럼 + IF 공통 6개 (id, source_refs, link_status, execution_id, extracted_at, updated_at)
- **기존 동작 보장** — 리팩터링이지 기능 변경 아님. E2E 결과 달라지면 안 됨
- **미구현 Step의 엔티티도 생성** — 새올 Loader Step은 아직 없지만 엔티티는 미리 준비
- **새올 원본(RGET*) 엔티티는 Read Only** — 우리 소유가 아닌 테이블. ddl-auto로 절대 수정하면 안 됨

## DDL 스크립트 관리

엔티티로 관리하지만 실서버에는 JPA가 없으므로, 배포용 DDL 스크립트도 함께 관리한다.

### 디렉토리 정리

현재 `scripts/`에 DDL, 유틸, 등록SQL, 메타 등이 뒤섞여 있음. DDL을 별도 분리한다.

```
scripts/
├── ddl/
│   ├── saeol-tibero/           ← 새올 Tibero 배포용 (DBA 전달)
│   │   └── if-snd-tables.sql       IF_SND_RGET* 16개
│   ├── internal-oracle/        ← 내부 Oracle 배포용
│   │   ├── if-rsv-tables.sql       IF_RSV 23개
│   │   ├── target-tables.sql       Target TM/PM 13개
│   │   └── source-saeol-tables.sql 새올 원본 RGET* 16개
│   └── dmz-pg/                 ← DMZ PG (참고용, JPA ddl-auto 가능)
│       └── README.md               "JPA가 관리하므로 별도 DDL 불필요"
├── encrypt.sh
├── gen_if_snd.py
├── gen_internal_saeol.py
├── testdata.sh
├── oracle_meta.txt
├── saeol-oracle-ddl-raw.txt
└── 명령어.txt
```

기존 `scripts/oracle-init-*.sql` 파일들은 `ddl/internal-oracle/`로 통합 이동.

엔티티 변경 시 해당 DDL 스크립트도 반드시 동기화할 것.

## 수정 대상 파일 요약

| 구분 | 수량 | 내용 |
|------|------|------|
| Entity 신규 | 52개 | IF 23 + Source 16 + Target 13 |
| 인프라 신규 | 2개 | DynamicEntityManagerService, CaseAwareNamingStrategy |
| DDL 스크립트 | 1개 신규 + 기존 갱신 | saeol-if-snd-ddl.sql 신규, 기존 스크립트 동기화 |
| Step 수정 | 4개 | 읽기를 JPA로 전환 |
| Repository 수정 | 1개 | GimsTargetRepository JPA 전환 |
| **합계** | **60개** | 신규 55 + 수정 5 |
