package com.sync.orchestrator.controller;

import com.sync.orchestrator.dto.DatasourceDto;
import com.sync.orchestrator.service.DatasourceService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceService datasourceService;

    /**
     * 전체 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<DatasourceDto.Response>> getDatasources() {
        return ResponseEntity.ok(datasourceService.findAll());
    }

    /**
     * 활성화된 목록만 조회
     */
    @GetMapping("/active")
    public ResponseEntity<List<DatasourceDto.Response>> getActiveDatasources() {
        return ResponseEntity.ok(datasourceService.findActive());
    }

    /**
     * Agent 등록 시 선택 목록용 (간단한 정보만)
     */
    @GetMapping("/simple")
    public ResponseEntity<List<DatasourceDto.SimpleResponse>> getDatasourcesSimple() {
        return ResponseEntity.ok(datasourceService.findAllSimple());
    }

    /**
     * sourceRef 해석용 lookup 데이터 조회
     * ID → 이름 매핑을 반환하여 프론트엔드에서 sourceRef를 해석할 수 있게 함
     */
    @GetMapping("/sourceref-lookup")
    public ResponseEntity<DatasourceDto.SourceRefLookup> getSourceRefLookup() {
        return ResponseEntity.ok(datasourceService.getSourceRefLookup());
    }

    /**
     * 테이블 alias 전역 조회 (tableName → tableAlias 매핑)
     */
    @GetMapping("/table-alias-map")
    public ResponseEntity<Map<String, String>> getTableAliasMap() {
        return ResponseEntity.ok(datasourceService.getTableAliasMap());
    }

    /**
     * 단건 조회
     */
    @GetMapping("/{datasourceId}")
    public ResponseEntity<DatasourceDto.Response> getDatasource(@PathVariable String datasourceId) {
        return ResponseEntity.ok(datasourceService.findById(datasourceId));
    }

    /**
     * 연결 정보 조회 (Agent 내부용 - 자격증명 포함)
     * Agent가 trace API 등에서 외부 DB에 접속해야 할 때 사용
     */
    @GetMapping("/{datasourceId}/connection-info")
    public ResponseEntity<DatasourceDto.ConnectionInfo> getConnectionInfo(@PathVariable String datasourceId) {
        return ResponseEntity.ok(datasourceService.getConnectionInfo(datasourceId));
    }

    /**
     * 생성
     */
    @PostMapping
    public ResponseEntity<DatasourceDto.Response> createDatasource(
            @Valid @RequestBody DatasourceDto.CreateRequest request) {
        DatasourceDto.Response response = datasourceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 수정
     */
    @PutMapping("/{datasourceId}")
    public ResponseEntity<DatasourceDto.Response> updateDatasource(
            @PathVariable String datasourceId,
            @RequestBody DatasourceDto.UpdateRequest request) {
        return ResponseEntity.ok(datasourceService.update(datasourceId, request));
    }

    /**
     * 삭제
     */
    @DeleteMapping("/{datasourceId}")
    public ResponseEntity<Void> deleteDatasource(@PathVariable String datasourceId) {
        datasourceService.delete(datasourceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 저장된 datasource 연결 테스트
     */
    @PostMapping("/{datasourceId}/test-connection")
    public ResponseEntity<DatasourceDto.ConnectionTestResponse> testConnection(
            @PathVariable String datasourceId) {
        return ResponseEntity.ok(datasourceService.testConnection(datasourceId));
    }

    /**
     * 저장 전 연결 테스트 (입력값으로 직접 테스트)
     */
    @PostMapping("/test-connection")
    public ResponseEntity<DatasourceDto.ConnectionTestResponse> testConnectionBeforeSave(
            @Valid @RequestBody DatasourceDto.ConnectionTestRequest request) {
        return ResponseEntity.ok(datasourceService.testConnection(request));
    }

    // ========== 테이블/컬럼 관리 ==========

    /**
     * 실제 DB에서 테이블 검색
     */
    @GetMapping("/{datasourceId}/search-tables")
    public ResponseEntity<List<DatasourceDto.TableSearchResult>> searchTables(
            @PathVariable String datasourceId,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(datasourceService.searchTables(datasourceId, query));
    }

    /**
     * 실제 DB에서 컬럼 검색
     */
    @GetMapping("/{datasourceId}/search-columns")
    public ResponseEntity<List<DatasourceDto.ColumnSearchResult>> searchColumns(
            @PathVariable String datasourceId,
            @RequestParam String tableName,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(datasourceService.searchColumns(datasourceId, tableName, query));
    }

    /**
     * 등록된 테이블 목록 조회
     */
    @GetMapping("/{datasourceId}/tables")
    public ResponseEntity<List<DatasourceDto.TableResponse>> getRegisteredTables(
            @PathVariable String datasourceId) {
        return ResponseEntity.ok(datasourceService.getRegisteredTables(datasourceId));
    }

    /**
     * 테이블 등록 (컬럼 포함)
     */
    @PostMapping("/{datasourceId}/tables")
    public ResponseEntity<DatasourceDto.TableResponse> registerTable(
            @PathVariable String datasourceId,
            @Valid @RequestBody DatasourceDto.TableCreateRequest request) {
        DatasourceDto.TableResponse response = datasourceService.registerTable(datasourceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 테이블 컬럼 갱신 (기존 컬럼 교체)
     */
    @PutMapping("/{datasourceId}/tables/{tableId}/columns")
    public ResponseEntity<DatasourceDto.TableResponse> refreshTableColumns(
            @PathVariable String datasourceId,
            @PathVariable Long tableId,
            @RequestBody DatasourceDto.TableCreateRequest request) {
        return ResponseEntity.ok(datasourceService.refreshTableColumns(datasourceId, tableId, request));
    }

    /**
     * 테이블 삭제
     */
    @DeleteMapping("/{datasourceId}/tables/{tableId}")
    public ResponseEntity<Void> deleteTable(
            @PathVariable String datasourceId,
            @PathVariable Long tableId) {
        datasourceService.deleteTable(datasourceId, tableId);
        return ResponseEntity.noContent().build();
    }
}
