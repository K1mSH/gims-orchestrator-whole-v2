'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { endpointApi, testApi, paramApi, mappingApi, TestCallResponse, TreeNode, InlineTestRequest } from '@/lib/collectorApi';
import { datasourceApi } from '@/lib/api';
import {
  ApiEndpointListItem,
  ApiEndpointCreateRequest,
  ApiParamRequest,
  ApiFieldMappingRequest,
  AuthType,
  CollectorZone,
  TransformType,
} from '@/types/api-collect';
import { DatasourceSimple, ColumnSearchResult, TableSearchResult } from '@/types';

// --- 매핑 행 타입 ---
interface MappingRow {
  sourceFieldPath: string;
  targetColumnName: string;
  isConflictKey: boolean;
  transformType: TransformType;
  transformConfig: string;
  excluded: boolean;
}

interface DerivedRow {
  sourceFieldPath: string;
  targetColumnName: string;
  isConflictKey: boolean;
  transformType: string;
  extractPattern: string;
  extractGroup: number;
  lookupParam: string;
  lookupKeyField: string;
  lookupValueField: string;
  lookupDataRootPath: string;
  lookupMatchType: string;
  defaultValue: string;
}

const EXTRACT_PRESETS = [
  { label: '도메인', pattern: 'https?://(?:www\\.)?([^/]+)', group: 1 },
  { label: '숫자만', pattern: '(\\d+)', group: 1 },
  { label: '괄호 안', pattern: '\\(([^)]+)\\)', group: 1 },
];

export default function ApiCollectPage() {
  const router = useRouter();
  const [endpoints, setEndpoints] = useState<ApiEndpointListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);

  // 등록 폼
  const [form, setForm] = useState<ApiEndpointCreateRequest>({
    apiName: '',
    url: '',
    httpMethod: 'GET',
    authType: 'NONE',
    zone: 'DMZ',
  });

  // 헤더 key-value
  const [headerRows, setHeaderRows] = useState<{ key: string; value: string }[]>([]);

  // 파라미터
  const [params, setParams] = useState<ApiParamRequest[]>([]);

  // 테스트 호출
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<TestCallResponse | null>(null);

  // 데이터 루트
  const [selectedDataRoot, setSelectedDataRoot] = useState('');

  // 적재 설정
  const [datasources, setDatasources] = useState<DatasourceSimple[]>([]);
  const [selectedDatasourceId, setSelectedDatasourceId] = useState('');
  const [tables, setTables] = useState<TableSearchResult[]>([]);
  const [targetTable, setTargetTable] = useState('');
  const [targetColumns, setTargetColumns] = useState<ColumnSearchResult[]>([]);
  const [upsertEnabled, setUpsertEnabled] = useState(true);

  // 매핑
  const [mappingRows, setMappingRows] = useState<MappingRow[]>([]);
  const [derivedRows, setDerivedRows] = useState<DerivedRow[]>([]);
  const [expandedDerived, setExpandedDerived] = useState<Set<number>>(new Set());

  // 저장
  const [saving, setSaving] = useState(false);

  // 자동 스크롤
  const mappingSectionRef = useRef<HTMLDivElement>(null);

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

  useEffect(() => { fetchEndpoints(); }, [fetchEndpoints]);

  // Datasource 목록 로드
  const refreshDatasources = useCallback(() => {
    datasourceApi.getSimple().then(setDatasources).catch(() => {});
  }, []);
  useEffect(() => { refreshDatasources(); }, [refreshDatasources]);

  // Datasource 선택 시 테이블 목록 로드
  const refreshTables = useCallback(() => {
    if (selectedDatasourceId) {
      datasourceApi.searchTables(selectedDatasourceId).then(setTables).catch(() => setTables([]));
    } else {
      setTables([]);
    }
  }, [selectedDatasourceId]);
  useEffect(() => { refreshTables(); }, [refreshTables]);

  // 테이블 선택 시 컬럼 로드
  useEffect(() => {
    if (targetTable && selectedDatasourceId) {
      datasourceApi.searchColumns(selectedDatasourceId, targetTable).then(setTargetColumns).catch(() => setTargetColumns([]));
    } else {
      setTargetColumns([]);
    }
  }, [targetTable, selectedDatasourceId]);

  const resetForm = () => {
    setForm({ apiName: '', url: '', httpMethod: 'GET', authType: 'NONE', zone: 'DMZ' });
    setHeaderRows([]);
    setParams([]);
    setTestResult(null);
    setSelectedDataRoot('');
    setSelectedDatasourceId('');
    setTargetTable('');
    setTargetColumns([]);
    setMappingRows([]);
    setDerivedRows([]);
    setExpandedDerived(new Set());
    setUpsertEnabled(true);
    setShowCreateForm(false);
  };

  const headersToJson = (rows: { key: string; value: string }[]): string => {
    const obj: Record<string, string> = {};
    rows.filter(r => r.key.trim()).forEach(r => { obj[r.key.trim()] = r.value; });
    return Object.keys(obj).length > 0 ? JSON.stringify(obj) : '';
  };

  // 테스트 호출 (인라인)
  const handleTestCall = async () => {
    if (!form.url) { alert('URL을 입력하세요.'); return; }
    try {
      setTesting(true);
      setSelectedDataRoot('');
      setMappingRows([]);
      const inlineReq: InlineTestRequest = {
        url: form.url,
        httpMethod: form.httpMethod,
        contentType: form.contentType,
        headers: headersToJson(headerRows),
        authType: form.authType,
        authConfig: form.authConfig,
        params: params.filter(p => p.paramName).map(p => ({
          paramName: p.paramName,
          paramType: p.paramType || 'QUERY',
          valueType: p.valueType || 'STATIC',
          staticValue: p.staticValue,
          dynamicType: p.dynamicType,
          dynamicFormat: p.dynamicFormat,
          dynamicOffset: p.dynamicOffset,
        })),
      };
      const result = await testApi.callInline(inlineReq);
      setTestResult(result);
    } catch (e: any) {
      alert('테스트 호출 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setTesting(false);
    }
  };

  // 데이터 루트 선택 → 필드 자동 추출 + 자동 스크롤
  const handleSelectRoot = (path: string) => {
    setSelectedDataRoot(path);
    if (testResult?.responseTree) {
      const fields = findFieldsFromTree(testResult.responseTree, path);
      setMappingRows(fields.map(f => ({
        sourceFieldPath: f.path,
        targetColumnName: '',
        isConflictKey: false,
        transformType: 'NONE' as TransformType,
        transformConfig: '',
        excluded: false,
      })));
      setDerivedRows([]);
      setExpandedDerived(new Set());
    }
    setTimeout(() => {
      mappingSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);
  };

  const updateMappingRow = (index: number, field: string, value: any) => {
    const updated = [...mappingRows];
    updated[index] = { ...updated[index], [field]: value };
    setMappingRows(updated);
  };

  // 등록 (validation + 일괄 저장)
  const handleCreate = async () => {
    // 1. 기본정보
    if (!form.apiName.trim()) { alert('API명을 입력하세요.'); return; }
    if (!form.url.trim()) { alert('URL을 입력하세요.'); return; }
    // 2. 테스트 호출 성공
    if (!testResult?.success) { alert('테스트 호출을 먼저 실행하세요.'); return; }
    // 3. 데이터 루트
    if (!selectedDataRoot) { alert('데이터 루트를 선택하세요.'); return; }
    // 4. 타겟 설정
    if (!selectedDatasourceId) { alert('Target Datasource를 선택하세요.'); return; }
    if (!targetTable.trim()) { alert('Target 테이블을 선택하세요.'); return; }
    // 5. 매핑
    const activeRows = mappingRows.filter(r => !r.excluded && r.targetColumnName);
    const activeDerived = derivedRows.filter(r => r.targetColumnName);
    if (activeRows.length === 0 && activeDerived.length === 0) { alert('필드 매핑을 1개 이상 설정하세요.'); return; }
    const hasConflictKey = [...activeRows, ...activeDerived].some(r => r.isConflictKey);
    if (upsertEnabled && !hasConflictKey) { alert('UPSERT 사용 시 중복키를 1개 이상 지정하세요.'); return; }

    try {
      setSaving(true);

      // Step 1: endpoint 생성 (기본정보 + 적재설정 한번에)
      const submitForm = {
        ...form,
        headers: headersToJson(headerRows),
        dataRootPath: selectedDataRoot,
        targetDatasourceId: selectedDatasourceId,
        targetTableName: targetTable,
        upsertEnabled,
      };
      const created = await endpointApi.create(submitForm);
      const endpointId = created.id;

      try {
        // Step 2: params 저장
        if (params.filter(p => p.paramName).length > 0) {
          await paramApi.save(endpointId, params.filter(p => p.paramName));
        }

        // Step 3: mappings 저장 (1:1 + 파생)
        const mappingRequests: ApiFieldMappingRequest[] = [
          ...activeRows.map((r, i) => ({
            sourceFieldPath: r.sourceFieldPath,
            targetColumnName: r.targetColumnName,
            isConflictKey: r.isConflictKey,
            transformType: r.transformType,
            transformConfig: r.transformConfig || undefined,
            displayOrder: i,
            isDerived: false,
          })),
          ...activeDerived.map((r, i) => ({
            sourceFieldPath: r.sourceFieldPath,
            targetColumnName: r.targetColumnName,
            isConflictKey: r.isConflictKey,
            transformType: r.transformType as TransformType,
            displayOrder: activeRows.length + i,
            isDerived: true,
            extractPattern: r.extractPattern || undefined,
            extractGroup: r.extractGroup,
            lookupParam: r.lookupParam || undefined,
            lookupKeyField: r.lookupKeyField || undefined,
            lookupValueField: r.lookupValueField || undefined,
            lookupDataRootPath: r.lookupDataRootPath || undefined,
            lookupMatchType: r.lookupMatchType || 'EXACT',
            defaultValue: r.defaultValue || undefined,
          })),
        ];
        await mappingApi.save(endpointId, mappingRequests);

        alert('등록 완료');
        resetForm();
        fetchEndpoints();
      } catch (e) {
        // rollback: 생성된 endpoint 삭제
        try { await endpointApi.delete(endpointId); } catch {}
        throw e;
      }
    } catch (e: any) {
      alert('등록 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  // 파라미터 관리
  const addParam = () => {
    setParams([...params, { paramName: '', paramType: 'QUERY', valueType: 'STATIC', staticValue: '', displayOrder: params.length }]);
  };
  const removeParam = (index: number) => { setParams(params.filter((_, i) => i !== index)); };
  const updateParam = (index: number, field: string, value: any) => {
    const updated = [...params];
    updated[index] = { ...updated[index], [field]: value };
    if (field === 'valueType') {
      if (value === 'STATIC') {
        updated[index].dynamicType = undefined;
        updated[index].dynamicFormat = '';
        updated[index].dynamicOffset = 0;
      } else {
        updated[index].staticValue = '';
        updated[index].dynamicType = 'TODAY';
        updated[index].dynamicFormat = 'yyyyMMdd';
        updated[index].dynamicOffset = 0;
      }
    }
    setParams(updated);
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

  const sectionLabel = { fontSize: '0.75rem', color: 'var(--gray-400)', marginBottom: '0.5rem', fontWeight: 600 } as const;
  const fieldLabel = { fontSize: '0.8rem', color: 'var(--gray-500)', marginBottom: '0.25rem', fontWeight: 500 } as const;
  const sectionStyle = { padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)' } as const;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>API 수집 관리</h1>
        <button className="btn btn-primary" onClick={() => showCreateForm ? resetForm() : setShowCreateForm(true)}>
          {showCreateForm ? '취소' : '+ API 등록'}
        </button>
      </div>

      {showCreateForm && (
        <div className="card" style={{ marginBottom: '1.5rem' }}>
          <div className="card-header"><h3 className="card-title">새 API 등록</h3></div>

          {/* 요청 설정 */}
          <div style={sectionStyle}>
            <div style={sectionLabel}>요청</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '0.75rem', alignItems: 'start' }}>
              <div>
                <div style={fieldLabel}>API명 *</div>
                <input className="form-input" value={form.apiName}
                  onChange={e => setForm({ ...form, apiName: e.target.value })}
                  placeholder="예: 네이버 뉴스 수집" />
              </div>
              <div>
                <div style={fieldLabel}>URL *</div>
                <input className="form-input" value={form.url}
                  onChange={e => setForm({ ...form, url: e.target.value })}
                  placeholder="https://openapi.naver.com/..." />
              </div>
              <div>
                <div style={fieldLabel}>HTTP Method</div>
                <select className="form-select" value={form.httpMethod}
                  onChange={e => setForm({ ...form, httpMethod: e.target.value })}>
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                </select>
              </div>
              <div>
                <div style={fieldLabel}>Content-Type</div>
                <select className="form-select" value={form.contentType || ''}
                  onChange={e => setForm({ ...form, contentType: e.target.value })}>
                  <option value="">기본 (없음)</option>
                  <option value="application/json">application/json</option>
                  <option value="application/x-www-form-urlencoded">application/x-www-form-urlencoded</option>
                </select>
              </div>
            </div>
          </div>

          {/* 헤더 */}
          <div style={sectionStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <div style={sectionLabel}>헤더</div>
              <button className="btn btn-sm btn-primary" onClick={() => setHeaderRows([...headerRows, { key: '', value: '' }])}
                style={{ fontSize: '0.75rem', padding: '2px 8px' }}>+ 추가</button>
            </div>
            {headerRows.length === 0 ? (
              <div style={{ fontSize: '0.85rem', color: 'var(--gray-400)', padding: '0.25rem 0' }}>커스텀 헤더 없음 (필요 시 추가)</div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                {headerRows.map((h, i) => (
                  <div key={i} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                    <input className="form-input" value={h.key} style={{ fontSize: '0.85rem', flex: 1 }}
                      onChange={e => { const u = [...headerRows]; u[i] = { ...u[i], key: e.target.value }; setHeaderRows(u); }}
                      placeholder="Header Name" />
                    <input className="form-input" value={h.value} style={{ fontSize: '0.85rem', flex: 2 }}
                      onChange={e => { const u = [...headerRows]; u[i] = { ...u[i], value: e.target.value }; setHeaderRows(u); }}
                      placeholder="Value" />
                    <button className="btn btn-danger btn-sm" onClick={() => setHeaderRows(headerRows.filter((_, idx) => idx !== i))}
                      style={{ fontSize: '0.75rem' }}>X</button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* 인증 */}
          <div style={sectionStyle}>
            <div style={sectionLabel}>인증</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '0.75rem', alignItems: 'start' }}>
              <div>
                <div style={fieldLabel}>인증 유형</div>
                <select className="form-select" value={form.authType}
                  onChange={e => setForm({ ...form, authType: e.target.value as AuthType })}>
                  <option value="NONE">없음</option>
                  <option value="BASIC">Basic Auth</option>
                  <option value="BEARER">Bearer Token</option>
                </select>
              </div>
              {form.authType === 'BASIC' && (
                <div>
                  <div style={fieldLabel}>인증 설정 (JSON)</div>
                  <input className="form-input" value={form.authConfig || ''}
                    onChange={e => setForm({ ...form, authConfig: e.target.value })}
                    placeholder='{"username": "...", "password": "..."}' />
                </div>
              )}
              {form.authType === 'BEARER' && (
                <div>
                  <div style={fieldLabel}>Bearer Token</div>
                  <input className="form-input" value={form.authConfig || ''}
                    onChange={e => setForm({ ...form, authConfig: e.target.value })}
                    placeholder='토큰 값 입력' />
                </div>
              )}
            </div>
          </div>

          {/* 기타 */}
          <div style={sectionStyle}>
            <div style={sectionLabel}>기타</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
              <div>
                <div style={fieldLabel}>Zone</div>
                <select className="form-select" value={form.zone}
                  onChange={e => setForm({ ...form, zone: e.target.value as CollectorZone })}>
                  <option value="DMZ">DMZ</option>
                  <option value="INTERNAL">INTERNAL</option>
                </select>
              </div>
              <div>
                <div style={fieldLabel}>설명</div>
                <input className="form-input" value={form.description || ''}
                  onChange={e => setForm({ ...form, description: e.target.value })}
                  placeholder="API 설명 (선택)" />
              </div>
            </div>
          </div>

          {/* 파라미터 */}
          <div style={sectionStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <div style={sectionLabel}>호출 파라미터</div>
              <button className="btn btn-sm btn-primary" onClick={addParam}
                style={{ fontSize: '0.75rem', padding: '2px 8px' }}>+ 추가</button>
            </div>
            {params.length === 0 ? (
              <div style={{ fontSize: '0.85rem', color: 'var(--gray-400)', padding: '0.25rem 0' }}>파라미터 없음 (필요 시 추가)</div>
            ) : (
              <table style={{ width: '100%', fontSize: '0.85rem' }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left', padding: '0.4rem 0.25rem' }}>파라미터명</th>
                    <th style={{ textAlign: 'left', padding: '0.4rem 0.25rem' }}>위치</th>
                    <th style={{ textAlign: 'left', padding: '0.4rem 0.25rem' }}>값 유형</th>
                    <th style={{ textAlign: 'left', padding: '0.4rem 0.25rem' }}>값 설정</th>
                    <th style={{ textAlign: 'left', padding: '0.4rem 0.25rem' }}>설명</th>
                    <th style={{ width: '40px' }}></th>
                  </tr>
                </thead>
                <tbody>
                  {params.map((p, i) => (
                    <tr key={i}>
                      <td style={{ padding: '0.25rem' }}>
                        <input className="form-input" value={p.paramName} style={{ fontSize: '0.85rem' }}
                          onChange={e => updateParam(i, 'paramName', e.target.value)} />
                      </td>
                      <td style={{ padding: '0.25rem' }}>
                        <select className="form-select" value={p.paramType} style={{ fontSize: '0.85rem' }}
                          onChange={e => updateParam(i, 'paramType', e.target.value)}>
                          <option value="QUERY">QUERY</option>
                          <option value="BODY">BODY</option>
                          <option value="PATH">PATH</option>
                          <option value="HEADER">HEADER</option>
                        </select>
                      </td>
                      <td style={{ padding: '0.25rem' }}>
                        <select className="form-select" value={p.valueType} style={{ fontSize: '0.85rem' }}
                          onChange={e => updateParam(i, 'valueType', e.target.value)}>
                          <option value="STATIC">고정값</option>
                          <option value="DYNAMIC">동적</option>
                        </select>
                      </td>
                      <td style={{ padding: '0.25rem' }}>
                        {p.valueType === 'STATIC' ? (
                          <input className="form-input" value={p.staticValue || ''} style={{ fontSize: '0.85rem' }}
                            onChange={e => updateParam(i, 'staticValue', e.target.value)} placeholder="고정값 입력" />
                        ) : (
                          <div style={{ display: 'flex', gap: '0.25rem' }}>
                            <select className="form-select" value={p.dynamicType || 'TODAY'} style={{ fontSize: '0.85rem', width: '80px' }}
                              onChange={e => updateParam(i, 'dynamicType', e.target.value)}>
                              <option value="TODAY">날짜</option>
                              <option value="NOW">시간</option>
                            </select>
                            <input className="form-input" value={p.dynamicFormat || ''} style={{ fontSize: '0.85rem', width: '100px' }}
                              onChange={e => updateParam(i, 'dynamicFormat', e.target.value)} placeholder="yyyyMMdd" />
                            <input className="form-input" type="number" value={p.dynamicOffset ?? 0} style={{ fontSize: '0.85rem', width: '60px' }}
                              onChange={e => updateParam(i, 'dynamicOffset', parseInt(e.target.value) || 0)} title="오프셋 (예: -1 = 어제)" />
                          </div>
                        )}
                      </td>
                      <td style={{ padding: '0.25rem' }}>
                        <input className="form-input" value={p.description || ''} style={{ fontSize: '0.85rem' }}
                          onChange={e => updateParam(i, 'description', e.target.value)} placeholder="설명" />
                      </td>
                      <td style={{ padding: '0.25rem' }}>
                        <button className="btn btn-danger btn-sm" onClick={() => removeParam(i)} style={{ fontSize: '0.75rem' }}>X</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* 테스트 호출 버튼 */}
          <div style={sectionStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={sectionLabel}>테스트 호출</div>
              <button className="btn btn-primary" onClick={handleTestCall} disabled={testing}>
                {testing ? '호출 중...' : '테스트 호출'}
              </button>
            </div>
            {testResult && (
              <div style={{ marginTop: '0.5rem' }}>
                <span style={{
                  padding: '2px 8px', borderRadius: '4px', fontWeight: 600, fontSize: '0.8rem',
                  background: testResult.success ? '#dcfce7' : '#fee2e2',
                  color: testResult.success ? '#166534' : '#991b1b',
                }}>
                  {testResult.success ? 'SUCCESS' : 'FAILED'} {testResult.httpStatusCode > 0 && `(${testResult.httpStatusCode})`}
                </span>
                {testResult.errorMessage && <span style={{ color: '#991b1b', fontSize: '0.8rem', marginLeft: '0.5rem' }}>{testResult.errorMessage}</span>}
              </div>
            )}
          </div>

          {/* 응답 트리 + 데이터 루트 선택 */}
          {testResult?.success && testResult.responseTree && (
            <div style={sectionStyle}>
              <div style={sectionLabel}>응답 구조 (배열 노드를 클릭하여 데이터 루트 선택)</div>
              <div style={{
                fontFamily: 'monospace', fontSize: '0.8rem', maxHeight: '300px', overflow: 'auto',
                border: '1px solid var(--gray-200)', borderRadius: '4px', padding: '0.75rem', background: 'var(--gray-50)',
              }}>
                <SelectableJsonTreeView node={testResult.responseTree} path="" selectedRoot={selectedDataRoot} onSelectRoot={handleSelectRoot} />
              </div>
              {selectedDataRoot && (
                <div style={{ marginTop: '0.5rem', fontSize: '0.85rem', background: '#f0fdf4', padding: '0.5rem', borderRadius: '4px' }}>
                  data_root: <code style={{ fontWeight: 600 }}>{selectedDataRoot}</code>
                </div>
              )}
            </div>
          )}

          {/* 적재 설정 */}
          {selectedDataRoot && (
            <div ref={mappingSectionRef} style={sectionStyle}>
              <div style={sectionLabel}>적재 설정</div>
              <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                  <span style={fieldLabel}>Target DB</span>
                  <select className="form-select" value={selectedDatasourceId} style={{ width: '250px' }}
                    onChange={e => { setSelectedDatasourceId(e.target.value); setTargetTable(''); setTargetColumns([]); }}>
                    <option value="">-- 선택 --</option>
                    {datasources.map(ds => (
                      <option key={ds.datasourceId} value={ds.datasourceId}>{ds.datasourceName} ({ds.dbType})</option>
                    ))}
                  </select>
                  <button className="btn btn-sm btn-secondary" onClick={refreshDatasources}
                    style={{ fontSize: '0.7rem', padding: '2px 6px' }} title="DB 목록 새로고침">↻</button>
                </div>
                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                  <span style={fieldLabel}>테이블</span>
                  <select className="form-select" value={targetTable} style={{ width: '200px' }} disabled={!selectedDatasourceId}
                    onChange={e => setTargetTable(e.target.value)}>
                    <option value="">-- 선택 --</option>
                    {tables.map(t => <option key={t.tableName} value={t.tableName}>{t.tableName}{t.remarks ? ` (${t.remarks})` : ''}</option>)}
                  </select>
                  <button className="btn btn-sm btn-secondary" onClick={refreshTables} disabled={!selectedDatasourceId}
                    style={{ fontSize: '0.7rem', padding: '2px 6px' }} title="테이블 목록 새로고침">↻</button>
                </div>
                <label style={{ fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                  <input type="checkbox" checked={upsertEnabled} onChange={e => setUpsertEnabled(e.target.checked)} />
                  UPSERT
                </label>
              </div>
            </div>
          )}

          {/* 필드 매핑 */}
          {selectedDataRoot && mappingRows.length > 0 && (
            <div style={sectionStyle}>
              <div style={sectionLabel}>필드 매핑 ({mappingRows.filter(r => !r.excluded && r.targetColumnName).length}/{mappingRows.length})</div>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', fontSize: '0.8rem' }}>
                  <thead>
                    <tr style={{ background: 'var(--gray-50)' }}>
                      <th style={{ padding: '0.5rem', textAlign: 'left' }}>API 필드</th>
                      <th style={{ padding: '0.5rem', textAlign: 'center', width: '16px' }}></th>
                      <th style={{ padding: '0.5rem', textAlign: 'left' }}>Target 컬럼</th>
                      <th style={{ padding: '0.5rem', textAlign: 'center', width: '60px' }}>중복키</th>
                      <th style={{ padding: '0.5rem', textAlign: 'left', width: '100px' }}>변환</th>
                      <th style={{ padding: '0.5rem', textAlign: 'center', width: '50px' }}>제외</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mappingRows.map((row, i) => (
                      <tr key={i} style={{ opacity: row.excluded ? 0.4 : 1, borderBottom: '1px solid var(--gray-100)' }}>
                        <td style={{ padding: '0.35rem 0.5rem' }}><code>{row.sourceFieldPath}</code></td>
                        <td style={{ padding: '0.35rem', textAlign: 'center', color: 'var(--gray-400)' }}>→</td>
                        <td style={{ padding: '0.35rem 0.5rem' }}>
                          {targetColumns.length > 0 ? (
                            <select className="form-select" value={row.targetColumnName} disabled={row.excluded}
                              style={{ fontSize: '0.8rem' }}
                              onChange={e => updateMappingRow(i, 'targetColumnName', e.target.value)}>
                              <option value="">-- 선택 --</option>
                              {targetColumns.map(c => (
                                <option key={c.columnName} value={c.columnName}>
                                  {c.columnName} ({c.dataType}){c.isPrimaryKey ? ' [PK]' : ''}{c.remarks ? ` - ${c.remarks}` : ''}
                                </option>
                              ))}
                            </select>
                          ) : (
                            <input className="form-input" value={row.targetColumnName} disabled={row.excluded}
                              style={{ fontSize: '0.8rem' }}
                              onChange={e => updateMappingRow(i, 'targetColumnName', e.target.value)}
                              placeholder="컬럼명 입력" />
                          )}
                        </td>
                        <td style={{ padding: '0.35rem', textAlign: 'center' }}>
                          <input type="checkbox" checked={row.isConflictKey} disabled={row.excluded}
                            onChange={e => updateMappingRow(i, 'isConflictKey', e.target.checked)} />
                        </td>
                        <td style={{ padding: '0.35rem 0.5rem' }}>
                          <select className="form-select" value={row.transformType} disabled={row.excluded}
                            style={{ fontSize: '0.75rem' }}
                            onChange={e => updateMappingRow(i, 'transformType', e.target.value)}>
                            <option value="NONE">없음</option>
                            <option value="DATE_FORMAT">날짜변환</option>
                            <option value="NUMBER">숫자</option>
                            <option value="SUBSTRING">자르기</option>
                            <option value="TRIM">공백제거</option>
                            <option value="REPLACE">치환</option>
                            <option value="DEFAULT_VALUE">기본값</option>
                          </select>
                        </td>
                        <td style={{ padding: '0.35rem', textAlign: 'center' }}>
                          <input type="checkbox" checked={row.excluded}
                            onChange={e => updateMappingRow(i, 'excluded', e.target.checked)} />
                        </td>
                      </tr>
                    ))}

                    {/* 파생 컬럼 구분선 */}
                    {derivedRows.length > 0 && (
                      <tr>
                        <td colSpan={6} style={{ padding: '0.25rem 0.5rem', background: 'var(--gray-100)', fontSize: '0.75rem', color: 'var(--gray-500)', fontWeight: 600 }}>
                          파생 컬럼 (LOOKUP)
                        </td>
                      </tr>
                    )}

                    {/* 파생 컬럼 행 */}
                    {derivedRows.map((row, i) => {
                      const isExpanded = expandedDerived.has(i);
                      const sourceFields = mappingRows.filter(r => !r.excluded).map(r => r.sourceFieldPath);
                      return (
                        <React.Fragment key={`d-${i}`}>
                          <tr style={{ borderBottom: isExpanded ? 'none' : '1px solid var(--gray-100)', background: '#fefce8' }}>
                            <td style={{ padding: '0.35rem 0.5rem' }}>
                              <select className="form-select" value={row.sourceFieldPath} style={{ fontSize: '0.8rem' }}
                                onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], sourceFieldPath: e.target.value }; setDerivedRows(u); }}>
                                <option value="">-- 소스 필드 --</option>
                                {sourceFields.map(f => <option key={f} value={f}>{f}</option>)}
                              </select>
                            </td>
                            <td style={{ padding: '0.35rem', textAlign: 'center', color: 'var(--gray-400)' }}>→</td>
                            <td style={{ padding: '0.35rem 0.5rem' }}>
                              {targetColumns.length > 0 ? (
                                <select className="form-select" value={row.targetColumnName} style={{ fontSize: '0.8rem' }}
                                  onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], targetColumnName: e.target.value }; setDerivedRows(u); }}>
                                  <option value="">-- 선택 --</option>
                                  {targetColumns.map(c => (
                                    <option key={c.columnName} value={c.columnName}>{c.columnName} ({c.dataType}){c.isPrimaryKey ? ' [PK]' : ''}</option>
                                  ))}
                                </select>
                              ) : (
                                <input className="form-input" value={row.targetColumnName} style={{ fontSize: '0.8rem' }}
                                  onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], targetColumnName: e.target.value }; setDerivedRows(u); }}
                                  placeholder="컬럼명 입력" />
                              )}
                            </td>
                            <td style={{ padding: '0.35rem', textAlign: 'center' }}>
                              <input type="checkbox" checked={row.isConflictKey}
                                onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], isConflictKey: e.target.checked }; setDerivedRows(u); }} />
                            </td>
                            <td style={{ padding: '0.35rem 0.5rem' }}>
                              <button className="btn btn-sm" onClick={() => {
                                setExpandedDerived(prev => { const n = new Set(prev); n.has(i) ? n.delete(i) : n.add(i); return n; });
                              }} style={{ fontSize: '0.7rem', padding: '1px 6px', background: isExpanded ? 'var(--primary)' : 'var(--gray-200)', color: isExpanded ? 'white' : 'var(--gray-700)' }}>
                                {isExpanded ? '설정 ▴' : '설정 ▾'}
                              </button>
                            </td>
                            <td style={{ padding: '0.35rem', textAlign: 'center' }}>
                              <button onClick={() => {
                                setDerivedRows(derivedRows.filter((_, idx) => idx !== i));
                                setExpandedDerived(prev => { const n = new Set<number>(); prev.forEach(v => { if (v < i) n.add(v); else if (v > i) n.add(v - 1); }); return n; });
                              }} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--danger)', fontSize: '1rem', padding: '0 4px' }}>✕</button>
                            </td>
                          </tr>
                          {isExpanded && (
                            <tr style={{ background: '#fefce8' }}>
                              <td colSpan={6} style={{ padding: '0.5rem 1rem 1rem 2rem' }}>
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', fontSize: '0.8rem' }}>
                                  <div style={{ gridColumn: '1 / -1' }}>
                                    <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>추출 정규식</label>
                                    <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginTop: '0.25rem' }}>
                                      <input className="form-input" value={row.extractPattern} style={{ flex: 1, fontSize: '0.8rem', fontFamily: 'monospace' }}
                                        onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], extractPattern: e.target.value }; setDerivedRows(u); }}
                                        placeholder="정규식 (예: https?://(?:www\.)?([^/]+))" />
                                      <label style={{ fontSize: '0.75rem', whiteSpace: 'nowrap' }}>그룹:</label>
                                      <input className="form-input" type="number" min={0} value={row.extractGroup} style={{ width: '50px', fontSize: '0.8rem' }}
                                        onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], extractGroup: parseInt(e.target.value) || 1 }; setDerivedRows(u); }} />
                                    </div>
                                    <div style={{ marginTop: '0.25rem', display: 'flex', gap: '0.25rem' }}>
                                      {EXTRACT_PRESETS.map(p => (
                                        <button key={p.label} className="btn btn-sm"
                                          style={{ fontSize: '0.65rem', padding: '1px 6px', background: 'var(--gray-100)' }}
                                          onClick={() => { const u = [...derivedRows]; u[i] = { ...u[i], extractPattern: p.pattern, extractGroup: p.group }; setDerivedRows(u); }}>
                                          {p.label}
                                        </button>
                                      ))}
                                    </div>
                                  </div>
                                  <div>
                                    <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>코드 파라미터 (그룹코드)</label>
                                    <input className="form-input" value={row.lookupParam} style={{ fontSize: '0.8rem', marginTop: '0.15rem' }}
                                      onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], lookupParam: e.target.value }; setDerivedRows(u); }}
                                      placeholder="NGW_0118" />
                                  </div>
                                  <div>
                                    <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>데이터 루트</label>
                                    <input className="form-input" value={row.lookupDataRootPath} style={{ fontSize: '0.8rem', marginTop: '0.15rem' }}
                                      onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], lookupDataRootPath: e.target.value }; setDerivedRows(u); }}
                                      placeholder="data.common" />
                                  </div>
                                  <div>
                                    <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>키 필드</label>
                                    <input className="form-input" value={row.lookupKeyField} style={{ fontSize: '0.8rem', marginTop: '0.15rem' }}
                                      onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], lookupKeyField: e.target.value }; setDerivedRows(u); }}
                                      placeholder="detailCode" />
                                  </div>
                                  <div>
                                    <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>값 필드</label>
                                    <input className="form-input" value={row.lookupValueField} style={{ fontSize: '0.8rem', marginTop: '0.15rem' }}
                                      onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], lookupValueField: e.target.value }; setDerivedRows(u); }}
                                      placeholder="detailCodeName" />
                                  </div>
                                  <div>
                                    <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>기본값 (매칭 실패 시)</label>
                                    <input className="form-input" value={row.defaultValue} style={{ fontSize: '0.8rem', marginTop: '0.15rem' }}
                                      onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], defaultValue: e.target.value }; setDerivedRows(u); }}
                                      placeholder="기타" />
                                  </div>
                                </div>
                              </td>
                            </tr>
                          )}
                        </React.Fragment>
                      );
                    })}
                  </tbody>
                </table>

                {/* 파생 컬럼 추가 버튼 */}
                <div style={{ padding: '0.5rem', borderTop: '1px solid var(--gray-100)' }}>
                  <button className="btn btn-sm" onClick={() => {
                    const sourceFields = mappingRows.filter(r => !r.excluded).map(r => r.sourceFieldPath);
                    setDerivedRows([...derivedRows, {
                      sourceFieldPath: sourceFields[0] || '',
                      targetColumnName: '',
                      isConflictKey: false,
                      transformType: 'LOOKUP',
                      extractPattern: '',
                      extractGroup: 1,
                      lookupParam: '',
                      lookupKeyField: '',
                      lookupValueField: '',
                      lookupDataRootPath: '',
                      lookupMatchType: 'EXACT',
                      defaultValue: '',
                    }]);
                    setExpandedDerived(prev => new Set(prev).add(derivedRows.length));
                  }} style={{ fontSize: '0.8rem', background: 'var(--gray-50)', border: '1px dashed var(--gray-300)', width: '100%', padding: '0.4rem' }}>
                    + 파생 컬럼 추가
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* 등록 버튼 */}
          <div style={{ padding: '0.75rem 1rem', display: 'flex', justifyContent: 'flex-end' }}>
            <button className="btn" onClick={handleCreate} disabled={saving}
              style={{ background: 'var(--success)', color: 'white', padding: '0.5rem 1.5rem' }}>
              {saving ? '등록 중...' : '등록'}
            </button>
          </div>
        </div>
      )}

      {/* API 목록 */}
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
                    <td>
                      <span style={{
                        padding: '2px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
                        background: ep.httpMethod === 'GET' ? '#dbeafe' : '#fef3c7',
                        color: ep.httpMethod === 'GET' ? '#1d4ed8' : '#92400e'
                      }}>{ep.httpMethod}</span>
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
                        background: ep.isActive ? 'var(--success)' : 'var(--gray-400)', marginRight: '6px'
                      }} />
                      {ep.isActive ? '활성' : '비활성'}
                    </td>
                    <td>{ep.zone}</td>
                    <td onClick={e => e.stopPropagation()}>
                      <button className="btn btn-danger btn-sm" onClick={() => handleDelete(ep.id, ep.apiName)}>삭제</button>
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

// --- JSON 트리 뷰 (데이터 루트 선택 가능) ---

function SelectableJsonTreeView({ node, path, selectedRoot, onSelectRoot, depth = 0 }: {
  node: TreeNode; path: string; selectedRoot: string; onSelectRoot: (path: string) => void; depth?: number;
}) {
  const [expanded, setExpanded] = useState(depth < 2);
  const currentPath = path ? (node.name === 'root' ? '' : path + '.' + node.name) : node.name === 'root' ? '' : node.name;
  const isSelected = currentPath === selectedRoot;
  const hasChildren = node.children && node.children.length > 0;
  const isExpandable = node.type === 'object' || node.type === 'array';

  return (
    <div style={{ marginLeft: depth > 0 ? '1.25rem' : 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '2px 0', background: isSelected ? '#dcfce7' : 'transparent', borderRadius: '3px' }}>
        {isExpandable && hasChildren ? (
          <span onClick={() => setExpanded(!expanded)} style={{ cursor: 'pointer', width: '16px', textAlign: 'center', userSelect: 'none' }}>
            {expanded ? '▾' : '▸'}
          </span>
        ) : (
          <span style={{ width: '16px', textAlign: 'center' }}>-</span>
        )}
        <span style={{ fontWeight: isExpandable ? 600 : 400 }}>{node.name === 'root' ? '(root)' : node.name}</span>
        <span style={{ color: 'var(--gray-400)', fontSize: '0.75rem' }}>({node.type}{node.arraySize != null ? `, ${node.arraySize}건` : ''})</span>
        {node.sampleValue != null && (
          <span style={{ color: 'var(--gray-500)', fontSize: '0.75rem' }}>
            {node.sampleValue.length > 40 ? `"${node.sampleValue.substring(0, 40)}..."` : `"${node.sampleValue}"`}
          </span>
        )}
        {node.type === 'array' && (
          <button onClick={() => onSelectRoot(currentPath || 'root')} className="btn btn-sm"
            style={{ fontSize: '0.7rem', padding: '1px 6px', marginLeft: '0.5rem',
              background: isSelected ? 'var(--success)' : 'var(--gray-200)', color: isSelected ? 'white' : 'var(--gray-700)' }}>
            {isSelected ? '선택됨' : '데이터 루트 선택'}
          </button>
        )}
      </div>
      {expanded && hasChildren && node.children!.map((child, i) => (
        <SelectableJsonTreeView key={`${child.name}-${i}`} node={child} path={currentPath} selectedRoot={selectedRoot} onSelectRoot={onSelectRoot} depth={depth + 1} />
      ))}
    </div>
  );
}

// --- 유틸 ---

function findFieldsFromTree(tree: TreeNode, path: string): { path: string; type: string; sample: string | null }[] {
  const node = navigateTree(tree, path);
  if (!node || !node.children) return [];
  const fields: { path: string; type: string; sample: string | null }[] = [];
  collectLeafFields(node.children, '', fields);
  return fields;
}

function navigateTree(node: TreeNode, path: string): TreeNode | null {
  if (!path || path === 'root') return node;
  const parts = path.split('.');
  let current: TreeNode | null = node;
  if (current.name === 'root' && current.children) {
    for (const part of parts) {
      if (!current?.children) return null;
      current = current.children.find(c => c.name === part) || null;
    }
  }
  return current;
}

function collectLeafFields(children: TreeNode[], prefix: string, fields: { path: string; type: string; sample: string | null }[]) {
  for (const child of children) {
    const path = prefix ? `${prefix}.${child.name}` : child.name;
    if (child.type === 'object' && child.children) {
      collectLeafFields(child.children, path, fields);
    } else if (child.type !== 'array') {
      fields.push({ path, type: child.type, sample: child.sampleValue });
    }
  }
}
