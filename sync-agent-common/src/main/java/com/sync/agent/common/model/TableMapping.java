package com.sync.agent.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * YAML table-mappings 섹션의 파싱 결과
 * Source→Target 관계를 명시적으로 정의 (N:M 대응)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TableMapping {
    private String name;           // 매핑 이름 (jewon, obsvdata 등)
    private List<String> source;   // source 테이블 목록
    private List<String> target;   // target 테이블 목록
}
