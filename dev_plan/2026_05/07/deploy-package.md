# 배포 패키지 정리 — orchestrator_v2_deploy (1차 반입)

> 작성일: 2026-05-07
> 목적: 폐쇄망 1차 배포 테스트를 위해 정리본 디렉토리 신설 + git 관리 + zip 반입

---

## 1. 결정 요약 (사용자 5/7)

- **배포 디렉토리**: `D:\dev\claude\GIMS\orchestrator_v2_deploy` (형제, 별도 git)
- **반입 형태**: 소스만 (폐쇄망 Nexus 로 빌드)
- **모듈 범위**: 12개 전체 (5/7 리네임 후 frontend 가 root 별도 모듈로 분리되어 11→12)
- **포함**: 오직 소스코드 + 빌드 설정 (gradle wrapper / build.gradle / settings.gradle / gradle.properties / package.json 등)
- **제외**: dev 관리 폴더 / 빌드 산출물 / 의존 캐시 / 로컬 jar / 테스트 / 문서 / docker / 메모리 / git
- **Mock 컨트롤러**: 유지 (collector 의 LOOKUP 자기호출 / provider Mock 모두 폐쇄망 미구현 의존)
- **테스트 제거**: src/test/ 디렉토리 + dev API key 값 비우기 (build.gradle 의 testImplementation 라인은 유지 — 빌드 자체는 영향 없음. 실행 시점에만 영향)
- **credential**: 11개 yml 의 ENC(...) 값 → 빈 값. jasypt 변환기는 기존 `scripts/encrypt.java` 그대로 `tools/` 로 복사 (이미 잘 작동)

---

## 2. 사전 점검 결과

### 2.1 ENC 값 분포 (50개, 11개 모듈)
| 모듈 | yml 위치 | ENC 갯수 |
|---|---|:--:|
| infolink-agent-bojo-dmz | application.yml | 6 (url/user/pw/orchestrator-url/proxy-url/api-key) |
| infolink-agent-bojo-internal | application.yml | 6 |
| infolink-agent-others-dmz | application.yml | 6 |
| infolink-agent-provide-dmz | application.yml | 6 |
| infolink-proxy-dmz | application.yml | 5 (proxy-url 없음) |
| infolink-proxy-internal | application.yml | 5 |
| infolink-api-collector | application.yml | 4 (datasource 3 + url 1) |
| infolink-api-provider | application.yml | 5 |
| infolink-auth | application.yml | 3 (datasource 만) |
| infolink-orchestrator-backend | application.yml | 5 (datasource 3 + api-key 2개소) |
| infolink-agent-common | (없음 — 라이브러리 모듈) | 0 |

### 2.2 jasypt 설정 (10개 yml 동일 패턴 — infolink-agent-common 제외 전 모듈)
```yaml
# Before
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
    algorithm: PBEWithHMACSHA512AndAES_256
    iv-generator-classname: org.jasypt.iv.RandomIvGenerator

# After (배포본)
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD}
    algorithm: PBEWithHMACSHA512AndAES_256
    iv-generator-classname: org.jasypt.iv.RandomIvGenerator
```
- **사용자 결정 (5/7)**: dev default key 제거. `${JASYPT_PASSWORD:...}` → `${JASYPT_PASSWORD}` 일괄 치환.
- 사유: env 미주입 시 부팅 fail-fast → 운영 환경변수 누락 검증 가능.
- 적용 모듈: infolink-agent-bojo-dmz / -bojo-internal / -others / -provide, infolink-proxy-dmz / -internal, infolink-api-collector, infolink-api-provider, infolink-auth, infolink-orchestrator-backend (10개)

### 2.3 dev 프로파일 yml
- `infolink-api-collector/src/main/resources/application-dev.yml` 1건 발견.
- → **배포본 제외** (dev 전용 — 운영 무관, "테스트 관련 요소" 정수에 정합).

### 2.4 기존 변환기 (`scripts/encrypt.java`) 스펙
- single-file `java --source 17` (shebang 포함)
- 인자 모드 (`encrypt "값"` / `decrypt "값"`) + 인터랙티브 모드 양쪽 지원
- ENC(...) 래핑/언래핑 자동
- 알고리즘 = PBEWithHMACSHA512AndAES_256 (시스템과 정합)
- default key = `sync-pipeline-secret-key-2024` (코드 안에 박혀있음 — 운영에선 사용자가 코드 한 줄 수정 또는 별도 .java 사본 사용)
- → **그대로 복사 활용**. 새로 만들지 않음.

---

## 3. 디렉토리 구조 (배포본 산출물)

```
orchestrator_v2_deploy/                    ← 신규, 별도 git
├── .gitignore                             ← 빌드 산출물/캐시/IDE/jar 재방지
├── README.md                              ← 반입 후 빌드 순서 + ENC 교체 가이드 + 변환기 사용법
├── tools/
│   ├── encrypt.java                       ← scripts/encrypt.java 복사
│   └── encrypt.sh                         ← scripts/encrypt.sh 복사 (Linux/Mac wrapper, jasypt jar 자동 탐색)
├── infolink-agent-common/
│   ├── build.gradle, settings.gradle, gradle.properties (있는 것만)
│   ├── gradle/, gradlew, gradlew.bat
│   └── src/main/                          ← src/test 제외
├── infolink-agent-bojo-dmz/
│   ├── build.gradle 등
│   ├── libs/                              ← 빈 디렉토리 (.gitkeep)
│   │                                       ※ 폐쇄망에서 infolink-agent-common 빌드 → 여기 복사 필요
│   └── src/main/
├── infolink-agent-bojo-internal/                   (동일 패턴, libs/ 포함)
├── infolink-agent-others-dmz/                     (동일)
├── infolink-agent-provide-dmz/                    (동일)
├── infolink-proxy-dmz/                        (동일)
├── infolink-proxy-internal/                   (동일)
├── infolink-api-collector/                (동일)
├── infolink-api-provider/                     (동일)
├── infolink-auth/                (동일)
├── infolink-orchestrator-backend/         (위 검증자 패턴 동일 — 5/7 리네임 후 별도 root 모듈로 평탄화됨)
└── infolink-orchestrator-frontend/        (5/7 리네임 후 별도 root 모듈로 평탄화)
    ├── package.json, package-lock.json
    ├── next.config.js, tsconfig.json, next-env.d.ts
    ├── middleware.ts
    ├── app/, components/, lib/, public/, types/
    ├── tailwind.config.ts, postcss.config.js (있는 것만)
    └── (node_modules/, .next/, .swc/, *.log 제외)
```

### libs/ 디렉토리 처리
- 9개 검증자 모듈에 `libs/infolink-agent-common-*.jar` 가 박혀있는데 — **로컬 jar 자체는 제거**, 디렉토리만 `.gitkeep` 으로 보존.
- README 에 명시: 폐쇄망 첫 빌드 시 `cd infolink-agent-common && ./gradlew clean build -x test` 후 `cp build/libs/infolink-agent-common-*.jar ../<참조모듈>/libs/` 단계 필요.
- 대상 9개 모듈: infolink-agent-bojo-dmz / -bojo-internal / -others / -provide, infolink-proxy-dmz / -internal, infolink-api-collector, infolink-api-provider, infolink-orchestrator-backend

---

## 4. 제외 목록 (전수)

### 4.1 모듈 내부 (각 모듈 안)
| 종류 | 제외 |
|---|---|
| 빌드 산출물 | `build/`, `out/`, `*.class`, `*.jar` (단 gradle wrapper jar 는 `gradle/wrapper/` 안에 있어 OK) |
| 의존 캐시 | `.gradle/`, `node_modules/`, `.next/`, `.swc/` |
| 로컬 jar | `libs/*.jar` (디렉토리는 보존) |
| 테스트 코드 | `src/test/` |
| IDE | `.idea/`, `.vscode/`, `*.iml`, `.classpath`, `.project`, `.settings/` |
| 잡 | `*.log`, `.DS_Store`, `Thumbs.db` |

### 4.2 루트 레벨 (orchestrator_v2 안에 있지만 배포본에 포함하지 않음)
| 항목 | 사유 |
|---|---|
| `CLAUDE.md` | AI 작업 가이드 — 폐쇄망 불필요 |
| `delayedTodoList` | 작업 메모 |
| `dev_logs/` | 작업 일지 |
| `dev_plan/` | 계획서 |
| `docker/` | 로컬 개발용 docker 자료 — 폐쇄망 별도 인프라 |
| `docs/` | 개발 가이드 / claude-memory 통째로 |
| `ex_source/` | 예제 소스 (legacy 참고용) |
| `nexus-dependencies.md` | Nexus 의존성 점검 표 (개발팀 내부용) |
| `scripts/` | dev 유틸 (단 `encrypt.java` 만 → tools/ 로 복사) |
| `test_plan/` | 테스트 시나리오 |
| `todo/` | TODO 추적 |
| `verify/` | 검증 세션 체계 |
| `.git/` | 원본 git history — 별도 신규 git init |
| `.claude/`, `.idea/`, `.vscode/` | IDE/AI 설정 |

---

## 5. ENC() 값 처리 정책

### 5.1 yml 일괄 치환
모든 11개 yml 안 `ENC(...)` 패턴 → 빈 문자열로:

```yaml
# Before
spring:
  datasource:
    url: ENC(5nfufl87NV...)
    username: ENC(EDM5TL2k...)
    password: ENC(hqhtiyt...)
agent:
  api-key: ENC(YzTJ0UGn...)

# After (배포본)
spring:
  datasource:
    url:
    username:
    password:
agent:
  api-key:
```

치환 대상 키 (검출된 패턴 전부):
- `spring.datasource.url/username/password`
- `agent.orchestrator-url`
- `agent.proxy-url`
- `agent.api-key`
- `proxy.api-key`
- `proxy.url` (infolink-orchestrator-backend)
- (그 외 ENC( 가 매칭되는 모든 위치)

→ regex: `ENC\([^)]*\)` 를 빈 문자열로 치환.

### 5.2 jasypt master password default 제거
- `${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}` → `${JASYPT_PASSWORD}` 일괄 (10개 yml)
- 폐쇄망 운영자 절차:
  1. `JASYPT_PASSWORD` 환경변수 설정 (운영 master key)
  2. `tools/encrypt.java` 의 `DEFAULT_KEY` 를 동일 운영 key 로 수정
  3. 평문 → ENC(...) 변환
  4. 10개 yml 의 빈 값에 박기
  5. 부팅 — env 미설정 시 즉시 실패 (의도된 fail-fast)

---

## 6. 작업 순서

| Step | 내용 | 비고 |
|:--:|---|---|
| 1 | `D:\dev\claude\GIMS\orchestrator_v2_deploy\` 생성 + `git init` | |
| 2 | `.gitignore` 작성 | 빌드/캐시/IDE/jar 재방지 |
| 3 | 11개 모듈 정리 복사 (robocopy `/XD build .gradle node_modules .next libs target out .idea .vscode` `/XF *.jar *.log .DS_Store *.class *.iml`) | 각 모듈 src/main + 빌드 설정만 들어옴 |
| 4 | src/test/ 제거 (모듈마다) | robocopy `/XD test` 추가 |
| 5 | libs/ 디렉토리 9개 신규 생성 + `.gitkeep` | jar 자체는 안 들어감 |
| 6 | 10개 yml ENC() 빈 값 치환 + jasypt default 제거 + application-dev.yml 제거 | sed/PowerShell 정규식 일괄 |
| 7 | `tools/encrypt.java` + `tools/encrypt.sh` ← `scripts/` 에서 복사 | |
| 8 | `README.md` 작성 | 빌드 순서 + jasypt 변환기 사용법 + libs/ 복사 단계 |
| 9 | git add + 첫 커밋 | `chore: initial deploy package (1st 반입)` |
| 10 | 사용자 검수 | 빌드 가능 여부 점검 후 zip 반입 |

추정 시간: **~30분** + 사용자 검수.

---

## 7. README.md 골자 (배포본 안)

```markdown
# orchestrator_v2 배포 패키지

폐쇄망 반입용 정리본. 소스 + 빌드 설정만 포함.

## 1. 빌드 순서
1. infolink-agent-common 먼저 빌드:
   cd infolink-agent-common
   ./gradlew clean build -x test
   cp build/libs/infolink-agent-common-1.0.0-SNAPSHOT.jar ../infolink-agent-bojo-dmz/libs/
   # ... 9개 참조 모듈 모두 동일 (infolink-agent-bojo-dmz 외 8개)
2. 나머지 모듈 빌드:
   cd <module>
   ./gradlew clean build -x test
3. frontend:
   cd infolink-orchestrator-frontend
   npm install
   npm run build

## 2. ENC() 값 채우기
빈 yml 자리에 jasypt 암호화 값을 박아야 함.

### 변환기 사용
1. JASYPT_PASSWORD 환경변수로 운영 master key 설정 (또는 tools/encrypt.java 안 DEFAULT_KEY 수정)
2. jasypt jar 위치 확인 (예: ~/.gradle/caches/modules-2/files-2.1/org.jasypt/jasypt/1.9.3/.../jasypt-1.9.3.jar)
3. 변환:
   java -cp <jasypt-jar> tools/encrypt.java encrypt "운영DB비번"
   → 출력: ENC(xxxxxx)
4. 11개 모듈 yml 의 해당 키에 박기

### 채워야 할 키 (11개 모듈 전수)
| 모듈 | 키 |
|---|---|
| infolink-agent-bojo-dmz / -bojo-internal / -others / -provide | datasource(url/user/pw) + agent(orchestrator-url/proxy-url/api-key) |
| infolink-proxy-dmz / -internal | datasource(url/user/pw) + agent(orchestrator-url/api-key) |
| infolink-api-collector | datasource(url/user/pw) + lookup.common-code-url |
| infolink-api-provider | datasource(url/user/pw) + proxy(url/api-key) |
| infolink-auth | datasource(url/user/pw) |
| infolink-orchestrator-backend | datasource(url/user/pw) + agent(api-key) + proxy(api-key) |

## 3. 실행
- 각 모듈 ./gradlew bootRun (또는 build 후 java -jar)
- frontend: npm run start (또는 dev)

## 4. 포트 매트릭스
auth 8096 / backend 8080 / agent-bojo 8082 / proxy-dmz 8083 / collector 8084 /
agent-others 8085 / agent-bojo-internal 8092 / proxy-internal 8093 / collector-int 8094 /
api-provider 8095 / frontend 3000
```

---

## 8. .gitignore 골자

```gitignore
# Build outputs
build/
out/
target/
*.class
*.jar
!gradle/wrapper/gradle-wrapper.jar

# Caches
.gradle/
node_modules/
.next/
.swc/

# Local libs (re-built each deploy)
libs/*.jar

# IDE
.idea/
.vscode/
*.iml
.classpath
.project
.settings/

# OS
.DS_Store
Thumbs.db
*.log
```

---

## 9. 회귀 위험 / 주의

1. **libs/ 빈 상태로 빌드 시도** → infolink-agent-common 의존 9개 모듈 컴파일 실패. README 1단계 누락 시 발생. → README 강조 + 첫 빌드 스크립트 제공 검토
2. **ENC 빈 값으로 부팅 시도** → jasypt 가 빈 문자열 decrypt 실패 (또는 datasource null connect 실패). 부팅 자체 안 됨. → README 2단계 강조
3. **frontend `next-env.d.ts`** — `.gitignore` 에 박는 게 일반적이나 빌드에 필요. 포함시킴 (자동 재생성됨)
4. **dev API key 값** — 사용자 결정대로 ENC() 안의 dev 자격증명만 제거. dev 도메인/포트 (`localhost:8080` 등) 의 yml 다른 값들은 유지 (운영 yml override 패턴 가정)

---

## 10. 보류 / 추후 고려

- 배포본 git 의 remote — 사내 GitLab/GitHub 등록 여부 (현재는 local only 로 시작)
- zip 도구 — robocopy 후 `Compress-Archive -Path orchestrator_v2_deploy -DestinationPath orchestrator_v2_deploy_2026-05-07.zip` 권장 (PowerShell 내장)
- 2차 반입 시 — 배포본 git 에 commit 누적되어 변경분 추적 가능

---

## 11. 결정 필요 항목

1. ✅ 배포 디렉토리 위치 — `orchestrator_v2_deploy` (형제) **확정**
2. ✅ 모듈 범위 — 11개 전체 **확정**
3. ✅ ENC() 처리 — 빈 값 + 변환기 별도 **확정**
4. ✅ Mock 컨트롤러 — 유지 **확정**
5. ✅ src/test/ — 제거 **확정**
6. ✅ jasypt master password default — 제거. `${JASYPT_PASSWORD}` 만 (env 미주입 fail-fast) **확정**
7. ✅ 배포본 README.md 위치 — 배포본 root **확정**
8. ✅ 첫 커밋 메시지 — `chore: initial deploy package (1st 반입, 2026-05-07)` **확정**
9. ✅ application-dev.yml — 배포본 제외 **확정**
