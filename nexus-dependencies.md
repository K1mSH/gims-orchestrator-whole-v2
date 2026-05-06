# Nexus 등록 대상 의존성 (명시 버전만)

폐쇄망 Nexus 등록을 위해 build.gradle / package.json에 **버전을 직접 명시한** 항목만 정리.
Spring Boot 2.7.12 BOM이 관리하는 transitive(starter 류)는 별도 산출 필요 — 본 문서에선 제외.

> 비고 칸은 비어 있음 — 검토하면서 옆에 수정/메모 적어주세요.

---

## 0. 빌드 도구 / 베이스라인

| 항목 | 버전 | 비고 |
|------|------|------|
| Java | 17 | |
| Gradle Wrapper | **8.13** | 전 11 모듈 통일 |
| Spring Boot Gradle plugin | 2.7.12 | |
| io.spring.dependency-management plugin | 1.1.0 | sync-agent-common 포함 전 모듈 |

---

## 1. sync-agent-common (BOM 적용 후 — 명시 버전만)

`spring-boot-dependencies:2.7.12` BOM 적용. BOM 관리 의존성은 버전 자동 결정. 아래는 BOM 비관리 의도 명시.

| GAV | 버전 | 비고 |
|-----|------|------|
| org.jasypt:jasypt | 1.9.3 | BOM 미관리 |
| io.jsonwebtoken:jjwt-api | 0.12.6 | BOM 미관리 |
| io.jsonwebtoken:jjwt-impl | 0.12.6 | BOM 미관리 |
| io.jsonwebtoken:jjwt-jackson | 0.12.6 | BOM 미관리 |
| com.nimbusds:nimbus-jose-jwt | 9.31 | BOM 기본값과 다른 버전 의도 사용 |

---

## 2. JWT (auth + orchestrator/backend 에서도 명시 — common과 동일 버전)

| GAV | 버전 | 비고 |
|-----|------|------|
| io.jsonwebtoken:jjwt-api | 0.12.6 | |
| io.jsonwebtoken:jjwt-impl | 0.12.6 | |
| io.jsonwebtoken:jjwt-jackson | 0.12.6 | |
| com.nimbusds:nimbus-jose-jwt | 9.31 | |

사용 모듈: sync-agent-common, sync-orchestrator-auth, sync-orchestrator/backend

---

## 3. 전 모듈 공통 (Boot starter 외 명시)

| GAV | 버전 | 비고 |
|-----|------|------|
| com.github.ulisesbocchio:jasypt-spring-boot-starter | 3.0.5 | 거의 모든 Boot 모듈에서 사용 |

---

## 4. infolink-api-collector 단독 명시

| GAV | 버전 | 비고 |
|-----|------|------|
| com.jayway.jsonpath:json-path | 2.8.0 | |
| org.locationtech.proj4j:proj4j | 1.3.0 | |
| org.locationtech.proj4j:proj4j-epsg | 1.3.0 | EPSG 데이터셋 (대용량 jar) |

---

## 4-1. Oracle JDBC (직접 명시 — 19.3.0.0 통일)

폐쇄망 Nexus = `ojdbc8 19.3.0.0`만 보유. Oracle이 19.3.0.0에 ojdbc-bom을 배포하지 않아 BOM property override는 빌드 실패 → 직접 명시.

| GAV | 버전 | 사용 모듈 |
|-----|------|----------|
| com.oracle.database.jdbc:ojdbc8 | 19.3.0.0 | agent-bojo-int, agent-others, agent-provide, proxy-dmz, proxy-internal, orchestrator/backend, gims-api-provider |

---

## 5. Frontend (sync-orchestrator/frontend, npm)

| 패키지 | 버전 | 비고 |
|--------|------|------|
| next | ^14.2.0 | |
| react | ^18.2.0 | |
| react-dom | ^18.2.0 | |
| axios | ^1.6.0 | |
| typescript | ^5.3.0 | dev |
| eslint | ^8.0.0 | dev |
| eslint-config-next | ^14.2.0 | dev |
| @types/node | ^20.0.0 | dev |
| @types/react | ^18.2.0 | dev |
| @types/react-dom | ^18.2.0 | dev |

---

## 참고 — BOM 따라가지만 알아둘 분기

Spring Boot BOM이 버전을 결정하므로 본문에선 제외했지만, 모듈마다 다른 GAV를 사용하는 항목:

| GAV | 사용 모듈 | 비고 |
|-----|----------|------|
| com.oracle.database.jdbc:ojdbc8 | sync-agent-others, sync-agent-provide, sync-proxy-dmz, gims-api-provider | |
| com.oracle.database.jdbc:ojdbc11 | sync-agent-bojo-int, sync-proxy-internal, sync-orchestrator/backend | |
| org.postgresql:postgresql | 거의 모든 모듈 | |
| com.mysql:mysql-connector-j | sync-agent-bojo, sync-proxy-dmz, sync-proxy-internal | |
| com.h2database:h2 | sync-agent-bojo, sync-agent-bojo-int, sync-orchestrator/backend, infolink-api-collector | |

→ Boot BOM 정확 버전은 `./gradlew :<모듈>:dependencies --configuration runtimeClasspath` 로 확인.
