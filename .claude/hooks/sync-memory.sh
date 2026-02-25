#!/bin/bash
# docs/claude-memory/MEMORY.md 수정 시 → .claude auto-memory에 자동 복사

INPUT=$(cat)

# jq 없이 file_path 추출 (grep + sed)
FILE_PATH=$(echo "$INPUT" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"file_path"[[:space:]]*:[[:space:]]*"//;s/"$//')

# Windows 백슬래시 → 포워드슬래시 정규화 (JSON 이스케이프 \\ 처리 포함)
NORMALIZED_PATH=$(echo "$FILE_PATH" | sed 's|\\\\|/|g; s|\\|/|g; s|//|/|g')

# MEMORY.md 수정이 아니면 무시
if [[ "$NORMALIZED_PATH" != *"claude-memory/MEMORY.md"* ]]; then
  exit 0
fi

# auto-memory 디렉토리 찾기
TARGET_DIR=$(ls -d "$HOME/.claude/projects/"*orchestrator*v2*/memory 2>/dev/null | head -1)

if [[ -z "$TARGET_DIR" ]]; then
  exit 0
fi

cp "$NORMALIZED_PATH" "$TARGET_DIR/MEMORY.md" 2>/dev/null
