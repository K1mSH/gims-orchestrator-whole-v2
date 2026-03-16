package com.infolink.collector.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 자체 DB 메타데이터 조회 (테이블/컬럼 목록)
 * 매핑 UI에서 target 테이블/컬럼 드롭다운에 사용
 */
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/tables")
    public List<String> getTables() {
        return jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                "AND table_name NOT LIKE 'api_%' " +  // collector 내부 테이블 제외
                "ORDER BY table_name",
                String.class
        );
    }

    @GetMapping("/tables/{tableName}/columns")
    public List<Map<String, Object>> getColumns(@PathVariable String tableName) {
        return jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable, " +
                "CASE WHEN column_name IN (" +
                "  SELECT kcu.column_name FROM information_schema.table_constraints tc " +
                "  JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                "  WHERE tc.table_name = ? AND tc.constraint_type = 'PRIMARY KEY'" +
                ") THEN 'YES' ELSE 'NO' END as is_pk " +
                "FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = ? " +
                "ORDER BY ordinal_position",
                tableName, tableName
        );
    }
}
