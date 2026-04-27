'use client';

import { useState } from 'react';
import { operationApi } from '@/lib/providerApi';
import { ApiPrvOperation } from '@/types/api-provide';

const fieldLabel: React.CSSProperties = { fontSize: '0.8rem', color: 'var(--gray-500)', marginBottom: '0.25rem', fontWeight: 500 };
const sectionStyle = { padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)' };

interface Props {
  operation: ApiPrvOperation;
  onUpdate: () => void;
}

export default function InfoTab({ operation, onUpdate }: Props) {
  const [editing, setEditing] = useState(false);
  const locked = operation.isLocked;

  const [form, setForm] = useState({
    operationId: operation.operationId,
    operationName: operation.operationName,
    description: operation.description || '',
    datasourceId: operation.datasourceId,
    tableName: operation.tableName,
    responseFormat: operation.responseFormat,
    pageSize: operation.pageSize,
    maxPageSize: operation.maxPageSize,
    orderByColumn: operation.orderByColumn || '',
    orderByDirection: operation.orderByDirection,
  });

  const handleSave = async () => {
    try {
      await operationApi.update(operation.id, {
        ...form,
        orderByColumn: form.orderByColumn || null,
      } as any);
      alert('저장되었습니다.');
      setEditing(false);
      onUpdate();
    } catch (e: any) {
      alert('수정 실패: ' + (e.response?.data?.message || e.message));
    }
  };

  if (!editing) {
    return (
      <div className="card">
        <div style={sectionStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>
              기본 정보 {locked && <span style={{ marginLeft: '0.5rem', color: 'var(--gray-500)', fontWeight: 400 }}>🔒 잠금 (오퍼레이션 ID/이름만 수정 가능)</span>}
            </div>
            <button className="btn btn-sm" onClick={() => setEditing(true)}>수정</button>
          </div>
        </div>
        <div style={sectionStyle}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <div><div style={fieldLabel}>오퍼레이션 ID</div><div><code>{operation.operationId}</code></div></div>
            <div><div style={fieldLabel}>이름</div><div>{operation.operationName}</div></div>
            <div><div style={fieldLabel}>Datasource ID</div><div>{operation.datasourceId}</div></div>
            <div><div style={fieldLabel}>테이블</div><div><code>{operation.tableName}</code></div></div>
            <div><div style={fieldLabel}>응답 포맷</div><div>{operation.responseFormat}</div></div>
            <div><div style={fieldLabel}>페이지 크기</div><div>{operation.pageSize} (최대 {operation.maxPageSize})</div></div>
            <div><div style={fieldLabel}>정렬</div><div>{operation.orderByColumn ? `${operation.orderByColumn} ${operation.orderByDirection}` : '없음'}</div></div>
            <div><div style={fieldLabel}>등록일</div><div>{operation.createdAt ? new Date(operation.createdAt).toLocaleString() : '-'}</div></div>
          </div>
        </div>
        {operation.description && (
          <div style={sectionStyle}>
            <div style={fieldLabel}>설명</div>
            <div style={{ fontSize: '0.85rem' }}>{operation.description}</div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="card">
      <div style={sectionStyle}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>기본 정보 수정</div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button className="btn btn-sm" onClick={() => setEditing(false)}>취소</button>
            <button className="btn btn-primary btn-sm" onClick={handleSave}>저장</button>
          </div>
        </div>
      </div>
      <div style={sectionStyle}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
          <div><div style={fieldLabel}>오퍼레이션 ID *</div>
            <input className="form-input" value={form.operationId}
              onChange={e => setForm({ ...form, operationId: e.target.value })} />
            <div style={{ fontSize: '0.7rem', color: 'var(--gray-400)', marginTop: '0.15rem' }}>
              URL: /api/provide/{form.operationId || '{id}'}
            </div>
          </div>
          <div><div style={fieldLabel}>이름 *</div>
            <input className="form-input" value={form.operationName}
              onChange={e => setForm({ ...form, operationName: e.target.value })} /></div>
          <div><div style={fieldLabel}>Datasource ID</div>
            <input className="form-input" value={form.datasourceId} disabled={locked}
              onChange={e => setForm({ ...form, datasourceId: e.target.value })} /></div>
          <div><div style={fieldLabel}>테이블명</div>
            <input className="form-input" value={form.tableName} disabled={locked}
              onChange={e => setForm({ ...form, tableName: e.target.value })} /></div>
          <div><div style={fieldLabel}>응답 포맷</div>
            <select className="form-input" value={form.responseFormat} disabled={locked}
              onChange={e => setForm({ ...form, responseFormat: e.target.value })}>
              <option value="JSON">JSON</option><option value="XML">XML</option>
            </select></div>
          <div><div style={fieldLabel}>기본 페이지 크기</div>
            <input className="form-input" type="number" value={form.pageSize} disabled={locked}
              onChange={e => setForm({ ...form, pageSize: parseInt(e.target.value) || 100 })} /></div>
          <div><div style={fieldLabel}>최대 페이지 크기</div>
            <input className="form-input" type="number" value={form.maxPageSize} disabled={locked}
              onChange={e => setForm({ ...form, maxPageSize: parseInt(e.target.value) || 1000 })} /></div>
          <div><div style={fieldLabel}>정렬 컬럼</div>
            <input className="form-input" value={form.orderByColumn} disabled={locked}
              onChange={e => setForm({ ...form, orderByColumn: e.target.value })} /></div>
          <div><div style={fieldLabel}>정렬 방향</div>
            <select className="form-input" value={form.orderByDirection} disabled={locked}
              onChange={e => setForm({ ...form, orderByDirection: e.target.value })}>
              <option value="ASC">ASC</option><option value="DESC">DESC</option>
            </select></div>
        </div>
        {locked && (
          <div style={{ marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--gray-500)' }}>
            🔒 시스템 내장 핸들러 — 오퍼레이션 ID/이름만 수정 가능. 나머지는 코드에 박혀있어 변경 불가.
          </div>
        )}
      </div>
      <div style={sectionStyle}>
        <div style={fieldLabel}>설명</div>
        <textarea className="form-input" rows={3} value={form.description} disabled={locked}
          onChange={e => setForm({ ...form, description: e.target.value })} />
      </div>
    </div>
  );
}
