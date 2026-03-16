'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { endpointApi } from '@/lib/collectorApi';
import { ApiEndpointDetail } from '@/types/api-collect';
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

  const fetchEndpoint = useCallback(async () => {
    try {
      setLoading(true);
      const data = await endpointApi.getById(id);
      setEndpoint(data);
    } catch (e) {
      console.error('조회 실패:', e);
      alert('API 정보를 불러올 수 없습니다.');
      router.push('/api-collect');
    } finally {
      setLoading(false);
    }
  }, [id, router]);

  useEffect(() => {
    fetchEndpoint();
  }, [fetchEndpoint]);

  if (loading || !endpoint) {
    return <div className="loading" style={{ padding: '2rem', textAlign: 'center' }}>로딩 중...</div>;
  }

  // Step Lock 판별
  const hasParams = endpoint.params.length > 0;
  const hasDataRoot = !!endpoint.dataRootPath;
  const hasMappings = endpoint.fieldMappings.length > 0;

  return (
    <div>
      {/* 헤더 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
        <button className="btn btn-sm" onClick={() => router.push('/api-collect')}
          style={{ background: 'var(--gray-200)' }}>
          &larr; 목록
        </button>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>{endpoint.apiName}</h1>
        <code style={{ fontSize: '0.8rem', background: 'var(--gray-100)', padding: '4px 8px', borderRadius: '4px' }}>
          {endpoint.apiCode}
        </code>
        <span style={{
          display: 'inline-block', width: '8px', height: '8px', borderRadius: '50%',
          background: endpoint.isActive ? 'var(--success)' : 'var(--gray-400)',
        }} />
      </div>

      {/* 상태 배지 */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', fontSize: '0.8rem' }}>
        <span style={{
          padding: '2px 8px', borderRadius: '4px',
          background: hasParams ? '#dcfce7' : '#fef3c7',
          color: hasParams ? '#166534' : '#92400e',
        }}>
          파라미터: {hasParams ? `${endpoint.params.length}개` : '미설정'}
        </span>
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
        {endpoint.targetTableName && (
          <span style={{
            padding: '2px 8px', borderRadius: '4px', background: '#dbeafe', color: '#1d4ed8',
          }}>
            적재: {endpoint.targetTableName}
          </span>
        )}
      </div>

      {/* 탭 */}
      <div style={{ borderBottom: '1px solid var(--gray-200)', marginBottom: '1rem', display: 'flex', gap: '0.25rem' }}>
        <TabButton label="기본정보" active={activeTab === 'info'} onClick={() => setActiveTab('info')} />
        <TabButton label="매핑" active={activeTab === 'mapping'} onClick={() => setActiveTab('mapping')}
          disabled={false} />
        <TabButton label="스케줄" active={activeTab === 'schedule'} onClick={() => setActiveTab('schedule')}
          disabled={!hasMappings} disabledReason="매핑 설정 후 이용 가능" />
        <TabButton label="실행 이력" active={activeTab === 'history'} onClick={() => setActiveTab('history')} />
      </div>

      {/* 탭 콘텐츠 */}
      {activeTab === 'info' && (
        <InfoTab endpoint={endpoint} onUpdate={fetchEndpoint} />
      )}
      {activeTab === 'mapping' && (
        <MappingTab endpoint={endpoint} onUpdate={fetchEndpoint} />
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
