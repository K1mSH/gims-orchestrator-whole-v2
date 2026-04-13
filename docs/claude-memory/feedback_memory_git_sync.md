---
name: 메모리 파일 git 동기화
description: 로컬 MEMORY.md 업데이트 시 docs/claude-memory/MEMORY.md에 복사 후 커밋/푸시 필요
type: feedback
---

메모리 파일 업데이트 시 git 추적 파일에도 동기화해야 한다.

**Why:** 로컬 메모리(`~/.claude/projects/.../memory/MEMORY.md`)는 git에 안 올라가므로, `docs/claude-memory/MEMORY.md`에 복사해서 커밋해야 다른 환경에서도 참조 가능.

**How to apply:** MEMORY.md 내용이 변경되면 `docs/claude-memory/MEMORY.md`에 복사해두고, 다른 git 작업(커밋/푸시)할 때 같이 포함시킨다. 매번 별도 커밋할 필요는 없음.
