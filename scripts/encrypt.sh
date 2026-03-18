#!/bin/bash
# Jasypt 암/복호화 도구
#
# 사용법:
#   ./encrypt.sh                       # 대화형 모드
#   ./encrypt.sh encrypt "평문"        # 단건 암호화
#   ./encrypt.sh decrypt "ENC(...)"    # 단건 복호화

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JASYPT_JAR=$(find ~/.gradle/caches -name "jasypt-1*.jar" 2>/dev/null | grep -v sources | head -1)

if [ -z "$JASYPT_JAR" ]; then
    echo "jasypt JAR not found. Run 'gradlew build' first."
    exit 1
fi

javac -cp "$JASYPT_JAR" "$SCRIPT_DIR/encrypt.java" -d /tmp/jasypt_enc 2>/dev/null
java -cp "/tmp/jasypt_enc:$JASYPT_JAR" encrypt "$@"
