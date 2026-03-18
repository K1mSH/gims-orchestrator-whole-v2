# Jasypt 암호화 키 외부 파일화

## 목적
- 현재 5개 모듈 `application.yml`에 Jasypt 기본값이 평문 노출됨
- `${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}` → 기본값 제거
- 외부 키 파일로 분리하여 git 미추적 + 실질적 보안 확보

## 현재 상태 (보안 미흡)
```yaml
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}  # ← 기본값 평문 노출
```

## 변경 내용

### 1. 외부 키 파일 생성
- 경로: `config/jasypt-secret.key`
- 내용: 암호화 키 1줄 (현재 값: `sync-pipeline-secret-key-2024`)
- git 미추적 (`.gitignore`에 추가)

### 2. 5개 모듈 application.yml 수정
기본값 제거:
```yaml
# 변경 전
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}

# 변경 후
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD}
```

대상 파일:
- `sync-orchestrator/backend/src/main/resources/application.yml`
- `sync-agent-bojo/src/main/resources/application.yml`
- `sync-agent-bojo-int/src/main/resources/application.yml`
- `sync-proxy-dmz/src/main/resources/application.yml`
- `sync-proxy-internal/src/main/resources/application.yml`

### 3. .gitignore 추가
```
# Jasypt secret key
config/jasypt-secret.key
```

### 4. 기동 스크립트 생성
`scripts/start-all.sh` — 키 파일 읽어서 환경변수로 전달하는 기동 스크립트
```bash
export JASYPT_PASSWORD=$(cat config/jasypt-secret.key)
java -jar build/libs/xxx.jar
```

## 영향 범위
- application.yml 5개 파일 (기본값 제거만)
- .gitignore 1줄 추가
- config/jasypt-secret.key 신규 (git 미추적)
- scripts/start-all.sh 신규

## 테스트 기준
- [ ] yml에 Jasypt password 기본값이 없어야 함
- [ ] `JASYPT_PASSWORD` 환경변수 설정 후 기동 → 정상 동작
- [ ] 환경변수 미설정 시 → 기동 실패 (의도된 동작)
- [ ] `config/jasypt-secret.key`가 git에 추적되지 않음
