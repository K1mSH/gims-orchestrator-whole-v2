'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { endpointApi } from '@/lib/collectorApi';
import { ApiEndpointListItem, ApiEndpointCreateRequest, AuthType, CollectorZone } from '@/types/api-collect';

export default function ApiCollectPage() {
  const router = useRouter();
  const [endpoints, setEndpoints] = useState<ApiEndpointListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [form, setForm] = useState<ApiEndpointCreateRequest>({
    apiName: '',
    apiCode: '',
    url: '',
    httpMethod: 'GET',
    authType: 'NONE',
    zone: 'DMZ',
  });

  const fetchEndpoints = useCallback(async () => {
    try {
      setLoading(true);
      const data = await endpointApi.getAll();
      setEndpoints(data);
    } catch (e) {
      console.error('목록 조회 실패:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchEndpoints();
  }, [fetchEndpoints]);

  const handleCreate = async () => {
    if (!form.apiName || !form.apiCode || !form.url) {
      alert('API명, 코드, URL은 필수입니다.');
      return;
    }
    try {
      const created = await endpointApi.create(form);
      router.push(`/api-collect/${created.id}`);
    } catch (e: any) {
      alert('등록 실패: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleDelete = async (id: number, name: string) => {
    if (!confirm(`"${name}" API를 삭제하시겠습니까?`)) return;
    try {
      await endpointApi.delete(id);
      fetchEndpoints();
    } catch (e: any) {
      alert('삭제 실패: ' + (e.response?.data?.message || e.message));
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>API 수집 관리</h1>
        <button className="btn btn-primary" onClick={() => setShowCreateForm(!showCreateForm)}>
          {showCreateForm ? '취소' : '+ API 등록'}
        </button>
      </div>

      {showCreateForm && (
        <div className="card" style={{ marginBottom: '1.5rem' }}>
          <div className="card-header"><h3 className="card-title">새 API 등록</h3></div>
          <div style={{ padding: '1rem', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">API명 *</label>
              <input className="form-input" value={form.apiName}
                onChange={e => setForm({ ...form, apiName: e.target.value })}
                placeholder="예: 지하수 수위 관측" />
            </div>
            <div className="form-group">
              <label className="form-label">API 코드 *</label>
              <input className="form-input" value={form.apiCode}
                onChange={e => setForm({ ...form, apiCode: e.target.value })}
                placeholder="예: gw-water-level (고유)" />
            </div>
            <div className="form-group" style={{ gridColumn: '1 / -1' }}>
              <label className="form-label">URL *</label>
              <input className="form-input" value={form.url}
                onChange={e => setForm({ ...form, url: e.target.value })}
                placeholder="https://apis.data.go.kr/..." />
            </div>
            <div className="form-group">
              <label className="form-label">HTTP Method</label>
              <select className="form-select" value={form.httpMethod}
                onChange={e => setForm({ ...form, httpMethod: e.target.value })}>
                <option value="GET">GET</option>
                <option value="POST">POST</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">인증 유형</label>
              <select className="form-select" value={form.authType}
                onChange={e => setForm({ ...form, authType: e.target.value as AuthType })}>
                <option value="NONE">없음</option>
                <option value="BASIC">Basic Auth</option>
                <option value="BEARER">Bearer Token</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Zone</label>
              <select className="form-select" value={form.zone}
                onChange={e => setForm({ ...form, zone: e.target.value as CollectorZone })}>
                <option value="DMZ">DMZ</option>
                <option value="INTERNAL">INTERNAL</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">설명</label>
              <input className="form-input" value={form.description || ''}
                onChange={e => setForm({ ...form, description: e.target.value })}
                placeholder="API 설명 (선택)" />
            </div>
            <div style={{ gridColumn: '1 / -1', textAlign: 'right' }}>
              <button className="btn btn-primary" onClick={handleCreate}>등록</button>
            </div>
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-header"><h3 className="card-title">API 목록</h3></div>
        {loading ? (
          <div className="loading" style={{ padding: '2rem', textAlign: 'center' }}>로딩 중...</div>
        ) : endpoints.length === 0 ? (
          <div className="empty-state" style={{ padding: '3rem', textAlign: 'center', color: 'var(--gray-500)' }}>
            등록된 API가 없습니다.
          </div>
        ) : (
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>API명</th>
                  <th>코드</th>
                  <th>Method</th>
                  <th>인증</th>
                  <th>적재 테이블</th>
                  <th>매핑</th>
                  <th>상태</th>
                  <th>Zone</th>
                  <th>작업</th>
                </tr>
              </thead>
              <tbody>
                {endpoints.map(ep => (
                  <tr key={ep.id} style={{ cursor: 'pointer' }}
                    onClick={() => router.push(`/api-collect/${ep.id}`)}>
                    <td style={{ fontWeight: 600 }}>{ep.apiName}</td>
                    <td><code style={{ fontSize: '0.8rem', background: 'var(--gray-100)', padding: '2px 6px', borderRadius: '4px' }}>{ep.apiCode}</code></td>
                    <td>
                      <span style={{
                        padding: '2px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
                        background: ep.httpMethod === 'GET' ? '#dbeafe' : '#fef3c7',
                        color: ep.httpMethod === 'GET' ? '#1d4ed8' : '#92400e'
                      }}>
                        {ep.httpMethod}
                      </span>
                    </td>
                    <td>{ep.authType === 'NONE' ? '-' : ep.authType}</td>
                    <td>{ep.targetTableName || <span style={{ color: 'var(--gray-400)' }}>미설정</span>}</td>
                    <td>{ep.hasMappings ?
                      <span style={{ color: 'var(--success)' }}>완료</span> :
                      <span style={{ color: 'var(--gray-400)' }}>미설정</span>
                    }</td>
                    <td>
                      <span style={{
                        display: 'inline-block', width: '8px', height: '8px', borderRadius: '50%',
                        background: ep.isActive ? 'var(--success)' : 'var(--gray-400)',
                        marginRight: '6px'
                      }} />
                      {ep.isActive ? '활성' : '비활성'}
                    </td>
                    <td>{ep.zone}</td>
                    <td onClick={e => e.stopPropagation()}>
                      <button className="btn btn-danger btn-sm"
                        onClick={() => handleDelete(ep.id, ep.apiName)}>삭제</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
