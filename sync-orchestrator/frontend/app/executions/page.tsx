'use client';

import { useEffect, useState, useCallback } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { executionHistoryApi, agentApi } from '@/lib/api';
import type { ExecutionHistory, ExecutionHistorySearchParams, PageResponse, Agent, AgentType, Zone } from '@/types';
import StatusBadge from '@/components/StatusBadge';
import Link from 'next/link';

const AGENT_TYPE_LABELS: Record<AgentType, string> = {
  RCV: '수신(RCV)',
  SND: '송신(SND)',
  LOADER: 'Loader',
  DB_CON_PROXY: 'DB Proxy',
};

const ZONE_LABELS: Record<Zone, string> = {
  EXTERNAL: '외부망',
  DMZ: 'DMZ',
  INTERNAL: '내부망',
  INTERNAL_COMMON: '내부공통망',
  INTERNAL_SERVICE: '내부서비스망',
};

const PAGE_SIZE = 20;

export default function ExecutionsPage() {
  const searchParams = useSearchParams();
  const router = useRouter();

  // 필터 상태 (URL 쿼리 파라미터에서 초기화)
  const [status, setStatus] = useState<string>(searchParams.get('status') || '');
  const [agentCode, setAgentCode] = useState<string>(searchParams.get('agentCode') || '');
  const [agentType, setAgentType] = useState<string>(searchParams.get('agentType') || '');
  const [zone, setZone] = useState<string>(searchParams.get('zone') || '');
  const [startDate, setStartDate] = useState<string>(searchParams.get('startDate') || '');
  const [endDate, setEndDate] = useState<string>(searchParams.get('endDate') || '');
  const [search, setSearch] = useState<string>(searchParams.get('search') || '');
  const [searchInput, setSearchInput] = useState<string>(searchParams.get('search') || '');
  const [page, setPage] = useState<number>(Number(searchParams.get('page')) || 0);

  // 데이터 상태
  const [pageData, setPageData] = useState<PageResponse<ExecutionHistory> | null>(null);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);

  // Agent 목록 로드 (필터 드롭다운용)
  useEffect(() => {
    agentApi.getAll().then(setAgents).catch(console.error);
  }, []);

  // 사용 가능한 zone 목록 (Agent 데이터에서 추출)
  const availableZones = Array.from(new Set(agents.map(a => a.zone)));

  // URL 쿼리 동기화
  const updateUrl = useCallback((params: Record<string, string | number>) => {
    const url = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== '' && value !== 0 && value != null) {
        url.set(key, String(value));
      }
    });
    // page=0이면 생략
    if (params.page && Number(params.page) > 0) {
      url.set('page', String(params.page));
    }
    const qs = url.toString();
    router.replace(qs ? `/executions?${qs}` : '/executions');
  }, [router]);

  // 데이터 fetch
  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      const params: ExecutionHistorySearchParams = {
        page,
        size: PAGE_SIZE,
        status: (status || null) as ExecutionHistorySearchParams['status'],
        agentCode: agentCode || null,
        agentType: (agentType || null) as ExecutionHistorySearchParams['agentType'],
        zone: (zone || null) as ExecutionHistorySearchParams['zone'],
        startDate: startDate || null,
        endDate: endDate || null,
        search: search || null,
      };
      const data = await executionHistoryApi.getPaged(params);
      setPageData(data);
    } catch (error) {
      console.error('실행 이력 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page, status, agentCode, agentType, zone, startDate, endDate, search]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // 필터 변경 시 URL 업데이트 + 페이지 리셋
  useEffect(() => {
    updateUrl({ status, agentCode, agentType, zone, startDate, endDate, search, page });
  }, [status, agentCode, agentType, zone, startDate, endDate, search, page, updateUrl]);

  const handleFilterChange = (key: string, value: string) => {
    setPage(0);
    switch (key) {
      case 'status': setStatus(value); break;
      case 'agentCode': setAgentCode(value); break;
      case 'agentType': setAgentType(value); break;
      case 'zone': setZone(value); break;
      case 'startDate': setStartDate(value); break;
      case 'endDate': setEndDate(value); break;
    }
  };

  const handleSearch = () => {
    setPage(0);
    setSearch(searchInput);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSearch();
  };

  const totalPages = pageData?.totalPages ?? 0;
  const content = pageData?.content ?? [];

  // 페이지 번호 목록 생성
  const getPageNumbers = () => {
    const pages: number[] = [];
    const maxVisible = 5;
    let start = Math.max(0, page - Math.floor(maxVisible / 2));
    const end = Math.min(totalPages, start + maxVisible);
    if (end - start < maxVisible) {
      start = Math.max(0, end - maxVisible);
    }
    for (let i = start; i < end; i++) {
      pages.push(i);
    }
    return pages;
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">실행 이력</h1>
        <span style={{ color: 'var(--gray-400)', fontSize: '12px' }}>
          {pageData ? `총 ${pageData.totalElements}건` : ''}
        </span>
      </div>

      {/* 필터 바 */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap', padding: '0.75rem 1rem' }}>
          <select
            className="form-select"
            value={status}
            onChange={(e) => handleFilterChange('status', e.target.value)}
            style={{ width: 'auto', minWidth: '110px' }}
          >
            <option value="">전체 상태</option>
            <option value="SUCCESS">성공</option>
            <option value="FAILED">실패</option>
            <option value="RUNNING">실행중</option>
          </select>

          <select
            className="form-select"
            value={zone}
            onChange={(e) => handleFilterChange('zone', e.target.value)}
            style={{ width: 'auto', minWidth: '110px' }}
          >
            <option value="">전체 망</option>
            {availableZones.map(z => (
              <option key={z} value={z}>{ZONE_LABELS[z as Zone] || z}</option>
            ))}
          </select>

          <select
            className="form-select"
            value={agentCode}
            onChange={(e) => handleFilterChange('agentCode', e.target.value)}
            style={{ width: 'auto', minWidth: '150px' }}
          >
            <option value="">전체 Agent</option>
            {agents.map(a => (
              <option key={a.agentCode} value={a.agentCode}>{a.agentName}</option>
            ))}
          </select>

          <select
            className="form-select"
            value={agentType}
            onChange={(e) => handleFilterChange('agentType', e.target.value)}
            style={{ width: 'auto', minWidth: '110px' }}
          >
            <option value="">전체 타입</option>
            <option value="RCV">수신(RCV)</option>
            <option value="SND">송신(SND)</option>
            <option value="LOADER">Loader</option>
          </select>
        </div>

        {/* 두번째 줄: 날짜 + 검색 */}
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap', padding: '0 1rem 0.75rem 1rem' }}>
          <div style={{ display: 'flex', gap: '0.25rem', alignItems: 'center' }}>
            <input
              type="date"
              className="form-input"
              value={startDate}
              onChange={(e) => handleFilterChange('startDate', e.target.value)}
              style={{ width: '150px' }}
            />
            <span style={{ color: 'var(--gray-400)' }}>~</span>
            <input
              type="date"
              className="form-input"
              value={endDate}
              onChange={(e) => handleFilterChange('endDate', e.target.value)}
              style={{ width: '150px' }}
            />
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', flex: 1, minWidth: '200px' }}>
            <input
              type="text"
              className="form-input"
              placeholder="Agent 이름 검색..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              onKeyDown={handleKeyDown}
              style={{ flex: 1 }}
            />
            <button className="btn btn-primary btn-sm" onClick={handleSearch}>검색</button>
          </div>
        </div>
      </div>

      {/* 테이블 */}
      <div className="card">
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Agent</th>
                <th>타입</th>
                <th>상태</th>
                <th>읽기/쓰기/스킵</th>
                <th>소요시간</th>
                <th>트리거</th>
                <th>시작 시간</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7} className="empty-state">로딩중...</td>
                </tr>
              ) : content.length === 0 ? (
                <tr>
                  <td colSpan={7} className="empty-state">실행 이력이 없습니다</td>
                </tr>
              ) : (
                content.map(history => (
                  <tr key={history.executionId}>
                    <td>
                      <Link href={`/executions/${encodeURIComponent(history.executionId)}`} style={{ fontWeight: 500 }}>
                        {history.agentName}
                      </Link>
                      <span style={{ marginLeft: '0.5rem', fontSize: '0.75rem', color: 'var(--gray-400)' }}>
                        {history.agentCode}
                      </span>
                    </td>
                    <td>
                      <span className={`agent-type-badge agent-type-${history.agentType?.toLowerCase() || 'unknown'}`}>
                        {history.agentType ? (AGENT_TYPE_LABELS[history.agentType] || history.agentType) : '-'}
                      </span>
                    </td>
                    <td><StatusBadge status={history.status as 'SUCCESS' | 'FAILED' | 'RUNNING'} /></td>
                    <td>
                      {history.status === 'RUNNING' ? (
                        <span style={{ color: 'var(--gray-400)' }}>-</span>
                      ) : (
                        <span>
                          {history.totalReadCount ?? 0} / {history.totalWriteCount ?? 0} / {history.totalSkipCount ?? 0}
                        </span>
                      )}
                    </td>
                    <td>{history.durationMs != null ? `${(history.durationMs / 1000).toFixed(1)}s` : '-'}</td>
                    <td>
                      <span className={`trigger-badge trigger-${history.triggeredBy.toLowerCase()}`}>
                        {history.triggeredBy}
                      </span>
                    </td>
                    <td>{new Date(history.startedAt).toLocaleString('ko-KR')}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* 페이지네이션 */}
        {totalPages > 1 && (
          <div style={{
            display: 'flex', justifyContent: 'center', alignItems: 'center',
            gap: '0.25rem', padding: '1rem', borderTop: '1px solid var(--gray-700)'
          }}>
            <button
              className="btn btn-secondary btn-sm"
              disabled={page === 0}
              onClick={() => setPage(p => p - 1)}
            >
              ◀
            </button>
            {getPageNumbers().map(p => (
              <button
                key={p}
                className={`btn btn-sm ${p === page ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setPage(p)}
              >
                {p + 1}
              </button>
            ))}
            <button
              className="btn btn-secondary btn-sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage(p => p + 1)}
            >
              ▶
            </button>
            <span style={{ marginLeft: '1rem', fontSize: '12px', color: 'var(--gray-400)' }}>
              {PAGE_SIZE}건/페이지
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
