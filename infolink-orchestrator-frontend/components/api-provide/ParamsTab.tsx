'use client';

import { useEffect, useState } from 'react';
import { datasourceApi } from '@/lib/api';
import { paramApi } from '@/lib/providerApi';
import { ApiPrvOperation, ApiPrvOperationParam } from '@/types/api-provide';
import { ColumnSearchResult } from '@/types/index';
import styles from './ParamsTab.module.css';

const operators = [
  { value: 'EQ', label: '=' },
  { value: 'GT', label: '>' },
  { value: 'GTE', label: '>=' },
  { value: 'LT', label: '<' },
  { value: 'LTE', label: '<=' },
  { value: 'LIKE', label: '%포함%' },
  { value: 'LIKE_START', label: '시작%' },
  { value: 'LIKE_END', label: '%끝' },
  { value: 'IN', label: 'IN' },
  { value: 'BETWEEN', label: 'BETWEEN' },
];
const dataTypes = ['STRING', 'NUMBER', 'DATE'];

interface Props {
  operation: ApiPrvOperation;
  onUpdate: () => void;
}

function guessDataType(dbDataType: string): string {
  const upper = dbDataType.toUpperCase();
  if (upper.includes('INT') || upper.includes('NUM') || upper.includes('FLOAT') || upper.includes('DOUBLE') || upper.includes('DECIMAL'))
    return 'NUMBER';
  if (upper.includes('DATE') || upper.includes('TIME') || upper.includes('TIMESTAMP'))
    return 'DATE';
  return 'STRING';
}

export default function ParamsTab({ operation, onUpdate }: Props) {
  const [dbColumns, setDbColumns] = useState<ColumnSearchResult[]>([]);
  const [params, setParams] = useState<ApiPrvOperationParam[]>(operation.params || []);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    datasourceApi.searchColumns(operation.datasourceId, operation.tableName)
      .then(setDbColumns)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [operation.datasourceId, operation.tableName]);

  const addFromColumn = (colName: string) => {
    if (params.some(p => p.columnName === colName)) return;
    const col = dbColumns.find(c => c.columnName === colName);
    setParams([...params, {
      paramName: colName.toLowerCase(),
      columnName: colName,
      operator: 'EQ',
      isRequired: false,
      defaultValue: null,
      isHidden: false,
      dataType: col ? guessDataType(col.dataType) : 'STRING',
    }]);
  };

  const removeParam = (index: number) => {
    setParams(params.filter((_, i) => i !== index));
  };

  const updateParam = (index: number, field: string, value: any) => {
    const updated = [...params];
    (updated[index] as any)[field] = value;
    setParams(updated);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await paramApi.save(operation.id, params.map(p => ({
        paramName: p.paramName,
        columnName: p.columnName,
        operator: p.operator,
        isRequired: p.isRequired,
        defaultValue: p.defaultValue || undefined,
        dataType: p.dataType,
        isHidden: p.isHidden || undefined,
      })));
      alert('파라미터가 저장되었습니다.');
      onUpdate();
    } catch (e: any) {
      alert('저장 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="app-loading">컬럼 불러오는 중...</div>;

  const locked = operation.isLocked;

  if (locked) {
    return (
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">🔒 WHERE 파라미터 (읽기 전용 — {operation.params.length}개)</h2>
        </div>
        <div className={styles.lockNote}>시스템 내장 핸들러가 자동 등록한 메타입니다. 수정 불가.</div>
        {operation.params.length === 0 ? (
          <div className="app-empty">파라미터 없음</div>
        ) : (
          <div className={styles.readonlyTableWrap}>
            <table className="app-table">
              <thead>
                <tr>
                  <th>파라미터명</th>
                  <th>DB 컬럼</th>
                  <th>연산자</th>
                  <th>타입</th>
                  <th className={styles.readonlyCenter}>필수</th>
                  <th className={styles.readonlyCenter}>숨김</th>
                  <th>기본값</th>
                </tr>
              </thead>
              <tbody>
                {operation.params.map((p, i) => (
                  <tr key={p.id ?? i}>
                    <td className={styles.readonlyMono}>{p.paramName}</td>
                    <td className={styles.readonlyMono}>{p.columnName}</td>
                    <td>{p.operator}</td>
                    <td>{p.dataType}</td>
                    <td className={styles.readonlyCenter}>{p.isRequired ? '✓' : '-'}</td>
                    <td className={styles.readonlyCenter}>{p.isHidden ? '✓' : '-'}</td>
                    <td className={styles.readonlyMuted}>{p.defaultValue || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="app-card">
      <div className="app-card__header">
        <div>
          <h2 className="app-card__title">WHERE 파라미터</h2>
          <div className={styles.cardSubLine}>외부 호출 시 ?paramName=value 형식으로 전달</div>
        </div>
        <div className={styles.cardActions}>
          <button type="button" className="krds-btn small" onClick={handleSave} disabled={saving}>
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>

      {/* 컬럼 추가 드롭다운 */}
      <div className={styles.addColumnBar}>
        <select
          className={`krds-input small ${styles.addColumnSelect}`}
          onChange={e => { if (e.target.value) { addFromColumn(e.target.value); e.target.value = ''; } }}
        >
          <option value="">+ 조건 컬럼 추가</option>
          {dbColumns
            .filter(c => !params.some(p => p.columnName === c.columnName))
            .map(c => (
              <option key={c.columnName} value={c.columnName}>
                {c.columnName} ({c.dataType})
              </option>
            ))}
        </select>
      </div>

      {params.length === 0 ? (
        <div className="app-empty">파라미터 없음 — WHERE 조건 없이 전체 조회</div>
      ) : (
        <div className={styles.paramGrid}>
          <div className={styles.paramGridHeader}>
            <div>파라미터명</div>
            <div>DB 컬럼</div>
            <div>연산자</div>
            <div className={styles.headerCenter}>필수</div>
            <div className={styles.headerCenter}>숨김</div>
            <div>타입</div>
            <div>기본값</div>
            <div></div>
          </div>
          {params.map((p, i) => (
            <div key={i} className={styles.paramGridRow}>
              <input
                className={`krds-input small ${p.isHidden ? styles.inputDimmed : ''}`}
                value={p.paramName}
                disabled={p.isHidden}
                onChange={e => updateParam(i, 'paramName', e.target.value)}
              />
              <span className={styles.paramColName}>{p.columnName}</span>
              <select
                className="krds-input small"
                value={p.operator}
                onChange={e => updateParam(i, 'operator', e.target.value)}
              >
                {operators.map(op => <option key={op.value} value={op.value}>{op.label}</option>)}
              </select>
              <div className={styles.checkCell}>
                <div className="krds-form-check medium">
                  <input
                    type="checkbox"
                    id={`param-req-${i}`}
                    checked={p.isRequired}
                    disabled={p.isHidden}
                    onChange={e => updateParam(i, 'isRequired', e.target.checked)}
                  />
                  <label htmlFor={`param-req-${i}`} aria-label="필수"></label>
                </div>
              </div>
              <div className={styles.checkCell}>
                <div className="krds-form-check medium">
                  <input
                    type="checkbox"
                    id={`param-hidden-${i}`}
                    checked={p.isHidden}
                    onChange={e => updateParam(i, 'isHidden', e.target.checked)}
                  />
                  <label htmlFor={`param-hidden-${i}`} aria-label="숨김"></label>
                </div>
              </div>
              <select
                className="krds-input small"
                value={p.dataType}
                onChange={e => updateParam(i, 'dataType', e.target.value)}
              >
                {dataTypes.map(dt => <option key={dt} value={dt}>{dt}</option>)}
              </select>
              <input
                className="krds-input small"
                placeholder={p.isHidden ? '고정값 (필수)' : '기본값'}
                value={p.defaultValue || ''}
                onChange={e => updateParam(i, 'defaultValue', e.target.value || null)}
              />
              <button
                type="button"
                className="krds-btn small app-btn-danger"
                onClick={() => removeParam(i)}
              >
                X
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
