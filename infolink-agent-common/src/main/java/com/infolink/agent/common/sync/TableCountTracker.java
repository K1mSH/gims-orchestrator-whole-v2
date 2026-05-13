package com.infolink.agent.common.sync;

import java.util.LinkedHashMap;

/**
 * 매핑(step) 1회 실행 동안 테이블별 처리행수를 누적.
 * source / target 양쪽에 공통 사용 (현재는 target per-table 카운트 표시에 사용).
 *
 * <p>생성자에 알려진 테이블명을 미리 넘기면 0건이어도 순서대로 노출된다
 * (sync_log.target_tables 의 [{"name":..,"count":..}, ...] 순서 = 생성자 인자 순서).
 */
public final class TableCountTracker {

    private final LinkedHashMap<String, Long> counts = new LinkedHashMap<>();

    public TableCountTracker(String... tables) {
        if (tables != null) {
            for (String t : tables) {
                if (t != null && !t.isBlank()) counts.putIfAbsent(t, 0L);
            }
        }
    }

    /** 해당 테이블 +1 */
    public TableCountTracker inc(String table) {
        return add(table, 1L);
    }

    /** 해당 테이블 +n (n<=0 이면 무시하지 않고 그대로 누적 — 보통 0/1 이 들어옴) */
    public TableCountTracker add(String table, long n) {
        if (table != null && !table.isBlank()) counts.merge(table, n, Long::sum);
        return this;
    }

    /** 전체 합 (sync_log.write_count 로 사용) */
    public long total() {
        return counts.values().stream().mapToLong(v -> v == null ? 0L : v).sum();
    }

    /** 테이블명 → 건수 (순서 보존) */
    public LinkedHashMap<String, Long> asMap() {
        return counts;
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }
}
