'use client';

import { useEffect, useState, useCallback, Fragment } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { executionApi, datasourceApi, agentApi, ExecutionDataSummary, TableData, TableDataParams, TraceResult } from '@/lib/api';
import type { Agent, ExecutionDetail, TableStats } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import TabButton from '@/components/agent/TabButton';

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
        // SOURCE 행 클릭 → backend trace API 로 정확한 매칭 target 1건 조회
        // (이전: target 1000건 fetch + frontend Object.values.some 매칭 — 컬럼 무관 false positive)
        const sourceTableName = tableData.tableName;
        const pkCol = tableData.columns[0];
        const pkValue = String(row[pkCol] ?? '');
        result = await executionApi.traceBySourcePk(executionId, pkValue, pkCol, sourceTableName);
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
    return <div className="loading">로딩중...</div>;
  }

  if (!executionDetail) {
    return <div className="empty-state">실행 정보를 찾을 수 없습니다</div>;
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
      const write = stat.writeCount ?? 0;
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
    <div style={{ width: '100%', maxWidth: '100%', overflowX: 'hidden' }}>
      {/* 헤더 (Agent 상세와 동일) */}
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <Link href={agent ? `/agents/${agent.id}` : `/agents`} style={{ textDecoration: 'none', color: 'inherit' }}>
            <h1 className="page-title" style={{ cursor: 'pointer' }}>
              {agent?.agentName || decodeURIComponent(agentId)}
            </h1>
          </Link>
          {agent && <StatusBadge status={agent.status} />}
        </div>
        <div />
      </div>

      {/* 탭 네비게이션 (Agent 상세와 동일) */}
      <div style={{ display: 'flex', gap: '0', marginBottom: '1.5rem', borderBottom: '2px solid var(--gray-200)' }}>
        <TabButton active={false} onClick={() => agent && router.push(`/agents/${agent.id}`)}>
          기본정보
        </TabButton>
        <TabButton active={true} onClick={() => agent && router.push(`/agents/${agent.id}?tab=history`)}>
          실행이력 ({executionCount})
        </TabButton>
      </div>

      {/* 실행 정보 요약 */}
      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <h2 className="card-title" style={{ marginBottom: '1rem' }}>실행 정보</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem' }}>
          <div>
            <strong>실행 ID</strong>
            <div style={{ marginTop: '0.25rem', fontFamily: 'monospace', fontSize: '0.85rem' }}>
              {decodeURIComponent(executionId)}
            </div>
          </div>
          <div>
            <strong>상태</strong>
            <div style={{ marginTop: '0.25rem' }}>
              <StatusBadge status={executionDetail.status as 'SUCCESS' | 'FAILED' | 'RUNNING'} />
            </div>
          </div>
          <div>
            <strong>시작 시간</strong>
            <div style={{ marginTop: '0.25rem' }}>
              {new Date(executionDetail.startedAt).toLocaleString('ko-KR')}
            </div>
          </div>
          <div>
            <strong>종료 시간</strong>
            <div style={{ marginTop: '0.25rem' }}>
              {executionDetail.finishedAt ? new Date(executionDetail.finishedAt).toLocaleString('ko-KR') : '-'}
            </div>
          </div>
          <div>
            <strong>소요 시간</strong>
            <div style={{ marginTop: '0.25rem' }}>
              {executionDetail?.durationMs ? `${(executionDetail.durationMs / 1000).toFixed(1)}초` : '-'}
            </div>
          </div>
          <div>
            <strong>총 읽기</strong>
            <div style={{ marginTop: '0.25rem', fontSize: '1.25rem', fontWeight: 600, color: 'var(--blue-600)' }}>
              {totalReadCount}
            </div>
          </div>
          <div>
            <strong>총 쓰기</strong>
            <div style={{ marginTop: '0.25rem', fontSize: '1.25rem', fontWeight: 600, color: 'var(--green-600)' }}>
              {totalWriteCount}
            </div>
          </div>
          <div>
            <strong>총 스킵</strong>
            <div style={{ marginTop: '0.25rem', fontSize: '1.25rem', fontWeight: 600, color: 'var(--gray-500)' }}>
              {totalSkipCount}
            </div>
          </div>
        </div>

        {executionDetail?.errorMessage && (
          <div style={{ marginTop: '1rem', padding: '1rem', background: 'var(--red-50)', borderRadius: '0.5rem', border: '1px solid var(--red-200)' }}>
            <strong style={{ color: 'var(--red-700)' }}>오류 메시지:</strong>
            <div style={{ marginTop: '0.5rem', color: 'var(--red-600)', fontFamily: 'monospace', fontSize: '0.85rem' }}>
              {executionDetail.errorMessage}
            </div>
          </div>
        )}
      </div>

      {/* 테이블별 처리 현황 */}
      {executionDetail.status !== 'RUNNING' && (
        <div className="card" style={{ marginBottom: '1.5rem' }}>
          <h2 className="card-title" style={{ marginBottom: '1rem' }}>테이블별 처리 현황</h2>
            <div className="table-container">
              <table>
                <thead>
                  <tr>
                    <th>구분</th>
                    <th>테이블명</th>
                    <th>건수</th>
                    <th>성공</th>
                    <th>실패</th>
                    <th>상세</th>
                  </tr>
                </thead>
                <tbody>
                  {flatTableStats.length === 0 ? (
                    <tr>
                      <td colSpan={6} style={{ textAlign: 'center', color: 'var(--gray-500)', padding: '2rem' }}>
                        테이블 통계 정보가 없습니다
                      </td>
                    </tr>
                  ) : (
                    // SOURCE → TARGET 순서로 정렬
                    [...flatTableStats].sort((a, b) => {
                      const typeOrder = { SOURCE: 0, TARGET: 1 };
                      const orderA = typeOrder[a.tableType as keyof typeof typeOrder] ?? 99;
                      const orderB = typeOrder[b.tableType as keyof typeof typeOrder] ?? 99;
                      return orderA - orderB;
                    }).map((stat) => {
                      const typeConfigMap: Record<string, { label: string; bg: string; color: string; borderColor: string }> = {
                        SOURCE: { label: 'SOURCE', bg: 'var(--blue-100)', color: 'var(--blue-700)', borderColor: 'var(--blue-500)' },
                        TARGET: { label: 'TARGET', bg: 'var(--green-100)', color: 'var(--green-700)', borderColor: 'var(--green-500)' },
                      };
                      const typeConfig = typeConfigMap[stat.tableType] ?? { label: stat.tableType, bg: 'var(--gray-100)', color: 'var(--gray-700)', borderColor: 'var(--gray-500)' };

                      const isSelected = selectedTable?.tableName === stat.tableName && selectedTable?.tableType === stat.tableType;
                      const hasFailed = (stat.failedCount ?? 0) > 0;

                      return (
                        <tr
                          key={`${stat.tableType}-${stat.tableName}`}
                          style={{
                            cursor: 'pointer',
                            background: isSelected ? typeConfig.bg : hasFailed ? 'var(--red-50)' : 'transparent',
                            borderLeft: isSelected ? `4px solid ${typeConfig.borderColor}` : '4px solid transparent',
                            fontWeight: isSelected ? 600 : 400,
                          }}
                          onClick={() => setSelectedTable({ tableName: stat.tableName, tableType: stat.tableType })}
                        >
                          <td>
                            <span style={{
                              padding: '0.25rem 0.5rem',
                              borderRadius: '0.25rem',
                              fontSize: '0.75rem',
                              fontWeight: 500,
                              background: typeConfig.bg,
                              color: typeConfig.color,
                            }}>
                              {typeConfig.label}
                            </span>
                          </td>
                          <td>
                            <code>{stat.tableName}</code>
                            {tableAliasMap[stat.tableName] && <span style={{ fontSize: '0.75rem', color: 'var(--gray-500)', marginLeft: '0.5rem' }}>({tableAliasMap[stat.tableName]})</span>}
                          </td>
                          <td style={{ fontWeight: 500 }}>{stat.totalCount}</td>
                          <td style={{ color: 'var(--green-600)', fontWeight: 500 }}>{stat.successCount}</td>
                          <td style={{
                            color: hasFailed ? 'var(--red-600)' : 'var(--gray-400)',
                            fontWeight: hasFailed ? 600 : 400
                          }}>
                            {stat.failedCount}
                          </td>
                          <td>
                            <button
                              className={isSelected ? 'btn btn-primary btn-sm' : 'btn btn-secondary btn-sm'}
                              onClick={(e) => {
                                e.stopPropagation();
                                setSelectedTable({ tableName: stat.tableName, tableType: stat.tableType });
                              }}
                            >
                              {isSelected ? '선택됨' : '상세'}
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
              <div style={{ fontSize: '0.75rem', color: 'var(--gray-500)', padding: '0.5rem 0.75rem', borderTop: '1px solid var(--border-color)' }}>
                write 건수에는 집계/후처리 등 파생 데이터가 포함될 수 있습니다
              </div>
            </div>
          </div>
      )}

      {/* 선택된 테이블의 실제 데이터 (가로 크기 고정, 세로는 자유) */}
      {selectedTable && (
        <div className="card" style={{
          display: 'flex',
          flexDirection: 'column',
          overflowX: 'hidden',
          contain: 'inline-size'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <div>
              <h2 className="card-title" style={{ margin: 0, display: 'inline' }}>
                테이블 상세 조회
              </h2>
              {isTraceable && (
                <span style={{ marginLeft: '1rem', fontSize: '0.85rem', color: 'var(--gray-500)' }}>
                  (행을 클릭하면 처리 상태를 추적할 수 있습니다)
                </span>
              )}
            </div>
            <button
              className="btn btn-secondary btn-sm"
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
            const typeConfig = {
              SOURCE: { label: 'SOURCE', bg: 'var(--blue-100)', color: 'var(--blue-700)' },
              TARGET: { label: 'TARGET', bg: 'var(--green-100)', color: 'var(--green-700)' },
            }[tableType] ?? { label: tableType ?? 'UNKNOWN', bg: 'var(--gray-100)', color: 'var(--gray-700)' };

            return (
              <div style={{
                padding: '0.5rem 1rem',
                background: 'var(--gray-50)',
                borderRadius: '0.375rem',
                marginBottom: '1rem',
                display: 'flex',
                alignItems: 'center',
                gap: '0.75rem',
              }}>
                <span style={{
                  padding: '0.25rem 0.5rem',
                  borderRadius: '0.25rem',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  background: typeConfig.bg,
                  color: typeConfig.color,
                }}>
                  {typeConfig.label}
                </span>
                <code style={{ fontSize: '0.9rem', fontWeight: 500 }}>{tableName}</code>
                {tableAliasMap[tableName] && <span style={{ fontSize: '0.8rem', color: 'var(--gray-500)' }}>({tableAliasMap[tableName]})</span>}
              </div>
            );
          })()}

          {/* 검색/필터 영역 */}
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', gap: '0.5rem', flex: 1, minWidth: '200px' }}>
              <select
                value={searchColumn}
                onChange={(e) => setSearchColumn(e.target.value)}
                style={{
                  padding: '0.5rem 0.75rem',
                  border: '1px solid var(--gray-300)',
                  borderRadius: '0.375rem',
                  fontSize: '0.875rem',
                  background: 'white',
                  minWidth: '120px',
                }}
              >
                <option value="">전체 컬럼</option>
                {tableData?.columns?.map((col) => (
                  <option key={col} value={col}>{col}</option>
                ))}
              </select>
              <input
                type="text"
                placeholder={searchColumn ? `${searchColumn} 검색...` : '검색어 입력...'}
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                style={{
                  flex: 1,
                  padding: '0.5rem 0.75rem',
                  border: '1px solid var(--gray-300)',
                  borderRadius: '0.375rem',
                  fontSize: '0.875rem',
                }}
              />
              <button className="btn btn-primary btn-sm" onClick={handleSearch}>
                검색
              </button>
            </div>
            {/* TARGET 테이블만 상태 필터 표시 */}
            {isTargetTable && (
              <select
                value={statusFilter}
                onChange={(e) => handleStatusFilterChange(e.target.value)}
                style={{
                  padding: '0.5rem 0.75rem',
                  border: '1px solid var(--gray-300)',
                  borderRadius: '0.375rem',
                  fontSize: '0.875rem',
                  background: 'white',
                }}
              >
                <option value="">전체 상태</option>
                <option value="SUCCESS">성공</option>
                <option value="FAILED">실패</option>
              </select>
            )}
          </div>

          {/* 데이터가 없을 때만 로딩 표시, 기존 데이터가 있으면 유지 */}
          {!tableData && dataLoading ? (
            <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--gray-500)' }}>데이터 로딩중...</div>
          ) : !tableData || !tableData.columns || !tableData.data || tableData.data.length === 0 ? (
            <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--gray-500)' }}>
              {searchKeyword || statusFilter ? '검색 결과가 없습니다' : '데이터가 없습니다'}
            </div>
          ) : (
            <>
              {/* Fallback 모드 경고 배너 (IF/TARGET: 서버 응답, SOURCE: 프론트 감지) */}
              {(tableData.fallbackMode || (isOverwrittenExecution && isSourceTable)) && (
                <div style={{
                  padding: '0.75rem 1rem',
                  marginBottom: '0.75rem',
                  background: 'var(--yellow-50)',
                  border: '1px solid var(--yellow-300)',
                  borderRadius: '0.375rem',
                  fontSize: '0.85rem',
                  color: 'var(--yellow-800)',
                }}>
                  {tableData.fallbackReason
                    || (isSourceTable
                      ? '이 실행의 execution_id가 덮어씌워진 실행입니다. Source 테이블은 현재 전체 데이터를 표시합니다.'
                      : '이 실행의 execution_id가 덮어씌워져서, 처리 이력 기반으로 데이터를 표시합니다.')}
                </div>
              )}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <div style={{ fontSize: '0.9rem' }}>
                  <span style={{ color: 'var(--gray-600)' }}>
                    총 <strong>{tableData.totalCount}</strong>건
                  </span>
                </div>
              </div>
              <div className="table-container" style={{
                minWidth: 0,
                width: '100%',
                overflowX: 'auto',
                opacity: dataLoading ? 0.6 : 1,
                pointerEvents: dataLoading ? 'none' : 'auto',
                transition: 'opacity 0.15s',
              }}>
                <table style={{ fontSize: '0.85rem', minWidth: '1200px', tableLayout: 'auto' }}>
                  <thead>
                    <tr>
                      {isTraceable && (
                        <th style={{ width: '30px' }}></th>
                      )}
                      {tableData.columns.map((col) => (
                        <th
                          key={col}
                          style={{
                            whiteSpace: 'nowrap',
                            cursor: 'pointer',
                            userSelect: 'none',
                            background: sortColumn === col ? 'var(--gray-200)' : undefined,
                          }}
                          onClick={() => handleSortChange(col)}
                        >
                          {col}
                          {sortColumn === col && (
                            <span style={{ marginLeft: '0.25rem', fontSize: '0.7rem' }}>
                              {sortDirection === 'asc' ? '▲' : '▼'}
                            </span>
                          )}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {tableData.data.map((row, idx) => {
                      // 행 식별: 인덱스 기반 (복합PK 중복 방지)
                      const rowPk = String(idx);
                      const isExpanded = isTraceable && expandedRowPk === rowPk;

                      return (
                        <Fragment key={`${idx}-${rowPk}`}>
                          <tr
                            onClick={() => handleRowClick(row, idx)}
                            style={{
                              cursor: isTraceable ? 'pointer' : 'default',
                              background: isExpanded ? 'var(--blue-50)' : 'transparent',
                            }}
                          >
                            {isTraceable && (
                              <td style={{ padding: '0.5rem', textAlign: 'center' }}>
                                <span style={{
                                  display: 'inline-block',
                                  width: '16px',
                                  height: '16px',
                                  lineHeight: '16px',
                                  textAlign: 'center',
                                  fontSize: '0.75rem',
                                  color: 'var(--gray-500)',
                                  transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
                                  transition: 'transform 0.2s',
                                }}>
                                  ▶
                                </span>
                              </td>
                            )}
                            {tableData.columns.map((col) => (
                              <td key={col} style={{
                                whiteSpace: 'nowrap',
                                maxWidth: '300px',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                              }}>
                                {formatCellValue(row[col])}
                              </td>
                            ))}
                          </tr>
                          {/* 트레이스 결과 확장 행 */}
                          {isExpanded && (
                            <tr>
                              <td colSpan={tableData.columns.length + 1} style={{ padding: 0, background: 'var(--gray-50)' }}>
                                <div style={{ padding: '1rem', borderLeft: '4px solid var(--blue-500)' }}>
                                  {traceLoading ? (
                                    <div style={{ textAlign: 'center', color: 'var(--gray-500)', padding: '1rem' }}>
                                      추적 데이터 로딩중...
                                    </div>
                                  ) : traceResult && !traceResult.error ? (
                                    <div>
                                      {/* 트레이스 상태 */}
                                      <div style={{ marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '1rem' }}>
                                        <span style={{ fontWeight: 600 }}>
                                          {traceResult.sourceRefs ? (
                                            <>sourceRefs: <code style={{ fontSize: '0.75rem' }}>{traceResult.sourceRefs}</code></>
                                          ) : traceResult.pkValue ? (
                                            <>{traceResult.pkColumn ?? 'PK'}: <code>{traceResult.pkValue}</code></>
                                          ) : null}
                                        </span>
                                        {traceResult.traceStatus && (
                                          <span style={{
                                            padding: '0.25rem 0.5rem',
                                            borderRadius: '0.25rem',
                                            fontSize: '0.75rem',
                                            fontWeight: 500,
                                            background: traceResult.traceStatus === 'NOT_TRACKABLE' ? 'var(--gray-100)' : (traceResult.traceStatus === 'SYNCED' || traceResult.traceStatus === 'FULLY_SYNCED' || traceResult.traceStatus === 'FOUND' || traceResult.traceStatus === 'FOUND_IN_IF') ? 'var(--green-100)' : 'var(--red-100)',
                                            color: traceResult.traceStatus === 'NOT_TRACKABLE' ? 'var(--gray-600)' : (traceResult.traceStatus === 'SYNCED' || traceResult.traceStatus === 'FULLY_SYNCED' || traceResult.traceStatus === 'FOUND' || traceResult.traceStatus === 'FOUND_IN_IF') ? 'var(--green-700)' : 'var(--red-700)',
                                          }}>
                                            {traceResult.traceStatus === 'SYNCED' || traceResult.traceStatus === 'FULLY_SYNCED' ? '동기화 완료' :
                                             traceResult.traceStatus === 'FOUND' || traceResult.traceStatus === 'FOUND_IN_IF' ? '원본 찾음' :
                                             traceResult.traceStatus === 'NOT_TRACKABLE' ? '추적 비대상 (파생 데이터)' :
                                             traceResult.traceStatus === 'SOURCE_NOT_FOUND' ? '원본 없음' : '미동기화'}
                                          </span>
                                        )}
                                      </div>

                                      {/* Source 또는 Target 테이블 데이터 */}
                                      {traceResult.sourceRecords ? (
                                        // 역추적: Source 데이터 표시
                                        <div>
                                          <h4 style={{ fontSize: '0.9rem', marginBottom: '0.5rem', color: traceResult.sourceTableName ? 'var(--blue-700)' : 'var(--red-600)' }}>
                                            Source 테이블 ({traceResult.sourceTableName ?? '조회 실패'}) - {traceResult.sourceCount ?? 0}건
                                          </h4>
                                          {traceResult.sourceRecords.length > 0 ? (
                                            <div style={{ maxHeight: '200px', overflowX: 'auto', overflowY: 'auto', border: '1px solid var(--gray-200)', borderRadius: '0.25rem' }}>
                                              <table style={{ fontSize: '0.8rem', minWidth: '800px', tableLayout: 'auto' }}>
                                                <thead>
                                                  <tr>
                                                    {Object.keys(traceResult.sourceRecords[0]).map(key => (
                                                      <th key={key} style={{ whiteSpace: 'nowrap', padding: '0.4rem 0.6rem', background: 'var(--blue-50)', position: 'sticky', top: 0 }}>{key}</th>
                                                    ))}
                                                  </tr>
                                                </thead>
                                                <tbody>
                                                  {traceResult.sourceRecords.map((rec, i) => (
                                                    <tr key={i}>
                                                      {Object.entries(rec).map(([key, val], j) => (
                                                        <td key={j} style={{ whiteSpace: 'nowrap', padding: '0.4rem 0.6rem' }}>
                                                          {formatCellValue(val)}
                                                        </td>
                                                      ))}
                                                    </tr>
                                                  ))}
                                                </tbody>
                                              </table>
                                            </div>
                                          ) : (
                                            <div style={{ color: 'var(--gray-500)', fontStyle: 'italic' }}>Source 테이블에 데이터 없음</div>
                                          )}
                                        </div>
                                      ) : (
                                        // 정방향 추적: Target 데이터 표시
                                        <div>
                                          <h4 style={{ fontSize: '0.9rem', marginBottom: '0.5rem', color: traceResult.targetTableName ? 'var(--green-700)' : 'var(--red-600)' }}>
                                            Target 테이블 ({traceResult.targetTableName ?? '조회 실패'}) - {traceResult.targetCount ?? 0}건
                                          </h4>
                                          {traceResult.targetRecords && traceResult.targetRecords.length > 0 ? (
                                            <div style={{ maxHeight: '200px', overflowX: 'auto', overflowY: 'auto', border: '1px solid var(--gray-200)', borderRadius: '0.25rem' }}>
                                              <table style={{ fontSize: '0.8rem', minWidth: '800px', tableLayout: 'auto' }}>
                                                <thead>
                                                  <tr>
                                                    {Object.keys(traceResult.targetRecords[0]).map(key => (
                                                      <th key={key} style={{ whiteSpace: 'nowrap', padding: '0.4rem 0.6rem', background: 'var(--green-50)', position: 'sticky', top: 0 }}>{key}</th>
                                                    ))}
                                                  </tr>
                                                </thead>
                                                <tbody>
                                                  {traceResult.targetRecords.map((rec, i) => (
                                                    <tr key={i}>
                                                      {Object.entries(rec).map(([key, val], j) => (
                                                        <td key={j} style={{
                                                          whiteSpace: 'nowrap',
                                                          padding: '0.4rem 0.6rem',
                                                        }}>
                                                          {formatCellValue(val)}
                                                        </td>
                                                      ))}
                                                    </tr>
                                                  ))}
                                                </tbody>
                                              </table>
                                            </div>
                                          ) : (
                                            <div style={{ color: 'var(--gray-500)', fontStyle: 'italic' }}>Target 테이블에 데이터 없음</div>
                                          )}
                                        </div>
                                      )}
                                    </div>
                                  ) : (
                                    <div style={{ color: 'var(--red-500)' }}>
                                      {traceResult?.error
                                        ? `오류: ${traceResult.error}`
                                        : '추적 데이터를 불러올 수 없습니다'}
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
                <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.5rem', marginTop: '1rem' }}>
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => handlePageChange(0)}
                    disabled={!tableData.hasPrevious}
                  >
                    처음
                  </button>
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => handlePageChange(currentPage - 1)}
                    disabled={!tableData.hasPrevious}
                  >
                    이전
                  </button>
                  <span style={{ padding: '0 1rem', fontSize: '0.875rem', color: 'var(--gray-600)' }}>
                    {currentPage + 1} / {tableData.totalPages}
                  </span>
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => handlePageChange(currentPage + 1)}
                    disabled={!tableData.hasNext}
                  >
                    다음
                  </button>
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => handlePageChange(tableData.totalPages - 1)}
                    disabled={!tableData.hasNext}
                  >
                    마지막
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {executionDetail.status === 'RUNNING' && (
        <div className="card" style={{ background: 'var(--blue-50)', border: '2px solid var(--blue-200)' }}>
          <div style={{ padding: '2rem', textAlign: 'center' }}>
            <div style={{ fontSize: '1.2rem', color: 'var(--blue-700)', marginBottom: '0.5rem' }}>
              실행 중...
            </div>
            <div style={{ color: 'var(--blue-600)' }}>
              실행이 완료되면 처리된 데이터를 확인할 수 있습니다.
            </div>
          </div>
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

// Zone별 색상 설정
const zoneColors: Record<string, { bg: string; color: string; border: string }> = {
  EXTERNAL: { bg: '#fef3c7', color: '#92400e', border: '#f59e0b' },
  DMZ: { bg: '#dbeafe', color: '#1e40af', border: '#3b82f6' },
  INTERNAL_COMMON: { bg: '#d1fae5', color: '#065f46', border: '#10b981' },
  INTERNAL_SERVICE: { bg: '#ede9fe', color: '#5b21b6', border: '#8b5cf6' },
  UNKNOWN: { bg: '#f3f4f6', color: '#374151', border: '#9ca3af' },
};

// Zone 약어 -> 전체 이름 매핑
const zoneShortCodeToName: Record<string, string> = {
  E: 'EXTERNAL',
  D: 'DMZ',
  IC: 'INTERNAL_COMMON',
  IS: 'INTERNAL_SERVICE',
  U: 'UNKNOWN',
};

// sourceRefs 파싱 결과 타입
interface ParsedSourceRef {
  zone: string;       // 전체 zone 이름 (EXTERNAL, DMZ 등)
  dsId: string;       // datasource ID
  tableId: string;    // table ID
  pk: string;         // record PK
  raw: string;        // 원본 문자열
}

// sourceRefs를 파싱하여 구조화된 형태로 반환
function parseSourceRefs(sourceRefs: string): { refs: ParsedSourceRef[]; byZone: Record<string, ParsedSourceRef[]> } | null {
  if (!sourceRefs || typeof sourceRefs !== 'string') return null;

  try {
    const trimmed = sourceRefs.trim();
    const refs: ParsedSourceRef[] = [];

    // 신규 간결 형식: {zoneShortCode}:{dsId}:{tableId}:{pk} (예: D:1:5:12345)
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) {
      const parts = trimmed.split(':');
      if (parts.length === 4) {
        const zoneShortCode = parts[0];
        const zoneName = zoneShortCodeToName[zoneShortCode] || 'UNKNOWN';
        refs.push({
          zone: zoneName,
          dsId: parts[1],
          tableId: parts[2],
          pk: parts[3],
          raw: trimmed,
        });
      }
    }
    // 현재 형식: {"zoneShortCode":["dsId:tableId:pk",...]}
    // 하위 호환: {"ZONE":["ds:table:pk"]}
    else if (trimmed.startsWith('{')) {
      const parsed = JSON.parse(trimmed) as Record<string, string[]>;
      for (const [zoneKey, refList] of Object.entries(parsed)) {
        // zone 키가 약어(D, E, IC, IS)면 전체 이름으로 변환
        const zoneName = zoneShortCodeToName[zoneKey] || zoneKey;
        for (const ref of refList) {
          const parts = ref.split(':');
          refs.push({
            zone: zoneName,
            dsId: parts[0] || '',
            tableId: parts[1] || '',
            pk: parts[2] || '',
            raw: ref,
          });
        }
      }
    }
    // 레거시 JSON 배열 형식: ["zone:ds:table:pk"]
    else if (trimmed.startsWith('[')) {
      const parsed = JSON.parse(trimmed) as string[];
      for (const ref of parsed) {
        const parts = ref.split(':');
        if (parts.length >= 4) {
          refs.push({
            zone: parts[0],
            dsId: parts[1],
            tableId: parts[2],
            pk: parts[3],
            raw: ref,
          });
        } else if (parts.length >= 3) {
          refs.push({
            zone: 'UNKNOWN',
            dsId: parts[0],
            tableId: parts[1],
            pk: parts[2],
            raw: ref,
          });
        }
      }
    }

    if (refs.length === 0) return null;

    // zone별로 그룹화
    const byZone: Record<string, ParsedSourceRef[]> = {};
    for (const ref of refs) {
      if (!byZone[ref.zone]) byZone[ref.zone] = [];
      byZone[ref.zone].push(ref);
    }

    return { refs, byZone };
  } catch {
    // 파싱 실패 시 null 반환
  }
  return null;
}

// sourceRefs 표시 컴포넌트
interface SourceRefsDisplayProps {
  value: string;
  lookup?: {
    datasources: Record<string, string>;
    tables: Record<string, string>;
  } | null;
}

function SourceRefsDisplay({ value, lookup }: SourceRefsDisplayProps) {
  const [expanded, setExpanded] = useState(false);
  const parsed = parseSourceRefs(value);

  // lookup을 사용해 ID를 이름으로 변환
  const resolveDsName = (dsId: string) => lookup?.datasources?.[dsId] || `ds:${dsId}`;
  const resolveTableName = (tableId: string) => lookup?.tables?.[tableId] || `tbl:${tableId}`;

  if (!parsed) {
    // 파싱 실패 시 원본 표시
    return <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{value}</span>;
  }

  const { refs, byZone } = parsed;
  const zones = Object.keys(byZone);

  return (
    <div style={{ position: 'relative' }}>
      {/* 축약된 표시 (클릭하면 확장) */}
      <div
        onClick={(e) => { e.stopPropagation(); setExpanded(!expanded); }}
        style={{
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          gap: '0.25rem',
          flexWrap: 'wrap',
        }}
      >
        {zones.map((zone) => {
          const colors = zoneColors[zone] || zoneColors.UNKNOWN;
          const refCount = byZone[zone].length;
          return (
            <span
              key={zone}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.25rem',
                padding: '0.15rem 0.4rem',
                borderRadius: '0.25rem',
                fontSize: '0.7rem',
                fontWeight: 500,
                background: colors.bg,
                color: colors.color,
                border: `1px solid ${colors.border}`,
              }}
            >
              {zone}
              {refCount > 1 && <span style={{ opacity: 0.7 }}>({refCount})</span>}
            </span>
          );
        })}
        <span style={{ fontSize: '0.7rem', color: 'var(--gray-400)', marginLeft: '0.25rem' }}>
          {expanded ? '▼' : '▶'}
        </span>
      </div>

      {/* 확장된 상세 표시 */}
      {expanded && (
        <div
          onClick={(e) => e.stopPropagation()}
          style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            zIndex: 100,
            marginTop: '0.25rem',
            padding: '0.5rem',
            background: 'white',
            border: '1px solid var(--gray-300)',
            borderRadius: '0.375rem',
            boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
            minWidth: '280px',
            maxWidth: '400px',
          }}
        >
          {zones.map((zone) => {
            const colors = zoneColors[zone] || zoneColors.UNKNOWN;
            return (
              <div key={zone} style={{ marginBottom: zones.indexOf(zone) < zones.length - 1 ? '0.5rem' : 0 }}>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  marginBottom: '0.25rem',
                }}>
                  <span style={{
                    padding: '0.15rem 0.4rem',
                    borderRadius: '0.25rem',
                    fontSize: '0.7rem',
                    fontWeight: 600,
                    background: colors.bg,
                    color: colors.color,
                    border: `1px solid ${colors.border}`,
                  }}>
                    {zone}
                  </span>
                </div>
                <div style={{
                  paddingLeft: '0.5rem',
                  borderLeft: `2px solid ${colors.border}`,
                }}>
                  {byZone[zone].map((ref, idx) => (
                    <div
                      key={idx}
                      style={{
                        fontSize: '0.75rem',
                        fontFamily: 'monospace',
                        padding: '0.15rem 0',
                        color: 'var(--gray-700)',
                      }}
                    >
                      <span style={{ color: 'var(--blue-600)' }}>{resolveDsName(ref.dsId)}</span>
                      <span style={{ color: 'var(--gray-400)' }}>:</span>
                      <span style={{ color: 'var(--green-600)' }}>{resolveTableName(ref.tableId)}</span>
                      <span style={{ color: 'var(--gray-400)' }}>:</span>
                      <span style={{ color: 'var(--orange-600)', fontWeight: 500 }}>{ref.pk}</span>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
