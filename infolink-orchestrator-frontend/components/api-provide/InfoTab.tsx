'use client';

import { useState } from 'react';
import { operationApi } from '@/lib/providerApi';
import { ApiPrvOperation } from '@/types/api-provide';
import styles from './InfoTab.module.css';

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
      <div className="app-card">
        <div className="app-card__header">
          <h2 className={`app-card__title ${styles.cardHeaderTitle}`}>
            기본 정보
            {locked && <span className={styles.lockNote}>🔒 잠금 (오퍼레이션 ID/이름만 수정 가능)</span>}
          </h2>
          <button type="button" className="krds-btn small secondary" onClick={() => setEditing(true)}>수정</button>
        </div>
        <div className={styles.infoGrid}>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>오퍼레이션 ID</div>
            <div className={styles.infoValueCode}>{operation.operationId}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>이름</div>
            <div className={styles.infoValue}>{operation.operationName}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>Datasource ID</div>
            <div className={styles.infoValueCode}>{operation.datasourceId}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>테이블</div>
            <div className={styles.infoValueCode}>{operation.tableName}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>응답 포맷</div>
            <div className={styles.infoValue}>{operation.responseFormat}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>페이지 크기</div>
            <div className={styles.infoValue}>{operation.pageSize} (최대 {operation.maxPageSize})</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>정렬</div>
            <div className={styles.infoValue}>{operation.orderByColumn ? `${operation.orderByColumn} ${operation.orderByDirection}` : '없음'}</div>
          </div>
          <div className={styles.infoCell}>
            <div className={styles.infoLabel}>등록일</div>
            <div className={styles.infoValue}>{operation.createdAt ? new Date(operation.createdAt).toLocaleString() : '-'}</div>
          </div>
        </div>
        {operation.description && (
          <div className={styles.descSection}>
            <div className={styles.infoLabel}>설명</div>
            <div className={styles.descText}>{operation.description}</div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="app-card">
      <div className="app-card__header">
        <h2 className="app-card__title">기본 정보 수정</h2>
        <div className={styles.cardActions}>
          <button type="button" className="krds-btn small secondary" onClick={() => setEditing(false)}>취소</button>
          <button type="button" className="krds-btn small" onClick={handleSave}>저장</button>
        </div>
      </div>
      <div className="app-form-grid">
        <div className="app-form-field">
          <label className="app-form-label">오퍼레이션 ID *</label>
          <input
            className="krds-input small"
            value={form.operationId}
            onChange={e => setForm({ ...form, operationId: e.target.value })}
          />
          <div className={styles.urlPreview}>URL: /api/provide/{form.operationId || '{id}'}</div>
        </div>
        <div className="app-form-field">
          <label className="app-form-label">이름 *</label>
          <input
            className="krds-input small"
            value={form.operationName}
            onChange={e => setForm({ ...form, operationName: e.target.value })}
          />
        </div>
        <div className="app-form-field">
          <label className="app-form-label">Datasource ID</label>
          <input
            className="krds-input small"
            value={form.datasourceId}
            disabled={locked}
            onChange={e => setForm({ ...form, datasourceId: e.target.value })}
          />
        </div>
        <div className="app-form-field">
          <label className="app-form-label">테이블명</label>
          <input
            className="krds-input small"
            value={form.tableName}
            disabled={locked}
            onChange={e => setForm({ ...form, tableName: e.target.value })}
          />
        </div>
        <div className="app-form-field">
          <label className="app-form-label">응답 포맷</label>
          <select
            className="krds-input small"
            value={form.responseFormat}
            disabled={locked}
            onChange={e => setForm({ ...form, responseFormat: e.target.value })}
          >
            <option value="JSON">JSON</option>
            <option value="XML">XML</option>
          </select>
        </div>
        <div className="app-form-field">
          <label className="app-form-label">기본 페이지 크기</label>
          <input
            className="krds-input small"
            type="number"
            value={form.pageSize}
            disabled={locked}
            onChange={e => setForm({ ...form, pageSize: parseInt(e.target.value) || 100 })}
          />
        </div>
        <div className="app-form-field">
          <label className="app-form-label">최대 페이지 크기</label>
          <input
            className="krds-input small"
            type="number"
            value={form.maxPageSize}
            disabled={locked}
            onChange={e => setForm({ ...form, maxPageSize: parseInt(e.target.value) || 1000 })}
          />
        </div>
        <div className="app-form-field">
          <label className="app-form-label">정렬 컬럼</label>
          <input
            className="krds-input small"
            value={form.orderByColumn}
            disabled={locked}
            onChange={e => setForm({ ...form, orderByColumn: e.target.value })}
          />
        </div>
        <div className="app-form-field">
          <label className="app-form-label">정렬 방향</label>
          <select
            className="krds-input small"
            value={form.orderByDirection}
            disabled={locked}
            onChange={e => setForm({ ...form, orderByDirection: e.target.value })}
          >
            <option value="ASC">ASC</option>
            <option value="DESC">DESC</option>
          </select>
        </div>
      </div>
      {locked && (
        <div className={styles.lockedHint}>
          🔒 시스템 내장 핸들러 — 오퍼레이션 ID/이름만 수정 가능. 나머지는 코드에 박혀있어 변경 불가.
        </div>
      )}
      <div className={styles.descSection}>
        <div className="app-form-field">
          <label className="app-form-label">설명</label>
          <textarea
            className={`krds-input small ${styles.descTextarea}`}
            rows={3}
            value={form.description}
            disabled={locked}
            onChange={e => setForm({ ...form, description: e.target.value })}
          />
        </div>
      </div>
    </div>
  );
}
