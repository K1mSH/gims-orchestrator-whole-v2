# 폐쇄망 Nexus 호환 + 시스템 의존성 일관성 정리

**작성일**: 2026-05-06
**범위**: 전 모듈 build.gradle 의존성 정리 (자바 코드 수정 없음)
**전제**:
- 사내 폐쇄망 Nexus 보유 — `ojdbc8 19.3.0.0`만 보유, `ojdbc11` 미보유. Gradle `8.13` 보유.
- 다른 Spring Boot 2.7.12 ecosystem은 운영 중 (transitive 보유 가정).

**원칙** (사용자 명시 — 본 작업 외 향후 모든 작업에 적용):
> 우리 시스템 모듈끼리는 같은 의존성(GAV/버전)을 써야 한다. 모듈별 차이는 허용하지 않는다.

---

## 1. 목적

본 작업은 **운영 환경 = 폐쇄망 Nexus** 라는 제약에 맞춰, 현재 build.gradle이 BOM 기본값으로 끌어오는 의존성 중 **사내 미보유 GAV/버전을 사내 보유 GAV/버전으로 교체**한다.
자바 코드/리소스 수정은 일체 없음 — JDBC 표준 API만 사용하므로 ojdbc8/ojdbc11은 코드 호환됨.

---

## 2. 작업 항목

### A. ojdbc11 → ojdbc8 GAV 교체 (3 모듈)

사내 Nexus에 ojdbc11 미보유 → ojdbc8로 교체.

| 모듈 | build.gradle 변경 |
|------|-------------------|
| sync-agent-bojo-int | `runtimeOnly 'com.oracle.database.jdbc:ojdbc11'` → `runtimeOnly 'com.oracle.database.jdbc:ojdbc8'` |
| sync-proxy-internal | 동일 |
| sync-orchestrator/backend | 동일 |

**호환성 근거**:
- ojdbc8/ojdbc11은 동일 기능, Java 호환 baseline만 다름 (8 / 11)
- Java 17에서 ojdbc8 정상 동작 (Oracle 공식 호환 매트릭스)
- 자바 코드는 `java.sql.*`/`javax.sql.*` 표준 API만 사용 — 패키지 import 변경 없음

### B. Oracle JDBC 버전 강제 (7 모듈, 직접 명시 ⇐ 실제 적용)

**최초 계획**: `ext['oracle-database.version'] = '19.3.0.0'` BOM property override.
**실제 적용**: 직접 명시. 이유 — Oracle은 19.3.0.0에 ojdbc-bom을 배포하지 않음 (ojdbc-bom은 21.x부터). Boot BOM이 ojdbc-bom 의존하므로 override 시 빌드 실패.

```gradle
runtimeOnly 'com.oracle.database.jdbc:ojdbc8:19.3.0.0'
```

대상 모듈 (Oracle JDBC 사용 7개):
- sync-agent-bojo-int
- sync-agent-others
- sync-agent-provide
- sync-proxy-dmz
- sync-proxy-internal
- sync-orchestrator/backend
- gims-api-provider

> 비대상: sync-agent-bojo (DMZ 자체 — PG/MySQL만), sync-agent-common, sync-orchestrator-auth, infolink-api-collector

### D. sync-agent-common BOM 적용 — 명시 버전 떼기

현재 java-library + 명시 버전 패턴. 사내 폐쇄망의 "Spring 산하는 버전 명시 안 함" 관행 + 시스템 일관성 룰에 맞춰, Boot 2.7.12 BOM 적용 후 BOM 관리 대상 의존성은 버전 떼기.

**근거**: 명시 버전들이 Boot 2.7.12 BOM 기본값과 거의 다 일치 (spring-* 5.3.27, hibernate 5.6.15.Final, jackson 2.13.5, HikariCP 4.0.3, slf4j 1.7.36, spring-security 5.7.10) — 떼어도 같은 버전이 들어오므로 문법 영향 없음.

**변경**: `sync-agent-common/build.gradle`

```gradle
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'io.spring.dependency-management' version '1.1.0'  // 추가
}

dependencyManagement {
    imports {
        mavenBom 'org.springframework.boot:spring-boot-dependencies:2.7.12'
    }
}
```

**버전 떼기 대상 (BOM 관리)**: spring-web, spring-tx, spring-jdbc, spring-data-jpa, hibernate-core, jackson-databind, HikariCP, slf4j-api, spring-security-web, spring-security-core, javax.persistence-api, javax.servlet-api, spring-boot-autoconfigure, lombok, junit-jupiter, mockito-core, junit-platform-launcher

**명시 유지 (BOM 비관리 / 의도된 명시)**: 
- `io.jsonwebtoken:jjwt-{api,impl,jackson}:0.12.6` (Boot BOM 미관리)
- `com.nimbusds:nimbus-jose-jwt:9.31` (Boot BOM 기본값과 다른 버전 의도 사용)
- `org.jasypt:jasypt:1.9.3` (Boot BOM 미관리)

**검증**: 떼기 전/후 `./gradlew :sync-agent-common:dependencies` 결과가 동일한지 확인.

---

### C. Gradle Wrapper → 8.13 통일 (전 모듈 11개)

현재 8.5와 8.10 혼재. 사내 폐쇄망 Nexus가 **8.13** 보유 → 8.13으로 통일.

대상: 전체 11개 모듈. 각 모듈의 `gradle/wrapper/gradle-wrapper.properties` 의 `distributionUrl` 을 8.13으로 변경.

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
```

> 운영 시에는 사내 미러 URL로 치환됨 (배포 단계 작업).

---

## 3. 시스템 일관성 점검 결과

| 의존성 | 일관성 | 조치 |
|--------|--------|------|
| Spring Boot 2.7.12 | ✓ | 유지 |
| io.spring.dependency-management 1.1.0 | ✓ | 유지 |
| Java 17 | ✓ | 유지 |
| jjwt 0.12.6 | ✓ | 유지 (사내 보유 확인됨) |
| nimbus-jose-jwt 9.31 | ✓ | 유지 (사내 보유 확인됨) |
| jasypt-spring-boot-starter 3.0.5 | ✓ | 유지 |
| **Oracle JDBC** | ✗ ojdbc8/11 혼재 | A + B 작업 |
| **Gradle wrapper** | ✗ 8.5/8.10 혼재 | C 작업 (8.13 통일) |

## 4. 작업 범위 외 (사내 Nexus 보유 여부만 확인 — 별 트랙)

| GAV | 사용 모듈 | 비고 |
|-----|----------|------|
| com.github.ulisesbocchio:jasypt-spring-boot-starter 3.0.5 | 거의 모든 모듈 | 사내 보유 확인 필요 |
| com.jayway.jsonpath:json-path 2.8.0 | api-collector | 사내 보유 확인 필요 |
| org.locationtech.proj4j:proj4j 1.3.0 | api-collector | 사내 보유 확인 필요 |
| org.locationtech.proj4j:proj4j-epsg 1.3.0 | api-collector | 사내 보유 확인 필요 |

→ **버전 변경 없이** 사내 Nexus에 같은 버전이 있는지만 확인. 없으면 등록 요청 후 별 작업.

---

## 5. 영향 범위

### 코드
- build.gradle 10 곳 수정 (A: 3개, B: 7개, 일부 중복)
- gradle-wrapper.properties 11개 모듈 모두
- 자바/JSP/리소스 파일 **수정 없음**

### 런타임
- ojdbc8 19.3.0.0 사용 — 기존 ojdbc11 21.x.x 대비 기능 차이 없음 (JDBC 표준 API 사용 범위 내)
- HikariCP, Hibernate 등 상위 계층 동작 동일

### 빌드/테스트
- 모든 Oracle 사용 모듈 빌드 성공 확인 (`./gradlew clean build -x test`)
- Gradle 8.13 wrapper 동작 확인 (전 모듈)
- 가능하면 기동 테스트 (Oracle 연결 — internal-oracle/saeol-oracle)

---

## 6. 작업 순서 (완료)

1. ✅ 본 계획 사용자 승인
2. ✅ C 작업 — 전 11 모듈 gradle wrapper 8.13으로 변경
3. ✅ A 작업 — 3 모듈 ojdbc11 → ojdbc8
4. ✅ B 작업 — 7 모듈 `ojdbc8:19.3.0.0` 직접 명시 (BOM property override 폐기)
5. ✅ D 작업 — sync-agent-common BOM 적용 + 버전 떼기 + 검증
6. ✅ 빌드 테스트 — 11/11 모듈 BUILD SUCCESSFUL
7. ✅ dev_logs / nexus-dependencies.md 갱신
8. (다음) 메모리 동기화(`docs/claude-memory/`) + 커밋

---

## 7. 롤백

build.gradle / gradle-wrapper.properties만 수정하므로 git revert로 즉시 복구 가능.

---

## 8. 사용자 확인 사항

작업 시작 전 답변:

1. 본 계획 진행해도 OK?
2. ojdbc8 외 Oracle 의존 라이브러리 (예: `oracle-pki`, `ucp` 등) 사내 보유 여부 — Boot BOM이 함께 끌어올 수 있음. 빌드 시 에러로 드러나면 그때 추가 처리해도 OK?
