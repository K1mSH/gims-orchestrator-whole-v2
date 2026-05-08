---
id: VER-014
title: PG/Oracle DDL 의 숫자 컬럼 precision 명시화 (Tibero NUMBER(p,s) 운영 정합)
status: OPEN
created: 2026-05-08
parts: [P2-bojo, P4-others, P5-jeju, schema-design, type-mapping]
parallel_safe: true
assignee: forward
related: [bojo-internal-if-entity-standardization-fix]
---

## 증상 요약

운영 환경의 외부 DMZ 제원/관측데이터 DB는 **Tibero**이고 실제 컬럼 정의는 `NUMBER(8,2)` / `NUMBER(7,2)` / `NUMBER(6,1)` 같이 **precision/scale 을 명시**한 상태. 우리 dev 환경은 PG (`double precision`) + Oracle (`NUMBER` precision/scale 미지정) 로 운용 중이라 운영 환경 정의가 정확히 보존되지 않음.

5/8 03-bojo Step 5 fix (`bojo-internal-if-entity-standardization-fix`) 진행 중 외부 운영 환경 정보 입수.

## 현재 상태 (dev)

| 위치 | 컬럼 type | 비고 |
|---|---|---|
| 외부 PG view (`dev.sec_obsvdata_view.gwdep` 등) | `double precision` | IEEE 754, 약 15 유효 자리. sensor 정밀도엔 충분 |
| DMZ IF/Target (PG) | `double precision` | 동일 |
| Internal Oracle DDL (`IF_RSV_SEC_OBSVDATA.GWDEP` 등) | `NUMBER` (precision/scale 미지정) | 임의 정밀도. storage 효율/스키마 명확성 떨어짐 |
| Java Entity | `Double` (5/8 fix 후) | DMZ baseline 정합 |

## 운영 환경 (Tibero)

| 위치 | 컬럼 type | 비고 |
|---|---|---|
| 원본 외부 DB | `NUMBER(8,2)` / `NUMBER(7,2)` / `NUMBER(6,1)` 등 | 정수 자릿수 + 소수 자릿수 명시. 십진 정확. |

## 영향 / 위험

- **dev → prod 마이그레이션 시 type 미스매치 위험** — 운영 Tibero 의 정확한 십진 표현이 dev 의 `double precision` IEEE 754 binary 와 1:1 정합 X
- **누적 산술 연산 시 binary 오차** — 현재 IF/Loader 흐름은 단순 SELECT/INSERT 라 누적 연산 X 라 영향 없으나, 향후 분석 / 통계 / 비교 코드 추가 시 위험
- **Oracle DDL 의 precision 미지정** — storage 필요 이상 / 스키마 의도 모호 / 실 운영 Tibero 와 정합 약함
- **Entity 의 BigDecimal 검토** — Tibero `NUMBER(p,s)` 와 1:1 정합 위해 `Double` → `BigDecimal` 전환은 현재 fix(Long→Double) 와 별 의제

## 처리 방향 (옵션)

### A. dev DB 를 운영 Tibero 와 정확 정합 (PG numeric(p,s) + Oracle NUMBER(p,s))

- PG `double precision` → `numeric(p,s)` (Tibero 정의 그대로)
- Oracle `NUMBER` → `NUMBER(p,s)` 명시
- Java Entity `Double` → `BigDecimal`
- **영향**: 모든 entity (DMZ + Internal) + 사용처 코드 (toDouble 등) + 비교/연산 코드. 큼.
- **장점**: 운영 정합 완벽. 정확 십진 보존.

### B. dev 는 `double precision` 유지, Java Entity 도 Double 유지

- 현재 상태 유지
- 운영 Tibero 와 dev PG 가 type 표현 layer 다르지만 sensor 정밀도엔 영향 0
- **장점**: 영향 0 / 즉시 운영 가능
- **단점**: 운영 환경 정의가 명시적이지 않음. 향후 분석 코드 추가 시 위험.

### C. Oracle 만 NUMBER(p,s) 명시화 (PG 는 그대로)

- Internal Oracle DDL 만 운영 Tibero 와 정합
- DMZ PG 는 dev 편의로 double precision 유지
- **장점**: 최종 Target (Internal Oracle) 만 운영 정합
- **단점**: DMZ 일관성 깨짐, Java Entity 양쪽 매핑 차이 발생

## 결정 시점

1차 반입 (1차 통합 테스트) 완료 후 **운영 환경 마이그레이션 설계 사이클**에서 일괄 재논의. 표준화 문서 갱신 + DDL 갱신 + Entity 갱신 동시 결정.

## 참고

- `dev_plan/2026_05/08/bojo-internal-if-entity-standardization-fix.md` §5 별 사이클 명시
- `dev_plan/2026_04/06/표준화_컬럼매핑.md` (보조망 4 Target 표준)
- `scripts/ddl/internal-oracle/jpa-generated-ddl.sql` (현재 NUMBER precision 미지정)
