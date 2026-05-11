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
} from '@/types/api-collect';
import styles from './InfoTab.module.css';

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

  const headerParams = params.map((p, i) => ({ ...p, _idx: i })).filter(p => p.paramType === 'HEADER');
  const queryParams = params.map((p, i) => ({ ...p, _idx: i })).filter(p => p.paramType !== 'HEADER');

  return (
    <div>
      {/* 기본정보 섹션 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">기본정보</h2>
          <button type="button" className="krds-btn small" onClick={handleSaveInfo} disabled={saving}>
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>

        {/* 요청 설정 */}
        <div className={styles.section}>
          <div className={styles.sectionLabel}>{isCustom ? '프리셋 정보' : '요청'}</div>
          <div className={styles.gridForm}>
            <div className="app-form-field">
              <label className="app-form-label">API명</label>
              <input className="krds-input small" value={form.apiName}
                onChange={e => setForm({ ...form, apiName: e.target.value })} />
            </div>
            {isCustom && (
              <div className="app-form-field">
                <label className="app-form-label">실행기</label>
                <select className="krds-input small" value={executorType}
                  onChange={e => setExecutorType(e.target.value)}>
                  {customExecutors.map(ce => (
                    <option key={ce.id} value={ce.id}>{ce.displayName}</option>
                  ))}
                </select>
              </div>
            )}
            <div className="app-form-field">
              <label className="app-form-label">Target Datasource</label>
              <select className="krds-input small" value={targetDsId}
                onChange={e => setTargetDsId(e.target.value)}>
                <option value="">-- 선택 --</option>
                {datasources.map(ds => (
                  <option key={ds.datasourceId} value={ds.datasourceId}>
                    {ds.datasourceId} ({ds.dbType})
                  </option>
                ))}
              </select>
            </div>
            <div className={`app-form-field ${styles.fullSpan}`}>
              <label className="app-form-label">URL</label>
              <input className="krds-input small" value={form.url}
                onChange={e => setForm({ ...form, url: e.target.value })} />
            </div>
            <div className="app-form-field">
              <label className="app-form-label">HTTP Method</label>
              <select className="krds-input small" value={form.httpMethod}
                onChange={e => setForm({ ...form, httpMethod: e.target.value })}>
                <option value="GET">GET</option>
                <option value="POST">POST</option>
              </select>
            </div>
            {!isCustom && (
              <div className="app-form-field">
                <label className="app-form-label">Content-Type</label>
                <select className="krds-input small" value={form.contentType || ''}
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
          <div className={styles.section}>
            <div className={styles.sectionLabel}>인증</div>
            <div className={styles.gridForm}>
              <div className="app-form-field">
                <label className="app-form-label">인증 유형</label>
                <select className="krds-input small" value={form.authType}
                  onChange={e => setForm({ ...form, authType: e.target.value as AuthType })}>
                  <option value="NONE">없음</option>
                  <option value="BASIC">Basic Auth</option>
                  <option value="BEARER">Bearer Token</option>
                </select>
              </div>
              {form.authType === 'BASIC' && (
                <div className="app-form-field">
                  <label className="app-form-label">인증 설정 (JSON)</label>
                  <input className="krds-input small" value={form.authConfig || ''}
                    onChange={e => setForm({ ...form, authConfig: e.target.value })}
                    placeholder='{"username": "...", "password": "..."}' />
                </div>
              )}
              {form.authType === 'BEARER' && (
                <div className="app-form-field">
                  <label className="app-form-label">Bearer Token</label>
                  <input className="krds-input small" value={form.authConfig || ''}
                    onChange={e => setForm({ ...form, authConfig: e.target.value })}
                    placeholder='토큰 값 입력' />
                </div>
              )}
            </div>
          </div>
        )}

        {/* 기타 */}
        <div className={styles.section}>
          <div className="app-form-field">
            <label className="app-form-label">설명</label>
            <input className="krds-input small" value={form.description || ''}
              onChange={e => setForm({ ...form, description: e.target.value })}
              placeholder="API 설명 (선택)" />
          </div>
        </div>
      </div>

      {/* 테스트 호출 (커스텀) */}
      {isCustom && (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">API 연결 테스트</h2>
            <button type="button" className="krds-btn small" disabled={customTesting} onClick={async () => {
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
            }}>
              {customTesting ? '호출 중...' : '테스트 호출'}
            </button>
          </div>
          {customTestResult && (
            <div className={`app-alert ${customTestResult.success ? 'app-alert--success' : 'app-alert--danger'} ${styles.testResultBox}`}>
              {customTestResult.success
                ? `HTTP ${customTestResult.httpStatusCode} — 응답 수신 성공`
                : `실패: ${customTestResult.errorMessage || 'HTTP ' + customTestResult.httpStatusCode}`}
            </div>
          )}
        </div>
      )}

      {/* 헤더 섹션 (paramType=HEADER) */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">헤더</h2>
          <button type="button" className="krds-btn small" onClick={() => {
            setParams([...params, { paramName: '', paramType: 'HEADER', valueType: 'STATIC', staticValue: '', isApiKeyRef: false, displayOrder: params.length }]);
          }}>+ 추가</button>
        </div>
        {headerParams.length === 0 ? (
          <div className="app-empty">헤더 없음</div>
        ) : (
          <div className={styles.paramGrid}>
            <div className={styles.headerGridHeader}>
              <div>헤더명</div>
              <div>값 유형</div>
              <div>값 설정</div>
              <div>설명</div>
              <div></div>
            </div>
            {headerParams.map(hp => {
              const i = hp._idx;
              const p = params[i];
              return (
                <div key={i} className={styles.headerGridRow}>
                  <input className="krds-input small" value={p.paramName}
                    onChange={e => updateParam(i, 'paramName', e.target.value)} placeholder="Header Name" />
                  <select className="krds-input small" value={p.isApiKeyRef ? 'APIKEY' : 'STATIC'}
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
                  {p.isApiKeyRef ? (
                    <select className="krds-input small"
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
                    <input className="krds-input small" value={p.staticValue || ''}
                      onChange={e => updateParam(i, 'staticValue', e.target.value)} placeholder="값 입력" />
                  )}
                  <input className="krds-input small" value={p.description || ''}
                    onChange={e => updateParam(i, 'description', e.target.value)} placeholder="설명" />
                  <button type="button" className="krds-btn small app-btn-danger"
                    onClick={() => removeParam(i)}>X</button>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 파라미터 섹션 (QUERY/BODY/PATH) */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">호출 파라미터</h2>
          <div className={styles.cardActions}>
            <button type="button" className="krds-btn small secondary" onClick={addParam}>+ 추가</button>
            <button type="button" className="krds-btn small" onClick={handleSaveParams} disabled={paramsSaving}>
              {paramsSaving ? '저장 중...' : '저장'}
            </button>
          </div>
        </div>
        {queryParams.length === 0 ? (
          <div className="app-empty">파라미터가 없습니다.</div>
        ) : (
          <div className={styles.paramGrid}>
            <div className={styles.paramGridHeader}>
              <div>파라미터명</div>
              <div>위치</div>
              <div>값 유형</div>
              <div>값 설정</div>
              <div>설명</div>
              <div></div>
            </div>
            {queryParams.map(qp => {
              const i = qp._idx;
              const p = params[i];
              return (
                <div key={i} className={styles.paramGridRow}>
                  <input className="krds-input small" value={p.paramName}
                    onChange={e => updateParam(i, 'paramName', e.target.value)} />
                  <select className="krds-input small" value={p.paramType}
                    onChange={e => updateParam(i, 'paramType', e.target.value)}>
                    <option value="QUERY">쿼리 (?key=val)</option>
                    <option value="BODY">본문 (POST body)</option>
                    <option value="PATH">경로 (/path/{'{id}'})</option>
                  </select>
                  <select className="krds-input small" value={p.isApiKeyRef ? 'APIKEY' : p.valueType}
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
                  {p.isApiKeyRef ? (
                    <select className="krds-input small"
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
                    <input className="krds-input small" value={p.staticValue || ''}
                      onChange={e => updateParam(i, 'staticValue', e.target.value)}
                      placeholder="고정값 입력" />
                  ) : (
                    <div className={styles.dynamicCell}>
                      <select className={`krds-input small ${styles.dynamicType}`} value={p.dynamicType || 'TODAY'}
                        onChange={e => updateParam(i, 'dynamicType', e.target.value)}>
                        <option value="TODAY">날짜</option>
                        <option value="NOW">시간</option>
                        <option value="YEAR">연도</option>
                      </select>
                      <input className={`krds-input small ${styles.dynamicFormat}`} value={p.dynamicFormat || ''}
                        onChange={e => updateParam(i, 'dynamicFormat', e.target.value)}
                        placeholder="yyyyMMdd" />
                      <input className={`krds-input small ${styles.dynamicOffset}`} type="number" value={p.dynamicOffset ?? 0}
                        onChange={e => updateParam(i, 'dynamicOffset', parseInt(e.target.value) || 0)}
                        title="오프셋 (예: -1 = 어제)" />
                    </div>
                  )}
                  <input className="krds-input small" value={p.description || ''}
                    onChange={e => updateParam(i, 'description', e.target.value)}
                    placeholder="설명 (선택)" />
                  <button type="button" className="krds-btn small app-btn-danger"
                    onClick={() => removeParam(i)}>X</button>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
