package com.sync.agent.common.datasource;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Lazy 초기화 DataSource
 * 실제 Connection 요청 시점에 대표 Agent로부터 접속 정보를 가져와서 초기화
 */
@Slf4j
public class LazyDataSource implements DataSource {

    private final String datasourceId;
    private final Supplier<DataSourceInfo> dataSourceInfoSupplier;
    private final PasswordEncryptor passwordEncryptor;
    private final AtomicReference<HikariDataSource> delegate = new AtomicReference<>();
    private final Object lock = new Object();

    private int maxRetries = 5;
    private long retryDelayMs = 3000;

    public LazyDataSource(String datasourceId,
                          Supplier<DataSourceInfo> dataSourceInfoSupplier,
                          PasswordEncryptor passwordEncryptor) {
        this.datasourceId = datasourceId;
        this.dataSourceInfoSupplier = dataSourceInfoSupplier;
        this.passwordEncryptor = passwordEncryptor;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    private DataSource getDelegate() {
        HikariDataSource ds = delegate.get();
        if (ds != null) {
            return ds;
        }

        synchronized (lock) {
            ds = delegate.get();
            if (ds != null) {
                return ds;
            }

            ds = initializeWithRetry();
            delegate.set(ds);
            return ds;
        }
    }

    private HikariDataSource initializeWithRetry() {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Initializing DataSource [{}], attempt {}/{}", datasourceId, attempt, maxRetries);

                DataSourceInfo info = dataSourceInfoSupplier.get();
                if (info == null) {
                    throw new RuntimeException("DataSourceInfo is null for: " + datasourceId);
                }

                HikariDataSource ds = new HikariDataSource();
                ds.setPoolName("HikariPool-" + datasourceId);
                ds.setJdbcUrl(info.getJdbcUrl());
                ds.setUsername(info.getUsername());
                ds.setPassword(passwordEncryptor.decrypt(info.getPassword()));
                ds.setDriverClassName(info.getDriverClassName());
                ds.setMaximumPoolSize(10);
                ds.setMinimumIdle(2);
                ds.setConnectionTimeout(30000);

                // 연결 테스트
                try (Connection conn = ds.getConnection()) {
                    log.info("DataSource [{}] initialized successfully: {}", datasourceId, info.getJdbcUrl());
                }

                return ds;

            } catch (Exception e) {
                lastException = e;
                log.warn("Failed to initialize DataSource [{}], attempt {}/{}: {}",
                        datasourceId, attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting to retry", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to initialize DataSource after " + maxRetries + " attempts: " + datasourceId, lastException);
    }

    public boolean isInitialized() {
        return delegate.get() != null;
    }

    public void close() {
        HikariDataSource ds = delegate.get();
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    // DataSource 인터페이스 구현
    @Override
    public Connection getConnection() throws SQLException {
        return getDelegate().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getDelegate().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return getDelegate().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        getDelegate().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        getDelegate().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return getDelegate().getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return getDelegate().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || getDelegate().isWrapperFor(iface);
    }
}
