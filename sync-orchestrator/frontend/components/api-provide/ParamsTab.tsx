'use client';

import { useEffect, useState } from 'react';
import { datasourceApi } from '@/lib/api';
import { paramApi } from '@/lib/providerApi';
import { ApiPrvOperation, ApiPrvOperationParam } from '@/types/api-provide';
import { ColumnSearchResult } from '@/types/index';

const fieldLabel: React.CSSProperties = { fontSize: '0.8rem', color: 'var(--gray-500)', marginBottom: '0.25rem', fontWeight: 500 };
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

  if (loading) return <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--gray-400)' }}>컬럼 불러오는 중...</div>;

  return (
    <div className="card">
      <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>WHERE 파라미터</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)' }}>
            외부 호출 시 ?paramName=value 형식으로 전달
          </div>
        </div>
        <button className="btn btn-primary btn-sm" onClick={handleSave} disabled={saving}>
          {saving ? '저장 중...' : '저장'}
        </button>
      </div>

      {/* 컬럼 추가 드롭다운 */}
      <div style={{ padding: '0.5rem 1rem', borderBottom: '1px solid var(--gray-100)' }}>
        <select className="form-input" style={{ width: '280px' }}
          onChange={e => { if (e.target.value) { addFromColumn(e.target.value); e.target.value = ''; } }}>
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
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--gray-400)', fontSize: '0.85rem' }}>
          파라미터 없음 — WHERE 조건 없이 전체 조회
        </div>
      ) : (
        <div style={{ padding: '0.5rem 1rem', overflowX: 'auto' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 140px 50px 50px 110px 1fr 40px', gap: '0.5rem', marginBottom: '0.5rem', minWidth: '900px', alignItems: 'center' }}>
            <div style={fieldLabel}>파라미터명</div>
            <div style={fieldLabel}>DB 컬럼</div>
            <div style={fieldLabel}>연산자</div>
            <div style={{ ...fieldLabel, textAlign: 'center' }}>필수</div>
            <div style={{ ...fieldLabel, textAlign: 'center' }}>숨김</div>
            <div style={fieldLabel}>타입</div>
            <div style={fieldLabel}>기본값</div>
            <div></div>
          </div>
          {params.map((p, i) => (
            <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 140px 50px 50px 110px 1fr 40px', gap: '0.5rem', marginBottom: '0.25rem', minWidth: '900px', alignItems: 'center' }}>
              <input className="form-input" value={p.paramName} disabled={p.isHidden}
                onChange={e => updateParam(i, 'paramName', e.target.value)}
                style={{ opacity: p.isHidden ? 0.4 : 1 }} />
              <div style={{ fontSize: '0.8rem', fontFamily: 'monospace' }}>{p.columnName}</div>
              <select className="form-input" value={p.operator}
                onChange={e => updateParam(i, 'operator', e.target.value)}>
                {operators.map(op => <option key={op.value} value={op.value}>{op.label}</option>)}
              </select>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <input type="checkbox" checked={p.isRequired} disabled={p.isHidden}
                  onChange={e => updateParam(i, 'isRequired', e.target.checked)} />
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <input type="checkbox" checked={p.isHidden}
                  onChange={e => updateParam(i, 'isHidden', e.target.checked)} />
              </div>
              <select className="form-input" value={p.dataType}
                onChange={e => updateParam(i, 'dataType', e.target.value)}>
                {dataTypes.map(dt => <option key={dt} value={dt}>{dt}</option>)}
              </select>
              <input className="form-input" placeholder={p.isHidden ? '고정값 (필수)' : '기본값'} value={p.defaultValue || ''}
                onChange={e => updateParam(i, 'defaultValue', e.target.value || null)} />
              <button className="btn btn-danger btn-sm" onClick={() => removeParam(i)}>X</button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
