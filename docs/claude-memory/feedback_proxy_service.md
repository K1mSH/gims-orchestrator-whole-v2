---
name: 프록시 서비스도 함께 기동
description: API Collector나 Agent 기동 시 프록시 서비스(infolink-proxy-dmz 등)도 같이 올려야 함
type: feedback
---

서비스 기동 시 프록시 서비스도 함께 올릴 것.

**Why:** API Collector, Agent 등이 프록시 경유로 통신하므로 프록시 없이는 연동이 안 됨. 매번 빠뜨려서 사용자가 지적함.

**How to apply:** "서비스 띄워줘" 요청 시 해당 서비스가 의존하는 프록시도 같이 기동. API Collector → infolink-proxy-dmz(8083), Agent Internal → infolink-proxy-internal(8093) 등.
