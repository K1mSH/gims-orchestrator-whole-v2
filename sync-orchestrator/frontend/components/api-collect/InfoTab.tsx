'use client';

import { useCallback, useEffect, useState } from 'react';
import { endpointApi, paramApi, apiKeyApi, testApi, customExecutorApi, ApiKeyItem, TestCallResponse, CustomExecutorItem } from '@/lib/collectorApi';
import { datasourceApi } from '@/lib/api';
import type { DatasourceSimple } from '@/types';
import {
  ApiEndpointDetail,
  ApiEndpointUpdateRequest,
  ApiParamRequest,
  AuthType,
  ParamType,
  ValueType,
  DynamicType,
} from '@/types/api-collect';

interface InfoTabProps {
  endpoint: ApiEndpointDetail;
  onUpdate: () => void;
}


export default function InfoTab({ endpoint, onUpdate }: InfoTabProps) {
  const isCustom = !!endpoint.executorType;
  // --- 실행기 목록 (커스텀용) ---
  const [executorType, setExecutorType] = useState<string>(endpoint.executorType || '');
  const [customExecutors, setCustomExecutors] = useState<CustomExecutorItem[]>([]);
  useEffect(() => {
    if (isCustom) {
      customExecutorApi.getAll().then(setCustomExecutors).catch(() => {});
    }
  }, [isCustom]);

  // --- Target Datasource ---
  const [targetDsId, setTargetDsId] = useState<string>(endpoint.targetDatasourceId || '');
  const [datasources, setDatasources] = useState<DatasourceSimple[]>([]);
  useEffect(() => {
    datasourceApi.getSimple().then(setDatasources).catch(() => {});
  }, []);

  // --- 기본정보 ---
  const [form, setForm] = useState<ApiEndpointUpdateRequest>({
    apiName: endpoint.apiName,
    url: endpoint.url,
    httpMethod: endpoint.httpMethod,
    contentType: endpoint.contentType || '',
    authType: endpoint.authType,
    authConfig: endpoint.authConfig || '',
    description: endpoint.description || '',
    isActive: endpoint.isActive,
  });
  const [saving, setSaving] = useState(false);

  // --- 파라미터 ---
  const [params, setParams] = useState<ApiParamRequest[]>(
    endpoint.params.map(p => ({
      paramName: p.paramName,
      paramType: p.paramType,
      valueType: p.valueType,
      staticValue: p.staticValue || '',
      isApiKeyRef: p.isApiKeyRef || false,
      dynamicType: p.dynamicType || undefined,
      dynamicFormat: p.dynamicFormat || '',
      dynamicOffset: p.dynamicOffset ?? 0,
      description: p.description || '',
      displayOrder: p.displayOrder,
    }))
  );
  const [paramsSaving, setParamsSaving] = useState(false);

  // --- 테스트 호출 (커스텀용) ---
  const [customTesting, setCustomTesting] = useState(false);
  const [customTestResult, setCustomTestResult] = useState<TestCallResponse | null>(null);

  // API 키 목록
  const [apiKeys, setApiKeys] = useState<ApiKeyItem[]>([]);
  const [apiKeysLoaded, setApiKeysLoaded] = useState(false);
  const loadApiKeys = useCallback(async () => {
    if (apiKeysLoaded) return;
    try {
      const keys = await apiKeyApi.getAll();
      setApiKeys(keys);
      setApiKeysLoaded(true);
    } catch { /* 무시 */ }
  }, [apiKeysLoaded]);

  // isApiKeyRef인 파라미터가 있으면 진입 시 API키 목록 미리 로드
  useEffect(() => {
    if (endpoint.params.some(p => p.isApiKeyRef)) {
      loadApiKeys();
    }
  }, [endpoint.params, loadApiKeys]);

  const handleSaveInfo = async () => {
    try {
      setSaving(true);
      const trimmed = {
        ...form,
        apiName: form.apiName?.trim(),
        url: form.url?.trim(),
        contentType: form.contentType?.trim(),
        authConfig: form.authConfig?.trim(),
        description: form.description?.trim(),
        targetDatasourceId: targetDsId.trim() || undefined,
        executorType: executorType || undefined,
      };
      await endpointApi.update(endpoint.id, trimmed);
      alert('저장되었습니다.');
      onUpdate();
    } catch (e: any) {
      alert('저장 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  const handleSaveParams = async () => {
    try {
      setParamsSaving(true);
      const trimmedParams = params.map(p => ({
        ...p,
        paramName: p.paramName?.trim(),
        staticValue: p.staticValue?.trim(),
        dynamicFormat: p.dynamicFormat?.trim(),
        description: p.description?.trim(),
      }));
      await paramApi.save(endpoint.id, trimmedParams);
      alert('파라미터가 저장되었습니다.');
      onUpdate();
    } catch (e: any) {
      alert('파라미터 저장 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setParamsSaving(false);
    }
  };

  const addParam = () => {
    setParams([...params, {
      paramName: '',
      paramType: 'QUERY',
      valueType: 'STATIC',
      staticValue: '',
      isApiKeyRef: false,
      displayOrder: params.length,
    }]);
  };

  const removeParam = (index: number) => {
    setParams(params.filter((_, i) => i !== index));
  };

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

  const sectionStyle = {
    padding: '0.75rem 1rem',
    borderBottom: '1px solid var(--gray-100)',
  } as const;

  const sectionLabel = {
    fontSize: '0.75rem',
    color: 'var(--gray-400)',
    marginBottom: '0.5rem',
    fontWeight: 600,
  } as const;

  const labelStyle = {
    fontSize: '0.8rem',
    color: 'var(--gray-500)',
    marginBottom: '0.25rem',
    fontWeight: 500,
  } as const;

  return (
    <div>
      {/* 기본정보 섹션 */}
      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <div className="card-header"><h3 className="card-title">기본정보</h3></div>

        {/* 요청 설정 */}
        <div style={sectionStyle}>
          <div style={sectionLabel}>{isCustom ? '프리셋 정보' : '요청'}</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '0.75rem', alignItems: 'start' }}>
            <div>
              <div style={labelStyle}>API명</div>
              <input className="form-input" value={form.apiName}
                onChange={e => setForm({ ...form, apiName: e.target.value })} />
            </div>
            {isCustom && (
              <div>
                <div style={labelStyle}>실행기</div>
                <select className="form-select" value={executorType}
                  onChange={e => setExecutorType(e.target.value)}>
                  {customExecutors.map(ce => (
                    <option key={ce.id} value={ce.id}>{ce.displayName}</option>
                  ))}
                </select>
              </div>
            )}
            <div>
              <div style={labelStyle}>Target Datasource</div>
              <select className="form-select" value={targetDsId}
                onChange={e => setTargetDsId(e.target.value)}>
                <option value="">-- 선택 --</option>
                {datasources.map(ds => (
                  <option key={ds.datasourceId} value={ds.datasourceId}>
                    {ds.datasourceId} ({ds.dbType})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <div style={labelStyle}>URL</div>
              <input className="form-input" value={form.url}
                onChange={e => setForm({ ...form, url: e.target.value })} />
            </div>
            <div>
              <div style={labelStyle}>HTTP Method</div>
              <select className="form-select" value={form.httpMethod}
                onChange={e => setForm({ ...form, httpMethod: e.target.value })}>
                <option value="GET">GET</option>
                <option value="POST">POST</option>
              </select>
            </div>
            {!isCustom && (
              <div>
                <div style={labelStyle}>Content-Type</div>
                <select className="form-select" value={form.contentType || ''}
                  onChange={e => setForm({ ...form, contentType: e.target.value })}>
                  <option value="">기본 (없음)</option>
                  <option value="application/json">application/json</option>
                  <option value="application/x-www-form-urlencoded">application/x-www-form-urlencoded</option>
                  <option value="multipart/form-data">multipart/form-data</option>
                </select>
              </div>
            )}
          </div>
        </div>

        {/* 인증 설정 — 커스텀은 숨김 */}
        {!isCustom && (
        <div style={sectionStyle}>
          <div style={sectionLabel}>인증</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '0.75rem', alignItems: 'start' }}>
            <div>
              <div style={labelStyle}>인증 유형</div>
              <select className="form-select" value={form.authType}
                onChange={e => setForm({ ...form, authType: e.target.value as AuthType })}>
                <option value="NONE">없음</option>
                <option value="BASIC">Basic Auth</option>
                <option value="BEARER">Bearer Token</option>
              </select>
            </div>
            {form.authType === 'BASIC' && (
              <div>
                <div style={labelStyle}>인증 설정 (JSON)</div>
                <input className="form-input" value={form.authConfig || ''}
                  onChange={e => setForm({ ...form, authConfig: e.target.value })}
                  placeholder='{"username": "...", "password": "..."}' />
              </div>
            )}
            {form.authType === 'BEARER' && (
              <div>
                <div style={labelStyle}>Bearer Token</div>
                <input className="form-input" value={form.authConfig || ''}
                  onChange={e => setForm({ ...form, authConfig: e.target.value })}
                  placeholder='토큰 값 입력' />
              </div>
            )}
          </div>
        </div>
        )}

        {/* 기타 */}
        <div style={{ ...sectionStyle, borderBottom: 'none' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr auto auto', gap: '0.75rem', alignItems: 'end' }}>
            <div>
              <div style={labelStyle}>설명</div>
              <input className="form-input" value={form.description || ''}
                onChange={e => setForm({ ...form, description: e.target.value })}
                placeholder="API 설명 (선택)" />
            </div>
            <label style={{ fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer', paddingBottom: '0.5rem' }}>
              <input type="checkbox" checked={form.isActive ?? true}
                onChange={e => setForm({ ...form, isActive: e.target.checked })} />
              활성 (실행 가능)
            </label>
            <button className="btn btn-primary" onClick={handleSaveInfo} disabled={saving}>
              {saving ? '저장 중...' : '기본정보 저장'}
            </button>
          </div>
        </div>
      </div>

      {/* 테스트 호출 (커스텀) */}
      {isCustom && (
        <div className="card" style={{ marginBottom: '1.5rem' }}>
          <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 className="card-title">API 연결 테스트</h3>
            <button className="btn btn-primary" onClick={async () => {
              try {
                setCustomTesting(true);
                setCustomTestResult(null);
                const result = await testApi.call(endpoint.id);
                setCustomTestResult(result);
              } catch (e: any) {
                setCustomTestResult({ success: false, httpStatusCode: 0, errorMessage: e.message, responseTree: null, dataRootPath: null, fields: null, resolvedParams: {} });
              } finally {
                setCustomTesting(false);
              }
            }} disabled={customTesting}>
              {customTesting ? '호출 중...' : '테스트 호출'}
            </button>
          </div>
          {customTestResult && (
            <div style={{ padding: '0.75rem 1rem' }}>
              <div style={{
                padding: '0.5rem 0.75rem',
                borderRadius: '4px',
                fontSize: '0.85rem',
                background: customTestResult.success ? '#f0fdf4' : '#fef2f2',
                color: customTestResult.success ? '#166534' : '#991b1b',
                border: `1px solid ${customTestResult.success ? '#bbf7d0' : '#fecaca'}`,
              }}>
                {customTestResult.success
                  ? `HTTP ${customTestResult.httpStatusCode} — 응답 수신 성공`
                  : `실패: ${customTestResult.errorMessage || 'HTTP ' + customTestResult.httpStatusCode}`}
              </div>
            </div>
          )}
        </div>
      )}

      {/* 헤더 섹션 (paramType=HEADER) */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 className="card-title">헤더</h3>
          <button className="btn btn-sm btn-primary" onClick={() => {
            setParams([...params, { paramName: '', paramType: 'HEADER', valueType: 'STATIC', staticValue: '', isApiKeyRef: false, displayOrder: params.length }]);
          }}>+ 추가</button>
        </div>
        <div style={{ padding: '1rem' }}>
          {(() => {
            const headerParams = params.map((p, i) => ({ ...p, _idx: i })).filter(p => p.paramType === 'HEADER');
            return headerParams.length === 0 ? (
              <div style={{ textAlign: 'center', color: 'var(--gray-500)', padding: '0.5rem' }}>헤더 없음</div>
            ) : (
              <table style={{ width: '100%', fontSize: '0.85rem' }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left', padding: '0.5rem' }}>헤더명</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem' }}>값 유형</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem' }}>값 설정</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem' }}>설명</th>
                    <th style={{ width: '50px' }}></th>
                  </tr>
                </thead>
                <tbody>
                  {headerParams.map(hp => {
                    const i = hp._idx;
                    const p = params[i];
                    return (
                      <tr key={i}>
                        <td style={{ padding: '0.25rem' }}>
                          <input className="form-input" value={p.paramName} style={{ fontSize: '0.85rem' }}
                            onChange={e => updateParam(i, 'paramName', e.target.value)} placeholder="Header Name" />
                        </td>
                        <td style={{ padding: '0.25rem' }}>
                          <select className="form-select" value={p.isApiKeyRef ? 'APIKEY' : 'STATIC'} style={{ fontSize: '0.85rem' }}
                            onChange={e => {
                              if (e.target.value === 'APIKEY') {
                                loadApiKeys();
                                const u = [...params]; u[i] = { ...u[i], isApiKeyRef: true, staticValue: '' }; setParams(u);
                              } else {
                                const u = [...params]; u[i] = { ...u[i], isApiKeyRef: false }; setParams(u);
                              }
                            }}>
                            <option value="STATIC">직접입력</option>
                            <option value="APIKEY">🔑 API키</option>
                          </select>
                        </td>
                        <td style={{ padding: '0.25rem' }}>
                          {p.isApiKeyRef ? (
                            <select className="form-select" style={{ fontSize: '0.85rem' }}
                              value={p.staticValue || ''} onFocus={() => loadApiKeys()}
                              onChange={e => {
                                const key = apiKeys.find(k => String(k.id) === e.target.value);
                                const u = [...params]; u[i] = { ...u[i], staticValue: e.target.value, description: key ? `🔑 ${key.serviceName}` : '' }; setParams(u);
                              }}>
                              <option value="">-- API 키 선택 --</option>
                              {apiKeys.filter(k => k.useAt === 'Y').map(k => (
                                <option key={k.id} value={String(k.id)}>{k.serviceName} (D-{k.dday})</option>
                              ))}
                            </select>
                          ) : (
                            <input className="form-input" value={p.staticValue || ''} style={{ fontSize: '0.85rem' }}
                              onChange={e => updateParam(i, 'staticValue', e.target.value)} placeholder="값 입력" />
                          )}
                        </td>
                        <td style={{ padding: '0.25rem' }}>
                          <input className="form-input" value={p.description || ''} style={{ fontSize: '0.85rem' }}
                            onChange={e => updateParam(i, 'description', e.target.value)} placeholder="설명" />
                        </td>
                        <td style={{ padding: '0.25rem' }}>
                          <button className="btn btn-danger btn-sm" onClick={() => removeParam(i)}
                            style={{ fontSize: '0.75rem' }}>X</button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            );
          })()}
        </div>
      </div>

      {/* 파라미터 섹션 (QUERY/BODY/PATH) */}
      <div className="card">
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 className="card-title">호출 파라미터</h3>
          <button className="btn btn-sm btn-primary" onClick={addParam}>+ 추가</button>
        </div>
        <div style={{ padding: '1rem' }}>
          {(() => {
            const queryParams = params.map((p, i) => ({ ...p, _idx: i })).filter(p => p.paramType !== 'HEADER');
            return queryParams.length === 0 ? (
              <div style={{ textAlign: 'center', color: 'var(--gray-500)', padding: '1rem' }}>파라미터가 없습니다.</div>
            ) : (
            <table style={{ width: '100%', fontSize: '0.85rem' }}>
              <thead>
                <tr>
                  <th style={{ textAlign: 'left', padding: '0.5rem' }}>파라미터명</th>
                  <th style={{ textAlign: 'left', padding: '0.5rem' }}>위치</th>
                  <th style={{ textAlign: 'left', padding: '0.5rem' }}>값 유형</th>
                  <th style={{ textAlign: 'left', padding: '0.5rem' }}>값 설정</th>
                  <th style={{ textAlign: 'left', padding: '0.5rem' }}>설명</th>
                  <th style={{ width: '50px' }}></th>
                </tr>
              </thead>
              <tbody>
                {queryParams.map(qp => {
                  const i = qp._idx;
                  const p = params[i];
                  return (
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
                      </select>
                    </td>
                    <td style={{ padding: '0.25rem' }}>
                      <select className="form-select" value={p.isApiKeyRef ? 'APIKEY' : p.valueType} style={{ fontSize: '0.85rem' }}
                        onChange={e => {
                          const v = e.target.value;
                          if (v === 'APIKEY') {
                            loadApiKeys();
                            const u = [...params]; u[i] = { ...u[i], valueType: 'STATIC', isApiKeyRef: true, staticValue: '' }; setParams(u);
                          } else {
                            const u = [...params]; u[i] = { ...u[i], valueType: v as any, isApiKeyRef: false }; setParams(u);
                          }
                        }}>
                        <option value="STATIC">직접입력</option>
                        <option value="APIKEY">🔑 API키</option>
                        <option value="DYNAMIC">동적</option>
                      </select>
                    </td>
                    <td style={{ padding: '0.25rem' }}>
                      {p.isApiKeyRef ? (
                        <select className="form-select" style={{ fontSize: '0.85rem' }}
                          value={p.staticValue || ''} onFocus={() => loadApiKeys()}
                          onChange={e => {
                            const key = apiKeys.find(k => String(k.id) === e.target.value);
                            const u = [...params]; u[i] = { ...u[i], staticValue: e.target.value, description: key ? `🔑 ${key.serviceName}` : '' }; setParams(u);
                          }}>
                          <option value="">-- API 키 선택 --</option>
                          {apiKeys.filter(k => k.useAt === 'Y').map(k => (
                            <option key={k.id} value={String(k.id)}>{k.serviceName} (D-{k.dday})</option>
                          ))}
                        </select>
                      ) : p.valueType === 'STATIC' ? (
                        <input className="form-input" value={p.staticValue || ''} style={{ fontSize: '0.85rem' }}
                          onChange={e => updateParam(i, 'staticValue', e.target.value)}
                          placeholder="고정값 입력" />
                      ) : (
                        <div style={{ display: 'flex', gap: '0.25rem' }}>
                          <select className="form-select" value={p.dynamicType || 'TODAY'}
                            style={{ fontSize: '0.85rem', width: '80px' }}
                            onChange={e => updateParam(i, 'dynamicType', e.target.value)}>
                            <option value="TODAY">날짜</option>
                            <option value="NOW">시간</option>
                            <option value="YEAR">연도</option>
                          </select>
                          <input className="form-input" value={p.dynamicFormat || ''}
                            style={{ fontSize: '0.85rem', width: '100px' }}
                            onChange={e => updateParam(i, 'dynamicFormat', e.target.value)}
                            placeholder="yyyyMMdd" />
                          <input className="form-input" type="number" value={p.dynamicOffset ?? 0}
                            style={{ fontSize: '0.85rem', width: '60px' }}
                            onChange={e => updateParam(i, 'dynamicOffset', parseInt(e.target.value) || 0)}
                            title="오프셋 (예: -1 = 어제)" />
                        </div>
                      )}
                    </td>
                    <td style={{ padding: '0.25rem' }}>
                      <input className="form-input" value={p.description || ''} style={{ fontSize: '0.85rem' }}
                        onChange={e => updateParam(i, 'description', e.target.value)}
                        placeholder="설명 (선택)" />
                    </td>
                    <td style={{ padding: '0.25rem' }}>
                      <button className="btn btn-danger btn-sm" onClick={() => removeParam(i)}
                        style={{ fontSize: '0.75rem' }}>X</button>
                    </td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
            );
          })()}
          <div style={{ textAlign: 'right', marginTop: '1rem' }}>
            <button className="btn btn-primary" onClick={handleSaveParams} disabled={paramsSaving}>
              {paramsSaving ? '저장 중...' : '파라미터 저장'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
