package com.sync.orchestrator.service;

import com.sync.orchestrator.dto.DatasourceDto;
import com.sync.orchestrator.entity.Datasource;
import com.sync.orchestrator.entity.DatasourceColumn;
import com.sync.orchestrator.entity.DatasourceTable;
import com.sync.orchestrator.entity.DbType;
import com.sync.orchestrator.repository.DatasourceRepository;
import com.sync.orchestrator.repository.DatasourceTableRepository;

import com.sync.agent.common.datasource.PasswordEncryptor;
import com.sync.orchestrator.entity.Agent;
import com.sync.orchestrator.repository.AgentRepository;
import com.sync.orchestrator.entity.ZoneConfig;
import com.sync.orchestrator.repository.ZoneConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DatasourceService {

    private final DatasourceRepository datasourceRepository;
    private final DatasourceTableRepository tableRepository;
    private final AgentRepository agentRepository;
    private final ZoneConfigRepository zoneConfigRepository;
    private final RestTemplate restTemplate;
    private final PasswordEncryptor passwordEncryptor;

    /**
     * 전체 목록 조회
     */
    public List<DatasourceDto.Response> findAll() {
        return datasourceRepository.findAll().stream()
                .map(DatasourceDto.Response::from)
                .collect(Collectors.toList());
    }

    /**
     * 활성화된 목록 조회
     */
    public List<DatasourceDto.Response> findActive() {
        return datasourceRepository.findByIsActiveTrue().stream()
                .map(DatasourceDto.Response::from)
                .collect(Collectors.toList());
    }

    /**
     * Agent 내부용 연결 정보 조회 (암호문 그대로 전달)
     * Agent/Proxy/api-collector가 각자 PasswordEncryptor로 복호화
     */
    public DatasourceDto.ConnectionInfo getConnectionInfo(String datasourceId) {
        Datasource ds = datasourceRepository.findById(datasourceId)
                .orElseThrow(() -> new RuntimeException("Datasource not found: " + datasourceId));
        return DatasourceDto.ConnectionInfo.builder()
                .datasourceId(ds.getDatasourceId())
                .dbType(ds.getDbType().name())
                .host(ds.getHost())
                .port(ds.getPort())
                .databaseName(ds.getDatabaseName())
                .username(ds.getUsername())
                .password(ds.getPassword())
                .build();
    }

    /**
     * Agent 등록 시 선택 목록용 (간단한 정보만)
     */
    public List<DatasourceDto.SimpleResponse> findAllSimple() {
        return datasourceRepository.findByIsActiveTrue().stream()
                .map(DatasourceDto.SimpleResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 테이블 alias 전역 조회 (tableName → tableAlias 매핑)
     * 같은 테이블명이 여러 datasource에 있을 수 있으므로, alias가 있는 것을 우선 사용
     */
    public Map<String, String> getTableAliasMap() {
        return tableRepository.findAll().stream()
                .filter(t -> (t.getTableAlias() != null && !t.getTableAlias().isEmpty())
                        || (t.getDescription() != null && !t.getDescription().isEmpty()))
                .collect(Collectors.toMap(
                        DatasourceTable::getTableName,
                        t -> {
                            // alias 우선, 없으면 description fallback
                            if (t.getTableAlias() != null && !t.getTableAlias().isEmpty()) {
                                return t.getTableAlias();
                            }
                            return t.getDescription();
                        },
                        (existing, replacement) -> existing
                ));
    }

    /**
     * sourceRef 해석용 lookup 데이터 조회
     * ID → 이름 매핑을 반환하여 프론트엔드에서 sourceRef를 해석할 수 있게 함
     */
    public DatasourceDto.SourceRefLookup getSourceRefLookup() {
        // datasource id → name 매핑
        java.util.Map<Long, String> datasourceMap = datasourceRepository.findAll().stream()
                .filter(ds -> ds.getId() != null)
                .collect(Collectors.toMap(
                        Datasource::getId,
                        Datasource::getDatasourceName,
                        (existing, replacement) -> existing  // 중복 시 기존 값 유지
                ));

        // table id → name 매핑
        java.util.Map<Long, String> tableMap = tableRepository.findAll().stream()
                .collect(Collectors.toMap(
                        DatasourceTable::getId,
                        DatasourceTable::getTableName,
                        (existing, replacement) -> existing
                ));

        return DatasourceDto.SourceRefLookup.builder()
                .datasources(datasourceMap)
                .tables(tableMap)
                .build();
    }

    /**
     * 단건 조회
     */
    public DatasourceDto.Response findById(String datasourceId) {
        Datasource datasource = datasourceRepository.findById(datasourceId)
                .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + datasourceId));
        return DatasourceDto.Response.from(datasource);
    }

    /**
     * 생성
     */
    @Transactional
    public DatasourceDto.Response create(DatasourceDto.CreateRequest request) {
        if (datasourceRepository.existsById(request.getDatasourceId())) {
            throw new IllegalArgumentException("Datasource already exists: " + request.getDatasourceId());
        }

        Datasource datasource = Datasource.builder()
                .datasourceId(request.getDatasourceId())
                .datasourceName(request.getDatasourceName())
                .dbType(request.getDbType())
                .host(request.getHost())
                .port(request.getPort())
                .databaseName(request.getDatabaseName())
                .username(passwordEncryptor.encrypt(request.getUsername()))
                .password(passwordEncryptor.encrypt(request.getPassword()))
                .description(request.getDescription())
                .zone(request.getZone())
                .build();

        Datasource saved = datasourceRepository.save(datasource);
        log.info("Datasource created: {}", saved.getDatasourceId());
        return DatasourceDto.Response.from(saved);
    }

    /**
     * 수정
     */
    @Transactional
    public DatasourceDto.Response update(String datasourceId, DatasourceDto.UpdateRequest request) {
        Datasource datasource = datasourceRepository.findById(datasourceId)
                .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + datasourceId));

        if (request.getDatasourceName() != null) {
            datasource.setDatasourceName(request.getDatasourceName());
        }
        if (request.getDbType() != null) {
            datasource.setDbType(request.getDbType());
        }
        if (request.getHost() != null) {
            datasource.setHost(request.getHost());
        }
        if (request.getPort() != null) {
            datasource.setPort(request.getPort());
        }
        if (request.getDatabaseName() != null) {
            datasource.setDatabaseName(request.getDatabaseName());
        }
        // username/password는 빈값이 아닐 때만 업데이트 (수정 화면에서 빈값으로 표시)
        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            datasource.setUsername(passwordEncryptor.encrypt(request.getUsername()));
        }
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            datasource.setPassword(passwordEncryptor.encrypt(request.getPassword()));
        }
        if (request.getDescription() != null) {
            datasource.setDescription(request.getDescription());
        }
        if (request.getZone() != null) {
            datasource.setZone(request.getZone());
        }
        if (request.getIsActive() != null) {
            datasource.setIsActive(request.getIsActive());
        }

        log.info("Datasource updated: {}", datasourceId);
        return DatasourceDto.Response.from(datasource);
    }

    /**
     * 삭제
     */
    @Transactional
    public void delete(String datasourceId) {
        if (!datasourceRepository.existsById(datasourceId)) {
            throw new IllegalArgumentException("Datasource not found: " + datasourceId);
        }
        // TODO: Agent에서 참조 중인지 확인
        datasourceRepository.deleteById(datasourceId);
        log.info("Datasource deleted: {}", datasourceId);
    }

    /**
     * 연결 테스트 (저장된 datasource로)
     * zone이 설정되어 있으면 해당 zone의 master Agent에게 프록시
     */
    public DatasourceDto.ConnectionTestResponse testConnection(String datasourceId) {
        Datasource datasource = datasourceRepository.findById(datasourceId)
                .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + datasourceId));

        // 복호화된 username/password
        String decryptedUsername = passwordEncryptor.decrypt(datasource.getUsername());
        String decryptedPassword = passwordEncryptor.decrypt(datasource.getPassword());

        // zone이 설정되어 있으면 해당 zone의 master Agent에게 프록시
        if (datasource.getZone() != null && !datasource.getZone().isEmpty()) {
            return testConnectionViaAgent(
                    datasource.getZone(),
                    datasource.getDbType(),
                    datasource.getHost(),
                    datasource.getPort(),
                    datasource.getDatabaseName(),
                    decryptedUsername,
                    decryptedPassword
            );
        }

        // zone 없으면 직접 테스트 (Orchestrator에서 직접 접근 가능한 DB)
        return testConnectionInternal(
                datasource.getDbType(),
                datasource.getHost(),
                datasource.getPort(),
                datasource.getDatabaseName(),
                decryptedUsername,
                decryptedPassword
        );
    }

    /**
     * 연결 테스트 (입력값으로 - 저장 전 테스트용)
     * zone이 설정되어 있으면 해당 zone의 master Agent에게 프록시
     */
    public DatasourceDto.ConnectionTestResponse testConnection(DatasourceDto.ConnectionTestRequest request) {
        // zone이 설정되어 있으면 해당 zone의 master Agent에게 프록시
        if (request.getZone() != null && !request.getZone().isEmpty()) {
            return testConnectionViaAgent(
                    request.getZone(),
                    request.getDbType(),
                    request.getHost(),
                    request.getPort(),
                    request.getDatabaseName(),
                    request.getUsername(),
                    request.getPassword()
            );
        }

        // zone 없으면 직접 테스트
        return testConnectionInternal(
                request.getDbType(),
                request.getHost(),
                request.getPort(),
                request.getDatabaseName(),
                request.getUsername(),
                request.getPassword()
        );
    }

    /**
     * zone의 프록시 Agent를 통해 연결 테스트
     * ZoneConfig에서 proxyAgentUrl 조회
     */
    private DatasourceDto.ConnectionTestResponse testConnectionViaAgent(
            String zone, DbType dbType, String host, int port, String databaseName, String username, String password) {

        // ZoneConfig에서 proxyAgentUrl 조회
        String proxyAgentUrl = zoneConfigRepository.findByZoneAndIsActiveTrue(zone)
                .map(ZoneConfig::getProxyAgentUrl)
                .orElse(null);

        if (proxyAgentUrl == null) {
            log.warn("No proxy agent URL found for zone: {}, falling back to direct test", zone);
            // master Agent URL이 없으면 직접 테스트 시도
            return testConnectionInternal(dbType, host, port, databaseName, username, password);
        }

        log.info("Testing connection via agent URL: {}", proxyAgentUrl);

        try {
            // Agent에게 연결 테스트 요청
            DatasourceDto.ConnectionTestRequest agentRequest = DatasourceDto.ConnectionTestRequest.builder()
                    .dbType(dbType)
                    .host(host)
                    .port(port)
                    .databaseName(databaseName)
                    .username(username)
                    .password(password)
                    .build();

            String agentApiUrl = proxyAgentUrl + "/api/datasource/test-connection";
            ResponseEntity<DatasourceDto.ConnectionTestResponse> response = restTemplate.postForEntity(
                    agentApiUrl,
                    agentRequest,
                    DatasourceDto.ConnectionTestResponse.class
            );

            if (response.getBody() != null) {
                return response.getBody();
            } else {
                return DatasourceDto.ConnectionTestResponse.builder()
                        .success(false)
                        .message("Empty response from agent")
                        .responseTimeMs(0L)
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to test connection via agent {}: {}", proxyAgentUrl, e.getMessage());
            return DatasourceDto.ConnectionTestResponse.builder()
                    .success(false)
                    .message("Agent connection failed: " + e.getMessage())
                    .responseTimeMs(0L)
                    .build();
        }
    }

    /**
     * 실제 연결 테스트 수행
     */
    private DatasourceDto.ConnectionTestResponse testConnectionInternal(
            DbType dbType, String host, int port, String databaseName, String username, String password) {

        long startTime = System.currentTimeMillis();
        String jdbcUrl = dbType.buildJdbcUrl(host, port, databaseName);

        try {
            // 드라이버 로드
            Class.forName(dbType.getDriverClassName());

            // 연결 시도 (타임아웃 5초)
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                long responseTime = System.currentTimeMillis() - startTime;
                log.info("Connection test successful: {} ({}ms)", jdbcUrl, responseTime);

                return DatasourceDto.ConnectionTestResponse.builder()
                        .success(true)
                        .message("Connection successful")
                        .responseTimeMs(responseTime)
                        .build();
            }
        } catch (ClassNotFoundException e) {
            log.error("Driver not found: {}", dbType.getDriverClassName());
            return DatasourceDto.ConnectionTestResponse.builder()
                    .success(false)
                    .message("Driver not found: " + dbType.getDriverClassName())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            log.error("Connection test failed: {} - {}", jdbcUrl, e.getMessage());
            return DatasourceDto.ConnectionTestResponse.builder()
                    .success(false)
                    .message("Connection failed: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    // ========== 테이블/컬럼 검색 및 관리 ==========

    /**
     * 테이블 검색 (zone이 있으면 agent를 통해 검색)
     */
    public List<DatasourceDto.TableSearchResult> searchTables(String datasourceId, String query) {
        log.info("=== searchTables called: datasourceId={}, query={} ===", datasourceId, query);

        Datasource datasource = datasourceRepository.findById(datasourceId)
                .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + datasourceId));

        log.info("Datasource found: id={}, zone={}, dbType={}, host={}",
                datasource.getDatasourceId(), datasource.getZone(), datasource.getDbType(), datasource.getHost());

        // zone이 설정되어 있으면 agent를 통해 검색
        if (datasource.getZone() != null && !datasource.getZone().isEmpty()) {
            log.info("Zone is set ({}), searching via agent", datasource.getZone());
            return searchTablesViaAgent(datasource, query);
        }

        // zone 없으면 직접 검색
        log.info("No zone set, searching directly");
        return searchTablesInternal(datasource, query);
    }

    /**
     * Agent를 통해 테이블 검색
     */
    private List<DatasourceDto.TableSearchResult> searchTablesViaAgent(Datasource datasource, String query) {
        log.info("searchTablesViaAgent called for zone: {}", datasource.getZone());

        String proxyAgentUrl = getProxyAgentUrl(datasource.getZone());
        log.info("Master agent URL resolved: {}", proxyAgentUrl);

        if (proxyAgentUrl == null) {
            log.warn("No proxy agent URL found for zone: {}, falling back to direct search", datasource.getZone());
            return searchTablesInternal(datasource, query);
        }

        log.info("Searching tables via agent: {}", proxyAgentUrl);

        try {
            var request = new java.util.HashMap<String, Object>();
            request.put("dbType", datasource.getDbType().name());
            request.put("host", datasource.getHost());
            request.put("port", datasource.getPort());
            request.put("databaseName", datasource.getDatabaseName());
            request.put("username", passwordEncryptor.decrypt(datasource.getUsername()));
            request.put("password", passwordEncryptor.decrypt(datasource.getPassword()));
            request.put("query", query);

            String agentUrl = proxyAgentUrl + "/api/datasource/search-tables";
            log.info("Calling agent API: {} with request: dbType={}, host={}, query={}",
                    agentUrl, datasource.getDbType().name(), datasource.getHost(), query);

            var response = restTemplate.postForEntity(agentUrl, request, DatasourceDto.TableSearchResult[].class);

            log.info("Agent response status: {}", response.getStatusCode());

            if (response.getBody() != null) {
                log.info("Agent returned {} tables", response.getBody().length);
                return java.util.Arrays.asList(response.getBody());
            }
            log.warn("Agent returned null body");
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to search tables via agent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search tables via agent: " + e.getMessage(), e);
        }
    }

    /**
     * 직접 테이블 검색
     */
    private List<DatasourceDto.TableSearchResult> searchTablesInternal(Datasource datasource, String query) {
        List<DatasourceDto.TableSearchResult> results = new ArrayList<>();
        String jdbcUrl = datasource.getJdbcUrl();

        try {
            Class.forName(datasource.getDriverClassName());
            String decryptedUsername = passwordEncryptor.decrypt(datasource.getUsername());
            String decryptedPassword = passwordEncryptor.decrypt(datasource.getPassword());
            try (Connection conn = DriverManager.getConnection(jdbcUrl, decryptedUsername, decryptedPassword)) {
                DatabaseMetaData metaData = conn.getMetaData();

                // MySQL은 catalog에 DB명, Oracle은 schema에 유저명 지정
                String catalog = datasource.getDbType() == DbType.MYSQL ? datasource.getDatabaseName() : null;
                String schema = (datasource.getDbType() == DbType.ORACLE || datasource.getDbType() == DbType.TIBERO)
                        ? passwordEncryptor.decrypt(datasource.getUsername()).toUpperCase() : null;
                String[] types = {"TABLE", "VIEW"};
                String searchPattern = query != null && !query.isEmpty() ? "%" + query.toUpperCase() + "%" : "%";

                try (ResultSet rs = metaData.getTables(catalog, schema, searchPattern, types)) {
                    int count = 0;
                    while (rs.next() && count < 100) {
                        results.add(DatasourceDto.TableSearchResult.builder()
                                .tableName(rs.getString("TABLE_NAME"))
                                .tableType(rs.getString("TABLE_TYPE"))
                                .remarks(rs.getString("REMARKS"))
                                .build());
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to search tables: {}", e.getMessage());
            throw new RuntimeException("Failed to search tables: " + e.getMessage(), e);
        }

        log.info("Found {} tables matching '{}' in {}", results.size(), query, datasource.getDatasourceId());
        return results;
    }

    /**
     * 컬럼 검색 (zone이 있으면 agent를 통해 검색)
     */
    public List<DatasourceDto.ColumnSearchResult> searchColumns(String datasourceId, String tableName, String query) {
        Datasource datasource = datasourceRepository.findById(datasourceId)
                .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + datasourceId));

        // zone이 설정되어 있으면 agent를 통해 검색
        if (datasource.getZone() != null && !datasource.getZone().isEmpty()) {
            return searchColumnsViaAgent(datasource, tableName, query);
        }

        // zone 없으면 직접 검색
        return searchColumnsInternal(datasource, tableName, query);
    }

    /**
     * Agent를 통해 컬럼 검색
     */
    private List<DatasourceDto.ColumnSearchResult> searchColumnsViaAgent(Datasource datasource, String tableName, String query) {
        String proxyAgentUrl = getProxyAgentUrl(datasource.getZone());
        if (proxyAgentUrl == null) {
            log.warn("No proxy agent URL found for zone: {}, falling back to direct search", datasource.getZone());
            return searchColumnsInternal(datasource, tableName, query);
        }

        log.info("Searching columns via agent: {}", proxyAgentUrl);

        try {
            var request = new java.util.HashMap<String, Object>();
            request.put("dbType", datasource.getDbType().name());
            request.put("host", datasource.getHost());
            request.put("port", datasource.getPort());
            request.put("databaseName", datasource.getDatabaseName());
            request.put("username", passwordEncryptor.decrypt(datasource.getUsername()));
            request.put("password", passwordEncryptor.decrypt(datasource.getPassword()));
            request.put("tableName", tableName);
            request.put("query", query);

            String agentUrl = proxyAgentUrl + "/api/datasource/search-columns";
            var response = restTemplate.postForEntity(agentUrl, request, DatasourceDto.ColumnSearchResult[].class);

            if (response.getBody() != null) {
                return java.util.Arrays.asList(response.getBody());
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to search columns via agent: {}", e.getMessage());
            throw new RuntimeException("Failed to search columns via agent: " + e.getMessage(), e);
        }
    }

    /**
     * 직접 컬럼 검색
     */
    private List<DatasourceDto.ColumnSearchResult> searchColumnsInternal(Datasource datasource, String tableName, String query) {
        List<DatasourceDto.ColumnSearchResult> results = new ArrayList<>();
        String jdbcUrl = datasource.getJdbcUrl();

        try {
            Class.forName(datasource.getDriverClassName());
            String decryptedUsername = passwordEncryptor.decrypt(datasource.getUsername());
            String decryptedPassword = passwordEncryptor.decrypt(datasource.getPassword());
            try (Connection conn = DriverManager.getConnection(jdbcUrl, decryptedUsername, decryptedPassword)) {
                DatabaseMetaData metaData = conn.getMetaData();

                // MySQL은 catalog에 DB명을 지정해야 해당 DB 테이블만 반환
                String catalog = datasource.getDbType() == DbType.MYSQL ? datasource.getDatabaseName() : null;

                Set<String> pkColumns = new HashSet<>();
                try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, null, tableName)) {
                    while (pkRs.next()) {
                        pkColumns.add(pkRs.getString("COLUMN_NAME"));
                    }
                }

                String columnPattern = query != null && !query.isEmpty() ? "%" + query.toUpperCase() + "%" : "%";
                try (ResultSet rs = metaData.getColumns(catalog, null, tableName, columnPattern)) {
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");
                        results.add(DatasourceDto.ColumnSearchResult.builder()
                                .columnName(columnName)
                                .dataType(rs.getString("TYPE_NAME"))
                                .columnSize(rs.getInt("COLUMN_SIZE"))
                                .isNullable("YES".equals(rs.getString("IS_NULLABLE")))
                                .isPrimaryKey(pkColumns.contains(columnName))
                                .remarks(rs.getString("REMARKS"))
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to search columns: {}", e.getMessage());
            throw new RuntimeException("Failed to search columns: " + e.getMessage(), e);
        }

        log.info("Found {} columns for table {} matching '{}'", results.size(), tableName, query);
        return results;
    }

    /**
     * Zone의 프록시 Agent URL 조회
     */
    private String getProxyAgentUrl(String zone) {
        log.info("getProxyAgentUrl called for zone: {}", zone);

        // ZoneConfig에서 조회
        var zoneConfig = zoneConfigRepository.findByZoneAndIsActiveTrue(zone);
        log.info("ZoneConfig lookup result: {}", zoneConfig.isPresent() ? "found" : "not found");

        String url = zoneConfig.map(ZoneConfig::getProxyAgentUrl).orElse(null);

        if (url != null) {
            log.info("Using ZoneConfig proxyAgentUrl: {}", url);
        } else {
            log.warn("No proxy agent URL found for zone: {}", zone);
        }

        return url;
    }

    /**
     * 등록된 테이블 목록 조회
     */
    public List<DatasourceDto.TableResponse> getRegisteredTables(String datasourceId) {
        return tableRepository.findByDatasourceId(datasourceId).stream()
                .map(DatasourceDto.TableResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 테이블 등록 (컬럼 포함)
     */
    @Transactional
    public DatasourceDto.TableResponse registerTable(String datasourceId, DatasourceDto.TableCreateRequest request) {
        // 중복 체크
        if (tableRepository.existsByDatasourceIdAndTableName(datasourceId, request.getTableName())) {
            throw new IllegalArgumentException("Table already registered: " + request.getTableName());
        }

        DatasourceTable table = DatasourceTable.builder()
                .datasourceId(datasourceId)
                .tableName(request.getTableName())
                .tableAlias(request.getTableAlias())
                .description(request.getDescription())
                .build();

        // 컬럼 추가
        if (request.getColumns() != null) {
            for (DatasourceDto.ColumnCreateRequest colReq : request.getColumns()) {
                DatasourceColumn column = DatasourceColumn.builder()
                        .columnName(colReq.getColumnName())
                        .columnAlias(colReq.getColumnAlias())
                        .dataType(colReq.getDataType())
                        .isPrimaryKey(colReq.getIsPrimaryKey() != null ? colReq.getIsPrimaryKey() : false)
                        .isNullable(colReq.getIsNullable() != null ? colReq.getIsNullable() : true)
                        .description(colReq.getDescription())
                        .build();
                table.addColumn(column);
            }
        }

        DatasourceTable saved = tableRepository.save(table);
        log.info("Table registered: {} for datasource {}", request.getTableName(), datasourceId);
        return DatasourceDto.TableResponse.from(saved);
    }

    /**
     * 테이블 삭제
     */
    @Transactional
    public void deleteTable(String datasourceId, Long tableId) {
        DatasourceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        if (!table.getDatasourceId().equals(datasourceId)) {
            throw new IllegalArgumentException("Table does not belong to datasource: " + datasourceId);
        }

        tableRepository.delete(table);
        log.info("Table deleted: {} from datasource {}", table.getTableName(), datasourceId);
    }
}
