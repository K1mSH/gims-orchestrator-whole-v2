'use client';

import { useEffect, useState } from 'react';
import { datasourceApi } from '@/lib/api';
import { columnApi } from '@/lib/providerApi';
import { ApiPrvOperation } from '@/types/api-provide';
import { ColumnSearchResult } from '@/types/index';
import styles from './ColumnsTab.module.css';

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

  // 기본 설정 생성 — alias 는 기본적으로 컬럼명 대문자 (레거시 호환 관례)
  // 수동 편집 가능하며, 빈 문자열로 저장하면 엔진이 원본 컬럼명 그대로 사용
  const defaultCfg = (alias: string = ''): ColConfig => ({ alias, transformType: 'NONE', transformParam: '' });

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
          setSelectedColumns(new Map(cols.map(c => [c.columnName, defaultCfg(c.columnName.toUpperCase())])));
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
      next.has(colName) ? next.delete(colName) : next.set(colName, defaultCfg(colName.toUpperCase()));
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

  if (loading) return <div className="app-loading">컬럼 불러오는 중...</div>;

  const locked = operation.isLocked;

  if (locked) {
    return (
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">🔒 컬럼 설정 (읽기 전용 — {operation.columns.length}개)</h2>
        </div>
        <div className={styles.lockNote}>시스템 내장 핸들러가 자동 등록한 메타입니다. 수정 불가.</div>
        <div className={styles.readonlyTableWrap}>
          <table className="app-table">
            <thead>
              <tr>
                <th>컬럼명</th>
                <th>응답 필드명</th>
                <th>가공</th>
              </tr>
            </thead>
            <tbody>
              {operation.columns.map(c => (
                <tr key={c.id ?? c.columnName}>
                  <td className={styles.readonlyCol}>{c.columnName}</td>
                  <td className={styles.readonlyAlias}>
                    {c.aliasName || <span className={styles.readonlyAliasMuted}>(원본)</span>}
                  </td>
                  <td className={styles.readonlyTransform}>
                    {c.transformType === 'NONE' ? '-' : `${c.transformType}${c.transformParam ? ` (${c.transformParam})` : ''}`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  const allSelectedNoCustom =
    selectedColumns.size === dbColumns.length &&
    !Array.from(selectedColumns.values()).some(c => c.alias.trim() || c.transformType !== 'NONE');

  return (
    <div className="app-card">
      <div className="app-card__header">
        <div>
          <h2 className="app-card__title">SELECT 컬럼 ({selectedColumns.size}/{dbColumns.length})</h2>
          <div className={styles.cardSubLine}>
            {allSelectedNoCustom ? 'SELECT * (전체 컬럼)' : `${selectedColumns.size}개 컬럼 선택됨`}
          </div>
        </div>
        <div className={styles.cardActions}>
          <button
            type="button"
            className="krds-btn small"
            onClick={handleSave}
            disabled={saving}
          >
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>

      {dbColumns.length === 0 ? (
        <div className="app-empty">DB에서 컬럼을 조회할 수 없습니다. Datasource 연결을 확인하세요.</div>
      ) : (
        <>
        <div className={styles.toolbar}>
          <button
            type="button"
            className="krds-btn small secondary"
            onClick={() => setSelectedColumns(new Map(dbColumns.map(c => [c.columnName, defaultCfg(c.columnName.toUpperCase())])))}
          >
            전체 선택
          </button>
          <button
            type="button"
            className="krds-btn small secondary"
            onClick={() => setSelectedColumns(new Map())}
          >
            전체 해제
          </button>
          <button
            type="button"
            className="krds-btn small secondary"
            onClick={() => setSelectedColumns(prev => {
              const next = new Map<string, ColConfig>();
              prev.forEach((cfg, colName) => next.set(colName, { ...cfg, alias: colName.toUpperCase() }));
              return next;
            })}
            title="선택된 컬럼의 응답필드명을 일괄 대문자로 채웁니다"
          >
            응답필드명 자동(대문자)
          </button>
        </div>
        <div className={styles.colGrid}>
          <div className={styles.colGridHeader}>
            <div>사용</div>
            <div>컬럼명</div>
            <div>타입</div>
            <div>응답 필드명</div>
            <div>가공</div>
            <div>가공 파라미터</div>
          </div>
          {dbColumns.map(col => {
            const cfg = selectedColumns.get(col.columnName);
            const checked = !!cfg;
            return (
              <div key={col.columnName} className={styles.colGridRow}>
                <div className="krds-form-check medium">
                  <input
                    type="checkbox"
                    id={`col-chk-${col.columnName}`}
                    checked={checked}
                    onChange={() => toggleColumn(col.columnName)}
                  />
                  <label htmlFor={`col-chk-${col.columnName}`} aria-label={`${col.columnName} 사용`}></label>
                </div>
                <div className={`${styles.colName} ${checked ? styles.colNameChecked : ''}`}>
                  <div className={styles.colNameTop}>
                    <span className={styles.colNameText}>{col.columnName}</span>
                    {col.isPrimaryKey && <span className={styles.colNamePk}>PK</span>}
                  </div>
                  {col.remarks && <div className={styles.colNameRemarks}>{col.remarks}</div>}
                </div>
                <span className={styles.colMeta}>{col.dataType}</span>
                <input
                  className={`krds-input small ${checked ? '' : styles.inputDimmed}`}
                  placeholder={col.columnName}
                  disabled={!checked}
                  value={cfg?.alias || ''}
                  onChange={e => updateCfg(col.columnName, 'alias', e.target.value)}
                />
                <select
                  className={`krds-input small ${checked ? '' : styles.inputDimmed}`}
                  disabled={!checked}
                  value={cfg?.transformType || 'NONE'}
                  onChange={e => updateCfg(col.columnName, 'transformType', e.target.value)}
                >
                  <option value="NONE">없음</option>
                  <option value="ROUND">소수점 (N자리 반올림)</option>
                  <option value="DATE_FORMAT">날짜형식 (YYYY-MM-DD 등)</option>
                  <option value="COALESCE">NULL대체 (기본값 지정)</option>
                  <option value="SUBSTRING">문자절삭 (앞 N자)</option>
                </select>
                <input
                  className={`krds-input small ${checked && cfg?.transformType !== 'NONE' ? '' : styles.inputDimmed}`}
                  disabled={!checked || cfg?.transformType === 'NONE'}
                  placeholder={
                    cfg?.transformType === 'ROUND' ? '예: 2 (소수점 2자리)' :
                    cfg?.transformType === 'DATE_FORMAT' ? '예: YYYY-MM-DD' :
                    cfg?.transformType === 'COALESCE' ? '예: 0 또는 없음' :
                    cfg?.transformType === 'SUBSTRING' ? '예: 50 (앞 50자)' : ''
                  }
                  value={cfg?.transformParam || ''}
                  onChange={e => updateCfg(col.columnName, 'transformParam', e.target.value)}
                />
              </div>
            );
          })}
        </div>
        </>
      )}
    </div>
  );
}
