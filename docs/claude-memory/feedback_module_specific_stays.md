---
name: 모듈 전용 로직은 common으로 올리지 않기
description: 특정 모듈에서만 쓰는 로직(link-based fetcher 등)은 common으로 이동하지 않고 해당 모듈 전용 step type으로 분리
type: feedback
---

모듈 전용 로직은 common으로 올리지 말고 해당 모듈에서 자체 step type으로 등록한다.

**Why:** 흔히 쓰는 패턴이 아닌 것은 범용화하면 오히려 복잡도만 증가. bojo에서만 쓰는 LinkTableObsvDataFetcher를 common에 올리면 bojo-int에는 불필요한 의존이 생김.

**How to apply:** StepFactory 구조에서 common에는 범용 step type만 두고(source-to-if 등), 모듈 전용 로직은 별도 step type으로 해당 모듈에 등록(source-to-if-link, link-update 등). "common으로 올릴까요?" 제안 전에 다른 모듈에서도 실제로 쓰는지 먼저 확인.
