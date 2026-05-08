'use client';

import { useEffect, useState } from 'react';
import { datasourceApi } from '@/lib/api';
import type {
  Datasource,
  DbType,
  DatasourceCreateRequest,
  DatasourceUpdateRequest,
  ConnectionTestRequest,
  TableSearchResult,
  ColumnSearchResult,
  DatasourceTable,
  ColumnCreateRequest,
} from '@/types';
import styles from './datasources.module.css';

const DB_TYPE_LABELS: Record<DbType, string> = {
  POSTGRESQL: 'PostgreSQL',
  ORACLE: 'Oracle',
  MYSQL: 'MySQL',
  MARIADB: 'MariaDB',
  MSSQL: 'SQL Server',
};

const DEFAULT_PORTS: Record<DbType, number> = {
  POSTGRESQL: 5432,
  ORACLE: 1521,
  MYSQL: 3306,
  MARIADB: 3306,
  MSSQL: 1433,
};

const ZONE_OPTIONS = [
  { value: '', label: '선택안함 (Orchestrator 직접)' },
  { value: 'EXTERNAL', label: 'EXTERNAL (외부망)' },
  { value: 'DMZ', label: 'DMZ' },
  { value: 'INTERNAL_COMMON', label: 'INTERNAL_COMMON (내부망공통)' },
  { value: 'INTERNAL_SERVICE', label: 'INTERNAL_SERVICE (내부망서비스)' },
];

export default function DatasourcesPage() {
  const [datasources, setDatasources] = useState<Datasource[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingDatasource, setEditingDatasource] = useState<Datasource | null>(null);
  const [selectedDatasource, setSelectedDatasource] = useState<Datasource | null>(null);

  useEffect(() => {
    fetchDatasources();
  }, []);

  const fetchDatasources = async () => {
    try {
      const data = await datasourceApi.getAll();
      setDatasources(data);
    } catch (error) {
      console.error('Datasource 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (datasourceId: string) => {
    if (!confirm('정말 삭제하시겠습니까?')) return;
    try {
      await datasourceApi.delete(datasourceId);
      fetchDatasources();
    } catch (error) {
      console.error('Datasource 삭제 실패:', error);
      alert('삭제에 실패했습니다. Agent에서 사용 중인지 확인하세요.');
    }
  };

  const handleTestConnection = async (datasourceId: string) => {
    try {
      const result = await datasourceApi.testConnection(datasourceId);
      if (result.success) {
        alert(`연결 성공! (${result.responseTimeMs}ms)`);
      } else {
        alert(`연결 실패: ${result.message}`);
      }
    } catch (error) {
      console.error('연결 테스트 실패:', error);
      alert('연결 테스트 실패');
    }
  };

  const handleEdit = (datasource: Datasource) => {
    setEditingDatasource(datasource);
    setShowForm(true);
  };

  const handleCloseForm = () => {
    setShowForm(false);
    setEditingDatasource(null);
  };

  if (loading) {
    return <div className="app-loading">로딩중...</div>;
  }

  return (
    <div>
      <div className="app-page-header">
        <h1 className="app-page-header__title">DB 관리</h1>
        <button
          type="button"
          className="krds-btn small primary"
          onClick={() => (showForm ? handleCloseForm() : setShowForm(true))}
        >
          {showForm ? '취소' : 'DB 등록'}
        </button>
      </div>

      {showForm && (
        <DatasourceForm
          editMode={!!editingDatasource}
          initialData={editingDatasource}
          onSuccess={() => {
            handleCloseForm();
            fetchDatasources();
          }}
          onCancel={handleCloseForm}
        />
      )}

      <div className="app-card">
        <table className={`app-table ${styles.datasourceTable}`}>
          <thead>
            <tr>
              <th>ID</th>
              <th>이름</th>
              <th>DB 타입</th>
              <th>Zone</th>
              <th>호스트</th>
              <th>포트</th>
              <th>데이터베이스</th>
              <th>상태</th>
              <th>작업</th>
            </tr>
          </thead>
          <tbody>
            {datasources.length === 0 ? (
              <tr>
                <td colSpan={9} className="app-empty">
                  등록된 Datasource가 없습니다
                </td>
              </tr>
            ) : (
              datasources.map((ds) => (
                <tr key={ds.datasourceId}>
                  <td>{ds.datasourceId}</td>
                  <td>{ds.datasourceName}</td>
                  <td>
                    <span className={styles.dbTypeBadge}>{DB_TYPE_LABELS[ds.dbType]}</span>
                  </td>
                  <td>
                    <span className={`zone-${(ds.zone || 'none').toLowerCase().replace('_', '-')}`}>
                      {ds.zone || '-'}
                    </span>
                  </td>
                  <td>{ds.host}</td>
                  <td>{ds.port}</td>
                  <td>{ds.databaseName}</td>
                  <td>
                    <span className={`krds-badge ${ds.isActive ? 'bg-light-success' : 'bg-light-gray'}`}>
                      {ds.isActive ? '활성' : '비활성'}
                    </span>
                  </td>
                  <td>
                    <div className={styles.actionCell}>
                      <button
                        type="button"
                        className="krds-btn xsmall secondary"
                        onClick={() => setSelectedDatasource(ds)}
                      >
                        테이블관리
                      </button>
                      <button
                        type="button"
                        className="krds-btn xsmall primary"
                        onClick={() => handleTestConnection(ds.datasourceId)}
                      >
                        연결테스트
                      </button>
                      <button
                        type="button"
                        className="krds-btn xsmall secondary"
                        onClick={() => handleEdit(ds)}
                      >
                        수정
                      </button>
                      <button
                        type="button"
                        className="krds-btn xsmall app-btn-danger"
                        onClick={() => handleDelete(ds.datasourceId)}
                      >
                        삭제
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {selectedDatasource && (
        <TableManagementModal
          datasource={selectedDatasource}
          onClose={() => setSelectedDatasource(null)}
        />
      )}
    </div>
  );
}

interface DatasourceFormProps {
  editMode?: boolean;
  initialData?: Datasource | null;
  onSuccess: () => void;
  onCancel: () => void;
}

function DatasourceForm({ editMode = false, initialData, onSuccess, onCancel }: DatasourceFormProps) {
  const [formData, setFormData] = useState<DatasourceCreateRequest & { password: string }>({
    datasourceId: initialData?.datasourceId || '',
    datasourceName: initialData?.datasourceName || '',
    dbType: initialData?.dbType || 'POSTGRESQL',
    host: initialData?.host || '',
    port: initialData?.port || 5432,
    databaseName: initialData?.databaseName || '',
    username: editMode ? '' : '',
    password: '',
    description: initialData?.description || '',
    zone: initialData?.zone || '',
  });

  const [originalData] = useState({
    dbType: initialData?.dbType || 'POSTGRESQL',
    host: initialData?.host || '',
    port: initialData?.port || 5432,
    databaseName: initialData?.databaseName || '',
    zone: initialData?.zone || '',
  });

  const [submitting, setSubmitting] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);

  const isConnectionFieldChanged = () => {
    if (!editMode) return true;
    return (
      formData.dbType !== originalData.dbType ||
      formData.host !== originalData.host ||
      formData.port !== originalData.port ||
      formData.databaseName !== originalData.databaseName ||
      formData.username !== '' ||
      formData.password !== '' ||
      formData.zone !== originalData.zone
    );
  };

  const needsConnectionTest = () => {
    if (!editMode) return !testResult?.success;
    if (isConnectionFieldChanged()) return !testResult?.success;
    return false;
  };

  const handleDbTypeChange = (dbType: DbType) => {
    setFormData({ ...formData, dbType, port: DEFAULT_PORTS[dbType] });
    setTestResult(null);
  };

  const handleConnectionFieldChange = (field: string, value: string | number) => {
    setFormData({ ...formData, [field]: value });
    setTestResult(null);
  };

  const handleTestConnection = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const testReq: ConnectionTestRequest = {
        dbType: formData.dbType,
        host: formData.host,
        port: formData.port,
        databaseName: formData.databaseName,
        username: formData.username,
        password: formData.password,
        zone: formData.zone,
      };
      const result = await datasourceApi.testConnectionBeforeSave(testReq);
      setTestResult({
        success: result.success,
        message: result.success ? `연결 성공! (${result.responseTimeMs}ms)` : result.message,
      });
    } catch (error) {
      console.error('연결 테스트 실패:', error);
      setTestResult({ success: false, message: '연결 테스트 요청 실패' });
    } finally {
      setTesting(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (needsConnectionTest()) {
      alert('연결 테스트를 먼저 수행하고 성공해야 합니다.');
      return;
    }
    setSubmitting(true);
    try {
      if (editMode && initialData) {
        const updateData: DatasourceUpdateRequest = {
          datasourceName: formData.datasourceName,
          dbType: formData.dbType,
          host: formData.host,
          port: formData.port,
          databaseName: formData.databaseName,
          description: formData.description,
          zone: formData.zone,
        };
        if (formData.username) updateData.username = formData.username;
        if (formData.password) updateData.password = formData.password;
        await datasourceApi.update(initialData.datasourceId, updateData);
      } else {
        await datasourceApi.create(formData);
      }
      onSuccess();
    } catch (error) {
      console.error('Datasource 저장 실패:', error);
      alert('저장에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const canSubmit = !needsConnectionTest();

  return (
    <div className="app-card">
      <h3 className={styles.sectionTitle}>{editMode ? 'DB 수정' : 'DB 등록'}</h3>
      <form onSubmit={handleSubmit}>
        <div className="app-form-grid">
          <div className="app-form-field">
            <label className="app-form-label">Datasource ID</label>
            <input
              type="text"
              className="krds-input"
              value={formData.datasourceId}
              onChange={(e) => setFormData({ ...formData, datasourceId: e.target.value })}
              placeholder="external-postgres-1"
              required
              disabled={editMode}
            />
          </div>
          <div className="app-form-field">
            <label className="app-form-label">이름</label>
            <input
              type="text"
              className="krds-input"
              value={formData.datasourceName}
              onChange={(e) => setFormData({ ...formData, datasourceName: e.target.value })}
              placeholder="대전시 외부 DB"
              required
            />
          </div>
          <div className="app-form-field">
            <label className="app-form-label">DB 타입</label>
            <select
              className="krds-form-select"
              value={formData.dbType}
              onChange={(e) => handleDbTypeChange(e.target.value as DbType)}
            >
              {Object.entries(DB_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div className="app-form-field">
            <label className="app-form-label">호스트</label>
            <input
              type="text"
              className="krds-input"
              value={formData.host}
              onChange={(e) => handleConnectionFieldChange('host', e.target.value)}
              placeholder="192.168.1.100"
              required
            />
          </div>
          <div className="app-form-field">
            <label className="app-form-label">포트</label>
            <input
              type="number"
              className="krds-input"
              value={formData.port}
              onChange={(e) => handleConnectionFieldChange('port', parseInt(e.target.value))}
              required
            />
          </div>
          <div className="app-form-field">
            <label className="app-form-label">데이터베이스명</label>
            <input
              type="text"
              className="krds-input"
              value={formData.databaseName}
              onChange={(e) => handleConnectionFieldChange('databaseName', e.target.value)}
              placeholder="gims"
              required
            />
          </div>
          <div className="app-form-field">
            <label className="app-form-label">
              사용자명{editMode && <span className="app-form-label__hint">(변경시에만 입력)</span>}
            </label>
            <input
              type="text"
              className="krds-input"
              value={formData.username}
              onChange={(e) => handleConnectionFieldChange('username', e.target.value)}
              placeholder={editMode ? '변경하려면 입력하세요' : 'postgres'}
              required={!editMode}
            />
          </div>
          <div className="app-form-field">
            <label className="app-form-label">
              비밀번호{editMode && <span className="app-form-label__hint">(변경시에만 입력)</span>}
            </label>
            <input
              type="password"
              className="krds-input"
              value={formData.password}
              onChange={(e) => handleConnectionFieldChange('password', e.target.value)}
              required={!editMode}
              placeholder={editMode ? '변경하려면 입력하세요' : ''}
            />
          </div>
          <div className="app-form-field">
            <label className="app-form-label">설명 (선택)</label>
            <input
              type="text"
              className="krds-input"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              placeholder="대전시에서 제공하는 외부 관측 데이터 DB"
            />
          </div>
          <div className="app-form-field">
            <label className="app-form-label">네트워크 Zone</label>
            <select
              className="krds-form-select"
              value={formData.zone}
              onChange={(e) => handleConnectionFieldChange('zone', e.target.value)}
            >
              {ZONE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
            <small className="app-form-help">연결 테스트 시 해당 Zone의 Master Agent가 대신 테스트합니다</small>
          </div>
        </div>

        {needsConnectionTest() && (
          <div className="app-alert app-alert--warning" style={{ marginTop: '1.6rem' }}>
            {editMode && isConnectionFieldChanged()
              ? '연결 정보가 변경되었습니다. 연결 테스트를 다시 수행해주세요.'
              : '저장하기 전에 연결 테스트를 수행해주세요.'}
          </div>
        )}

        {testResult && (
          <div
            className={`app-alert ${testResult.success ? 'app-alert--success' : 'app-alert--danger'}`}
            style={{ marginTop: '1.6rem' }}
          >
            {testResult.message}
          </div>
        )}

        <div className="app-btn-row">
          <button
            type="button"
            className="krds-btn small secondary"
            onClick={handleTestConnection}
            disabled={
              testing ||
              !formData.host ||
              !formData.databaseName ||
              (!editMode && (!formData.username || !formData.password))
            }
          >
            {testing ? '테스트중...' : '연결 테스트'}
          </button>
          <button
            type="submit"
            className="krds-btn small primary"
            disabled={submitting || !canSubmit}
            title={!canSubmit ? '연결 테스트를 먼저 성공해야 합니다' : ''}
          >
            {submitting ? '저장중...' : editMode ? '수정' : '등록'}
          </button>
          <button type="button" className="krds-btn small tertiary" onClick={onCancel}>
            취소
          </button>
        </div>
      </form>
    </div>
  );
}

function TableManagementModal({
  datasource,
  onClose,
}: {
  datasource: Datasource;
  onClose: () => void;
}) {
  const [registeredTables, setRegisteredTables] = useState<DatasourceTable[]>([]);
  const [allTables, setAllTables] = useState<TableSearchResult[]>([]);
  const [loadingTables, setLoadingTables] = useState(false);
  const [selectedTable, setSelectedTable] = useState<TableSearchResult | null>(null);
  const [allColumns, setAllColumns] = useState<ColumnSearchResult[]>([]);
  const [selectedColumns, setSelectedColumns] = useState<ColumnSearchResult[]>([]);
  const [loadingColumns, setLoadingColumns] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [loading, setLoading] = useState(true);

  const fetchRegisteredTables = async () => {
    try {
      const tables = await datasourceApi.getRegisteredTables(datasource.datasourceId);
      setRegisteredTables(tables);
    } catch (error) {
      console.error('등록된 테이블 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRegisteredTables();

    const loadAllTables = async () => {
      setLoadingTables(true);
      try {
        const results = await datasourceApi.searchTables(datasource.datasourceId);
        setAllTables(results);
      } catch (error) {
        console.error('테이블 자동 검색 실패:', error);
      } finally {
        setLoadingTables(false);
      }
    };
    loadAllTables();
  }, [datasource.datasourceId]);

  const searchTablesFromDb = async (query: string) => {
    setLoadingTables(true);
    try {
      const results = await datasourceApi.searchTables(datasource.datasourceId, query);
      setAllTables(results);
    } catch (error) {
      console.error('테이블 검색 실패:', error);
    } finally {
      setLoadingTables(false);
    }
  };

  const [tableSearchQuery, setTableSearchQuery] = useState('');

  const handleSearchTables = () => {
    searchTablesFromDb(tableSearchQuery);
  };

  const handleSelectTable = async (tableOption: Record<string, unknown> | null) => {
    if (!tableOption) {
      setSelectedTable(null);
      setAllColumns([]);
      setSelectedColumns([]);
      return;
    }

    const table = tableOption as unknown as TableSearchResult;
    setSelectedTable(table);
    setSelectedColumns([]);
    setLoadingColumns(true);

    try {
      const columns = await datasourceApi.searchColumns(datasource.datasourceId, table.tableName);
      setAllColumns(columns);
    } catch (error) {
      console.error('컬럼 조회 실패:', error);
      alert('컬럼 조회에 실패했습니다. 콘솔을 확인하세요.');
    } finally {
      setLoadingColumns(false);
    }
  };

  const handleToggleColumn = (column: ColumnSearchResult) => {
    setSelectedColumns((prev) => {
      const exists = prev.find((c) => c.columnName === column.columnName);
      if (exists) {
        return prev.filter((c) => c.columnName !== column.columnName);
      }
      return [...prev, column];
    });
  };

  const handleSelectAllColumns = () => {
    if (selectedColumns.length === allColumns.length) {
      setSelectedColumns([]);
    } else {
      setSelectedColumns([...allColumns]);
    }
  };

  const handleRegisterTable = async () => {
    if (!selectedTable || selectedColumns.length === 0) {
      alert('테이블과 컬럼을 선택하세요.');
      return;
    }

    if (registeredTables.some((t) => t.tableName === selectedTable.tableName)) {
      alert('이미 등록된 테이블입니다.');
      return;
    }

    setRegistering(true);
    try {
      const columns: ColumnCreateRequest[] = selectedColumns.map((col) => ({
        columnName: col.columnName,
        columnAlias: col.remarks || undefined,
        dataType: col.dataType,
        isPrimaryKey: col.isPrimaryKey,
        isNullable: col.isNullable,
      }));

      await datasourceApi.registerTable(datasource.datasourceId, {
        tableName: selectedTable.tableName,
        tableAlias: selectedTable.remarks || undefined,
        description: selectedTable.remarks || undefined,
        columns,
      });

      alert('테이블이 등록되었습니다.');
      setSelectedTable(null);
      setAllColumns([]);
      setSelectedColumns([]);
      fetchRegisteredTables();
      searchTablesFromDb(tableSearchQuery);
    } catch (error) {
      console.error('테이블 등록 실패:', error);
      alert('테이블 등록에 실패했습니다.');
    } finally {
      setRegistering(false);
    }
  };

  const handleDeleteTable = async (tableId: number) => {
    if (!confirm('테이블을 삭제하시겠습니까?')) return;
    try {
      await datasourceApi.deleteTable(datasource.datasourceId, tableId);
      fetchRegisteredTables();
      searchTablesFromDb(tableSearchQuery);
    } catch (error) {
      console.error('테이블 삭제 실패:', error);
      alert('테이블 삭제에 실패했습니다.');
    }
  };

  const [refreshingTableId, setRefreshingTableId] = useState<number | null>(null);
  const [expandedTableId, setExpandedTableId] = useState<number | null>(null);
  const [refreshTarget, setRefreshTarget] = useState<DatasourceTable | null>(null);
  const [refreshDbColumns, setRefreshDbColumns] = useState<ColumnSearchResult[]>([]);
  const [refreshSelectedColumns, setRefreshSelectedColumns] = useState<ColumnSearchResult[]>([]);
  const [refreshSaving, setRefreshSaving] = useState(false);

  const handleRefreshColumns = async (table: DatasourceTable) => {
    setRefreshingTableId(table.id);
    try {
      const dbColumns = await datasourceApi.searchColumns(datasource.datasourceId, table.tableName);
      setRefreshDbColumns(dbColumns);
      const existingNames = new Set(table.columns.map((c) => c.columnName));
      setRefreshSelectedColumns(dbColumns.filter((c) => existingNames.has(c.columnName)));
      setRefreshTarget(table);
    } catch (error) {
      console.error('컬럼 재수집 실패:', error);
      alert('컬럼 재수집에 실패했습니다.');
    } finally {
      setRefreshingTableId(null);
    }
  };

  const handleRefreshConfirm = async () => {
    if (!refreshTarget || refreshSelectedColumns.length === 0) return;
    setRefreshSaving(true);
    try {
      const dbTables = await datasourceApi.searchTables(datasource.datasourceId, refreshTarget.tableName);
      const dbTable = dbTables.find((t) => t.tableName === refreshTarget.tableName);

      await datasourceApi.refreshTableColumns(datasource.datasourceId, refreshTarget.id, {
        tableName: refreshTarget.tableName,
        tableAlias: dbTable?.remarks || refreshTarget.tableAlias || undefined,
        description: dbTable?.remarks || refreshTarget.description || undefined,
        columns: refreshSelectedColumns.map((c) => ({
          columnName: c.columnName,
          columnAlias: c.remarks || undefined,
          dataType: c.dataType,
          isPrimaryKey: c.isPrimaryKey,
          isNullable: c.isNullable,
        })),
      });
      fetchRegisteredTables();
      setRefreshTarget(null);
      alert(`${refreshTarget.tableName} 컬럼 갱신 완료 (${refreshSelectedColumns.length}개)`);
    } catch (error) {
      console.error('컬럼 갱신 실패:', error);
      alert('컬럼 갱신에 실패했습니다.');
    } finally {
      setRefreshSaving(false);
    }
  };

  const handleRefreshToggleColumn = (col: ColumnSearchResult) => {
    setRefreshSelectedColumns((prev) =>
      prev.some((c) => c.columnName === col.columnName)
        ? prev.filter((c) => c.columnName !== col.columnName)
        : [...prev, col]
    );
  };

  const isTableRegistered = (tableName: string) => {
    return registeredTables.some((t) => t.tableName === tableName);
  };

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modalDialog} onClick={(e) => e.stopPropagation()}>
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitle}>테이블 관리 - {datasource.datasourceName}</h2>
          <button type="button" className="krds-btn xsmall secondary" onClick={onClose}>
            닫기
          </button>
        </div>

        {/* 테이블 추가 섹션 */}
        <div className="app-card">
          <h4 className={styles.sectionTitle}>테이블 추가</h4>

          {/* 테이블 검색 */}
          <div className="app-form-field" style={{ marginBottom: '1.6rem' }}>
            <label className="app-form-label">테이블 검색</label>
            <div className={styles.searchRow}>
              <input
                type="text"
                className={`krds-input ${styles.searchRow__input}`}
                placeholder="테이블명 입력 (비우면 전체 조회)"
                value={tableSearchQuery}
                onChange={(e) => setTableSearchQuery(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearchTables()}
              />
              <button
                type="button"
                className="krds-btn small primary"
                onClick={handleSearchTables}
                disabled={loadingTables}
              >
                {loadingTables ? '검색중...' : '검색'}
              </button>
            </div>
          </div>

          {loadingTables && allTables.length === 0 && (
            <div className="app-empty">테이블 목록 로딩중...</div>
          )}

          {allTables.length > 0 && (
            <div className="app-form-field" style={{ marginBottom: '1.6rem' }}>
              <label className="app-form-label">테이블 선택 ({allTables.length}개)</label>
              <select
                className="krds-form-select"
                value={selectedTable?.tableName || ''}
                onChange={(e) => {
                  const table = allTables.find((t) => t.tableName === e.target.value);
                  handleSelectTable(table ? { ...table, label: table.tableName, value: table.tableName } : null);
                }}
              >
                <option value="">테이블을 선택하세요</option>
                {[...allTables]
                  .sort((a, b) => {
                    const aReg = isTableRegistered(a.tableName) ? 1 : 0;
                    const bReg = isTableRegistered(b.tableName) ? 1 : 0;
                    return aReg - bReg || a.tableName.localeCompare(b.tableName);
                  })
                  .map((table) => (
                    <option
                      key={table.tableName}
                      value={table.tableName}
                      disabled={isTableRegistered(table.tableName)}
                    >
                      {table.tableName}
                      {table.remarks ? ` (${table.remarks})` : ` (${table.tableType})`}
                      {isTableRegistered(table.tableName) ? ' - 등록됨' : ''}
                    </option>
                  ))}
              </select>
            </div>
          )}

          {/* 컬럼 선택 */}
          {selectedTable && (
            <div>
              <div className={styles.columnSelectStatus}>
                <span className={styles.columnSelectStatus__count}>
                  컬럼 선택 ({selectedColumns.length}/{allColumns.length})
                </span>
                <button
                  type="button"
                  className="krds-btn xsmall secondary"
                  onClick={handleSelectAllColumns}
                  disabled={loadingColumns}
                >
                  {selectedColumns.length === allColumns.length ? '전체해제' : '전체선택'}
                </button>
              </div>

              {loadingColumns ? (
                <div className="app-empty">컬럼 로딩중...</div>
              ) : (
                <div className={styles.columnList}>
                  {allColumns.map((col) => {
                    const isSelected = selectedColumns.some((c) => c.columnName === col.columnName);
                    return (
                      <label
                        key={col.columnName}
                        className={`${styles.columnList__row} ${isSelected ? styles['columnList__row--selected'] : ''}`}
                      >
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => handleToggleColumn(col)}
                          className={styles.columnList__checkbox}
                        />
                        <div className={styles.columnList__main}>
                          <span className={`${styles.columnList__name} ${col.isPrimaryKey ? styles['columnList__name--pk'] : ''}`}>
                            {col.columnName}
                            {col.isPrimaryKey && <span className={styles.columnList__pkLabel}>(PK)</span>}
                          </span>
                          <span className={styles.columnList__type}>
                            {col.dataType} {col.isNullable ? '' : '(NOT NULL)'}
                          </span>
                        </div>
                      </label>
                    );
                  })}
                </div>
              )}

              <div className="app-btn-row app-btn-row--end">
                <button
                  type="button"
                  className="krds-btn small primary"
                  onClick={handleRegisterTable}
                  disabled={
                    registering ||
                    selectedColumns.length === 0 ||
                    isTableRegistered(selectedTable.tableName)
                  }
                >
                  {registering
                    ? '등록중...'
                    : isTableRegistered(selectedTable.tableName)
                    ? '이미 등록된 테이블'
                    : `${selectedTable.tableName} 등록 (${selectedColumns.length}개 컬럼)`}
                </button>
              </div>
            </div>
          )}
        </div>

        {/* 등록된 테이블 목록 */}
        <div className="app-card">
          <h4 className={styles.sectionTitle}>등록된 테이블 ({registeredTables.length})</h4>
          {loading ? (
            <div className="app-empty">로딩중...</div>
          ) : registeredTables.length === 0 ? (
            <div className="app-empty">등록된 테이블이 없습니다. 위에서 테이블을 검색하여 추가하세요.</div>
          ) : (
            <div className={styles.tableListScroll}>
              {registeredTables.map((table) => (
                <div key={table.id} className={styles.registeredTable}>
                  <div className={styles.registeredTable__header}>
                    <span>
                      <span className={styles.registeredTable__name}>{table.tableName}</span>
                      {(table.description || table.tableAlias) && (
                        <span className={styles.registeredTable__alias}>
                          ({table.description || table.tableAlias})
                        </span>
                      )}
                    </span>
                    <div className={styles.registeredTable__actions}>
                      <button
                        type="button"
                        className="krds-btn xsmall secondary"
                        onClick={() => handleRefreshColumns(table)}
                        disabled={refreshingTableId === table.id}
                      >
                        {refreshingTableId === table.id ? '수집중...' : '컬럼 재수집'}
                      </button>
                      <button
                        type="button"
                        className="krds-btn xsmall app-btn-danger"
                        onClick={() => handleDeleteTable(table.id)}
                      >
                        삭제
                      </button>
                    </div>
                  </div>
                  <button
                    type="button"
                    className={styles.registeredTable__expand}
                    onClick={() => setExpandedTableId(expandedTableId === table.id ? null : table.id)}
                  >
                    {expandedTableId === table.id ? '▼' : '▶'} 컬럼 ({table.columns.length}개)
                  </button>
                  {expandedTableId === table.id && (
                    <div className={styles.registeredTable__columnChips}>
                      {table.columns.map((c) => (
                        <span
                          key={c.id}
                          className={`${styles.columnChip} ${c.isPrimaryKey ? styles['columnChip--pk'] : ''}`}
                        >
                          {c.columnName}
                          {c.isPrimaryKey && <span className={styles.columnChip__pkLabel}>(PK)</span>}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 컬럼 재수집 모달 */}
        {refreshTarget && (
          <div className={`${styles.modalOverlay} ${styles.modalZIndex2}`}>
            <div className={`${styles.modalDialog} ${styles['modalDialog--small']}`}>
              <div className={styles.modalHeader}>
                <h4 className={styles.modalTitle}>컬럼 재수집 — {refreshTarget.tableName}</h4>
                <button type="button" className={styles.modalCloseBtn} onClick={() => setRefreshTarget(null)}>
                  ✕
                </button>
              </div>
              <div className={styles.columnSelectStatus}>
                <span className={styles.columnSelectStatus__count}>
                  선택: {refreshSelectedColumns.length}/{refreshDbColumns.length}
                </span>
                <button
                  type="button"
                  className="krds-btn xsmall secondary"
                  onClick={() =>
                    setRefreshSelectedColumns(
                      refreshSelectedColumns.length === refreshDbColumns.length ? [] : [...refreshDbColumns]
                    )
                  }
                >
                  {refreshSelectedColumns.length === refreshDbColumns.length ? '전체해제' : '전체선택'}
                </button>
              </div>
              <div className={`${styles.columnList} ${styles['columnList--scrollable']}`}>
                {refreshDbColumns.map((col) => {
                  const isExisting = refreshTarget.columns.some((c) => c.columnName === col.columnName);
                  const isSelected = refreshSelectedColumns.some((c) => c.columnName === col.columnName);
                  return (
                    <label
                      key={col.columnName}
                      className={`${styles.columnList__row} ${isSelected ? styles['columnList__row--selected'] : ''}`}
                    >
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => handleRefreshToggleColumn(col)}
                        className={styles.columnList__checkbox}
                      />
                      <div className={styles.columnList__main}>
                        <span className={`${styles.columnList__name} ${col.isPrimaryKey ? styles['columnList__name--pk'] : ''}`}>
                          {col.columnName}
                          {col.isPrimaryKey && <span className={styles.columnList__pkLabel}>(PK)</span>}
                        </span>
                        <span className={styles.columnList__type}>
                          {col.dataType} {col.isNullable ? '' : '(NOT NULL)'}
                        </span>
                        {!isExisting && <span className={styles.columnList__newLabel}>NEW</span>}
                      </div>
                    </label>
                  );
                })}
              </div>
              <div className="app-btn-row app-btn-row--end">
                <button type="button" className="krds-btn small tertiary" onClick={() => setRefreshTarget(null)}>
                  취소
                </button>
                <button
                  type="button"
                  className="krds-btn small primary"
                  onClick={handleRefreshConfirm}
                  disabled={refreshSaving || refreshSelectedColumns.length === 0}
                >
                  {refreshSaving ? '저장중...' : `갱신 (${refreshSelectedColumns.length}개 컬럼)`}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
