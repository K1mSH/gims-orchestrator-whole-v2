---
name: KRDS 체크박스/라디오는 .krds-form-check 패턴 필수
description: KRDS CSS 는 모든 input[type=checkbox/radio] 를 !important 로 1px 사이즈+절대 위치로 숨김. .krds-form-check medium 패턴(input + label::before/::after)으로만 시각 노출
type: feedback
originSessionId: c1918da9-0e6a-4f21-bd99-52c082ca924d
---
KRDS의 `input[type=checkbox]` / `input[type=radio]` 는 모두 `!important` 로 시각적으로 숨겨져 있다. label 의 `::before`/`::after` 가 실제 체크박스 모양을 그린다.

**Why:**
- KRDS `component.css` 9928 라인: `position: absolute !important; width: 1px !important; height: 1px !important; clip: rect(0,0,0,0) !important;`
- 일반 마크업 (input 단독, label 없이) 사용 시 화면에서 안 보임
- 5/11 ColumnsTab/ParamsTab/MappingTab/InfoTab 등에서 같은 이슈 반복 발견

**How to apply:**

체크박스 사용 시 항상 다음 패턴:
```tsx
<div className="krds-form-check medium">
  <input type="checkbox" id="고유ID" checked={...} onChange={...} />
  <label htmlFor="고유ID" aria-label="설명"></label>
</div>
```

라벨 텍스트 옆에 두려면:
```tsx
<div className={styles.wrap}>
  <div className="krds-form-check medium">
    <input type="checkbox" id="upsert-enabled" ... />
    <label htmlFor="upsert-enabled" aria-label="..."></label>
  </div>
  <label htmlFor="upsert-enabled" className={styles.text}>중복키 충돌 시 갱신</label>
</div>
```

CSS module 안에서 grid cell 가운데정렬 시:
```css
.checkCell > :global(.krds-form-check) {
  justify-self: center;
}
```

- 자체 `accent-color` / `transform: scale()` 등으로 보이게 시도하면 KRDS `!important` 가 이김 — 패턴 사용 필수
- KRDS sizes: small / medium / large — 우리는 medium 통일
