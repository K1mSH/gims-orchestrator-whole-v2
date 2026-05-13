'use client';

import { useEffect, useState, useCallback, Fragment } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { executionApi, datasourceApi, agentApi, ExecutionDataSummary, TableData, TableDataParams, TraceResult } from '@/lib/api';
import type { Agent, ExecutionDetail, TableStats } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import TabButton from '@/components/agent/TabButton';
import styles from './page.module.css';

// 선택된 테이블 (tableName + tableType 조합으로 유니크하게 식별)
type SelectedTable = { tableName: string; tableType: string } | null;

// executionId에서 agentId 추출 (format: {agentId}_{uuid})
function extractAgentId(executionId: string): string {
  const lastUnderscoreIndex = executionId.lastIndexOf('_');
  if (lastUnderscoreIndex > 0) {
    return executionId.substring(0, lastUnderscoreIndex);
  }
  return executionId;
}

export default function ExecutionDetailPage() {
  const params = useParams();
  const router = useRouter();
  // URL에서 인코딩된 한글/공백을 디코딩 (이중 인코딩 방지)
  const executionId = decodeURIComponent(params.id as string);
  const agentId = extractAgentId(executionId);

  const [agent, setAgent] = useState<Agent | null>(null);
  const [executionCount, setExecutionCount] = useState<number>(0);
  const [executionDetail, setExecutionDetail] = useState<ExecutionDetail | null>(null);
  const [summary, setSummary] = useState<ExecutionDataSummary | null>(null);
  const [tableStats, setTableStats] = useState<TableStats[]>([]);
  const [selectedTable, setSelectedTable] = useState<SelectedTable>(null);
  const [tableData, setTableData] = useState<TableData | null>(null);
  const [loading, setLoading] = useState(true);
  const [dataLoading, setDataLoading] = useState(false);

  // 검색/필터/페이징 상태
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchColumn, setSearchColumn] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(5);

  // 트레이싱 상태 (Source 행 클릭 시 확장)
  const [expandedRowPk, setExpandedRowPk] = useState<string | null>(null);
  const [traceResult, setTraceResult] = useState<TraceResult | null>(null);
  const [traceLoading, setTraceLoading] = useState(false);

  // 페이지/테이블 이동 시 추적 토글 자동 닫기
  useEffect(() => {
    setExpandedRowPk(null);
    setTraceResult(null);
  }, [currentPage, selectedTable]);


  // execution_id가 덮어씌워진 실행인지 여부 (IF 테이블에서 fallbackMode 감지 시 set)
  const [isOverwrittenExecution, setIsOverwrittenExecution] = useState(false);

  // 정렬 상태 (기본값: id 오름차순)
  const [sortColumn, setSortColumn] = useState<string>('id');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

  // sourceRef 해석용 lookup 데이터
  const [sourceRefLookup, setSourceRefLookup] = useState<{
    datasources: Record<string, string>;
    tables: Record<string, string>;
  } | null>(null);

  // 테이블 alias lookup (tableName → alias)
  const [tableAliasMap, setTableAliasMap] = useState<Record<string, string>>({});

  const fetchExecution = useCallback(async () => {
    try {
      // Agent 정보 조회 (헤더 표시용)
      if (!agent) {
        try {
          const agents = await agentApi.getAll();
          const found = agents.find(a => a.agentCode === agentId);
          if (found) {
            setAgent(found);
            // 실행이력 건수 조회
            try {
              const { executionHistoryApi } = await import('@/lib/api');
              const histories = await executionHistoryApi.getByAgent(found.id);
              setExecutionCount(histories.length);
            } catch { /* 무시 */ }
          }
        } catch { /* 무시 */ }
      }

      // Agent DB에서 실행 상세 정보 조회
      const detail = await executionApi.getDetail(executionId);
      setExecutionDetail(detail);

      // 실행이 완료된 상태에서만 추가 정보 조회
      if (detail.status !== 'RUNNING') {
        try {
          const sum = await executionApi.getSummary(executionId);
          setSummary(sum);
        } catch {
          console.log('Summary not available');
        }

        // Agent DB에서 테이블 통계 조회
        try {
          const stats = await executionApi.getTableStats(executionId);
          setTableStats(stats);
        } catch {
          console.log('Table stats not available');
        }
      }

    } catch (error) {
      console.error('실행 정보 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [executionId]);

  // 실제 테이블 데이터 조회 (전체 컬럼) - 페이징/검색/정렬 지원
  const fetchTableData = useCallback(async (
    selected: SelectedTable,
    page: number = 0,
    search: string = '',
    column: string = '',
    status: string = '',
    sort: string = 'id',
    direction: 'asc' | 'desc' = 'asc'
  ) => {
    if (!selected) return;

    setDataLoading(true);
    try {
      const { tableName, tableType } = selected;

      const params: TableDataParams = {
        page,
        size: pageSize,
        tableName,  // 테이블명 파라미터
        ...(search && { search }),
        ...(column && { searchColumn: column }),
        ...(status && { status }),
        sortColumn: sort,
        sortDirection: direction,
      };

      let data: TableData;
      switch (tableType) {
        case 'SOURCE':
          data = await executionApi.getSourceData(executionId, params);
          break;
        case 'TARGET':
        default:
          data = await executionApi.getTargetData(executionId, params);
      }
      // API가 error 응답을 반환한 경우 처리
      if ((data as unknown as { error?: string })?.error) {
        console.error('API error:', (data as unknown as { error: string }).error);
        setTableData(null);
      } else {
        setTableData(data);
        if (data.fallbackMode) {
          setIsOverwrittenExecution(true);
        }
      }
    } catch (error) {
      console.error('테이블 데이터 조회 실패:', error);
      setTableData(null);
    } finally {
      setDataLoading(false);
    }
  }, [executionId, pageSize]);

  // 행 클릭 시 트레이싱 조회 (SOURCE, IF, TARGET 테이블에서)
  const handleRowClick = useCallback(async (row: Record<string, unknown>, rowIndex: number) => {
    const tableType = selectedTable?.tableType;

    // SOURCE, TARGET 테이블에서 트레이싱 가능
    if (!tableType || (tableType !== 'SOURCE' && tableType !== 'TARGET') || !tableData) return;

    // 행 식별용 키: 인덱스 기반 (복합PK 테이블에서 첫 컬럼 중복 방지)
    const rowId = String(rowIndex);
    if (!rowId) return;

    // 이미 확장된 행 클릭 시 접기
    if (expandedRowPk === rowId) {
      setExpandedRowPk(null);
      setTraceResult(null);

      return;
    }

    setExpandedRowPk(rowId);
    setTraceLoading(true);
    setTraceResult(null);

    // TARGET에서 source_refs가 null인 행은 추적 비대상 (파생 데이터: 집계/후처리)
    // SOURCE 테이블은 source_refs 유무와 무관하게 PK 기반으로 Target 추적 가능
    if (tableType !== 'SOURCE') {
      const sourceRefs = row.source_refs ?? row.SOURCE_REFS;
      if (sourceRefs === null || sourceRefs === undefined || sourceRefs === '') {
        setTraceResult({
          executionId,
          traceStatus: 'NOT_TRACKABLE' as any,
        } as TraceResult);
        setTraceLoading(false);
        return;
      }
    }

    try {
      let result;

      if (tableType === 'SOURCE') {
        // SOURCE 행 클릭 → backend trace API 로 정확한 매칭 target 조회
        // (이전: target 1000건 fetch + frontend Object.values.some 매칭 — 컬럼 무관 false positive)
        const sourceTableName = tableData.tableName;
        // PK 컬럼 = backend 가 준 pkColumns (DB 제약조건 순). 없으면 첫 컬럼 fallback.
        // 복합 PK 면 컬럼·값을 콤마로 묶어 전송 → backend 가 토큰 독립매칭 (순서 무관)
        const pkColsRaw = (tableData.pkColumns && tableData.pkColumns.length > 0)
          ? tableData.pkColumns : [tableData.columns[0]];
        // 표시 row 의 키 대소문자가 컬럼명과 다를 수 있어 대소문자 무시 매칭
        const pkColsResolved = pkColsRaw.map(c =>
          Object.keys(row).find(k => k.toLowerCase() === c.toLowerCase()) ?? c);
        const pkColParam = pkColsResolved.join(',');
        const pkValueParam = pkColsResolved.map(c => String(row[c] ?? '')).join(',');
        result = await executionApi.traceBySourcePk(executionId, pkValueParam, pkColParam, sourceTableName);
      } else {
        // TARGET 행 클릭 → Source 데이터 조회 (역추적)
        const sourceRefs = row.sourceRefs as string || row.source_refs as string || row.SOURCE_REFS as string;
        if (!sourceRefs) {
          setTraceResult({ error: 'sourceRefs가 없습니다.' });
          return;
        }
        // IF 테이블명을 전달하여 SOURCE 테이블 추론
        const ifTableName = selectedTable?.tableName;
        result = await executionApi.traceToSource(executionId, sourceRefs, ifTableName);
      }

      setTraceResult(result);
    } catch (error) {
      console.error('트레이싱 실패:', error);
      setTraceResult({ error: String(error) });
    } finally {
      setTraceLoading(false);
    }

  }, [executionId, selectedTable, expandedRowPk, tableData]);

  useEffect(() => {
    fetchExecution();
  }, [fetchExecution]);

  // sourceRef lookup 데이터 + table alias map 로드 (한 번만)
  useEffect(() => {
    datasourceApi.getSourceRefLookup()
      .then(setSourceRefLookup)
      .catch((err) => console.log('SourceRef lookup 로드 실패:', err));
    datasourceApi.getTableAliasMap()
      .then(setTableAliasMap)
      .catch((err) => console.log('Table alias map 로드 실패:', err));
  }, []);

  // 테이블 선택이 변경되면 검색/필터/정렬 초기화 후 데이터 조회
  useEffect(() => {
    if (selectedTable) {
      setSearchKeyword('');
      setSearchColumn('');
      setStatusFilter('');
      setCurrentPage(0);
      setExpandedRowPk(null);
      setTraceResult(null);
      setSortColumn('id');
      setSortDirection('asc');
      fetchTableData(selectedTable, 0, '', '', '', 'id', 'asc');
    }
  }, [selectedTable, fetchTableData]);

  // 검색/필터/페이지 변경 시 데이터 다시 조회
  const handleSearch = () => {
    setCurrentPage(0);
    fetchTableData(selectedTable, 0, searchKeyword, searchColumn, statusFilter, sortColumn, sortDirection);
  };

  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
    fetchTableData(selectedTable, newPage, searchKeyword, searchColumn, statusFilter, sortColumn, sortDirection);
  };

  const handleStatusFilterChange = (newStatus: string) => {
    setStatusFilter(newStatus);
    setCurrentPage(0);
    fetchTableData(selectedTable, 0, searchKeyword, searchColumn, newStatus, sortColumn, sortDirection);
  };

  // 정렬 변경 핸들러
  const handleSortChange = (column: string) => {
    let newDirection: 'asc' | 'desc' = 'asc';
    if (sortColumn === column) {
      newDirection = sortDirection === 'asc' ? 'desc' : 'asc';
    }
    setSortColumn(column);
    setSortDirection(newDirection);
    setCurrentPage(0);
    fetchTableData(selectedTable, 0, searchKeyword, searchColumn, statusFilter, column, newDirection);
  };

  // 실행중일 때 자동 새로고침
  useEffect(() => {
    if (executionDetail?.status === 'RUNNING') {
      const interval = setInterval(() => {
        fetchExecution();
      }, 5000); // 5초마다 갱신
      return () => clearInterval(interval);
    }
  }, [executionDetail?.status, fetchExecution]);

  if (loading) {
    return <div className="app-loading">로딩중...</div>;
  }

  if (!executionDetail) {
    return <div className="app-empty">실행 정보를 찾을 수 없습니다</div>;
  }

  // 매핑 데이터를 이전 형식(테이블별 행)으로 변환
  // SOURCE 행 = sourceTables, TARGET 행 = targetTables
  type FlatTableStat = { tableName: string; tableType: string; totalCount: number; successCount: number; failedCount: number; skipCount: number };
  const flatTableStats: FlatTableStat[] = [];
  for (const stat of tableStats) {
    for (const t of (stat.sourceTables ?? [])) {
      const read = stat.readCount ?? 0;
      const failed = stat.failedCount ?? 0;
      const skip = stat.skipCount ?? 0;
      flatTableStats.push({
        tableName: t,
        tableType: 'SOURCE',
        totalCount: read,
        successCount: read - failed - skip,
        failedCount: failed,
        skipCount: skip,
      });
    }
    for (const t of (stat.targetTables ?? [])) {
      // Per-target count 메타가 있으면 각 target 의 실 적재 카운트 사용, 없으면 mapping write 합산 fallback
      const perTargetCount = stat.targetCounts?.[t];
      const write = perTargetCount ?? (stat.writeCount ?? 0);
      const failed = stat.failedCount ?? 0;
      const skip = stat.skipCount ?? 0;
      flatTableStats.push({
        tableName: t,
        tableType: 'TARGET',
        totalCount: write + failed,
        successCount: write,
        failedCount: failed,
        skipCount: skip,
      });
    }
  }

  // 테이블 현황(SyncLog)이 있으면 그 합산 사용, 없으면 Execution 값 fallback
  const hasTableStats = tableStats.length > 0;
  const totalReadCount = hasTableStats
    ? tableStats.reduce((sum, s) => sum + (s.readCount ?? 0), 0)
    : (executionDetail.totalReadCount ?? 0);
  const totalWriteCount = hasTableStats
    ? tableStats.reduce((sum, s) => sum + (s.writeCount ?? 0), 0)
    : (executionDetail.totalWriteCount ?? 0);
  const totalSkipCount = hasTableStats
    ? tableStats.reduce((sum, s) => sum + (s.skipCount ?? 0), 0)
    : (executionDetail.totalSkipCount ?? 0);

  // 현재 선택된 테이블의 타입
  const selectedTableStat = selectedTable
    ? flatTableStats.find(s => s.tableName === selectedTable.tableName && s.tableType === selectedTable.tableType)
    : null;
  const isSourceTable = selectedTable?.tableType === 'SOURCE';
  const isTargetTable = selectedTable?.tableType === 'TARGET';
  // SOURCE, TARGET 테이블에서 행 클릭으로 추적 가능
  const isTraceable = isSourceTable || isTargetTable;

  return (
    <div className={styles.pageWrap}>
      {/* 헤더 (Agent 상세와 동일) */}
      <div className="app-page-header">
        <div className={styles.headerLeft}>
          <Link href={agent ? `/agents/${agent.id}` : `/agents`} className={styles.headerTitleLink}>
            <h1 className="app-page-header__title">
              {agent?.agentName || decodeURIComponent(agentId)}
            </h1>
          </Link>
          {agent && <StatusBadge status={agent.status} />}
        </div>
        <div />
      </div>

      {/* 탭 네비게이션 (Agent 상세와 동일) */}
      <div className={`krds-tab-area ${styles.tabsWrap}`}>
        <div className="tab line">
          <ul>
            <TabButton active={false} onClick={() => agent && router.push(`/agents/${agent.id}`)}>
              기본정보
            </TabButton>
            <TabButton active={true} onClick={() => agent && router.push(`/agents/${agent.id}?tab=history`)}>
              실행이력 ({executionCount})
            </TabButton>
          </ul>
        </div>
      </div>

      {/* 실행 정보 요약 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">실행 정보</h2>
        </div>
        <div className={styles.infoGrid}>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>실행 ID</div>
            <div className={styles.infoValueCode}>{decodeURIComponent(executionId)}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>상태</div>
            <div><StatusBadge status={executionDetail.status as 'SUCCESS' | 'FAILED' | 'RUNNING'} /></div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>시작 시간</div>
            <div className={styles.infoValue}>{new Date(executionDetail.startedAt).toLocaleString('ko-KR')}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>종료 시간</div>
            <div className={styles.infoValue}>{executionDetail.finishedAt ? new Date(executionDetail.finishedAt).toLocaleString('ko-KR') : '-'}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>소요 시간</div>
            <div className={styles.infoValue}>{executionDetail?.durationMs ? `${(executionDetail.durationMs / 1000).toFixed(1)}초` : '-'}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>총 읽기</div>
            <div className={`${styles.infoValueBig} ${styles.infoValueRead}`}>{totalReadCount}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>총 쓰기</div>
            <div className={`${styles.infoValueBig} ${styles.infoValueWrite}`}>{totalWriteCount}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>총 스킵</div>
            <div className={`${styles.infoValueBig} ${styles.infoValueSkip}`}>{totalSkipCount}</div>
          </div>
        </div>

        {executionDetail?.errorMessage && (
          <div className={`app-alert app-alert--danger ${styles.errorBox}`}>
            <strong>오류 메시지:</strong>
            <div className={styles.errorBoxMsg}>{executionDetail.errorMessage}</div>
          </div>
        )}
      </div>

      {/* 테이블별 처리 현황 */}
      {executionDetail.status !== 'RUNNING' && (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">테이블별 처리 현황</h2>
          </div>
          <div className={styles.statsTableWrap}>
            <table className="app-table">
              <thead>
                <tr>
                  <th>구분</th>
                  <th>테이블명</th>
                  <th>건수</th>
                  <th>성공</th>
                  <th>실패</th>
                </tr>
              </thead>
              <tbody>
                {flatTableStats.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="app-empty">테이블 통계 정보가 없습니다</td>
                  </tr>
                ) : (
                  [...flatTableStats].sort((a, b) => {
                    const typeOrder = { SOURCE: 0, TARGET: 1 };
                    const orderA = typeOrder[a.tableType as keyof typeof typeOrder] ?? 99;
                    const orderB = typeOrder[b.tableType as keyof typeof typeOrder] ?? 99;
                    return orderA - orderB;
                  }).map((stat) => {
                    const typeClass = stat.tableType === 'SOURCE' ? styles['typeBadge--source']
                      : stat.tableType === 'TARGET' ? styles['typeBadge--target']
                      : styles['typeBadge--other'];
                    const isSelected = selectedTable?.tableName === stat.tableName && selectedTable?.tableType === stat.tableType;
                    const hasFailed = (stat.failedCount ?? 0) > 0;

                    return (
                      <tr
                        key={`${stat.tableType}-${stat.tableName}`}
                        className={`${styles.statRow} ${isSelected ? styles.statRowSelected : hasFailed ? styles.statRowFailed : ''}`}
                        onClick={() => setSelectedTable({ tableName: stat.tableName, tableType: stat.tableType })}
                      >
                        <td>
                          <span className={`${styles.typeBadge} ${typeClass}`}>
                            {stat.tableType === 'SOURCE' ? 'SOURCE' : stat.tableType === 'TARGET' ? 'TARGET' : stat.tableType}
                          </span>
                        </td>
                        <td>
                          <code className={styles.tableNameCode}>{stat.tableName}</code>
                          {tableAliasMap[stat.tableName] && <span className={styles.tableAlias}>({tableAliasMap[stat.tableName]})</span>}
                        </td>
                        <td>{stat.totalCount}</td>
                        <td className={styles.cellSuccess}>{stat.successCount}</td>
                        <td className={hasFailed ? styles.cellFailed : styles.cellMuted}>{stat.failedCount}</td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
            <div className={styles.statFootNote}>
              write 건수에는 집계/후처리 등 파생 데이터가 포함될 수 있습니다
            </div>
          </div>
        </div>
      )}

      {/* 선택된 테이블의 실제 데이터 */}
      {selectedTable && (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">
              테이블 상세 조회
              {isTraceable && <span className={styles.tableAlias}> (행을 클릭하면 처리 상태를 추적할 수 있습니다)</span>}
            </h2>
            <button
              type="button"
              className="krds-btn small secondary"
              onClick={() => {
                setSelectedTable(null);
                setTableData(null);
              }}
            >
              닫기
            </button>
          </div>

          {/* 현재 선택된 테이블 정보 */}
          {(() => {
            const { tableName, tableType } = selectedTable!;
            const typeClass = tableType === 'SOURCE' ? styles['typeBadge--source']
              : tableType === 'TARGET' ? styles['typeBadge--target']
              : styles['typeBadge--other'];
            return (
              <div className={styles.selectedTableHeader}>
                <span className={`${styles.typeBadge} ${typeClass}`}>
                  {tableType === 'SOURCE' ? 'SOURCE' : tableType === 'TARGET' ? 'TARGET' : tableType ?? 'UNKNOWN'}
                </span>
                <code className={styles.selectedTableName}>{tableName}</code>
                {tableAliasMap[tableName] && <span className={styles.tableAlias}>({tableAliasMap[tableName]})</span>}
              </div>
            );
          })()}

          {/* 검색/필터 영역 */}
          <div className={styles.filterRow}>
            <div className={styles.searchGroup}>
              <select
                className={`krds-input small ${styles.filterSelect}`}
                value={searchColumn}
                onChange={(e) => setSearchColumn(e.target.value)}
              >
                <option value="">전체 컬럼</option>
                {tableData?.columns?.map((col) => (
                  <option key={col} value={col}>{col}</option>
                ))}
              </select>
              <input
                type="text"
                className={`krds-input small ${styles.searchInput}`}
                placeholder={searchColumn ? `${searchColumn} 검색...` : '검색어 입력...'}
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              />
              <button type="button" className="krds-btn small" onClick={handleSearch}>검색</button>
            </div>
            {isTargetTable && (
              <select
                className={`krds-input small ${styles.filterSelect}`}
                value={statusFilter}
                onChange={(e) => handleStatusFilterChange(e.target.value)}
              >
                <option value="">전체 상태</option>
                <option value="SUCCESS">성공</option>
                <option value="FAILED">실패</option>
              </select>
            )}
          </div>

          {!tableData && dataLoading ? (
            <div className={styles.emptyData}>데이터 로딩중...</div>
          ) : !tableData || !tableData.columns || !tableData.data || tableData.data.length === 0 ? (
            <div className={styles.emptyData}>
              {searchKeyword || statusFilter ? '검색 결과가 없습니다' : '데이터가 없습니다'}
            </div>
          ) : (
            <>
              {/* Fallback 모드 경고 배너 */}
              {(tableData.fallbackMode || (isOverwrittenExecution && isSourceTable)) && (
                <div className={`app-alert app-alert--warning ${styles.fallbackBanner}`}>
                  {tableData.fallbackReason
                    || (isSourceTable
                      ? '이 실행의 execution_id가 덮어씌워진 실행입니다. Source 테이블은 현재 전체 데이터를 표시합니다.'
                      : '이 실행의 execution_id가 덮어씌워져서, 처리 이력 기반으로 데이터를 표시합니다.')}
                </div>
              )}
              <div className={styles.totalCount}>총 <strong>{tableData.totalCount}</strong>건</div>
              <div className={`${styles.dataTableWrap} ${dataLoading ? styles.dataTableLoading : ''}`}>
                <table className={`app-table ${styles.dataTable}`}>
                  <thead>
                    <tr>
                      {isTraceable && <th className={styles.expandColTh}></th>}
                      {tableData.columns.map((col) => (
                        <th
                          key={col}
                          className={`${styles.sortableHeader} ${sortColumn === col ? styles.sortableHeaderActive : ''}`}
                          onClick={() => handleSortChange(col)}
                        >
                          {col}
                          {sortColumn === col && (
                            <span className={styles.sortIcon}>{sortDirection === 'asc' ? '▲' : '▼'}</span>
                          )}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {tableData.data.map((row, idx) => {
                      const rowPk = String(idx);
                      const isExpanded = isTraceable && expandedRowPk === rowPk;

                      return (
                        <Fragment key={`${idx}-${rowPk}`}>
                          <tr
                            onClick={() => handleRowClick(row, idx)}
                            className={`${isTraceable ? styles.dataRowClickable : styles.dataRow} ${isExpanded ? styles.dataRowExpanded : ''}`}
                          >
                            {isTraceable && (
                              <td className={styles.expandIconCell}>
                                <span className={`${styles.expandIcon} ${isExpanded ? styles.expandIconOpen : ''}`}>▶</span>
                              </td>
                            )}
                            {tableData.columns.map((col) => (
                              <td key={col} className={styles.dataCell}>
                                {formatCellValue(row[col])}
                              </td>
                            ))}
                          </tr>
                          {/* 트레이스 결과 확장 행 */}
                          {isExpanded && (
                            <tr>
                              <td colSpan={tableData.columns.length + 1} className={styles.traceExpandCell}>
                                <div className={styles.traceExpandPanel}>
                                  {traceLoading ? (
                                    <div className={styles.traceLoading}>추적 데이터 로딩중...</div>
                                  ) : traceResult && !traceResult.error ? (
                                    <div>
                                      {/* 트레이스 상태 */}
                                      <div className={styles.traceStatusRow}>
                                        <span className={styles.traceStatusLabel}>
                                          {traceResult.sourceRefs ? (
                                            <>sourceRefs: <code>{traceResult.sourceRefs}</code></>
                                          ) : traceResult.pkValue ? (
                                            <>{traceResult.pkColumn ?? 'PK'}: <code>{traceResult.pkValue}</code></>
                                          ) : null}
                                        </span>
                                        {traceResult.traceStatus && (() => {
                                          const ok = traceResult.traceStatus === 'SYNCED' || traceResult.traceStatus === 'FULLY_SYNCED' || traceResult.traceStatus === 'FOUND' || traceResult.traceStatus === 'FOUND_IN_IF';
                                          const notrack = traceResult.traceStatus === 'NOT_TRACKABLE';
                                          const cls = notrack ? styles['traceStatusBadge--notrack'] : ok ? styles['traceStatusBadge--ok'] : styles['traceStatusBadge--fail'];
                                          const label =
                                            (traceResult.traceStatus === 'SYNCED' || traceResult.traceStatus === 'FULLY_SYNCED') ? '동기화 완료' :
                                            (traceResult.traceStatus === 'FOUND' || traceResult.traceStatus === 'FOUND_IN_IF') ? '원본 찾음' :
                                            notrack ? '추적 비대상 (파생 데이터)' :
                                            traceResult.traceStatus === 'SOURCE_NOT_FOUND' ? '원본 없음' : '미동기화';
                                          return <span className={`${styles.traceStatusBadge} ${cls}`}>{label}</span>;
                                        })()}
                                      </div>

                                      {/* Source 또는 Target 테이블 데이터 */}
                                      {traceResult.sourceRecords ? (
                                        <div>
                                          <h4 className={`${styles.traceSubTitle} ${traceResult.sourceTableName ? styles.traceSubTitleSource : styles.traceSubTitleFail}`}>
                                            Source 테이블 ({traceResult.sourceTableName ?? '조회 실패'}) - {traceResult.sourceCount ?? 0}건
                                          </h4>
                                          {traceResult.sourceRecords.length > 0 ? (
                                            <div className={styles.traceSubTableWrap}>
                                              <table className={`app-table ${styles.traceSubTable} ${styles['traceSubTable--source']}`}>
                                                <thead>
                                                  <tr>
                                                    {Object.keys(traceResult.sourceRecords[0]).map(key => (
                                                      <th key={key}>{key}</th>
                                                    ))}
                                                  </tr>
                                                </thead>
                                                <tbody>
                                                  {traceResult.sourceRecords.map((rec, i) => (
                                                    <tr key={i}>
                                                      {Object.entries(rec).map(([key, val], j) => (
                                                        <td key={j}>{formatCellValue(val)}</td>
                                                      ))}
                                                    </tr>
                                                  ))}
                                                </tbody>
                                              </table>
                                            </div>
                                          ) : (
                                            <div className={styles.traceSubEmpty}>Source 테이블에 데이터 없음</div>
                                          )}
                                        </div>
                                      ) : (
                                        <div>
                                          <h4 className={`${styles.traceSubTitle} ${traceResult.targetTableName ? styles.traceSubTitleTarget : styles.traceSubTitleFail}`}>
                                            Target 테이블 ({traceResult.targetTableName ?? '조회 실패'}) - {traceResult.targetCount ?? 0}건
                                          </h4>
                                          {traceResult.targetRecords && traceResult.targetRecords.length > 0 ? (
                                            <div className={styles.traceSubTableWrap}>
                                              <table className={`app-table ${styles.traceSubTable} ${styles['traceSubTable--target']}`}>
                                                <thead>
                                                  <tr>
                                                    {Object.keys(traceResult.targetRecords[0]).map(key => (
                                                      <th key={key}>{key}</th>
                                                    ))}
                                                  </tr>
                                                </thead>
                                                <tbody>
                                                  {traceResult.targetRecords.map((rec, i) => (
                                                    <tr key={i}>
                                                      {Object.entries(rec).map(([key, val], j) => (
                                                        <td key={j}>{formatCellValue(val)}</td>
                                                      ))}
                                                    </tr>
                                                  ))}
                                                </tbody>
                                              </table>
                                            </div>
                                          ) : (
                                            <div className={styles.traceSubEmpty}>Target 테이블에 데이터 없음</div>
                                          )}
                                        </div>
                                      )}
                                    </div>
                                  ) : (
                                    <div className={styles.traceError}>
                                      {traceResult?.error ? `오류: ${traceResult.error}` : '추적 데이터를 불러올 수 없습니다'}
                                    </div>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* 페이징 */}
              {tableData.totalPages > 1 && (
                <div className={styles.pager}>
                  <button type="button" className="krds-btn xsmall secondary" onClick={() => handlePageChange(0)} disabled={!tableData.hasPrevious}>처음</button>
                  <button type="button" className="krds-btn xsmall secondary" onClick={() => handlePageChange(currentPage - 1)} disabled={!tableData.hasPrevious}>이전</button>
                  <span className={styles.pagerLabel}>{currentPage + 1} / {tableData.totalPages}</span>
                  <button type="button" className="krds-btn xsmall secondary" onClick={() => handlePageChange(currentPage + 1)} disabled={!tableData.hasNext}>다음</button>
                  <button type="button" className="krds-btn xsmall secondary" onClick={() => handlePageChange(tableData.totalPages - 1)} disabled={!tableData.hasNext}>마지막</button>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {executionDetail.status === 'RUNNING' && (
        <div className={`app-card ${styles.runningCard}`}>
          <div className={styles.runningTitle}>실행 중...</div>
          <div className={styles.runningDesc}>실행이 완료되면 처리된 데이터를 확인할 수 있습니다.</div>
        </div>
      )}
    </div>
  );
}

function formatCellValue(value: unknown): string {
  if (value === null || value === undefined) return '-';
  if (typeof value === 'boolean') return value ? 'Y' : 'N';
  if (typeof value === 'object') {
    if (value instanceof Date) return value.toLocaleString('ko-KR');
    return JSON.stringify(value);
  }
  // ISO 날짜 형식 감지
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(value)) {
    return new Date(value).toLocaleString('ko-KR');
  }
  return String(value);
}
