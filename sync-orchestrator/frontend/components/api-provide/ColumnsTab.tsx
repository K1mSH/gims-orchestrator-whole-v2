'use client';

import { useEffect, useState } from 'react';
import { datasourceApi } from '@/lib/api';
import { columnApi } from '@/lib/providerApi';
import { ApiPrvOperation } from '@/types/api-provide';
import { ColumnSearchResult } from '@/types/index';

const fieldLabel: React.CSSProperties = { fontSize: '0.8rem', color: 'var(--gray-500)', marginBottom: '0.25rem', fontWeight: 500 };

type ColConfig = { alias: string; transformType: string; transformParam: string };

interface Props {
  operation: ApiPrvOperation;
  onUpdate: () => void;
}

export default function ColumnsTab({ operation, onUpdate }: Props) {
  const [dbColumns, setDbColumns] = useState<ColumnSearchResult[]>([]);
  const [selectedColumns, setSelectedColumns] = useState<Map<string, ColConfig>>(new Map());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const defaultCfg = (): ColConfig => ({ alias: '', transformType: 'NONE', transformParam: '' });

  useEffect(() => {
    const load = async () => {
      try {
        const cols = await datasourceApi.searchColumns(operation.datasourceId, operation.tableName);
        setDbColumns(cols);

        if (operation.columns.length > 0) {
          const map = new Map<string, ColConfig>();
          operation.columns.forEach(c => map.set(c.columnName, {
            alias: c.aliasName || '',
            transformType: c.transformType || 'NONE',
            transformParam: c.transformParam || '',
          }));
          setSelectedColumns(map);
        } else {
          setSelectedColumns(new Map(cols.map(c => [c.columnName, defaultCfg()])));
        }
      } catch (e) {
        console.error('컬럼 조회 실패:', e);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [operation.datasourceId, operation.tableName]);

  const toggleColumn = (colName: string) => {
    setSelectedColumns(prev => {
      const next = new Map(prev);
      next.has(colName) ? next.delete(colName) : next.set(colName, defaultCfg());
      return next;
    });
  };

  const updateCfg = (colName: string, field: keyof ColConfig, value: string) => {
    setSelectedColumns(prev => {
      const next = new Map(prev);
      const cur = next.get(colName) || defaultCfg();
      next.set(colName, { ...cur, [field]: value });
      return next;
    });
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const allSelected = selectedColumns.size === dbColumns.length;
      const hasCustom = Array.from(selectedColumns.values()).some(c => c.alias.trim() || c.transformType !== 'NONE');

      if (allSelected && !hasCustom) {
        await columnApi.save(operation.id, []);
      } else {
        const cols = Array.from(selectedColumns.entries()).map(([colName, cfg], i) => ({
          columnName: colName,
          aliasName: cfg.alias.trim() || undefined,
          displayOrder: i,
          transformType: cfg.transformType !== 'NONE' ? cfg.transformType : undefined,
          transformParam: cfg.transformParam.trim() || undefined,
        }));
        await columnApi.save(operation.id, cols);
      }
      alert('컬럼 설정이 저장되었습니다.');
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
          <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>SELECT 컬럼 ({selectedColumns.size}/{dbColumns.length})</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)' }}>
            {selectedColumns.size === dbColumns.length && !Array.from(selectedColumns.values()).some(c => c.alias.trim() || c.transformType !== 'NONE')
              ? 'SELECT * (전체 컬럼)'
              : `${selectedColumns.size}개 컬럼 선택됨`}
          </div>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button className="btn btn-sm" onClick={() => setSelectedColumns(new Map(dbColumns.map(c => [c.columnName, defaultCfg()])))}>전체 선택</button>
          <button className="btn btn-sm" onClick={() => setSelectedColumns(new Map())}>전체 해제</button>
          <button className="btn btn-primary btn-sm" onClick={handleSave} disabled={saving}>
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>

      {dbColumns.length === 0 ? (
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--gray-400)' }}>
          DB에서 컬럼을 조회할 수 없습니다. Datasource 연결을 확인하세요.
        </div>
      ) : (
        <div style={{ padding: '0.75rem 1rem', overflowX: 'auto' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '30px 1fr 80px 1fr 160px 140px', gap: '0.5rem', marginBottom: '0.5rem', minWidth: '700px' }}>
            <div></div>
            <div style={fieldLabel}>컬럼명</div>
            <div style={fieldLabel}>타입</div>
            <div style={fieldLabel}>응답 필드명</div>
            <div style={fieldLabel}>가공</div>
            <div style={fieldLabel}>가공 파라미터</div>
          </div>
          {dbColumns.map(col => {
            const cfg = selectedColumns.get(col.columnName);
            const checked = !!cfg;
            return (
              <div key={col.columnName} style={{ display: 'grid', gridTemplateColumns: '30px 1fr 80px 1fr 160px 140px', gap: '0.5rem', marginBottom: '0.15rem', alignItems: 'center', minWidth: '700px' }}>
                <input type="checkbox" checked={checked} onChange={() => toggleColumn(col.columnName)} />
                <span style={{ fontSize: '0.8rem', fontWeight: checked ? 600 : 400 }}>
                  <span style={{ fontFamily: 'monospace' }}>{col.columnName}</span>
                  {col.isPrimaryKey ? <span style={{ color: '#2563eb', fontSize: '0.7rem' }}> [PK]</span> : ''}
                  {!col.isNullable ? <span style={{ color: '#dc2626', fontSize: '0.7rem' }}> *</span> : ''}
                  {col.remarks ? <span style={{ color: 'var(--gray-400)', fontSize: '0.7rem' }}> {col.remarks}</span> : ''}
                </span>
                <span style={{ color: 'var(--gray-400)', fontSize: '0.75rem' }}>{col.dataType}</span>
                <input className="form-input" placeholder={col.columnName} disabled={!checked}
                  value={cfg?.alias || ''}
                  onChange={e => updateCfg(col.columnName, 'alias', e.target.value)}
                  style={{ fontSize: '0.8rem', opacity: checked ? 1 : 0.3 }} />
                <select className="form-input" disabled={!checked}
                  value={cfg?.transformType || 'NONE'}
                  onChange={e => updateCfg(col.columnName, 'transformType', e.target.value)}
                  style={{ fontSize: '0.75rem', opacity: checked ? 1 : 0.3 }}>
                  <option value="NONE">없음</option>
                  <option value="ROUND">소수점 (N자리 반올림)</option>
                  <option value="DATE_FORMAT">날짜형식 (YYYY-MM-DD 등)</option>
                  <option value="COALESCE">NULL대체 (기본값 지정)</option>
                  <option value="SUBSTRING">문자절삭 (앞 N자)</option>
                </select>
                <input className="form-input" disabled={!checked || cfg?.transformType === 'NONE'}
                  placeholder={cfg?.transformType === 'ROUND' ? '예: 2 (소수점 2자리)' : cfg?.transformType === 'DATE_FORMAT' ? '예: YYYY-MM-DD' : cfg?.transformType === 'COALESCE' ? '예: 0 또는 없음' : cfg?.transformType === 'SUBSTRING' ? '예: 50 (앞 50자)' : ''}
                  value={cfg?.transformParam || ''}
                  onChange={e => updateCfg(col.columnName, 'transformParam', e.target.value)}
                  style={{ fontSize: '0.8rem', opacity: checked && cfg?.transformType !== 'NONE' ? 1 : 0.3 }} />
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
