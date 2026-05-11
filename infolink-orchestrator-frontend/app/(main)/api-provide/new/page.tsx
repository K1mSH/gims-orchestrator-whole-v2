'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { datasourceApi } from '@/lib/api';
import { operationApi, columnApi, paramApi, customHandlerApi, CustomHandlerCatalogEntry } from '@/lib/providerApi';
import { DatasourceSimple, DatasourceTable, ColumnSearchResult } from '@/types/index';
import styles from './new.module.css';

export default function NewOperationPage() {
  const router = useRouter();
  const [saving, setSaving] = useState(false);

  // 등록 타입 — META(직접 등록) / CUSTOM(내장 핸들러 선택)
  const [mode, setMode] = useState<'META' | 'CUSTOM'>('META');
  const isCustom = mode === 'CUSTOM';

  // CUSTOM 모드: 핸들러 카탈로그
  const [catalog, setCatalog] = useState<CustomHandlerCatalogEntry[]>([]);
  const [selectedHandlerId, setSelectedHandlerId] = useState('');

  // 기본 정보
  const [form, setForm] = useState({
    operationId: '',
    operationName: '',
    description: '',
    responseFormat: 'JSON',
    pageSize: 100,
    maxPageSize: 1000,
  });

  // Datasource → Table → Columns 연쇄 로딩
  const [datasources, setDatasources] = useState<DatasourceSimple[]>([]);
  const [selectedDatasourceId, setSelectedDatasourceId] = useState('');
  const [tables, setTables] = useState<DatasourceTable[]>([]);
  const [selectedTable, setSelectedTable] = useState('');
  const [dbColumns, setDbColumns] = useState<ColumnSearchResult[]>([]);

  // 제공 컬럼 선택 + alias + 가공
  type ColConfig = { alias: string; transformType: string; transformParam: string };
  const [selectedColumns, setSelectedColumns] = useState<Map<string, ColConfig>>(new Map());

  // WHERE 파라미터
  const [whereParams, setWhereParams] = useState<{
    columnName: string; paramName: string; operator: string;
    isRequired: boolean; defaultValue: string; dataType: string;
    isHidden: boolean;
  }[]>([]);

  // ORDER BY
  const [orderByColumn, setOrderByColumn] = useState('');
  const [orderByDirection, setOrderByDirection] = useState('ASC');

  // 1. Datasource 목록 로드
  useEffect(() => {
    datasourceApi.getSimple().then(setDatasources).catch(() => {});
  }, []);

  // CUSTOM 모드 → 카탈로그 로드 (미등록만)
  useEffect(() => {
    if (mode === 'CUSTOM') {
      customHandlerApi.getCatalog()
        .then(list => setCatalog(list.filter(e => !e.registered)))
        .catch(() => setCatalog([]));
    } else {
      setCatalog([]);
      setSelectedHandlerId('');
    }
  }, [mode]);

  // CUSTOM 핸들러 선택 → preview 호출 → form 자동 채움 (readonly)
  useEffect(() => {
    if (mode !== 'CUSTOM' || !selectedHandlerId) return;
    customHandlerApi.preview(selectedHandlerId).then(meta => {
      setForm({
        operationId: meta.operationId,
        operationName: meta.operationName,
        description: meta.description || '',
        responseFormat: 'JSON',
        pageSize: meta.pageSize,
        maxPageSize: meta.maxPageSize,
      });
      setSelectedDatasourceId(meta.datasourceId);
      setSelectedTable(meta.tableName);
      const colMap = new Map<string, ColConfig>();
      for (const c of meta.columns) {
        colMap.set(c.columnName, {
          alias: c.aliasName || '',
          transformType: c.transformType || 'NONE',
          transformParam: c.transformParam || '',
        });
      }
      setSelectedColumns(colMap);
      setDbColumns(meta.columns.map(c => ({
        columnName: c.columnName,
        dataType: '-',
        isNullable: 'Y',
        isPrimaryKey: false,
        remarks: c.aliasName ? `→ ${c.aliasName}` : '',
      })) as any);
      setWhereParams(meta.params.map(p => ({
        columnName: p.columnName,
        paramName: p.paramName,
        operator: p.operator,
        isRequired: p.required,
        defaultValue: p.defaultValue || '',
        dataType: p.dataType,
        isHidden: p.hidden,
      })));
    }).catch(e => alert('핸들러 metadata 로드 실패: ' + e.message));
  }, [mode, selectedHandlerId]);

  const defaultColConfig = (): ColConfig => ({ alias: '', transformType: 'NONE', transformParam: '' });

  // 2. Datasource 변경 → 테이블 로드
  const onDatasourceChange = useCallback((dsId: string) => {
    setSelectedDatasourceId(dsId);
    setSelectedTable('');
    setDbColumns([]);
    setSelectedColumns(new Map<string, ColConfig>());
    setWhereParams([]);
    setOrderByColumn('');
    if (dsId) {
      datasourceApi.getRegisteredTables(dsId).then(setTables).catch(() => setTables([]));
    } else {
      setTables([]);
    }
  }, []);

  // 3. 테이블 변경 → 컬럼 로드 (CUSTOM 모드에선 metadata 의 columns 만 사용 — 덮어쓰기 금지)
  useEffect(() => {
    if (isCustom) return;
    if (selectedTable && selectedDatasourceId) {
      datasourceApi.searchColumns(selectedDatasourceId, selectedTable)
        .then(cols => {
          setDbColumns(cols);
          setSelectedColumns(new Map(cols.map(c => [c.columnName, defaultColConfig()])));
        })
        .catch(() => setDbColumns([]));
    } else {
      setDbColumns([]);
      setSelectedColumns(new Map());
    }
  }, [selectedTable, selectedDatasourceId, isCustom]);

  const toggleColumn = (colName: string) => {
    setSelectedColumns(prev => {
      const next = new Map(prev);
      next.has(colName) ? next.delete(colName) : next.set(colName, defaultColConfig());
      return next;
    });
  };

  const updateColConfig = (colName: string, field: keyof ColConfig, value: string) => {
    setSelectedColumns(prev => {
      const next = new Map(prev);
      const cur = next.get(colName) || defaultColConfig();
      next.set(colName, { ...cur, [field]: value });
      return next;
    });
  };

  const selectAllColumns = () => setSelectedColumns(new Map(dbColumns.map(c => [c.columnName, defaultColConfig()])));
  const deselectAllColumns = () => setSelectedColumns(new Map());

  const addWhereParam = (col: ColumnSearchResult) => {
    if (whereParams.some(p => p.columnName === col.columnName)) return;
    const dataType = col.dataType.includes('INT') || col.dataType.includes('NUM') || col.dataType.includes('FLOAT') || col.dataType.includes('DOUBLE')
      ? 'NUMBER'
      : col.dataType.includes('DATE') || col.dataType.includes('TIME')
        ? 'DATE'
        : 'STRING';
    setWhereParams([...whereParams, {
      columnName: col.columnName,
      paramName: col.columnName.toLowerCase(),
      operator: 'EQ',
      isRequired: false,
      defaultValue: '',
      dataType,
      isHidden: false,
    }]);
  };

  const removeWhereParam = (index: number) => {
    setWhereParams(whereParams.filter((_, i) => i !== index));
  };

  const updateWhereParam = (index: number, field: string, value: any) => {
    const updated = [...whereParams];
    (updated[index] as any)[field] = value;
    setWhereParams(updated);
  };

  // 등록
  const handleSubmit = async () => {
    if (isCustom) {
      if (!selectedHandlerId) {
        alert('등록할 핸들러를 선택하세요');
        return;
      }
      if (!form.operationId || !form.operationName) {
        alert('오퍼레이션 ID와 이름을 입력하세요');
        return;
      }
      setSaving(true);
      try {
        await customHandlerApi.register(selectedHandlerId, form.operationId, form.operationName);
        router.push('/api-provide');
      } catch (e: any) {
        alert('등록 실패: ' + (e.response?.data?.error || e.message));
      } finally {
        setSaving(false);
      }
      return;
    }

    if (!form.operationId || !form.operationName) {
      alert('오퍼레이션 ID와 이름을 입력하세요');
      return;
    }
    if (!selectedDatasourceId || !selectedTable) {
      alert('Datasource와 테이블을 선택하세요');
      return;
    }

    setSaving(true);
    try {
      const created = await operationApi.create({
        operationId: form.operationId,
        operationName: form.operationName,
        description: form.description || null,
        datasourceId: selectedDatasourceId,
        tableName: selectedTable,
        responseFormat: form.responseFormat,
        pageSize: form.pageSize,
        maxPageSize: form.maxPageSize,
        orderByColumn: orderByColumn || null,
        orderByDirection,
      } as any);

      const hasCustom = Array.from(selectedColumns.values()).some(c => c.alias.trim() || c.transformType !== 'NONE');
      const allSelected = selectedColumns.size === dbColumns.length;
      if (!allSelected || hasCustom) {
        const cols = Array.from(selectedColumns.entries()).map(([colName, cfg], i) => ({
          columnName: colName,
          aliasName: cfg.alias.trim() || undefined,
          displayOrder: i,
          transformType: cfg.transformType !== 'NONE' ? cfg.transformType : undefined,
          transformParam: cfg.transformParam.trim() || undefined,
        }));
        await columnApi.save(created.id, cols);
      }

      if (whereParams.length > 0) {
        await paramApi.save(created.id, whereParams.map(p => ({
          paramName: p.paramName,
          columnName: p.columnName,
          operator: p.operator,
          isRequired: p.isRequired,
          defaultValue: p.defaultValue || undefined,
          dataType: p.dataType,
          isHidden: p.isHidden || undefined,
        })));
      }

      router.push('/api-provide');
    } catch (e: any) {
      alert('등록 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

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

  const transformOptions = [
    { value: 'NONE', label: '없음' },
    { value: 'ROUND', label: '소수점 (N자리 반올림)' },
    { value: 'DATE_FORMAT', label: '날짜형식 (YYYY-MM-DD 등)' },
    { value: 'COALESCE', label: 'NULL대체 (기본값 지정)' },
    { value: 'SUBSTRING', label: '문자절삭 (앞 N자)' },
  ];
  const transformPlaceholder = (type: string) => {
    switch (type) {
      case 'ROUND': return '예: 2 (소수점 2자리)';
      case 'DATE_FORMAT': return '예: YYYY-MM-DD';
      case 'COALESCE': return '예: 0 또는 없음';
      case 'SUBSTRING': return '예: 50 (앞 50자)';
      default: return '';
    }
  };

  const submitDisabled = saving || (isCustom ? !selectedHandlerId : !selectedTable);

  return (
    <div>
      <div className="app-page-header">
        <h1 className="app-page-header__title">오퍼레이션 등록</h1>
        <div className="app-btn-row">
          <button className="krds-btn small secondary" onClick={() => router.back()}>취소</button>
          <button className="krds-btn small" onClick={handleSubmit} disabled={submitDisabled}>
            {saving ? '저장 중...' : '등록'}
          </button>
        </div>
      </div>

      {/* 0. 등록 타입 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">등록 타입</h2>
        </div>
        <div className={styles.modeOptions}>
          <label className={`${styles.modeOption} ${mode === 'META' ? styles.modeOptionActive : ''}`}>
            <input
              type="radio"
              checked={mode === 'META'}
              onChange={() => setMode('META')}
              className={styles.modeOptionRadio}
            />
            <div className={styles.modeOptionContent}>
              <span className={styles.modeOptionTitle}>
                직접 등록<span className={styles.modeOptionBadge}>META</span>
              </span>
              <span className={styles.modeOptionDesc}>
                DB 테이블 + 컬럼을 직접 선택해서 오퍼레이션을 만듭니다.
              </span>
            </div>
          </label>
          <label className={`${styles.modeOption} ${mode === 'CUSTOM' ? styles.modeOptionActive : ''}`}>
            <input
              type="radio"
              checked={mode === 'CUSTOM'}
              onChange={() => setMode('CUSTOM')}
              className={styles.modeOptionRadio}
            />
            <div className={styles.modeOptionContent}>
              <span className={styles.modeOptionTitle}>
                내장 핸들러 선택<span className={styles.modeOptionBadge}>CUSTOM</span>
              </span>
              <span className={styles.modeOptionDesc}>
                시스템에 내장된 핸들러를 그대로 등록합니다 (코드에 박혀있어 수정 불가).
              </span>
            </div>
          </label>
        </div>
        {isCustom && (
          <div className={styles.handlerSelectWrap}>
            <div className="app-form-field">
              <label className="app-form-label">핸들러 (이름 + 엔드포인트)</label>
              <select
                className="krds-input small"
                value={selectedHandlerId}
                onChange={e => setSelectedHandlerId(e.target.value)}
              >
                <option value="">-- 선택 --</option>
                {catalog.map(h => (
                  <option key={h.operationId} value={h.operationId}>
                    {h.operationName} — {h.operationId}
                  </option>
                ))}
              </select>
              {catalog.length === 0 && (
                <div className={styles.handlerEmptyNote}>등록 가능한 핸들러가 없습니다 (이미 모두 등록됨).</div>
              )}
            </div>
            {selectedHandlerId && (
              <div className={`app-alert app-alert--warning ${styles.handlerLockNote}`}>
                🔒 시스템 내장 핸들러 — 아래 정보는 코드에 박혀있어 수정 불가. 그대로 등록됩니다.
              </div>
            )}
          </div>
        )}
      </div>

      {/* 1. 기본 정보 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">
            기본 정보
            {isCustom && <span className={styles.subTitle}>(오퍼레이션 ID/이름은 운영자 직관성 위해 변경 가능)</span>}
          </h2>
        </div>
        <div className="app-form-grid">
          <div className="app-form-field">
            <label className="app-form-label">오퍼레이션 ID *</label>
            <input
              className="krds-input small"
              placeholder="drought-119"
              value={form.operationId}
              onChange={e => setForm({ ...form, operationId: e.target.value })}
            />
            <div className={styles.urlPreview}>URL: /api/provide/{form.operationId || '{id}'}</div>
          </div>
          <div className="app-form-field">
            <label className="app-form-label">이름 *</label>
            <input
              className="krds-input small"
              placeholder="가뭄119 공통가뭄상태"
              value={form.operationName}
              onChange={e => setForm({ ...form, operationName: e.target.value })}
            />
          </div>
        </div>
        <div className={`app-form-grid app-form-grid--single ${styles.descGrid}`}>
          <div className="app-form-field">
            <label className="app-form-label">설명</label>
            <input
              className="krds-input small"
              placeholder="오퍼레이션 설명"
              value={form.description}
              disabled={isCustom}
              onChange={e => setForm({ ...form, description: e.target.value })}
            />
          </div>
        </div>
      </div>

      {/* 아래 폼 — CUSTOM 모드에서는 fieldset 으로 일괄 disabled */}
      <fieldset disabled={isCustom} className={styles.bareFieldset}>

      {/* 2. Datasource + Table 선택 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">데이터 소스</h2>
        </div>
        <div className="app-form-grid">
          <div className="app-form-field">
            <label className="app-form-label">Datasource *</label>
            <select
              className="krds-input small"
              value={selectedDatasourceId}
              onChange={e => onDatasourceChange(e.target.value)}
            >
              <option value="">-- 선택 --</option>
              {datasources.map(ds => (
                <option key={ds.datasourceId} value={ds.datasourceId}>
                  {ds.datasourceName} ({ds.dbType})
                </option>
              ))}
            </select>
          </div>
          <div className="app-form-field">
            <label className="app-form-label">테이블 *</label>
            <select
              className="krds-input small"
              value={selectedTable}
              disabled={!selectedDatasourceId}
              onChange={e => setSelectedTable(e.target.value)}
            >
              <option value="">-- 선택 --</option>
              {tables.map(t => (
                <option key={t.tableName} value={t.tableName}>
                  {t.tableName}{t.tableAlias ? ` (${t.tableAlias})` : ''}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* 3. 컬럼 선택 */}
      {dbColumns.length > 0 && (
        <div className="app-card">
          <div className="app-card__header">
            <div>
              <h2 className="app-card__title">SELECT 컬럼 ({selectedColumns.size}/{dbColumns.length})</h2>
              <div className={styles.cardSubLine}>제공할 컬럼을 선택하세요 (미선택 시 전체)</div>
            </div>
            <div className={styles.cardActions}>
              <button type="button" className="krds-btn small secondary" onClick={selectAllColumns}>전체 선택</button>
              <button type="button" className="krds-btn small secondary" onClick={deselectAllColumns}>전체 해제</button>
            </div>
          </div>
          <div className={styles.colGrid}>
            <div className={styles.colGridHeader}>
              <div></div>
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
                  <input type="checkbox" checked={checked} onChange={() => toggleColumn(col.columnName)} />
                  <span className={styles.colName}>
                    {col.columnName}
                    {col.isPrimaryKey ? <span className={styles.colNamePk}> [PK]</span> : ''}
                    {col.remarks ? <span className={styles.colNameRemarks}> {col.remarks}</span> : ''}
                  </span>
                  <span className={styles.colMeta}>{col.dataType}</span>
                  <input
                    className={`krds-input small ${checked ? '' : styles.inputDimmed}`}
                    placeholder={col.columnName}
                    disabled={!checked}
                    value={cfg?.alias || ''}
                    onChange={e => updateColConfig(col.columnName, 'alias', e.target.value)}
                  />
                  <select
                    className={`krds-input small ${checked ? '' : styles.inputDimmed}`}
                    disabled={!checked}
                    value={cfg?.transformType || 'NONE'}
                    onChange={e => updateColConfig(col.columnName, 'transformType', e.target.value)}
                  >
                    {transformOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </select>
                  <input
                    className={`krds-input small ${checked && cfg?.transformType !== 'NONE' ? '' : styles.inputDimmed}`}
                    disabled={!checked || cfg?.transformType === 'NONE'}
                    placeholder={transformPlaceholder(cfg?.transformType || '')}
                    value={cfg?.transformParam || ''}
                    onChange={e => updateColConfig(col.columnName, 'transformParam', e.target.value)}
                  />
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 4. WHERE 파라미터 */}
      {dbColumns.length > 0 && (
        <div className="app-card">
          <div className="app-card__header">
            <div>
              <h2 className="app-card__title">WHERE 파라미터</h2>
              <div className={styles.cardSubLine}>외부 호출 시 필터 조건으로 사용할 컬럼 (선택)</div>
            </div>
          </div>
          <div className={styles.paramAddRow}>
            <select
              className={`krds-input small ${styles.paramAddSelect}`}
              onChange={e => {
                const col = dbColumns.find(c => c.columnName === e.target.value);
                if (col) { addWhereParam(col); e.target.value = ''; }
              }}
            >
              <option value="">+ 조건 컬럼 추가</option>
              {dbColumns
                .filter(c => !whereParams.some(p => p.columnName === c.columnName))
                .map(c => (
                  <option key={c.columnName} value={c.columnName}>
                    {c.columnName} ({c.dataType})
                  </option>
                ))}
            </select>
          </div>
          {whereParams.length === 0 ? (
            <div className={styles.paramEmpty}>조건 없음 — 전체 데이터 제공</div>
          ) : (
            <div className={styles.paramGrid}>
              <div className={styles.paramGridHeader}>
                <div>파라미터명</div>
                <div>DB 컬럼</div>
                <div>연산자</div>
                <div className={styles.paramGridHeaderCenter}>필수</div>
                <div className={styles.paramGridHeaderCenter}>숨김</div>
                <div>타입</div>
                <div>기본값</div>
                <div></div>
              </div>
              {whereParams.map((p, i) => (
                <div key={i} className={styles.paramGridRow}>
                  <input
                    className={`krds-input small ${p.isHidden ? styles.inputDimmed : ''}`}
                    value={p.paramName}
                    disabled={p.isHidden}
                    onChange={e => updateWhereParam(i, 'paramName', e.target.value)}
                  />
                  <div className={styles.paramColName}>{p.columnName}</div>
                  <select
                    className="krds-input small"
                    value={p.operator}
                    onChange={e => updateWhereParam(i, 'operator', e.target.value)}
                  >
                    {operators.map(op => <option key={op.value} value={op.value}>{op.label}</option>)}
                  </select>
                  <div className={styles.paramCheckCell}>
                    <input
                      type="checkbox"
                      checked={p.isRequired}
                      disabled={p.isHidden}
                      onChange={e => updateWhereParam(i, 'isRequired', e.target.checked)}
                    />
                  </div>
                  <div className={styles.paramCheckCell}>
                    <input
                      type="checkbox"
                      checked={p.isHidden}
                      onChange={e => updateWhereParam(i, 'isHidden', e.target.checked)}
                    />
                  </div>
                  <select
                    className="krds-input small"
                    value={p.dataType}
                    onChange={e => updateWhereParam(i, 'dataType', e.target.value)}
                  >
                    {dataTypes.map(dt => <option key={dt} value={dt}>{dt}</option>)}
                  </select>
                  <input
                    className="krds-input small"
                    placeholder={p.isHidden ? '고정값 (필수)' : '기본값'}
                    value={p.defaultValue}
                    onChange={e => updateWhereParam(i, 'defaultValue', e.target.value)}
                  />
                  <button
                    type="button"
                    className="krds-btn small app-btn-danger"
                    onClick={() => removeWhereParam(i)}
                  >
                    X
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 5. 정렬 + 응답 설정 */}
      {dbColumns.length > 0 && (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">정렬 / 응답 설정</h2>
          </div>
          <div className={styles.formatRow}>
            <div className="app-form-field">
              <label className="app-form-label">ORDER BY 컬럼</label>
              <select
                className="krds-input small"
                value={orderByColumn}
                onChange={e => setOrderByColumn(e.target.value)}
              >
                <option value="">없음</option>
                {dbColumns.map(c => (
                  <option key={c.columnName} value={c.columnName}>{c.columnName}</option>
                ))}
              </select>
            </div>
            <div className="app-form-field">
              <label className="app-form-label">방향</label>
              <select
                className="krds-input small"
                value={orderByDirection}
                onChange={e => setOrderByDirection(e.target.value)}
              >
                <option value="ASC">ASC</option>
                <option value="DESC">DESC</option>
              </select>
            </div>
            <div className="app-form-field">
              <label className="app-form-label">포맷</label>
              <select
                className="krds-input small"
                value={form.responseFormat}
                onChange={e => setForm({ ...form, responseFormat: e.target.value })}
              >
                <option value="JSON">JSON</option>
                <option value="XML">XML</option>
              </select>
            </div>
            <div className="app-form-field">
              <label className="app-form-label">페이지 크기</label>
              <input
                className="krds-input small"
                type="number"
                value={form.pageSize}
                onChange={e => setForm({ ...form, pageSize: parseInt(e.target.value) || 100 })}
              />
            </div>
            <div className="app-form-field">
              <label className="app-form-label">최대</label>
              <input
                className="krds-input small"
                type="number"
                value={form.maxPageSize}
                onChange={e => setForm({ ...form, maxPageSize: parseInt(e.target.value) || 1000 })}
              />
            </div>
          </div>
        </div>
      )}

      </fieldset>
    </div>
  );
}
