# api-collector 암호화 키 분리 + 배포 설정

## 목적
- `encryption-key-separation.md`의 설계를 api-collector에 먼저 적용하여 검증
- Dockerfile + docker-compose + Jenkinsfile 배포 파이프라인 구축
- Orchestrator/Agent 수정은 별도 진행 (이번 스코프 아님)

## 현재 상태
- jasypt 디폴트값이 application.yml에 하드코딩: `${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}`
- ENC() 암호문 + 키 + 알고리즘이 전부 소스에 존재 → 암호화 의미 없음
- **배포 설정 전무**: Dockerfile, docker-compose, Jenkinsfile 없음

## 참고: 기존 배포 패턴 (be-onestop-service)
```
Jenkins Pipeline 흐름:
  1. 작업 디렉토리 삭제
  2. Git checkout (GitLab)
  3. Git 태그 확인 → IMAGE_TAG
  4. Gradle 빌드
  5. (API spec 전송 - 해당 없음)
  6. Nexus Docker Registry 로그인
  7. Docker 이미지 빌드 + 푸시
  8. SSH로 원격 서버에 docker-compose.yaml + .env 전송
  9. docker compose pull → down → up -d
  10. Slack 알림
```

---

## 변경 내용

### 1. application.yml — jasypt 디폴트값 제거 + 키 변경
```yaml
# 변경 전
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}

# 변경 후
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD}    # 디폴트값 없음 — 환경변수 필수
```

### 2. ENC() 암호문 재암호화
- 키: `sync-pipeline-secret-key-2024` → `infolink-secret-key-2026`
- 대상: url, username, password 3개 값 재암호화

### 3. application-dev.yml 신규 — 개발 편의용 프로파일
```yaml
# 개발 환경에서는 키 직접 설정 (git에 올림)
jasypt:
  encryptor:
    password: infolink-secret-key-2026
```
- 개발: `--spring.profiles.active=dev`로 기동
- 운영: dev 프로파일 미사용 → 환경변수 필수

### 4. Dockerfile — 멀티스테이지 빌드
```dockerfile
# Build 단계
FROM gradle:8.11.1-jdk17 AS builder
WORKDIR /build
ARG GIT_TAG
ENV APP_VERSION=$GIT_TAG
COPY . /build/
RUN chmod +x gradlew
RUN ./gradlew clean build --no-daemon -x test
RUN rm -rf /build/.gradle

# curl 포함 (healthcheck용)
FROM curlimages/curl:8.12.1 AS curl_builder

# Runtime 단계
FROM openjdk:17-jdk-slim
WORKDIR /app
ARG GIT_TAG
ENV APP_VERSION=$GIT_TAG
COPY --from=curl_builder /usr/bin/curl /usr/bin/curl
COPY --from=curl_builder /usr/lib /usr/lib
COPY --from=curl_builder /lib /lib
COPY --from=builder /build/build/libs/*.jar infolink-api-collector.jar
ENTRYPOINT ["java", "-jar", "infolink-api-collector.jar"]
```
- 기존 패턴 동일: gradle 빌드 → slim runtime 이미지
- curl 포함 (healthcheck용)
- GIT_TAG로 버전 관리

### 5. docker-compose.yaml
```yaml
services:
  infolink-api-collector:
    image: ${DOCKER_REGISTRY}/infolink-api-collector:${IMAGE_TAG}
    env_file:
      - .env
    deploy:
      mode: replicated
      replicas: 1
      resources:
        limits:
          cpus: "0.5"
          memory: 512m
    ports:
      - "${SERVER_PORT}:${SERVER_PORT}"
    restart: unless-stopped
    environment:
      - SERVER_PORT=${SERVER_PORT}
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}
      - JASYPT_PASSWORD=${JASYPT_PASSWORD}
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:${SERVER_PORT}/api/endpoints || exit 1"]
      interval: 30s
      retries: 5
      timeout: 20s
      start_period: 60s
    networks:
      - app-network

networks:
  app-network:
    driver: bridge
```
- 기존 패턴 참고, Eureka 관련 설정 제거 (api-collector는 독립 서비스)
- healthcheck: `/api/endpoints` 엔드포인트 활용
- replicas 1 (단일 인스턴스)

### 6. Jenkinsfile
```groovy
def gitFetchWithCredential() {
  withCredentials([usernamePassword(
    credentialsId: 'gitlab-repo-credentials-02',
    usernameVariable: 'GIT_USERNAME',
    passwordVariable: 'GIT_PASSWORD'
  )]) {
    sh '''
      git fetch --tags "http://$GIT_USERNAME:$GIT_PASSWORD@soda-host-dev.iptime.org:37380/podo/be/infolink-api-collector.git"
    '''
  }
}

pipeline {
  agent any

  environment {
    DEFAULT_IMAGE_TAG = 'latest'
    DOCKER_REGISTRY = "192.168.0.176:5000/repository/gims-docker-hosted"
    GITLAB_REPO_CREDENTIALS = 'gitlab-repo-credentials-02'
    SSH_CREDENTIALS_ID = 'ssh-key-credentials-219'
    SSH_USER = 'gims'
    REMOTE_HOST = '192.168.0.177'
    REMOTE_HOST_PORT = '22024'
    MODULE_NAME = 'infolink-api-collector'
  }

  stages {
    stage('젠킨스 작업 디렉토리 삭제') {
      steps { deleteDir() }
    }

    stage('Git 레포지토리 코드 체크아웃') {
      steps {
        git branch: 'main',
            credentialsId: "${GITLAB_REPO_CREDENTIALS}",
            url: "http://soda-host-dev.iptime.org:37380/podo/be/${MODULE_NAME}.git"
      }
    }

    stage('Git 태그 확인') {
      steps {
        script {
          gitFetchWithCredential()
          env.RECENT_TAG = sh(
            script: "git describe --tags --abbrev=0 || echo '${DEFAULT_IMAGE_TAG}'",
            returnStdout: true
          ).trim()
          echo "이미지 태그: ${env.RECENT_TAG}"
        }
      }
    }

    stage('Gradle 빌드 실행') {
      steps {
        script {
          try { sh "chmod +x ./gradlew" } catch (Exception e) {}
          sh "./gradlew clean build --no-daemon -x test"
        }
      }
    }

    stage('NEXUS 로그인') {
      steps {
        script {
          withCredentials([usernamePassword(
            credentialsId: 'nexus-credentials-docker',
            usernameVariable: 'DOCKER_USERNAME',
            passwordVariable: 'DOCKER_PASSWORD'
          )]) {
            sh 'echo $DOCKER_PASSWORD | docker login ${DOCKER_REGISTRY} -u $DOCKER_USERNAME --password-stdin'
          }
        }
      }
    }

    stage('Docker 이미지 빌드 및 푸시') {
      steps {
        script {
          def imageTag = sh(script: 'git describe --tags', returnStdout: true).trim()
          sh """
            docker build --no-cache \
              --build-arg GIT_TAG=${imageTag} \
              -f Dockerfile \
              -t ${DOCKER_REGISTRY}/${MODULE_NAME}:${imageTag} .
          """
          sh "docker push ${DOCKER_REGISTRY}/${MODULE_NAME}:${imageTag}"
          sh "docker rmi ${DOCKER_REGISTRY}/${MODULE_NAME}:${imageTag}"
        }
      }
    }

    stage('Docker Compose 배포') {
      steps {
        script {
          gitFetchWithCredential()
          env.IMAGE_TAG = sh(script: "git describe --tags || echo 'latest'", returnStdout: true).trim()

          sshagent([SSH_CREDENTIALS_ID]) {
            def remoteDir = "/home/gims/app/${MODULE_NAME}"

            sh """
              ssh -o StrictHostKeyChecking=no -p ${REMOTE_HOST_PORT} ${SSH_USER}@${REMOTE_HOST} 'mkdir -p ${remoteDir}'
            """
            sh """
              scp -o StrictHostKeyChecking=no -P ${REMOTE_HOST_PORT} docker-compose.yaml ${SSH_USER}@${REMOTE_HOST}:${remoteDir}/
              scp -o StrictHostKeyChecking=no -P ${REMOTE_HOST_PORT} .env ${SSH_USER}@${REMOTE_HOST}:${remoteDir}/
            """
            sh """
              ssh -o StrictHostKeyChecking=no -p ${REMOTE_HOST_PORT} ${SSH_USER}@${REMOTE_HOST} \
                'IMAGE_TAG=${env.IMAGE_TAG} docker compose -f ${remoteDir}/docker-compose.yaml pull'
            """
            sh """
              ssh -o StrictHostKeyChecking=no -p ${REMOTE_HOST_PORT} ${SSH_USER}@${REMOTE_HOST} \
                'docker compose -f ${remoteDir}/docker-compose.yaml down || true'
            """
            sh """
              ssh -o StrictHostKeyChecking=no -p ${REMOTE_HOST_PORT} ${SSH_USER}@${REMOTE_HOST} \
                'IMAGE_TAG=${env.IMAGE_TAG} docker compose -f ${remoteDir}/docker-compose.yaml -p gims up -d --pull never'
            """
          }
        }
      }
    }
  }

  post {
    success {
      slackSend(channel: '#젠킨스빌드소식', color: '#00FF00',
        message: "배포성공: ${env.JOB_NAME} [${env.BUILD_NUMBER}]")
    }
    failure {
      slackSend(channel: '#젠킨스빌드소식', color: '#FF0000',
        message: "배포실패: ${env.JOB_NAME} [${env.BUILD_NUMBER}]")
    }
  }
}
```
- 기존 패턴 기반, Eureka/API spec 단계 제거
- SSH 배포: docker-compose.yaml + .env 전송 → pull → down → up

### 7. .env.example (git에 올림, 값은 비움)
```env
# 서버
SERVER_PORT=8084
SPRING_PROFILES_ACTIVE=prod

# 암호화
JASYPT_PASSWORD=

# Docker (Jenkins용)
DOCKER_REGISTRY=192.168.0.176:5000/repository/gims-docker-hosted
IMAGE_TAG=latest
```

### 8. .gitignore 추가
```
.env
!.env.example
```

---

## 수정 대상 파일
| 파일 | 작업 |
|------|------|
| `application.yml` | jasypt 디폴트값 제거, ENC() 새 키로 재암호화 |
| `application-dev.yml` (신규) | 개발용 키 설정 |
| `Dockerfile` (신규) | 멀티스테이지 빌드 (gradle → slim runtime) |
| `docker-compose.yaml` (신규) | 서비스 정의 + 환경변수 주입 + healthcheck |
| `Jenkinsfile` (신규) | CI/CD 파이프라인 (빌드 → 이미지 → 배포) |
| `.env.example` (신규) | 환경변수 템플릿 |
| `.gitignore` (수정) | .env 제외 |

## 영향 범위
- api-collector 모듈만 대상
- 기존 서비스(orchestrator, agent, proxy) 수정 없음
- 개발 환경: `dev` 프로파일로 기존과 동일하게 동작

## 검증
- `./gradlew bootRun` (dev 프로파일) → 정상 기동 확인
- 환경변수 미설정 시 기동 실패 확인 (의도된 동작)
- Docker 이미지 로컬 빌드 + 컨테이너 기동 테스트
