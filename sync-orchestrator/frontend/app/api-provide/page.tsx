'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { operationApi } from '@/lib/providerApi';
import { ApiPrvOperation } from '@/types/api-provide';

export default function ApiProvidePage() {
  const router = useRouter();
  const [operations, setOperations] = useState<ApiPrvOperation[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchOperations = async () => {
    try {
      const data = await operationApi.getAll();
      setOperations(data);
    } catch (e: any) {
      console.error('오퍼레이션 목록 조회 실패:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchOperations(); }, []);

  const handleDelete = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!confirm('이 오퍼레이션을 삭제하시겠습니까?')) return;
    try {
      await operationApi.delete(id);
      fetchOperations();
    } catch (e: any) {
      alert('삭제 실패: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleTogglePublish = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await operationApi.togglePublish(id);
      fetchOperations();
    } catch (e: any) {
      alert('상태 변경 실패: ' + (e.response?.data?.message || e.message));
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h1 style={{ fontSize: '1.25rem', fontWeight: 700 }}>API 제공 관리</h1>
        <button className="btn btn-primary" onClick={() => router.push('/api-provide/new')}>
          + 오퍼레이션 등록
        </button>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--gray-400)' }}>불러오는 중...</div>
      ) : operations.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--gray-400)' }}>
          등록된 오퍼레이션이 없습니다.
        </div>
      ) : (
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>이름</th>
                <th>오퍼레이션 ID</th>
                <th>테이블</th>
                <th>Datasource</th>
                <th>활성</th>
                <th>등록일</th>
                <th>작업</th>
              </tr>
            </thead>
            <tbody>
              {[...operations]
                .sort((a, b) => (a.operationName ?? '').localeCompare(b.operationName ?? '', 'ko'))
                .map(op => (
                <tr key={op.id} onClick={() => router.push(`/api-provide/${op.id}`)} style={{ cursor: 'pointer' }}>
                  <td>{op.operationName}</td>
                  <td><code style={{ fontSize: '0.8rem' }}>{op.operationId}</code></td>
                  <td><code style={{ fontSize: '0.8rem' }}>{op.tableName}</code></td>
                  <td>{op.datasourceId}</td>
                  <td>
                    <span
                      style={{
                        padding: '2px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
                        background: op.isPublished ? '#dcfce7' : '#f3f4f6',
                        color: op.isPublished ? '#166534' : '#6b7280',
                        cursor: 'pointer',
                      }}
                      onClick={(e) => handleTogglePublish(op.id, e)}
                    >
                      {op.isPublished ? '활성중' : '미활성'}
                    </span>
                  </td>
                  <td style={{ fontSize: '0.8rem', color: 'var(--gray-400)' }}>
                    {op.createdAt ? new Date(op.createdAt).toLocaleDateString() : '-'}
                  </td>
                  <td onClick={e => e.stopPropagation()}>
                    <button className="btn btn-danger btn-sm" onClick={(e) => handleDelete(op.id, e)}>삭제</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
