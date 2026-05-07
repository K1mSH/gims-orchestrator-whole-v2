package com.infolink.agent.bojo.config;

import com.infolink.agent.common.datasource.DataSourceInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Service;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PreDestroy;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 동적 EntityManagerFactory 생성 및 관리 서비스.
 *
 * <p>DataSourceInfo(Orchestrator에서 전달)별로 독립적인 HikariDataSource와
 * EntityManagerFactory를 생성하여 ConcurrentHashMap에 캐싱한다.</p>
 *
 * <ul>
 *   <li>Source/Target/IF 등 datasourceId별 EntityManager 제공</li>
 *   <li>DB 대소문자 감지(information_schema 조회)로 PhysicalNamingStrategy 분기</li>
 *   <li>PostgreSQL / MySQL Dialect 자동 선택</li>
 *   <li>JdbcTemplate 인스턴스도 함께 관리</li>
 * </ul>
 *
 * @see SyncDataSourceService
 */
@Slf4j
@Service
public class DynamicEntityManagerService {

    private final SyncDataSourceService syncDataSourceService;

    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, EntityManagerFactory> entityManagerFactories = new ConcurrentHashMap<>();

    @Autowired
    public DynamicEntityManagerService(SyncDataSourceService syncDataSourceService) {
        this.syncDataSourceService = syncDataSourceService;
    }

    public String getSourceDatasourceId() {
        return syncDataSourceService.getSourceDatasourceId();
    }

    public String getTargetDatasourceId() {
        return syncDataSourceService.getTargetDatasourceId();
    }

    public EntityManager getSourceEntityManager() {
        String dsId = getSourceDatasourceId();
        return getEntityManager(dsId, false, "com.infolink.agent.bojo.entity.source");
    }

    public EntityManager getTargetEntityManager() {
        String dsId = getTargetDatasourceId();
        return getEntityManager(dsId, true,
                "com.infolink.agent.bojo.entity.iftable.rsv",
                "com.infolink.agent.bojo.entity.iftable.snd",
                "com.infolink.agent.bojo.entity.target"
        );
    }

    /**
     * Target DB 타입 반환 (POSTGRESQL, MYSQL 등)
     */
    public String getTargetDbType() {
        String dsId = getTargetDatasourceId();
        DataSourceInfo info = findDataSourceInfo(dsId);
        return info != null ? info.getDbType() : "POSTGRESQL";
    }

    public JdbcTemplate getTargetJdbcTemplate() {
        String dsId = getTargetDatasourceId();
        DataSourceInfo info = findDataSourceInfo(dsId);
        if (info == null) {
            throw new IllegalArgumentException("DataSource info not found: " + dsId);
        }
        DataSource dataSource = dataSources.computeIfAbsent(dsId, id -> createDataSource(info));
        return new JdbcTemplate(dataSource);
    }

    public EntityManager getEntityManager(String datasourceId, boolean isTarget, String... packagesToScan) {
        String cacheKey = datasourceId + (isTarget ? "_target" : "_source");
        EntityManagerFactory emf = entityManagerFactories.computeIfAbsent(
                cacheKey,
                id -> createEntityManagerFactory(datasourceId, isTarget, packagesToScan)
        );
        return emf.createEntityManager();
    }

    private EntityManagerFactory createEntityManagerFactory(String datasourceId, boolean isTarget, String... packagesToScan) {
        log.info("Creating EntityManagerFactory for datasource: {}", datasourceId);

        DataSourceInfo info = findDataSourceInfo(datasourceId);
        if (info == null) {
            throw new IllegalArgumentException("DataSource info not found: " + datasourceId);
        }

        DataSource dataSource = dataSources.computeIfAbsent(datasourceId, id -> createDataSource(info));

        boolean useUpperCase;
        if (isTarget) {
            useUpperCase = false;
        } else {
            useUpperCase = detectUpperCaseColumns(dataSource);
        }

        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource(dataSource);
        emfBean.setPackagesToScan(packagesToScan);
        emfBean.setPersistenceUnitName("dynamic-" + datasourceId);
        emfBean.setPersistenceProviderClass(HibernatePersistenceProvider.class);

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(true);
        emfBean.setJpaVendorAdapter(vendorAdapter);

        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.dialect", getHibernateDialect(info.getDbType()));
        jpaProperties.put("hibernate.show_sql", "true");
        jpaProperties.put("hibernate.format_sql", "true");
        if (isTarget) {
            jpaProperties.put("hibernate.hbm2ddl.auto", "update");
        } else {
            jpaProperties.put("hibernate.hbm2ddl.auto", "none");
        }
        jpaProperties.put("hibernate.physical_naming_strategy", new CaseAwareNamingStrategy(useUpperCase));
        emfBean.setJpaProperties(jpaProperties);

        emfBean.afterPropertiesSet();
        return emfBean.getObject();
    }

    private boolean detectUpperCaseColumns(DataSource dataSource) {
        try (var conn = dataSource.getConnection()) {
            String sql = "SELECT column_name, table_schema FROM information_schema.columns " +
                    "WHERE lower(table_name) = 'sec_jewon_view' AND lower(column_name) = 'obsv_code' LIMIT 1";
            try (var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String columnName = rs.getString(1);
                    return isUpperCase(columnName);
                }
            }

            String fallbackSql = "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema NOT IN ('pg_catalog', 'information_schema') " +
                    "AND lower(column_name) NOT IN ('id', 'created_at', 'updated_at') LIMIT 1";
            try (var stmt = conn.prepareStatement(fallbackSql); var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return isUpperCase(rs.getString(1));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to detect column case, defaulting to lower_case: {}", e.getMessage());
        }
        return false;
    }

    private boolean isUpperCase(String name) {
        return name != null && name.equals(name.toUpperCase()) && !name.equals(name.toLowerCase());
    }

    private DataSourceInfo findDataSourceInfo(String datasourceId) {
        DataSourceInfo sourceInfo = syncDataSourceService.getSourceDatasourceInfoOrNull();
        if (sourceInfo != null && datasourceId.equals(sourceInfo.getDatasourceId())) return sourceInfo;

        DataSourceInfo targetInfo = syncDataSourceService.getTargetDatasourceInfoOrNull();
        if (targetInfo != null && datasourceId.equals(targetInfo.getDatasourceId())) return targetInfo;

        if (sourceInfo != null) return sourceInfo;
        if (targetInfo != null) return targetInfo;

        return null;
    }

    private HikariDataSource createDataSource(DataSourceInfo info) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("JpaPool-" + info.getDatasourceId());
        hikariConfig.setJdbcUrl(info.getJdbcUrl());
        hikariConfig.setUsername(info.getUsername());
        hikariConfig.setPassword(info.getPassword());
        hikariConfig.setDriverClassName(info.getDriverClassName());
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        return new HikariDataSource(hikariConfig);
    }

    private String getHibernateDialect(String dbType) {
        return switch (dbType.toUpperCase()) {
            case "POSTGRESQL" -> "org.hibernate.dialect.PostgreSQLDialect";
            case "ORACLE" -> "org.hibernate.dialect.Oracle12cDialect";
            case "MYSQL" -> "org.hibernate.dialect.MySQL8Dialect";
            case "MARIADB" -> "org.hibernate.dialect.MariaDB103Dialect";
            case "MSSQL" -> "org.hibernate.dialect.SQLServer2016Dialect";
            case "H2" -> "org.hibernate.dialect.H2Dialect";
            default -> "org.hibernate.dialect.PostgreSQLDialect";
        };
    }

    @PreDestroy
    public void closeAll() {
        entityManagerFactories.forEach((id, emf) -> {
            if (emf.isOpen()) { emf.close(); }
        });
        entityManagerFactories.clear();
        dataSources.forEach((id, ds) -> {
            if (!ds.isClosed()) { ds.close(); }
        });
        dataSources.clear();
    }
}
