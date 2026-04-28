'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { operationApi } from '@/lib/providerApi';
import { ApiPrvOperation } from '@/types/api-provide';
import TabButton from '@/components/api-provide/TabButton';
import InfoTab from '@/components/api-provide/InfoTab';
import ColumnsTab from '@/components/api-provide/ColumnsTab';
import ParamsTab from '@/components/api-provide/ParamsTab';
import TestTab from '@/components/api-provide/TestTab';
import HistoryTab from '@/components/api-provide/HistoryTab';
import SpecTab from '@/components/api-provide/SpecTab';

type TabType = 'info' | 'columns' | 'params' | 'test' | 'spec' | 'history';

export default function OperationDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = Number(params.id);

  const [operation, setOperation] = useState<ApiPrvOperation | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<TabType>('info');

  const fetchOperation = async () => {
    try {
      const data = await operationApi.getById(id);
      setOperation(data);
    } catch (e: any) {
      console.error('오퍼레이션 조회 실패:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchOperation(); }, [id]);

  if (loading) {
    return <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--gray-400)' }}>불러오는 중...</div>;
  }

  if (!operation) {
    return <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--gray-400)' }}>오퍼레이션을 찾을 수 없습니다.</div>;
  }

  const handleTogglePublish = async () => {
    try {
      const updated = await operationApi.togglePublish(operation.id);
      alert(updated.isPublished ? '활성화되었습니다.' : '비활성화되었습니다.');
      fetchOperation();
    } catch (e: any) {
      alert('상태 변경 실패: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleDelete = async () => {
    if (!confirm('이 오퍼레이션을 삭제하시겠습니까?')) return;
    try {
      await operationApi.delete(operation.id);
      router.push('/api-provide');
    } catch (e: any) {
      alert('삭제 실패: ' + (e.response?.data?.message || e.message));
    }
  };

  const isCustom = operation.operationType === 'CUSTOM';
  const isLocked = operation.isLocked;

  return (
    <div style={{ minWidth: 0, maxWidth: '100%', overflow: 'hidden' }}>
      {/* 헤더 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <h1 style={{ fontSize: '1.25rem', fontWeight: 700 }}>{operation.operationName}</h1>
            <span style={{
              padding: '2px 10px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
              background: operation.isPublished ? '#dcfce7' : '#f3f4f6',
              color: operation.isPublished ? '#166534' : '#6b7280',
            }}>
              {operation.isPublished ? '활성' : '비활성'}
            </span>
            {isCustom && (
              <span style={{
                padding: '2px 10px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
                background: '#fef3c7', color: '#92400e',
                display: 'inline-flex', alignItems: 'center', gap: '4px',
              }}>
                <span aria-hidden>🔒</span> CUSTOM
              </span>
            )}
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--gray-400)', marginTop: '0.25rem' }}>
            <code>{operation.operationId}</code> / {operation.tableName} / {operation.datasourceId}
          </div>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button className="btn" onClick={() => router.push('/api-provide')}>목록</button>
          <button className="btn" onClick={handleTogglePublish}>
            {operation.isPublished ? '비활성화' : '활성화'}
          </button>
          {!isLocked && (
            <button className="btn btn-danger" onClick={handleDelete}>삭제</button>
          )}
        </div>
      </div>

      {/* CUSTOM 핸들러 안내 배너 */}
      {isCustom && (
        <div style={{
          marginBottom: '1rem',
          padding: '0.75rem 1rem',
          background: '#fffbeb',
          border: '1px solid #fcd34d',
          borderRadius: '6px',
          fontSize: '0.85rem',
          color: '#78350f',
          lineHeight: 1.5,
        }}>
          <strong>시스템 내장 핸들러</strong> — 원본 GIMS Oracle 직접 조회. 적재되지 않는 데이터입니다.
          이름·컬럼·파라미터는 코드에 박혀있어 수정/삭제 불가 (활성/비활성 토글만 가능).
          <br/>
          <span style={{ fontSize: '0.75rem', color: 'var(--gray-500)' }}>
            관련 테이블 등 상세는 아래 탭의 설명·컬럼 참조.
          </span>
        </div>
      )}

      {/* 탭 */}
      <TabButton
        tabs={[
          { key: 'info', label: '기본정보' },
          { key: 'columns', label: `컬럼 (${operation.columns.length})` },
          { key: 'params', label: `파라미터 (${operation.params.length})` },
          { key: 'test', label: '테스트' },
          { key: 'spec', label: '명세서' },
          { key: 'history', label: '이력' },
        ]}
        active={activeTab}
        onChange={(key) => setActiveTab(key as TabType)}
      />

      {/* 탭 컨텐츠 */}
      {activeTab === 'info' && <InfoTab operation={operation} onUpdate={fetchOperation} />}
      {activeTab === 'columns' && <ColumnsTab operation={operation} onUpdate={fetchOperation} />}
      {activeTab === 'params' && <ParamsTab operation={operation} onUpdate={fetchOperation} />}
      {activeTab === 'test' && <TestTab operation={operation} />}
      {activeTab === 'spec' && <SpecTab operation={operation} />}
      {activeTab === 'history' && <HistoryTab operationId={operation.id} />}
    </div>
  );
}
