'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { endpointApi } from '@/lib/collectorApi';
import { ApiEndpointDetail } from '@/types/api-collect';
import axios from 'axios';
import TabButton from '@/components/api-collect/TabButton';
import InfoTab from '@/components/api-collect/InfoTab';
import MappingTab from '@/components/api-collect/MappingTab';
import HistoryTab from '@/components/api-collect/HistoryTab';
import ScheduleTab from '@/components/api-collect/ScheduleTab';

type TabType = 'info' | 'mapping' | 'schedule' | 'history';

export default function ApiCollectDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = Number(params.id);

  const [endpoint, setEndpoint] = useState<ApiEndpointDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<TabType>('info');
  const [running, setRunning] = useState(false);

  const fetchEndpoint = useCallback(async (silent = false) => {
    try {
      if (!silent) setLoading(true);
      const data = await endpointApi.getById(id);
      setEndpoint(data);
    } catch (e) {
      console.error('조회 실패:', e);
      alert('API 정보를 불러올 수 없습니다.');
      router.push('/api-collect');
    } finally {
      if (!silent) setLoading(false);
    }
  }, [id, router]);

  useEffect(() => {
    fetchEndpoint();
  }, [fetchEndpoint]);

  if (loading || !endpoint) {
    return <div className="loading" style={{ padding: '2rem', textAlign: 'center' }}>로딩 중...</div>;
  }

  const handleRun = async () => {
    if (!canRun) {
      const missing = [];
      if (!hasDataRoot) missing.push('데이터 루트');
      if (!endpoint.targetTableName) missing.push('적재 테이블');
      if (!hasMappings) missing.push('필드 매핑');
      alert(`실행할 수 없습니다.\n미설정 항목: ${missing.join(', ')}`);
      return;
    }
    if (!confirm('수동 실행하시겠습니까?')) return;
    try {
      setRunning(true);
      const result = await axios.post(`/collector-api/endpoints/${endpoint.id}/run`);
      const h = result.data;
      alert(`실행 완료: ${h.status}\n신규: ${h.insertCount}건, 갱신: ${h.updateCount ?? 0}건, 스킵: ${h.skipCount}건${h.errorMessage ? '\n에러: ' + h.errorMessage : ''}`);
      fetchEndpoint();
    } catch (e: any) {
      alert('실행 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setRunning(false);
    }
  };

  // Step Lock 판별
  const isCustom = !!endpoint.executorType;
  const hasParams = endpoint.params.length > 0;
  const hasDataRoot = !!endpoint.dataRootPath;
  const hasMappings = endpoint.fieldMappings.length > 0;
  const canRun = isCustom || (hasMappings && hasDataRoot && !!endpoint.targetTableName);

  return (
    <div>
      {/* 헤더 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
        <button className="btn btn-sm" onClick={() => router.push('/api-collect')}
          style={{ background: 'var(--gray-200)' }}>
          &larr; 목록
        </button>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>{endpoint.apiName}</h1>
        <span style={{
          display: 'inline-block', width: '8px', height: '8px', borderRadius: '50%',
          background: endpoint.isActive ? 'var(--success)' : 'var(--gray-400)',
        }} />
      </div>

      {/* 상태 배지 + 수동 실행 */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', fontSize: '0.8rem', alignItems: 'center', flexWrap: 'wrap' }}>
        {isCustom && (
          <span style={{
            padding: '2px 8px', borderRadius: '4px', background: '#fef3c7', color: '#92400e',
          }}>
            커스텀: {endpoint.executorType}
          </span>
        )}
        <span style={{
          padding: '2px 8px', borderRadius: '4px',
          background: hasParams ? '#dcfce7' : '#fef3c7',
          color: hasParams ? '#166534' : '#92400e',
        }}>
          파라미터: {hasParams ? `${endpoint.params.length}개` : '미설정'}
        </span>
        {!isCustom && (<>
        <span style={{
          padding: '2px 8px', borderRadius: '4px',
          background: hasDataRoot ? '#dcfce7' : '#fee2e2',
          color: hasDataRoot ? '#166534' : '#991b1b',
        }}>
          데이터 루트: {hasDataRoot ? endpoint.dataRootPath : '미설정'}
        </span>
        <span style={{
          padding: '2px 8px', borderRadius: '4px',
          background: hasMappings ? '#dcfce7' : '#fee2e2',
          color: hasMappings ? '#166534' : '#991b1b',
        }}>
          매핑: {hasMappings ? `${endpoint.fieldMappings.length}개` : '미설정'}
        </span>
        </>)}
        {endpoint.targetTableName && (
          <span style={{
            padding: '2px 8px', borderRadius: '4px', background: '#dbeafe', color: '#1d4ed8',
          }}>
            적재: {endpoint.targetTableName}
          </span>
        )}
        <div style={{ marginLeft: 'auto' }}>
          <button className="btn btn-sm" onClick={handleRun} disabled={running}
            style={{
              background: canRun ? 'var(--success)' : 'var(--gray-300)',
              color: canRun ? 'white' : 'var(--gray-500)',
              padding: '4px 16px', fontSize: '0.85rem',
              cursor: running ? 'wait' : 'pointer',
            }}>
            {running ? '실행 중...' : '수동 실행'}
          </button>
        </div>
      </div>

      {/* 탭 */}
      <div style={{ borderBottom: '1px solid var(--gray-200)', marginBottom: '1rem', display: 'flex', gap: '0.25rem' }}>
        <TabButton label="기본정보" active={activeTab === 'info'} onClick={() => setActiveTab('info')} />
        <TabButton label="매핑" active={activeTab === 'mapping'} onClick={() => setActiveTab('mapping')}
          disabled={isCustom} disabledReason="커스텀 실행기는 매핑 불필요" />
        <TabButton label="스케줄" active={activeTab === 'schedule'} onClick={() => setActiveTab('schedule')}
          disabled={!isCustom && !hasMappings} disabledReason="매핑 설정 후 이용 가능" />
        <TabButton label="실행 이력" active={activeTab === 'history'} onClick={() => setActiveTab('history')} />
      </div>

      {/* 탭 콘텐츠 */}
      {activeTab === 'info' && (
        <InfoTab endpoint={endpoint} onUpdate={() => fetchEndpoint(true)} />
      )}
      {activeTab === 'mapping' && (
        <MappingTab endpoint={endpoint} onUpdate={() => fetchEndpoint(true)} />
      )}
      {activeTab === 'schedule' && (
        <ScheduleTab endpointId={endpoint.id} />
      )}
      {activeTab === 'history' && (
        <HistoryTab endpointId={endpoint.id} />
      )}
    </div>
  );
}
