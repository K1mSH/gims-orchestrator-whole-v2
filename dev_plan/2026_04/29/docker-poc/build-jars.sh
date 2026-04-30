#!/bin/bash
# Docker PoC 용 JAR 생성 스크립트
# - 원본 yml 임시 수정 → gradle build → 즉시 복원 → JAR 복사
# - 호스트 dev 환경 영향 0 보장 (trap + git checkout 으로 yml 복원)
# - 변경 대상: gims-api-provider/proxy-internal application.yml 만

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
POC_DIR="$REPO_ROOT/dev_plan/2026_04/29/docker-poc"

API_PROVIDER_YML="gims-api-provider/src/main/resources/application.yml"
PROXY_INTERNAL_YML="sync-proxy-internal/src/main/resources/application.yml"

cd "$REPO_ROOT"

restore_ymls() {
    echo "[cleanup] yml 복원..."
    git checkout -- "$API_PROVIDER_YML" 2>/dev/null || true
    git checkout -- "$PROXY_INTERNAL_YML" 2>/dev/null || true
}
trap restore_ymls EXIT INT TERM

echo "[1/6] yml dirty 검사..."
if ! git diff --quiet "$API_PROVIDER_YML"; then
    echo "ERROR: $API_PROVIDER_YML 이 dirty 상태. 수동 복원 후 재실행" >&2
    exit 1
fi
if ! git diff --quiet "$PROXY_INTERNAL_YML"; then
    echo "ERROR: $PROXY_INTERNAL_YML 이 dirty 상태. 수동 복원 후 재실행" >&2
    exit 1
fi

echo "[2/6] yml 패치 적용..."
# api-provider: DB/Proxy URL 환경변수 패턴 추가 + JASYPT default 제거
sed -i \
    -e 's|^    url: jdbc:postgresql://localhost:29006/api_provider$|    url: ${API_PROVIDER_DB_URL:jdbc:postgresql://localhost:29006/api_provider}|' \
    -e 's|^    username: k1m$|    username: ${API_PROVIDER_DB_USER:k1m}|' \
    -e 's|^    password: 1111$|    password: ${API_PROVIDER_DB_PASSWORD:1111}|' \
    -e 's|^    url: http://localhost:8093$|    url: ${PROXY_INTERNAL_URL:http://localhost:8093}|' \
    -e 's|password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}|password: ${JASYPT_PASSWORD}|' \
    "$API_PROVIDER_YML"

# proxy-internal: JASYPT default 제거만
sed -i \
    -e 's|password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}|password: ${JASYPT_PASSWORD}|' \
    "$PROXY_INTERNAL_YML"

# 패치 결과 검증 (반영됐는지 grep)
if grep -q 'JASYPT_PASSWORD:sync-pipeline-secret-key-2024' "$API_PROVIDER_YML"; then
    echo "ERROR: api-provider yml 의 JASYPT default 가 제거되지 않음" >&2
    exit 1
fi
if grep -q 'JASYPT_PASSWORD:sync-pipeline-secret-key-2024' "$PROXY_INTERNAL_YML"; then
    echo "ERROR: proxy-internal yml 의 JASYPT default 가 제거되지 않음" >&2
    exit 1
fi
echo "  → yml 패치 확인됨"

echo "[3/6] gims-api-provider build..."
(cd "$REPO_ROOT/gims-api-provider" && ./gradlew clean build -x test --no-daemon)

echo "[4/6] sync-proxy-internal build..."
(cd "$REPO_ROOT/sync-proxy-internal" && ./gradlew clean build -x test --no-daemon)

echo "[5/6] yml 복원..."
restore_ymls

# 복원 검증
if ! git diff --quiet "$API_PROVIDER_YML"; then
    echo "ERROR: api-provider yml 복원 실패" >&2
    exit 1
fi
if ! git diff --quiet "$PROXY_INTERNAL_YML"; then
    echo "ERROR: proxy-internal yml 복원 실패" >&2
    exit 1
fi
echo "  → yml 원상 복원 확인"

echo "[6/6] JAR 복사..."
mkdir -p "$POC_DIR/jars"
# gims-api-provider 는 multi-jar (plain.jar 제외)
cp "$REPO_ROOT/gims-api-provider/build/libs/"*[!plain].jar "$POC_DIR/jars/api-provider.jar"
cp "$REPO_ROOT/sync-proxy-internal/build/libs/"*[!plain].jar "$POC_DIR/jars/proxy-internal.jar"
ls -la "$POC_DIR/jars/"

echo "[done] JAR 생성 완료, yml 원상 복원 확인됨"
