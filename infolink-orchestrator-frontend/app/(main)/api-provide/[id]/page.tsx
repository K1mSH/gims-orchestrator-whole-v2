'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { operationApi } from '@/lib/providerApi';
import { ApiPrvOperation } from '@/types/api-provide';
import TabButton from '@/components/api-provide/TabButton';
import styles from './page.module.css';
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
    return <div className="app-loading">불러오는 중...</div>;
  }

  if (!operation) {
    return <div className="app-empty">오퍼레이션을 찾을 수 없습니다.</div>;
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
    <div>
      {/* 헤더 */}
      <div className="app-page-header">
        <div className={styles.headerLeftCol}>
          <div className={styles.titleRow}>
            <h1 className="app-page-header__title">{operation.operationName}</h1>
            <span className={`krds-badge ${operation.isPublished ? 'bg-light-success' : 'bg-light-gray'}`}>
              {operation.isPublished ? '활성' : '비활성'}
            </span>
            {isCustom && (
              <span className={`krds-badge bg-light-warning ${styles.lockBadge}`}>
                <span aria-hidden>🔒</span> CUSTOM
              </span>
            )}
          </div>
          <div className={styles.metaLine}>
            <code>{operation.operationId}</code> / {operation.tableName} / {operation.datasourceId}
          </div>
        </div>
        <div className={styles.headerActions}>
          <button type="button" className="krds-btn small secondary" onClick={() => router.push('/api-provide')}>목록</button>
          <button type="button" className="krds-btn small" onClick={handleTogglePublish}>
            {operation.isPublished ? '비활성화' : '활성화'}
          </button>
          {!isLocked && (
            <button type="button" className="krds-btn small app-btn-danger" onClick={handleDelete}>삭제</button>
          )}
        </div>
      </div>

      {/* CUSTOM 핸들러 안내 배너 */}
      {isCustom && (
        <div className="app-alert app-alert--warning">
          <strong>시스템 내장 핸들러</strong> — 원본 GIMS Oracle 직접 조회. 적재되지 않는 데이터입니다.
          이름·컬럼·파라미터는 코드에 박혀있어 수정/삭제 불가 (활성/비활성 토글만 가능).
          <span className={styles.customBannerSub}>
            관련 테이블 등 상세는 아래 탭의 설명·컬럼 참조.
          </span>
        </div>
      )}

      {/* 탭 */}
      <div className={`krds-tab-area ${styles.tabsWrap}`}>
        <div className="tab line">
          <ul>
            <TabButton active={activeTab === 'info'} onClick={() => setActiveTab('info')}>
              기본정보
            </TabButton>
            <TabButton active={activeTab === 'columns'} onClick={() => setActiveTab('columns')}>
              컬럼 ({operation.columns.length})
            </TabButton>
            <TabButton active={activeTab === 'params'} onClick={() => setActiveTab('params')}>
              파라미터 ({operation.params.length})
            </TabButton>
            <TabButton active={activeTab === 'test'} onClick={() => setActiveTab('test')}>
              테스트
            </TabButton>
            <TabButton active={activeTab === 'spec'} onClick={() => setActiveTab('spec')}>
              명세서
            </TabButton>
            <TabButton active={activeTab === 'history'} onClick={() => setActiveTab('history')}>
              이력
            </TabButton>
          </ul>
        </div>
      </div>

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
