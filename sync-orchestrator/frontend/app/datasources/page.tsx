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
    return <div className="loading">로딩중...</div>;
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">DB 관리</h1>
        <button
          className="btn btn-primary"
          onClick={() => {
            if (showForm) {
              handleCloseForm();
            } else {
              setShowForm(true);
            }
          }}
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

      <div className="card">
        <div className="table-container">
          <table>
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
                  <td colSpan={9} className="empty-state">
                    등록된 Datasource가 없습니다
                  </td>
                </tr>
              ) : (
                datasources.map((ds) => (
                  <tr key={ds.datasourceId}>
                    <td>{ds.datasourceId}</td>
                    <td>{ds.datasourceName}</td>
                    <td>
                      <span className={`db-type db-type-${ds.dbType.toLowerCase()}`}>
                        {DB_TYPE_LABELS[ds.dbType]}
                      </span>
                    </td>
                    <td>
                      <span className={`zone-badge zone-${(ds.zone || 'none').toLowerCase().replace('_', '-')}`}>
                        {ds.zone || '-'}
                      </span>
                    </td>
                    <td>{ds.host}</td>
                    <td>{ds.port}</td>
                    <td>{ds.databaseName}</td>
                    <td>
                      <span className={`status-badge ${ds.isActive ? 'status-success' : 'status-offline'}`}>
                        {ds.isActive ? '활성' : '비활성'}
                      </span>
                    </td>
                    <td>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => setSelectedDatasource(ds)}
                        style={{ marginRight: '0.5rem' }}
                      >
                        테이블관리
                      </button>
                      <button
                        className="btn btn-primary btn-sm"
                        onClick={() => handleTestConnection(ds.datasourceId)}
                        style={{ marginRight: '0.5rem' }}
                      >
                        연결테스트
                      </button>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleEdit(ds)}
                        style={{ marginRight: '0.5rem' }}
                      >
                        수정
                      </button>
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => handleDelete(ds.datasourceId)}
                      >
                        삭제
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* 테이블 관리 모달 */}
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
    username: editMode ? '' : '', // 수정 시에는 보안상 비워둠
    password: '', // 비밀번호는 보안상 비워둠
    description: initialData?.description || '',
    zone: initialData?.zone || '',
  });

  // 원본 데이터 (수정 모드에서 변경 감지용) - username/password는 항상 비워두므로 제외
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

  // 연결 관련 필드가 변경되었는지 확인
  const isConnectionFieldChanged = () => {
    if (!editMode) return true; // 신규 등록은 항상 테스트 필요

    return (
      formData.dbType !== originalData.dbType ||
      formData.host !== originalData.host ||
      formData.port !== originalData.port ||
      formData.databaseName !== originalData.databaseName ||
      formData.username !== '' || // username이 입력되면 변경된 것으로 간주
      formData.password !== '' || // password가 입력되면 변경된 것으로 간주
      formData.zone !== originalData.zone
    );
  };

  // 연결 테스트가 필요한지 확인
  const needsConnectionTest = () => {
    if (!editMode) {
      // 신규 등록: 테스트 성공 필수
      return !testResult?.success;
    }
    // 수정 모드: 연결 관련 필드가 변경되었으면 재테스트 필요
    if (isConnectionFieldChanged()) {
      return !testResult?.success;
    }
    return false;
  };

  const handleDbTypeChange = (dbType: DbType) => {
    setFormData({
      ...formData,
      dbType,
      port: DEFAULT_PORTS[dbType],
    });
    setTestResult(null); // 테스트 결과 리셋
  };

  const handleConnectionFieldChange = (field: string, value: string | number) => {
    setFormData({ ...formData, [field]: value });
    setTestResult(null); // 연결 관련 필드 변경 시 테스트 결과 리셋
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

    // 연결 테스트 필수 체크
    if (needsConnectionTest()) {
      alert('연결 테스트를 먼저 수행하고 성공해야 합니다.');
      return;
    }

    setSubmitting(true);
    try {
      if (editMode && initialData) {
        // 수정 모드
        const updateData: DatasourceUpdateRequest = {
          datasourceName: formData.datasourceName,
          dbType: formData.dbType,
          host: formData.host,
          port: formData.port,
          databaseName: formData.databaseName,
          description: formData.description,
          zone: formData.zone,
        };
        // 사용자명이 입력된 경우에만 포함
        if (formData.username) {
          updateData.username = formData.username;
        }
        // 비밀번호가 입력된 경우에만 포함
        if (formData.password) {
          updateData.password = formData.password;
        }
        await datasourceApi.update(initialData.datasourceId, updateData);
      } else {
        // 신규 등록
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
    <div className="card">
      <h3 className="card-title" style={{ marginBottom: '1rem' }}>
        {editMode ? 'DB 수정' : 'DB 등록'}
      </h3>
      <form onSubmit={handleSubmit}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">Datasource ID</label>
            <input
              type="text"
              className="form-input"
              value={formData.datasourceId}
              onChange={(e) => setFormData({ ...formData, datasourceId: e.target.value })}
              placeholder="external-postgres-1"
              required
              disabled={editMode} // 수정 모드에서는 ID 변경 불가
            />
          </div>
          <div className="form-group">
            <label className="form-label">이름</label>
            <input
              type="text"
              className="form-input"
              value={formData.datasourceName}
              onChange={(e) => setFormData({ ...formData, datasourceName: e.target.value })}
              placeholder="대전시 외부 DB"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">DB 타입</label>
            <select
              className="form-select"
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
          <div className="form-group">
            <label className="form-label">호스트</label>
            <input
              type="text"
              className="form-input"
              value={formData.host}
              onChange={(e) => handleConnectionFieldChange('host', e.target.value)}
              placeholder="192.168.1.100"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">포트</label>
            <input
              type="number"
              className="form-input"
              value={formData.port}
              onChange={(e) => handleConnectionFieldChange('port', parseInt(e.target.value))}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">데이터베이스명</label>
            <input
              type="text"
              className="form-input"
              value={formData.databaseName}
              onChange={(e) => handleConnectionFieldChange('databaseName', e.target.value)}
              placeholder="gims"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">
              사용자명 {editMode && <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>(변경시에만 입력)</span>}
            </label>
            <input
              type="text"
              className="form-input"
              value={formData.username}
              onChange={(e) => handleConnectionFieldChange('username', e.target.value)}
              placeholder={editMode ? '변경하려면 입력하세요' : 'postgres'}
              required={!editMode}
            />
          </div>
          <div className="form-group">
            <label className="form-label">
              비밀번호 {editMode && <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>(변경시에만 입력)</span>}
            </label>
            <input
              type="password"
              className="form-input"
              value={formData.password}
              onChange={(e) => handleConnectionFieldChange('password', e.target.value)}
              required={!editMode}
              placeholder={editMode ? '변경하려면 입력하세요' : ''}
            />
          </div>
          <div className="form-group">
            <label className="form-label">설명 (선택)</label>
            <input
              type="text"
              className="form-input"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              placeholder="대전시에서 제공하는 외부 관측 데이터 DB"
            />
          </div>
          <div className="form-group">
            <label className="form-label">네트워크 Zone</label>
            <select
              className="form-select"
              value={formData.zone}
              onChange={(e) => handleConnectionFieldChange('zone', e.target.value)}
            >
              {ZONE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
            <small style={{ color: 'var(--text-muted, #666)', fontSize: '0.75rem' }}>
              연결 테스트 시 해당 Zone의 Master Agent가 대신 테스트합니다
            </small>
          </div>
        </div>

        {/* 연결 테스트 상태 메시지 */}
        {needsConnectionTest() && (
          <div
            style={{
              marginTop: '1rem',
              padding: '0.75rem',
              borderRadius: '0.5rem',
              background: 'var(--warning-bg, #fff3cd)',
              color: 'var(--warning-color, #856404)',
            }}
          >
            {editMode && isConnectionFieldChanged()
              ? '연결 정보가 변경되었습니다. 연결 테스트를 다시 수행해주세요.'
              : '저장하기 전에 연결 테스트를 수행해주세요.'}
          </div>
        )}

        {/* 연결 테스트 결과 */}
        {testResult && (
          <div
            style={{
              marginTop: '1rem',
              padding: '0.75rem',
              borderRadius: '0.5rem',
              background: testResult.success ? 'var(--success-bg, #d4edda)' : 'var(--danger-bg, #f8d7da)',
              color: testResult.success ? 'var(--success-color, #155724)' : 'var(--danger-color, #721c24)',
            }}
          >
            {testResult.message}
          </div>
        )}

        <div style={{ marginTop: '1rem', display: 'flex', gap: '0.5rem' }}>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={handleTestConnection}
            disabled={testing || !formData.host || !formData.databaseName || (!editMode && (!formData.username || !formData.password))}
          >
            {testing ? '테스트중...' : '연결 테스트'}
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={submitting || !canSubmit}
            title={!canSubmit ? '연결 테스트를 먼저 성공해야 합니다' : ''}
          >
            {submitting ? '저장중...' : editMode ? '수정' : '등록'}
          </button>
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
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

  // 모달 최초 오픈 시 등록된 테이블 + DB 테이블 목록 자동 로드
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

  // 실제 DB에서 테이블 검색
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

  // 테이블 검색 (버튼 클릭 시)
  const handleSearchTables = () => {
    searchTablesFromDb(tableSearchQuery);
  };

  // 테이블 선택 시 컬럼 자동 로드
  const handleSelectTable = async (tableOption: Record<string, unknown> | null) => {
    console.log('handleSelectTable called with:', tableOption);
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
      console.log('Calling datasourceApi.searchColumns for:', datasource.datasourceId, table.tableName);
      const columns = await datasourceApi.searchColumns(datasource.datasourceId, table.tableName);
      console.log('searchColumns results:', columns);
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

    // 이미 등록된 테이블인지 확인
    if (registeredTables.some(t => t.tableName === selectedTable.tableName)) {
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
      // 기존 등록된 컬럼은 pre-check
      const existingNames = new Set(table.columns.map(c => c.columnName));
      setRefreshSelectedColumns(dbColumns.filter(c => existingNames.has(c.columnName)));
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
      await datasourceApi.refreshTableColumns(datasource.datasourceId, refreshTarget.id, {
        tableName: refreshTarget.tableName,
        tableAlias: refreshTarget.tableAlias || undefined,
        description: refreshTarget.description || undefined,
        columns: refreshSelectedColumns.map(c => ({
          columnName: c.columnName,
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
    setRefreshSelectedColumns(prev =>
      prev.some(c => c.columnName === col.columnName)
        ? prev.filter(c => c.columnName !== col.columnName)
        : [...prev, col]
    );
  };

  // 이미 등록된 테이블인지 확인
  const isTableRegistered = (tableName: string) => {
    return registeredTables.some(t => t.tableName === tableName);
  };

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
      }}
      onClick={onClose}
    >
      <div
        style={{
          background: 'var(--card-bg, white)',
          borderRadius: '0.5rem',
          padding: '1.5rem',
          width: '90%',
          maxWidth: '900px',
          maxHeight: '90vh',
          overflow: 'auto',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ margin: 0 }}>
            테이블 관리 - {datasource.datasourceName}
          </h2>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>
            닫기
          </button>
        </div>

        {/* 테이블 선택 섹션 */}
        <div className="card" style={{ marginBottom: '1rem' }}>
          <h4 style={{ marginBottom: '0.75rem' }}>테이블 추가</h4>

          {/* 테이블 검색 */}
          <div style={{ marginBottom: '1rem' }}>
            <label className="form-label">테이블 검색</label>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <input
                type="text"
                className="form-input"
                placeholder="테이블명 입력 (비우면 전체 조회)"
                value={tableSearchQuery}
                onChange={(e) => setTableSearchQuery(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearchTables()}
                style={{ flex: 1 }}
              />
              <button
                className="btn btn-primary"
                onClick={handleSearchTables}
                disabled={loadingTables}
              >
                {loadingTables ? '검색중...' : '검색'}
              </button>
            </div>
          </div>

          {/* 테이블 선택 */}
          {loadingTables && allTables.length === 0 && (
            <div style={{ marginBottom: '1rem', padding: '1rem', textAlign: 'center', color: 'var(--text-muted)', border: '1px solid var(--border-color, #ddd)', borderRadius: '0.375rem', background: 'var(--bg-secondary, #f9f9f9)' }}>
              테이블 목록 로딩중...
            </div>
          )}
          {allTables.length > 0 && (
            <div style={{ marginBottom: '1rem' }}>
              <label className="form-label">테이블 선택 ({allTables.length}개)</label>
              <select
                className="form-select"
                value={selectedTable?.tableName || ''}
                onChange={(e) => {
                  const table = allTables.find(t => t.tableName === e.target.value);
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
                    {table.tableName} ({table.tableType}){isTableRegistered(table.tableName) ? ' - 등록됨' : ''}
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* 컬럼 선택 */}
          {selectedTable && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <label className="form-label" style={{ margin: 0 }}>
                  컬럼 선택 ({selectedColumns.length}/{allColumns.length})
                </label>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={handleSelectAllColumns}
                  disabled={loadingColumns}
                >
                  {selectedColumns.length === allColumns.length ? '전체해제' : '전체선택'}
                </button>
              </div>

              {loadingColumns ? (
                <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                  컬럼 로딩중...
                </div>
              ) : (
                <div style={{
                  maxHeight: '200px',
                  overflow: 'auto',
                  border: '1px solid var(--border-color, #ddd)',
                  borderRadius: '0.375rem',
                  background: 'var(--bg-secondary, #f9f9f9)'
                }}>
                  {allColumns.map((col) => (
                    <label
                      key={col.columnName}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        padding: '0.5rem 0.75rem',
                        cursor: 'pointer',
                        borderBottom: '1px solid var(--border-color, #eee)',
                        background: selectedColumns.some(c => c.columnName === col.columnName)
                          ? 'var(--primary-bg, #e3f2fd)'
                          : undefined,
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={selectedColumns.some((c) => c.columnName === col.columnName)}
                        onChange={() => handleToggleColumn(col)}
                        style={{ marginRight: '0.75rem' }}
                      />
                      <div style={{ flex: 1 }}>
                        <span style={{ fontWeight: col.isPrimaryKey ? 600 : 400 }}>
                          {col.columnName}
                          {col.isPrimaryKey && <span style={{ color: 'var(--primary-color, #1976d2)', marginLeft: '0.25rem' }}>(PK)</span>}
                        </span>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', marginLeft: '0.5rem' }}>
                          {col.dataType} {col.isNullable ? '' : '(NOT NULL)'}
                        </span>
                      </div>
                    </label>
                  ))}
                </div>
              )}

              {/* 등록 버튼 */}
              <div style={{ marginTop: '1rem', textAlign: 'right' }}>
                <button
                  className="btn btn-primary"
                  onClick={handleRegisterTable}
                  disabled={registering || selectedColumns.length === 0 || isTableRegistered(selectedTable.tableName)}
                >
                  {registering ? '등록중...' :
                   isTableRegistered(selectedTable.tableName) ? '이미 등록된 테이블' :
                   `${selectedTable.tableName} 등록 (${selectedColumns.length}개 컬럼)`}
                </button>
              </div>
            </div>
          )}
        </div>

        {/* 등록된 테이블 목록 */}
        <div className="card">
          <h4 style={{ marginBottom: '0.75rem' }}>
            등록된 테이블 ({registeredTables.length})
          </h4>
          {loading ? (
            <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--text-muted)' }}>로딩중...</div>
          ) : registeredTables.length === 0 ? (
            <div className="empty-state" style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
              등록된 테이블이 없습니다. 위에서 테이블을 검색하여 추가하세요.
            </div>
          ) : (
            <div style={{ maxHeight: '300px', overflow: 'auto' }}>
              {registeredTables.map((table) => (
                <div
                  key={table.id}
                  style={{
                    border: '1px solid var(--border-color, #ddd)',
                    borderRadius: '0.375rem',
                    padding: '0.75rem',
                    marginBottom: '0.5rem',
                    background: 'var(--bg-secondary, #f9f9f9)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>
                      <strong style={{ fontSize: '1rem' }}>{table.tableName}</strong>
                      {(table.description || table.tableAlias) && <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginLeft: '0.5rem' }}>({table.description || table.tableAlias})</span>}
                    </span>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleRefreshColumns(table)}
                        disabled={refreshingTableId === table.id}
                      >
                        {refreshingTableId === table.id ? '수집중...' : '컬럼 재수집'}
                      </button>
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => handleDeleteTable(table.id)}
                      >
                        삭제
                      </button>
                    </div>
                  </div>
                  <div style={{ marginTop: '0.5rem' }}>
                    <button
                      className="btn btn-sm"
                      style={{ fontSize: '0.75rem', padding: '0.125rem 0.5rem', background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
                      onClick={() => setExpandedTableId(expandedTableId === table.id ? null : table.id)}
                    >
                      {expandedTableId === table.id ? '▼' : '▶'} 컬럼 ({table.columns.length}개)
                    </button>
                    {expandedTableId === table.id && (
                      <div style={{ marginTop: '0.25rem', display: 'flex', flexWrap: 'wrap', gap: '0.25rem' }}>
                        {table.columns.map((c) => (
                          <span
                            key={c.id}
                            style={{
                              display: 'inline-block',
                              background: c.isPrimaryKey ? 'var(--primary-bg, #e3f2fd)' : 'var(--card-bg, white)',
                              border: '1px solid var(--border-color, #ddd)',
                              padding: '0.125rem 0.5rem',
                              borderRadius: '0.25rem',
                              fontSize: '0.75rem',
                            }}
                          >
                            {c.columnName}
                            {c.isPrimaryKey && <span style={{ color: 'var(--primary-color, #1976d2)' }}> (PK)</span>}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 컬럼 재수집 선택 모달 */}
        {refreshTarget && (
          <div style={{
            position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
            background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100,
          }}>
            <div className="card" style={{ width: '500px', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                <h4 style={{ margin: 0 }}>컬럼 재수집 — {refreshTarget.tableName}</h4>
                <button className="btn btn-sm" onClick={() => setRefreshTarget(null)} style={{ background: 'transparent', border: 'none', fontSize: '1.2rem', cursor: 'pointer' }}>✕</button>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                  선택: {refreshSelectedColumns.length}/{refreshDbColumns.length}
                </span>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={() => setRefreshSelectedColumns(
                    refreshSelectedColumns.length === refreshDbColumns.length ? [] : [...refreshDbColumns]
                  )}
                >
                  {refreshSelectedColumns.length === refreshDbColumns.length ? '전체해제' : '전체선택'}
                </button>
              </div>
              <div style={{
                flex: 1, overflow: 'auto', border: '1px solid var(--border-color, #ddd)',
                borderRadius: '0.375rem', background: 'var(--bg-secondary, #f9f9f9)',
              }}>
                {refreshDbColumns.map((col) => {
                  const isExisting = refreshTarget.columns.some(c => c.columnName === col.columnName);
                  const isSelected = refreshSelectedColumns.some(c => c.columnName === col.columnName);
                  return (
                    <label
                      key={col.columnName}
                      style={{
                        display: 'flex', alignItems: 'center', padding: '0.5rem 0.75rem', cursor: 'pointer',
                        borderBottom: '1px solid var(--border-color, #eee)',
                        background: isSelected ? 'var(--primary-bg, #e3f2fd)' : undefined,
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => handleRefreshToggleColumn(col)}
                        style={{ marginRight: '0.75rem' }}
                      />
                      <div style={{ flex: 1 }}>
                        <span style={{ fontWeight: col.isPrimaryKey ? 600 : 400 }}>
                          {col.columnName}
                          {col.isPrimaryKey && <span style={{ color: 'var(--primary-color, #1976d2)', marginLeft: '0.25rem' }}>(PK)</span>}
                        </span>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', marginLeft: '0.5rem' }}>
                          {col.dataType} {col.isNullable ? '' : '(NOT NULL)'}
                        </span>
                        {!isExisting && <span style={{ color: 'var(--success-color, #2e7d32)', fontSize: '0.7rem', marginLeft: '0.5rem' }}>NEW</span>}
                      </div>
                    </label>
                  );
                })}
              </div>
              <div style={{ marginTop: '0.75rem', textAlign: 'right', display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                <button className="btn btn-secondary" onClick={() => setRefreshTarget(null)}>취소</button>
                <button
                  className="btn btn-primary"
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
