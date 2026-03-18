# 암호화 키 분리 + Docker 환경변수 주입 설계

## 1. 현재 문제

### 키가 소스코드에 노출되어 있음
```yaml
# application.yml (모든 모듈 동일)
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
    algorithm: PBEWithHMACSHA512AndAES_256
    iv-generator-classname: org.jasypt.iv.RandomIvGenerator
```
- 디폴트값 `sync-pipeline-secret-key-2024`가 YML에 하드코딩
- `JASYPT_PASSWORD` 환경변수를 실제로 주입하는 곳이 없음 → 디폴트값이 그대로 사용됨
- 암호화 알고리즘 + 키 + 암호문이 전부 소스에 있음 = **암호화 의미 없음**

### CredentialEncryptor가 같은 키를 공유
```java
// sync-orchestrator/.../common/CredentialEncryptor.java
@Value("${jasypt.encryptor.password}")  // ← YML의 jasypt 키를 그대로 사용
private String secretKey;
```
- Orchestrator DB에 datasource 자격증명(username/password) 암호화 저장할 때 사용
- jasypt 프로퍼티 자동 복호화와 같은 키를 공유하고 있음

### 연결 정보 API가 평문 응답
```java
// DatasourceService.java - getConnectionInfo()
.username(credentialEncryptor.decrypt(ds.getUsername()))   // DB에서 복호화
.password(credentialEncryptor.decrypt(ds.getPassword()))   // 평문으로 응답
```
- Agent가 `GET /api/datasource/{id}/connection-info` 호출 시 평문 자격증명 반환
- 현재 개발 환경에서는 문제 없으나, 운영 시 전송 구간 보안 필요 (HTTPS 또는 암호문 전달)

---

## 2. 개선 방향

### 키 분리: jasypt 키 vs credential 키
| 용도 | 프로퍼티 | 설명 |
|------|---------|------|
| YML 프로퍼티 자동 복호화 | `jasypt.encryptor.password` | Spring Boot 기동 시 `ENC(...)` 자동 복호화 |
| DB 자격증명 암호화/복호화 | `credential.encryption.key` (신규) | CredentialEncryptor 전용 별도 키 |

### 환경변수로 키 주입 (디폴트값 제거)
```yaml
# application.yml (변경 후)
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD}          # 디폴트값 없음
    algorithm: PBEWithHMACSHA512AndAES_256
    iv-generator-classname: org.jasypt.iv.RandomIvGenerator

credential:
  encryption:
    key: ${CREDENTIAL_KEY}                # 디폴트값 없음, 별도 키
```

### CredentialEncryptor 수정
```java
// 변경 전
@Value("${jasypt.encryptor.password}")
private String secretKey;

// 변경 후
@Value("${credential.encryption.key}")
private String secretKey;
```

---

## 3. Docker 배포에서 환경변수 주입

### 기존 배포 패턴 (be-onestop-service 참고)
```
Jenkins Pipeline:
  Git Checkout → Gradle Build → Docker Image Build → Nexus Push → SSH 원격 배포

환경변수 관리:
  .env 파일 → scp로 서버 전송 → docker-compose에서 env_file로 참조
```

### 적용 방식
```env
# .env (배포 서버에만 존재, git에 올리지 않음)
JASYPT_PASSWORD=운영용-jasypt-키
CREDENTIAL_KEY=운영용-credential-키
SPRING_PROFILES_ACTIVE=prod
```

```yaml
# docker-compose.yaml
services:
  sync-orchestrator:
    image: ${DOCKER_REGISTRY}/sync-orchestrator:${IMAGE_TAG}
    env_file:
      - .env
    environment:
      - JASYPT_PASSWORD=${JASYPT_PASSWORD}
      - CREDENTIAL_KEY=${CREDENTIAL_KEY}
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}
    ports:
      - "8080:8080"
```

### 보안 수준
- 소스코드/git: 키 없음 (디폴트값 제거)
- Docker 이미지: 키 없음 (빌드 시 주입 안 함)
- 배포 서버 `.env` 파일: 키 존재 (서버 접근 권한 필요)
- `docker inspect`: 키 보임 (서버 접근 권한 필요)
- 폐쇄망이므로 서버 접근 자체가 제한됨 → 실질적 보안 확보

---

## 4. 영향 범위

### 수정 대상 파일
| 모듈 | 파일 | 변경 내용 |
|------|------|----------|
| sync-orchestrator/backend | `CredentialEncryptor.java` | `@Value` 키를 `credential.encryption.key`로 변경 |
| sync-orchestrator/backend | `application.yml` | `credential.encryption.key` 프로퍼티 추가, jasypt 디폴트값 제거 |
| sync-orchestrator/backend | `Dockerfile` (신규) | Docker 이미지 빌드 설정 |
| sync-orchestrator/backend | `docker-compose.yaml` (신규) | 환경변수 주입 + 서비스 정의 |
| sync-orchestrator/backend | `.env.example` (신규) | 환경변수 템플릿 (키 값은 비움) |

### 주의사항
- **기존 암호문 재암호화 필요**: credential 키를 변경하면 DB에 저장된 기존 암호문을 새 키로 다시 암호화해야 함
- **개발 환경 편의**: 개발 시에는 `.env.local` 등으로 디폴트 키를 두거나, IDE 환경변수 설정으로 대응
- **Agent 모듈(bojo, bojo-int, proxy 등)**: Orchestrator가 평문으로 응답하는 현재 구조를 유지하면 Agent 쪽 수정 불필요. 추후 암호문 전달 방식으로 변경 시 Agent의 PasswordEncryptor 활용

---

## 5. 미사용 코드 참고

### sync-agent-common의 PasswordEncryptor 관련 (현재 미사용)
- `PasswordEncryptor.java` — 독립 키로 암호화/복호화 가능한 유틸리티
- `SyncDataSourceManager.java` — PasswordEncryptor로 복호화 후 DataSource 생성
- `LazyDataSource.java` — Lazy 초기화 + PasswordEncryptor 복호화

이 클래스들은 **Orchestrator가 암호문을 그대로 전달하고 Agent가 직접 복호화하는 구조**를 위해 설계되었으나, 현재는 Orchestrator가 평문으로 응답하는 구조(`SyncDataSourceService`)를 사용 중이라 dead code 상태.

추후 전송 구간 암호화가 필요하면 이 코드를 활용할 수 있음.

---

## 6. 참고: 기존 배포 설정 위치
- Dockerfile: `D:\dev\project\GIMS\be\be-onestop-service\Dockerfile`
- docker-compose: `D:\dev\project\GIMS\be\be-onestop-service\docker-compose.yaml`
- Jenkinsfile: `D:\dev\project\GIMS\be\be-onestop-service\Jenkinsfile`
