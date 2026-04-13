package com.sync.agent.bojoint.config;

import com.sync.agent.common.datasource.DataSourceInfo;
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
 * 동적 EntityManagerFactory 생성 및 관리 서비스 (내부망 Agent).
 *
 * Source(IF_RSV)와 Target 모두 내부 Oracle에 있으므로,
 * 하나의 datasource에 대해 iftable + source + target 패키지를 모두 스캔한다.
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

    /**
     * Source(IF_RSV) EntityManager — iftable 패키지 스캔
     */
    public EntityManager getSourceEntityManager() {
        String dsId = getSourceDatasourceId();
        return getEntityManager(dsId, false,
                "com.sync.agent.bojoint.entity.iftable");
    }

    /**
     * Target EntityManager — target + source(새올 원본 Read Only) 패키지 스캔
     */
    public EntityManager getTargetEntityManager() {
        String dsId = getTargetDatasourceId();
        return getEntityManager(dsId, true,
                "com.sync.agent.bojoint.entity.iftable",
                "com.sync.agent.bojoint.entity.source",
                "com.sync.agent.bojoint.entity.target");
    }

    /**
     * Target DB 타입 반환 (ORACLE 등)
     */
    public String getTargetDbType() {
        String dsId = getTargetDatasourceId();
        DataSourceInfo info = findDataSourceInfo(dsId);
        return info != null ? info.getDbType() : "ORACLE";
    }

    public JdbcTemplate getTargetJdbcTemplate() {
        String dsId = getTargetDatasourceId();
        DataSourceInfo info = findDataSourceInfo(dsId);
        if (info == null) {
            throw new IllegalArgumentException("DataSource 정보를 찾을 수 없습니다: " + dsId);
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
        log.info("[BojoInt] EntityManagerFactory 생성: {}", datasourceId);

        DataSourceInfo info = findDataSourceInfo(datasourceId);
        if (info == null) {
            throw new IllegalArgumentException("DataSource 정보를 찾을 수 없습니다: " + datasourceId);
        }

        DataSource dataSource = dataSources.computeIfAbsent(datasourceId, id -> createDataSource(info));

        // 내부망은 Oracle → 대문자
        boolean useUpperCase = "ORACLE".equalsIgnoreCase(info.getDbType());

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
        jpaProperties.put("hibernate.hbm2ddl.auto", "validate");
        jpaProperties.put("hibernate.physical_naming_strategy", new CaseAwareNamingStrategy(useUpperCase));
        emfBean.setJpaProperties(jpaProperties);

        emfBean.afterPropertiesSet();
        return emfBean.getObject();
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
            default -> "org.hibernate.dialect.Oracle12cDialect";
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
