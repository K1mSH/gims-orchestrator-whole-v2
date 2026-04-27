'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { datasourceApi } from '@/lib/api';
import { operationApi, columnApi, paramApi, customHandlerApi, CustomHandlerCatalogEntry } from '@/lib/providerApi';
import { DatasourceSimple, DatasourceTable, ColumnSearchResult } from '@/types/index';

const sectionStyle = { padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)' };
const fieldLabel: React.CSSProperties = { fontSize: '0.8rem', color: 'var(--gray-500)', marginBottom: '0.25rem', fontWeight: 500 };

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
      // 컬럼: metadata.columns 그대로 표시 (DB 컬럼 자동 로드 안 씀)
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
      // 파라미터
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

  // 3. 테이블 변경 → 컬럼 로드
  useEffect(() => {
    if (selectedTable && selectedDatasourceId) {
      datasourceApi.searchColumns(selectedDatasourceId, selectedTable)
        .then(cols => {
          setDbColumns(cols);
          setSelectedColumns(new Map(cols.map(c => [c.columnName, defaultColConfig()]))); // 기본: 전체 선택
        })
        .catch(() => setDbColumns([]));
    } else {
      setDbColumns([]);
      setSelectedColumns(new Map());
    }
  }, [selectedTable, selectedDatasourceId]);

  const defaultColConfig = (): ColConfig => ({ alias: '', transformType: 'NONE', transformParam: '' });

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
    // CUSTOM 모드 — 핸들러 register 호출. 운영자가 변경한 operationId/이름은 customOperationId/Name 로 전송
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
      // 1. 오퍼레이션 생성
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

      // 2. 컬럼 저장 (전체 선택 + 커스텀 없으면 생략 = SELECT *)
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

      // 3. WHERE 파라미터 저장
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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h1 style={{ fontSize: '1.25rem', fontWeight: 700 }}>오퍼레이션 등록</h1>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button className="btn" onClick={() => router.back()}>취소</button>
          <button className="btn btn-primary" onClick={handleSubmit} disabled={submitDisabled}>
            {saving ? '저장 중...' : '등록'}
          </button>
        </div>
      </div>

      {/* 0. 등록 타입 */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div style={sectionStyle}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '0.5rem' }}>등록 타입</div>
          <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
            <label style={{ fontSize: '0.85rem', cursor: 'pointer' }}>
              <input type="radio" checked={mode === 'META'} onChange={() => setMode('META')} /> 직접 등록 (META)
            </label>
            <label style={{ fontSize: '0.85rem', cursor: 'pointer' }}>
              <input type="radio" checked={mode === 'CUSTOM'} onChange={() => setMode('CUSTOM')} /> 내장 핸들러 선택 (CUSTOM)
            </label>
          </div>
          {isCustom && (
            <div style={{ marginTop: '0.75rem' }}>
              <div style={fieldLabel}>핸들러 (이름 + 엔드포인트)</div>
              <select className="form-input" value={selectedHandlerId}
                onChange={e => setSelectedHandlerId(e.target.value)}>
                <option value="">-- 선택 --</option>
                {catalog.map(h => (
                  <option key={h.operationId} value={h.operationId}>
                    {h.operationName} — {h.operationId}
                  </option>
                ))}
              </select>
              {catalog.length === 0 && (
                <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)', marginTop: '0.25rem' }}>
                  등록 가능한 핸들러가 없습니다 (이미 모두 등록됨).
                </div>
              )}
              {selectedHandlerId && (
                <div style={{
                  marginTop: '0.75rem', padding: '0.5rem 0.75rem',
                  background: '#fffbeb', border: '1px solid #fcd34d',
                  borderRadius: '4px', fontSize: '0.8rem', color: '#78350f',
                }}>
                  🔒 시스템 내장 핸들러 — 아래 정보는 코드에 박혀있어 수정 불가. 그대로 등록됩니다.
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* 1. 기본 정보 — CUSTOM 시에도 operationId/operationName 만 변경 가능 (description 은 disabled) */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div style={sectionStyle}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '0.5rem' }}>
            기본 정보
            {isCustom && <span style={{ marginLeft: '0.5rem', fontSize: '0.75rem', color: 'var(--gray-500)', fontWeight: 400 }}>
              (오퍼레이션 ID/이름은 운영자 직관성 위해 변경 가능)
            </span>}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <div>
              <div style={fieldLabel}>오퍼레이션 ID *</div>
              <input className="form-input" placeholder="drought-119" value={form.operationId}
                onChange={e => setForm({ ...form, operationId: e.target.value })} />
              <div style={{ fontSize: '0.7rem', color: 'var(--gray-400)', marginTop: '0.15rem' }}>
                URL: /api/provide/{form.operationId || '{id}'}
              </div>
            </div>
            <div>
              <div style={fieldLabel}>이름 *</div>
              <input className="form-input" placeholder="가뭄119 공통가뭄상태" value={form.operationName}
                onChange={e => setForm({ ...form, operationName: e.target.value })} />
            </div>
          </div>
          <div style={{ marginTop: '0.5rem' }}>
            <div style={fieldLabel}>설명</div>
            <input className="form-input" placeholder="오퍼레이션 설명" value={form.description} disabled={isCustom}
              onChange={e => setForm({ ...form, description: e.target.value })} />
          </div>
        </div>
      </div>

      {/* 아래 폼 — CUSTOM 모드에서는 fieldset 으로 일괄 disabled */}
      <fieldset disabled={isCustom} style={{ border: 'none', padding: 0, margin: 0 }}>

      {/* 2. Datasource + Table 선택 */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div style={sectionStyle}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '0.5rem' }}>데이터 소스</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <div>
              <div style={fieldLabel}>Datasource *</div>
              <select className="form-input" value={selectedDatasourceId}
                onChange={e => onDatasourceChange(e.target.value)}>
                <option value="">-- 선택 --</option>
                {datasources.map(ds => (
                  <option key={ds.datasourceId} value={ds.datasourceId}>
                    {ds.datasourceName} ({ds.dbType})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <div style={fieldLabel}>테이블 *</div>
              <select className="form-input" value={selectedTable} disabled={!selectedDatasourceId}
                onChange={e => setSelectedTable(e.target.value)}>
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
      </div>

      {/* 3. 컬럼 선택 (테이블 선택 후 표시) */}
      {dbColumns.length > 0 && (
        <div className="card" style={{ marginBottom: '1rem' }}>
          <div style={sectionStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>SELECT 컬럼 ({selectedColumns.size}/{dbColumns.length})</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)' }}>제공할 컬럼을 선택하세요 (미선택 시 전체)</div>
              </div>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button className="btn btn-sm" onClick={selectAllColumns}>전체 선택</button>
                <button className="btn btn-sm" onClick={deselectAllColumns}>전체 해제</button>
              </div>
            </div>
          </div>
          <div style={{ padding: '0.75rem 1rem', overflowX: 'auto' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '30px 1fr 80px 1fr 160px 140px', gap: '0.5rem', marginBottom: '0.5rem', minWidth: '800px' }}>
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
                <div key={col.columnName} style={{ display: 'grid', gridTemplateColumns: '30px 1fr 80px 1fr 160px 140px', gap: '0.5rem', marginBottom: '0.15rem', alignItems: 'center', minWidth: '800px' }}>
                  <input type="checkbox" checked={checked} onChange={() => toggleColumn(col.columnName)} />
                  <span style={{ fontSize: '0.8rem' }}>
                    <span style={{ fontFamily: 'monospace' }}>{col.columnName}</span>
                    {col.isPrimaryKey ? <span style={{ color: '#2563eb', fontSize: '0.7rem' }}> [PK]</span> : ''}
                    {col.remarks ? <span style={{ color: 'var(--gray-400)', fontSize: '0.7rem' }}> {col.remarks}</span> : ''}
                  </span>
                  <span style={{ color: 'var(--gray-400)', fontSize: '0.75rem' }}>{col.dataType}</span>
                  <input className="form-input" placeholder={col.columnName} disabled={!checked}
                    value={cfg?.alias || ''}
                    onChange={e => updateColConfig(col.columnName, 'alias', e.target.value)}
                    style={{ fontSize: '0.8rem', opacity: checked ? 1 : 0.3 }} />
                  <select className="form-input" disabled={!checked}
                    value={cfg?.transformType || 'NONE'}
                    onChange={e => updateColConfig(col.columnName, 'transformType', e.target.value)}
                    style={{ fontSize: '0.75rem', opacity: checked ? 1 : 0.3 }}>
                    {transformOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </select>
                  <input className="form-input" disabled={!checked || cfg?.transformType === 'NONE'}
                    placeholder={transformPlaceholder(cfg?.transformType || '')}
                    value={cfg?.transformParam || ''}
                    onChange={e => updateColConfig(col.columnName, 'transformParam', e.target.value)}
                    style={{ fontSize: '0.8rem', opacity: checked && cfg?.transformType !== 'NONE' ? 1 : 0.3 }} />
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 4. WHERE 파라미터 (테이블 선택 후 표시) */}
      {dbColumns.length > 0 && (
        <div className="card" style={{ marginBottom: '1rem' }}>
          <div style={sectionStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>WHERE 파라미터</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)' }}>외부 호출 시 필터 조건으로 사용할 컬럼 (선택)</div>
              </div>
            </div>
          </div>
          {/* 컬럼 추가 드롭다운 */}
          <div style={{ padding: '0.5rem 1rem', borderBottom: '1px solid var(--gray-100)' }}>
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              <select className="form-input" style={{ width: '250px' }} id="add-where-col"
                onChange={e => {
                  const col = dbColumns.find(c => c.columnName === e.target.value);
                  if (col) { addWhereParam(col); e.target.value = ''; }
                }}>
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
          </div>
          {whereParams.length === 0 ? (
            <div style={{ padding: '1.5rem', textAlign: 'center', color: 'var(--gray-400)', fontSize: '0.85rem' }}>
              조건 없음 — 전체 데이터 제공
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
              {whereParams.map((p, i) => (
                <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 140px 50px 50px 110px 1fr 40px', gap: '0.5rem', marginBottom: '0.25rem', minWidth: '900px', alignItems: 'center' }}>
                  <input className="form-input" value={p.paramName} disabled={p.isHidden}
                    onChange={e => updateWhereParam(i, 'paramName', e.target.value)}
                    style={{ opacity: p.isHidden ? 0.4 : 1 }} />
                  <div style={{ fontSize: '0.8rem', fontFamily: 'monospace' }}>{p.columnName}</div>
                  <select className="form-input" value={p.operator}
                    onChange={e => updateWhereParam(i, 'operator', e.target.value)}>
                    {operators.map(op => <option key={op.value} value={op.value}>{op.label}</option>)}
                  </select>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <input type="checkbox" checked={p.isRequired} disabled={p.isHidden}
                      onChange={e => updateWhereParam(i, 'isRequired', e.target.checked)} />
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <input type="checkbox" checked={p.isHidden}
                      onChange={e => updateWhereParam(i, 'isHidden', e.target.checked)} />
                  </div>
                  <select className="form-input" value={p.dataType}
                    onChange={e => updateWhereParam(i, 'dataType', e.target.value)}>
                    {dataTypes.map(dt => <option key={dt} value={dt}>{dt}</option>)}
                  </select>
                  <input className="form-input" placeholder={p.isHidden ? '고정값 (필수)' : '기본값'} value={p.defaultValue}
                    onChange={e => updateWhereParam(i, 'defaultValue', e.target.value)} />
                  <button className="btn btn-danger btn-sm" onClick={() => removeWhereParam(i)}>X</button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 5. 정렬 + 응답 설정 (테이블 선택 후 표시) */}
      {dbColumns.length > 0 && (
        <div className="card" style={{ marginBottom: '1rem' }}>
          <div style={sectionStyle}>
            <div style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '0.5rem' }}>정렬 / 응답 설정</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 100px 100px 100px 100px', gap: '0.75rem' }}>
              <div>
                <div style={fieldLabel}>ORDER BY 컬럼</div>
                <select className="form-input" value={orderByColumn}
                  onChange={e => setOrderByColumn(e.target.value)}>
                  <option value="">없음</option>
                  {dbColumns.map(c => (
                    <option key={c.columnName} value={c.columnName}>{c.columnName}</option>
                  ))}
                </select>
              </div>
              <div>
                <div style={fieldLabel}>방향</div>
                <select className="form-input" value={orderByDirection}
                  onChange={e => setOrderByDirection(e.target.value)}>
                  <option value="ASC">ASC</option>
                  <option value="DESC">DESC</option>
                </select>
              </div>
              <div>
                <div style={fieldLabel}>포맷</div>
                <select className="form-input" value={form.responseFormat}
                  onChange={e => setForm({ ...form, responseFormat: e.target.value })}>
                  <option value="JSON">JSON</option>
                  <option value="XML">XML</option>
                </select>
              </div>
              <div>
                <div style={fieldLabel}>페이지 크기</div>
                <input className="form-input" type="number" value={form.pageSize}
                  onChange={e => setForm({ ...form, pageSize: parseInt(e.target.value) || 100 })} />
              </div>
              <div>
                <div style={fieldLabel}>최대</div>
                <input className="form-input" type="number" value={form.maxPageSize}
                  onChange={e => setForm({ ...form, maxPageSize: parseInt(e.target.value) || 1000 })} />
              </div>
            </div>
          </div>
        </div>
      )}

      </fieldset>
    </div>
  );
}
