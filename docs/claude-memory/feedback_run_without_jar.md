---
name: JAR 대신 Gradle bootRun 사용
description: Spring Boot 앱 실행 시 JAR 빌드 후 java -jar 대신 ./gradlew bootRun으로 직접 실행
type: feedback
---

JAR 파일로 실행하지 말고, `./gradlew bootRun`으로 직접 실행할 것.

**Why:** 사용자가 JAR 만들지 말고 실행하라고 요청. 개발 환경에서는 bootRun이 더 빠르고 편리함.

**How to apply:** Spring Boot 앱 기동 시 항상 `./gradlew bootRun` 사용. `./gradlew build` + `java -jar` 패턴 사용 금지.
