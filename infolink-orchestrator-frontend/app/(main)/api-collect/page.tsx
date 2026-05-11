'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { endpointApi, testApi, paramApi, mappingApi, apiKeyApi, customExecutorApi, ApiKeyItem, CustomExecutorItem, TestCallResponse, TreeNode, InlineTestRequest } from '@/lib/collectorApi';
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
import styles from './page.module.css';

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

  // 헤더 (레거시 — headerRows 제거, params에 paramType=HEADER로 통합)

  // 파라미터
  const [params, setParams] = useState<ApiParamRequest[]>([]);

  // 실행 방식
  const [executorType, setExecutorType] = useState<string>('');  // '' = 범용
  const [customExecutors, setCustomExecutors] = useState<CustomExecutorItem[]>([]);

  // API 키 목록 (GIMS 본체)
  const [apiKeys, setApiKeys] = useState<ApiKeyItem[]>([]);
  const [apiKeysLoaded, setApiKeysLoaded] = useState(false);

  // 테스트 호출
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<TestCallResponse | null>(null);

  // 커스텀 인라인 테스트
  const [customTesting, setCustomTesting] = useState(false);
  const [customTestResult, setCustomTestResult] = useState<TestCallResponse | null>(null);

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
      data.sort((a, b) => a.apiName.localeCompare(b.apiName, 'ko'));
      setEndpoints(data);
    } catch (e) {
      console.error('목록 조회 실패:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchEndpoints(); }, [fetchEndpoints]);

  // API 키 목록 로드
  const loadApiKeys = useCallback(async () => {
    if (apiKeysLoaded) return;
    try {
      const keys = await apiKeyApi.getAll();
      setApiKeys(keys);
      setApiKeysLoaded(true);
    } catch { /* 실패해도 무시 — 수동 입력 가능 */ }
  }, [apiKeysLoaded]);

  // Datasource 목록 + 커스텀 실행기 로드
  const refreshDatasources = useCallback(() => {
    datasourceApi.getSimple().then(setDatasources).catch(() => {});
  }, []);
  useEffect(() => { refreshDatasources(); }, [refreshDatasources]);
  useEffect(() => { customExecutorApi.getAll().then(setCustomExecutors).catch(() => {}); }, []);

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
    setCustomTestResult(null);
    setExecutorType('');
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


  // 테스트 호출 (인라인)
  const handleTestCall = async () => {
    if (!form.url) { alert('URL을 입력하세요.'); return; }
    try {
      setTesting(true);
      const inlineReq: InlineTestRequest = {
        url: form.url,
        httpMethod: form.httpMethod,
        contentType: form.contentType,
        authType: form.authType,
        authConfig: form.authConfig,
        params: params.filter(p => p.paramName).map(p => ({
          paramName: p.paramName,
          paramType: p.paramType || 'QUERY',
          valueType: p.valueType || 'STATIC',
          staticValue: p.staticValue,
          isApiKeyRef: p.isApiKeyRef || false,
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
  const isCustom = !!executorType;

  // 등록 가능 여부 — 미설정 항목 목록 계산
  const createMissing = (() => {
    const missing: string[] = [];
    if (!form.apiName.trim()) missing.push('API명');
    if (!form.url.trim()) missing.push('URL');
    if (!testResult?.success) missing.push('테스트 호출 성공');
    if (!isCustom) {
      if (!selectedDataRoot) missing.push('데이터 루트');
      if (!selectedDatasourceId) missing.push('Target Datasource');
      if (!targetTable.trim()) missing.push('Target 테이블');
      const activeMappings = mappingRows.filter(r => r.targetColumnName);
      const activeDerived = derivedRows.filter(r => r.targetColumnName);
      if (activeMappings.length === 0 && activeDerived.length === 0) missing.push('필드 매핑');
      const hasConflictKey = [...activeMappings, ...activeDerived].some(r => r.isConflictKey);
      if (upsertEnabled && !hasConflictKey) missing.push('UPSERT 중복키');
    }
    return missing;
  })();
  const canCreate = createMissing.length === 0;

  const handleCreate = async () => {
    // 1. 기본정보
    if (!form.apiName.trim()) { alert('API명을 입력하세요.'); return; }
    if (!form.url.trim()) { alert('URL을 입력하세요.'); return; }

    if (isCustom) {
      // 커스텀: 테스트 호출 통과 필수
      if (!testResult?.success) { alert('API 연결 테스트를 먼저 실행하세요.'); return; }
    }

    if (!isCustom) {
      // 범용: URL + 테스트/매핑 검증
      if (!form.url.trim()) { alert('URL을 입력하세요.'); return; }
      // 2. 테스트 호출 성공
      if (!testResult?.success) { alert('테스트 호출을 먼저 실행하세요.'); return; }
      // 3. 데이터 루트
      if (!selectedDataRoot) { alert('데이터 루트를 선택하세요.'); return; }
      // 4. 타겟 설정
      if (!selectedDatasourceId) { alert('Target Datasource를 선택하세요.'); return; }
      if (!targetTable.trim()) { alert('Target 테이블을 선택하세요.'); return; }
      // 5. 매핑
      const activeRows = mappingRows.filter(r => r.targetColumnName);
      const activeDerived = derivedRows.filter(r => r.targetColumnName);
      if (activeRows.length === 0 && activeDerived.length === 0) { alert('필드 매핑을 1개 이상 설정하세요.'); return; }
      const hasConflictKey = [...activeRows, ...activeDerived].some(r => r.isConflictKey);
      if (upsertEnabled && !hasConflictKey) { alert('UPSERT 사용 시 중복키를 1개 이상 지정하세요.'); return; }
    }

    try {
      setSaving(true);

      // Step 1: endpoint 생성 (기본정보 + 적재설정 한번에) — trim 처리
      const submitForm = {
        ...form,
        apiName: form.apiName?.trim(),
        url: form.url?.trim(),
        contentType: form.contentType?.trim(),
        authConfig: form.authConfig?.trim(),
        description: form.description?.trim(),
        executorType: executorType || undefined,
        dataRootPath: isCustom ? undefined : selectedDataRoot,
        targetDatasourceId: isCustom ? (selectedDatasourceId || undefined) : selectedDatasourceId,
        targetTableName: isCustom ? undefined : targetTable?.trim(),
        upsertEnabled: isCustom ? undefined : upsertEnabled,
      };
      const created = await endpointApi.create(submitForm);
      const endpointId = created.id;

      try {
        // Step 2: params 저장
        if (params.filter(p => p.paramName).length > 0) {
          const trimmedParams = params.filter(p => p.paramName).map(p => ({
            ...p,
            paramName: p.paramName?.trim(),
            staticValue: p.staticValue?.trim(),
            dynamicFormat: p.dynamicFormat?.trim(),
            description: p.description?.trim(),
          }));
          await paramApi.save(endpointId, trimmedParams);
        }

        // Step 3: mappings 저장 (범용만)
        if (!isCustom) {
          const activeRows = mappingRows.filter(r => r.targetColumnName);
          const activeDerived = derivedRows.filter(r => r.targetColumnName);
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
        }

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
    setParams([...params, { paramName: '', paramType: 'QUERY', valueType: 'STATIC', staticValue: '', isApiKeyRef: false, displayOrder: params.length }]);
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

  return (
    <div>
      <div className="app-page-header">
        <h1 className="app-page-header__title">API 수집 관리</h1>
        <button className="krds-btn small" onClick={() => showCreateForm ? resetForm() : setShowCreateForm(true)}>
          {showCreateForm ? '취소' : '+ API 등록'}
        </button>
      </div>

      {showCreateForm && (
        <div className={styles.formWrap}>
          {/* 기본 정보 카드 — 등록 유형 + 요청 + 인증 + 설명 묶음 */}
          <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">기본정보</h2>
            <button type="button" className="krds-btn small" onClick={handleCreate}
              disabled={saving || !canCreate}
              title={!canCreate ? `미설정: ${createMissing.join(', ')}` : undefined}>
              {saving ? '등록 중...' : '등록'}
            </button>
          </div>
          {/* 등록 유형 선택 — 카드형 라디오 */}
          <div className={styles.section}>
            <div className={styles.sectionLabel}>등록 유형</div>
            <div className={styles.modeOptions}>
              <label className={`${styles.modeOption} ${!isCustom ? styles.modeOptionActive : ''}`}>
                <input
                  type="radio"
                  checked={!isCustom}
                  onChange={() => setExecutorType('')}
                  className={styles.modeOptionRadio}
                />
                <div className={styles.modeOptionContent}>
                  <span className={styles.modeOptionTitle}>
                    일반<span className={styles.modeOptionBadge}>META</span>
                  </span>
                  <span className={styles.modeOptionDesc}>
                    범용 매핑 — 응답 트리에서 필드 선택해서 직접 매핑.
                  </span>
                </div>
              </label>
              <label className={`${styles.modeOption} ${isCustom ? styles.modeOptionActive : ''}`}>
                <input
                  type="radio"
                  checked={isCustom}
                  onChange={() => setExecutorType(customExecutors[0]?.id || '')}
                  className={styles.modeOptionRadio}
                />
                <div className={styles.modeOptionContent}>
                  <span className={styles.modeOptionTitle}>
                    프리셋<span className={styles.modeOptionBadge}>CUSTOM</span>
                  </span>
                  <span className={styles.modeOptionDesc}>
                    커스텀 실행기 — 시스템 내장 로직으로 처리 (매핑 불필요).
                  </span>
                </div>
              </label>
            </div>
          </div>

          {/* 커스텀일 때 실행기 선택 */}
          {isCustom && (
            <div className={styles.section}>
              <div className={styles.sectionLabel}>실행기</div>
              <div className={styles.gridForm}>
                <div className="app-form-field">
                  <label className="app-form-label">실행기 *</label>
                  <select className="krds-input small" value={executorType}
                    onChange={e => setExecutorType(e.target.value)}>
                    {customExecutors.map(ce => (
                      <option key={ce.id} value={ce.id}>{ce.displayName}</option>
                    ))}
                  </select>
                </div>
                <div className="app-form-field">
                  <label className="app-form-label">Target Datasource</label>
                  <select className="krds-input small" value={selectedDatasourceId}
                    onChange={e => setSelectedDatasourceId(e.target.value)}>
                    <option value="">-- 기본 DB --</option>
                    {datasources.map(ds => (
                      <option key={ds.datasourceId} value={ds.datasourceId}>{ds.datasourceName || ds.datasourceId}</option>
                    ))}
                  </select>
                </div>
              </div>
            </div>
          )}

          {/* 요청 설정 */}
          <div className={styles.section}>
            <div className={styles.sectionLabel}>요청</div>
            <div className={styles.gridForm}>
              <div className="app-form-field">
                <label className="app-form-label">API명 *</label>
                <input className="krds-input small" value={form.apiName}
                  onChange={e => setForm({ ...form, apiName: e.target.value })}
                  placeholder="예: 네이버 뉴스 수집" />
              </div>
              <div className="app-form-field">
                <label className="app-form-label">HTTP Method</label>
                <select className="krds-input small" value={form.httpMethod}
                  onChange={e => setForm({ ...form, httpMethod: e.target.value })}>
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                </select>
              </div>
              <div className={`app-form-field ${styles.fullSpan}`}>
                <label className="app-form-label">URL *</label>
                <input className="krds-input small" value={form.url}
                  onChange={e => setForm({ ...form, url: e.target.value })}
                  placeholder="https://openapi.naver.com/..." />
              </div>
              <div className="app-form-field">
                <label className="app-form-label">Content-Type</label>
                <select className="krds-input small" value={form.contentType || ''}
                  onChange={e => setForm({ ...form, contentType: e.target.value })}>
                  <option value="">기본 (없음)</option>
                  <option value="application/json">application/json</option>
                  <option value="application/x-www-form-urlencoded">application/x-www-form-urlencoded</option>
                </select>
              </div>
              <div className="app-form-field">
                <label className="app-form-label">Zone</label>
                <select className="krds-input small" value={form.zone}
                  onChange={e => setForm({ ...form, zone: e.target.value as CollectorZone })}>
                  <option value="DMZ">DMZ</option>
                  <option value="INTERNAL">INTERNAL</option>
                </select>
              </div>
            </div>
          </div>

          {/* 인증 */}
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

          {/* 설명 */}
          <div className={styles.section}>
            <div className="app-form-field">
              <label className="app-form-label">설명</label>
              <input className="krds-input small" value={form.description || ''}
                onChange={e => setForm({ ...form, description: e.target.value })}
                placeholder="API 설명 (선택)" />
            </div>
          </div>
          </div>
          {/* /기본정보 카드 끝 */}

          {/* 헤더 카드 */}
          <div className="app-card">
            <div className="app-card__header">
              <h2 className="app-card__title">헤더</h2>
              <button type="button" className="krds-btn small" onClick={() => {
                setParams([...params, { paramName: '', paramType: 'HEADER', valueType: 'STATIC', staticValue: '', isApiKeyRef: false, displayOrder: params.length }]);
              }}>+ 추가</button>
            </div>
            {(() => {
              const headerParams = params.map((p, i) => ({ ...p, _idx: i })).filter(p => p.paramType === 'HEADER');
              return headerParams.length === 0 ? (
                <div className="app-empty">커스텀 헤더 없음 (필요 시 추가)</div>
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
              );
            })()}
          </div>

          {/* 파라미터 (QUERY/BODY/PATH) 카드 */}
          <div className="app-card">
            <div className="app-card__header">
              <h2 className="app-card__title">호출 파라미터</h2>
              <button type="button" className="krds-btn small" onClick={addParam}>+ 추가</button>
            </div>
            {(() => {
              const queryParams = params.map((p, i) => ({ ...p, _idx: i })).filter(p => p.paramType !== 'HEADER');
              return queryParams.length === 0 ? (
                <div className="app-empty">파라미터 없음 (필요 시 추가)</div>
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
                              const u = [...params];
                              u[i] = { ...u[i], valueType: 'STATIC', isApiKeyRef: true, staticValue: '' };
                              setParams(u);
                            } else {
                              const u = [...params];
                              u[i] = { ...u[i], valueType: v as any, isApiKeyRef: false, ...(v === 'DYNAMIC' ? { dynamicType: 'TODAY' } : {}) };
                              setParams(u);
                            }
                          }}>
                          <option value="STATIC">직접입력</option>
                          <option value="APIKEY">🔑 API키</option>
                          <option value="DYNAMIC">동적</option>
                        </select>
                        {p.isApiKeyRef ? (
                          <select className="krds-input small"
                            value={p.staticValue || ''}
                            onFocus={() => loadApiKeys()}
                            onChange={e => {
                              const key = apiKeys.find(k => String(k.id) === e.target.value);
                              const u = [...params];
                              u[i] = { ...u[i], staticValue: e.target.value, description: key ? `🔑 ${key.serviceName}` : '' };
                              setParams(u);
                            }}>
                            <option value="">-- API 키 선택 --</option>
                            {apiKeys.filter(k => k.useAt === 'Y').map(k => (
                              <option key={k.id} value={String(k.id)}>
                                {k.serviceName} (D-{k.dday})
                              </option>
                            ))}
                          </select>
                        ) : p.valueType === 'STATIC' ? (
                          <input className="krds-input small" value={p.staticValue || ''}
                            onChange={e => updateParam(i, 'staticValue', e.target.value)} placeholder="고정값 입력" />
                        ) : (
                          <div className={styles.dynamicCell}>
                            <select className={`krds-input small ${styles.dynamicType}`} value={p.dynamicType || 'TODAY'}
                              onChange={e => updateParam(i, 'dynamicType', e.target.value)}>
                              <option value="TODAY">날짜</option>
                              <option value="NOW">시간</option>
                              <option value="YEAR">연도</option>
                            </select>
                            <input className={`krds-input small ${styles.dynamicFormat}`} value={p.dynamicFormat || ''}
                              onChange={e => updateParam(i, 'dynamicFormat', e.target.value)} placeholder="yyyyMMdd" />
                            <input className={`krds-input small ${styles.dynamicOffset}`} type="number" value={p.dynamicOffset ?? 0}
                              onChange={e => updateParam(i, 'dynamicOffset', parseInt(e.target.value) || 0)} title="오프셋 (예: -1 = 어제)" />
                          </div>
                        )}
                        <input className="krds-input small" value={p.description || ''}
                          onChange={e => updateParam(i, 'description', e.target.value)} placeholder="설명" />
                        <button type="button" className="krds-btn small app-btn-danger"
                          onClick={() => removeParam(i)}>X</button>
                      </div>
                    );
                  })}
                </div>
              );
            })()}
          </div>

          {/* 테스트 호출 카드 */}
          <div className="app-card">
            <div className="app-card__header">
              <h2 className="app-card__title">테스트 호출</h2>
              <button type="button" className="krds-btn small" onClick={handleTestCall} disabled={testing}>
                {testing ? '호출 중...' : '테스트 호출'}
              </button>
            </div>
            {testResult && (
              <div className={styles.testStatusRow}>
                <span className={`krds-badge ${testResult.success ? 'bg-light-success' : 'bg-light-danger'}`}>
                  {testResult.success ? 'SUCCESS' : 'FAILED'} {testResult.httpStatusCode > 0 && `(${testResult.httpStatusCode})`}
                </span>
                {testResult.errorMessage && <span className={styles.testErrorText}>{testResult.errorMessage}</span>}
              </div>
            )}
          </div>

          {/* 커스텀: 테스트 성공 시 안내 + 등록 버튼 */}
          {isCustom && (
            <>
              {testResult?.success && (
                <div className={`app-alert app-alert--warning ${styles.customCompleteAlert}`}>
                  API 연결 확인 완료. 매핑/적재 로직은 실행기 내부에서 처리됩니다. 상단 [등록] 버튼으로 저장하세요.
                </div>
              )}
            </>
          )}

          {/* 이하 범용 전용: 응답 트리 + 데이터 루트 + 매핑 + 적재 + 등록 */}
          {!isCustom && <>
          {/* 응답 트리 카드 */}
          {testResult?.success && testResult.responseTree && (
            <div className="app-card">
              <div className="app-card__header">
                <h2 className="app-card__title">응답 구조</h2>
              </div>
              <div className={styles.sectionLabel}>배열 노드를 클릭하여 데이터 루트 선택</div>
              <div className={styles.treeWrap}>
                <SelectableJsonTreeView node={testResult.responseTree} path="" selectedRoot={selectedDataRoot} onSelectRoot={handleSelectRoot} />
              </div>
              {selectedDataRoot && (
                <div className={styles.dataRootFoot}>
                  data_root:<code className={styles.dataRootCode}>{selectedDataRoot}</code>
                </div>
              )}
            </div>
          )}

          {/* 적재 설정 카드 */}
          {selectedDataRoot && (
            <div ref={mappingSectionRef} className="app-card">
              <div className="app-card__header">
                <h2 className="app-card__title">적재 설정</h2>
              </div>
              <div className={styles.loadRow}>
                <div className={styles.loadField}>
                  <span className={styles.loadFieldLabel}>Target DB</span>
                  <select className={`krds-input small ${styles.loadSelect}`} value={selectedDatasourceId}
                    onChange={e => { setSelectedDatasourceId(e.target.value); setTargetTable(''); setTargetColumns([]); }}>
                    <option value="">-- 선택 --</option>
                    {datasources.map(ds => (
                      <option key={ds.datasourceId} value={ds.datasourceId}>{ds.datasourceName} ({ds.dbType})</option>
                    ))}
                  </select>
                  <button type="button" className="krds-btn xsmall secondary" onClick={refreshDatasources} title="DB 목록 새로고침">↻</button>
                </div>
                <div className={styles.loadField}>
                  <span className={styles.loadFieldLabel}>테이블</span>
                  <select className={`krds-input small ${styles.loadSelectShort}`} value={targetTable} disabled={!selectedDatasourceId}
                    onChange={e => setTargetTable(e.target.value)}>
                    <option value="">-- 선택 --</option>
                    {tables.map(t => <option key={t.tableName} value={t.tableName}>{t.tableName}{t.remarks ? ` (${t.remarks})` : ''}</option>)}
                  </select>
                  <button type="button" className="krds-btn xsmall secondary" onClick={refreshTables} disabled={!selectedDatasourceId} title="테이블 목록 새로고침">↻</button>
                </div>
                <div className={styles.upsertWrap}>
                  <div className="krds-form-check medium">
                    <input type="checkbox" id="reg-upsert" checked={upsertEnabled}
                      onChange={e => setUpsertEnabled(e.target.checked)} />
                    <label htmlFor="reg-upsert" aria-label="UPSERT"></label>
                  </div>
                  <label htmlFor="reg-upsert" className={styles.loadFieldLabel}>UPSERT</label>
                </div>
              </div>
            </div>
          )}

          {/* 필드 매핑 카드 */}
          {selectedDataRoot && mappingRows.length > 0 && (
            <div className="app-card">
              <div className="app-card__header">
                <h2 className="app-card__title">필드 매핑 ({mappingRows.filter(r => r.targetColumnName).length}/{mappingRows.length})</h2>
              </div>
              <div className={styles.paramGrid}>
                <table className={styles.mappingTable}>
                  <thead>
                    <tr>
                      <th>API 필드</th>
                      <th className={styles.thArrow}></th>
                      <th>Target 컬럼</th>
                      <th className={`${styles.thCenter} ${styles.thWidth60}`}>중복키</th>
                      <th className={styles.thWidth120}>변환</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mappingRows.map((row, i) => (
                      <tr key={i} className={row.targetColumnName ? '' : styles.mappingRowDimmed}>
                        <td><span className={styles.sourceCode}>{row.sourceFieldPath}</span></td>
                        <td className={styles.arrowCell}>→</td>
                        <td>
                          {targetColumns.length > 0 ? (
                            <select className="krds-input small" value={row.targetColumnName}
                              onChange={e => updateMappingRow(i, 'targetColumnName', e.target.value)}>
                              <option value="">-- 선택 --</option>
                              {targetColumns.map(c => (
                                <option key={c.columnName} value={c.columnName}>
                                  {c.columnName} ({c.dataType}){c.isPrimaryKey ? ' [PK]' : ''}{c.remarks ? ` - ${c.remarks}` : ''}
                                </option>
                              ))}
                            </select>
                          ) : (
                            <input className="krds-input small" value={row.targetColumnName}
                              onChange={e => updateMappingRow(i, 'targetColumnName', e.target.value)}
                              placeholder="컬럼명 입력" />
                          )}
                        </td>
                        <td className={styles.thCenter}>
                          <div className="krds-form-check medium">
                            <input type="checkbox" id={`reg-mkey-${i}`}
                              checked={row.isConflictKey} disabled={!row.targetColumnName}
                              onChange={e => updateMappingRow(i, 'isConflictKey', e.target.checked)} />
                            <label htmlFor={`reg-mkey-${i}`} aria-label="중복키"></label>
                          </div>
                        </td>
                        <td>
                          <select className="krds-input small" value={row.transformType} disabled={!row.targetColumnName}
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
                      </tr>
                    ))}

                    {derivedRows.length > 0 && (
                      <tr className={styles.sectionDivider}>
                        <td colSpan={5}>파생 컬럼 (LOOKUP / 고정값)</td>
                      </tr>
                    )}

                    {derivedRows.map((row, i) => {
                      const isExpanded = expandedDerived.has(i);
                      const isFixed = row.transformType === 'DEFAULT_VALUE';
                      const sourceFields = mappingRows.filter(r => r.targetColumnName).map(r => r.sourceFieldPath);
                      return (
                        <React.Fragment key={`d-${i}`}>
                          <tr className={isFixed ? styles.derivedFixedRow : styles.derivedDerivedRow}>
                            <td>
                              {isFixed ? (
                                <span className={styles.muted}>고정값</span>
                              ) : (
                                <select className="krds-input small" value={row.sourceFieldPath}
                                  onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], sourceFieldPath: e.target.value }; setDerivedRows(u); }}>
                                  <option value="">-- 소스 필드 --</option>
                                  {sourceFields.map(f => <option key={f} value={f}>{f}</option>)}
                                </select>
                              )}
                            </td>
                            <td className={styles.arrowCell}>→</td>
                            <td>
                              {targetColumns.length > 0 ? (
                                <select className="krds-input small" value={row.targetColumnName}
                                  onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], targetColumnName: e.target.value }; setDerivedRows(u); }}>
                                  <option value="">-- 선택 --</option>
                                  {targetColumns.map(c => (
                                    <option key={c.columnName} value={c.columnName}>{c.columnName} ({c.dataType}){c.isPrimaryKey ? ' [PK]' : ''}</option>
                                  ))}
                                </select>
                              ) : (
                                <input className="krds-input small" value={row.targetColumnName}
                                  onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], targetColumnName: e.target.value }; setDerivedRows(u); }}
                                  placeholder="컬럼명 입력" />
                              )}
                            </td>
                            <td className={styles.thCenter}>
                              <div className="krds-form-check medium">
                                <input type="checkbox" id={`reg-dkey-${i}`} checked={row.isConflictKey}
                                  onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], isConflictKey: e.target.checked }; setDerivedRows(u); }} />
                                <label htmlFor={`reg-dkey-${i}`} aria-label="중복키"></label>
                              </div>
                            </td>
                            <td>
                              <div className={styles.derivedActionCell}>
                                {isFixed ? (
                                  <input className="krds-input small" value={row.defaultValue || ''}
                                    onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], defaultValue: e.target.value }; setDerivedRows(u); }}
                                    placeholder="고정값 입력" />
                                ) : (
                                  <button type="button"
                                    className={`krds-btn xsmall ${isExpanded ? '' : 'secondary'}`}
                                    onClick={() => {
                                      setExpandedDerived(prev => { const n = new Set(prev); n.has(i) ? n.delete(i) : n.add(i); return n; });
                                    }}>
                                    {isExpanded ? '설정 ▴' : '설정 ▾'}
                                  </button>
                                )}
                                <button type="button" className={styles.removeBtn}
                                  onClick={() => {
                                    setDerivedRows(derivedRows.filter((_, idx) => idx !== i));
                                    setExpandedDerived(prev => { const n = new Set<number>(); prev.forEach(v => { if (v < i) n.add(v); else if (v > i) n.add(v - 1); }); return n; });
                                  }}>✕</button>
                              </div>
                            </td>
                          </tr>
                          {isExpanded && (
                            <tr className={styles.derivedDerivedRow}>
                              <td colSpan={5} className={styles.lookupPanel}>
                                <div className={styles.lookupGrid}>
                                  <div className={styles.lookupFull}>
                                    <label className="app-form-label">추출 정규식</label>
                                    <div className={styles.lookupExtractRow}>
                                      <input className={`krds-input small ${styles.lookupExtractInput}`} value={row.extractPattern}
                                        onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], extractPattern: e.target.value }; setDerivedRows(u); }}
                                        placeholder="정규식 (예: https?://(?:www\.)?([^/]+))" />
                                      <span className={styles.lookupExtractGroupLabel}>그룹:</span>
                                      <input className={`krds-input small ${styles.lookupExtractGroup}`} type="number" min={0} value={row.extractGroup}
                                        onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], extractGroup: parseInt(e.target.value) || 1 }; setDerivedRows(u); }} />
                                    </div>
                                    <div className={styles.presetRow}>
                                      {EXTRACT_PRESETS.map(p => (
                                        <button key={p.label} type="button" className="krds-btn xsmall secondary"
                                          onClick={() => { const u = [...derivedRows]; u[i] = { ...u[i], extractPattern: p.pattern, extractGroup: p.group }; setDerivedRows(u); }}>
                                          {p.label}
                                        </button>
                                      ))}
                                    </div>
                                  </div>
                                  <div className="app-form-field">
                                    <label className="app-form-label">코드 파라미터 (그룹코드)</label>
                                    <input className="krds-input small" value={row.lookupParam}
                                      onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], lookupParam: e.target.value }; setDerivedRows(u); }}
                                      placeholder="NGW_0118" />
                                  </div>
                                  <div className="app-form-field">
                                    <label className="app-form-label">데이터 루트</label>
                                    <input className="krds-input small" value={row.lookupDataRootPath}
                                      onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], lookupDataRootPath: e.target.value }; setDerivedRows(u); }}
                                      placeholder="data.common" />
                                  </div>
                                  <div className="app-form-field">
                                    <label className="app-form-label">키 필드</label>
                                    <input className="krds-input small" value={row.lookupKeyField}
                                      onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], lookupKeyField: e.target.value }; setDerivedRows(u); }}
                                      placeholder="detailCode" />
                                  </div>
                                  <div className="app-form-field">
                                    <label className="app-form-label">값 필드</label>
                                    <input className="krds-input small" value={row.lookupValueField}
                                      onChange={e => { const u = [...derivedRows]; u[i] = { ...u[i], lookupValueField: e.target.value }; setDerivedRows(u); }}
                                      placeholder="detailCodeName" />
                                  </div>
                                  <div className="app-form-field">
                                    <label className="app-form-label">기본값 (매칭 실패 시)</label>
                                    <input className="krds-input small" value={row.defaultValue}
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
              </div>

              <div className={styles.addDerivedRow}>
                <button type="button" className={styles.addDerivedBtn}
                  onClick={() => {
                    const sourceFields = mappingRows.filter(r => r.targetColumnName).map(r => r.sourceFieldPath);
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
                  }}>
                  + 파생 컬럼 추가
                </button>
                <button type="button" className={`${styles.addDerivedBtn} ${styles.addFixedBtn}`}
                  onClick={() => {
                    setDerivedRows([...derivedRows, {
                      sourceFieldPath: '',
                      targetColumnName: '',
                      isConflictKey: false,
                      transformType: 'DEFAULT_VALUE',
                      extractPattern: '',
                      extractGroup: 1,
                      lookupParam: '',
                      lookupKeyField: '',
                      lookupValueField: '',
                      lookupDataRootPath: '',
                      lookupMatchType: 'EXACT',
                      defaultValue: '',
                    }]);
                  }}>
                  + 고정값 컬럼 추가
                </button>
              </div>
            </div>
          )}

          </>}
        </div>
      )}

      {/* API 목록 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">API 목록</h2>
        </div>
        {loading ? (
          <div className="app-loading">로딩 중...</div>
        ) : endpoints.length === 0 ? (
          <div className="app-empty">등록된 API가 없습니다.</div>
        ) : (
          <table className="app-table">
            <thead>
              <tr>
                <th>API명</th>
                <th>Method</th>
                <th>적재 테이블</th>
                <th>상태</th>
                <th>Zone</th>
              </tr>
            </thead>
            <tbody>
              {endpoints.map(ep => (
                <tr key={ep.id} className={styles.clickableRow}
                  onClick={() => router.push(`/api-collect/${ep.id}`)}>
                  <td className={styles.boldCell}>{ep.apiName}</td>
                  <td>
                    <span className={`krds-badge ${ep.httpMethod === 'GET' ? 'bg-light-information' : 'bg-light-warning'}`}>
                      {ep.httpMethod}
                    </span>
                  </td>
                  <td>{ep.targetTableName || <span className={styles.muted}>미설정</span>}</td>
                  <td>
                    <span className={styles.statusCell}>
                      <span className={`${styles.statusDot} ${ep.isActive ? styles.statusDotActive : styles.statusDotInactive}`} />
                      {ep.isActive ? '활성' : '비활성'}
                    </span>
                  </td>
                  <td>{ep.zone}</td>
                </tr>
              ))}
            </tbody>
          </table>
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
    <div className={depth > 0 ? styles.treeNode : styles.treeNodeRoot}>
      <div className={`${styles.treeRow} ${isSelected ? styles.treeRowSelected : ''}`}>
        {isExpandable && hasChildren ? (
          <span onClick={() => setExpanded(!expanded)} className={styles.treeToggle}>
            {expanded ? '▾' : '▸'}
          </span>
        ) : (
          <span className={styles.treeBullet}>-</span>
        )}
        <span className={`${styles.treeName} ${isExpandable ? styles.treeNameExpandable : ''}`}>
          {node.name === 'root' ? '(root)' : node.name}
        </span>
        <span className={styles.treeMeta}>({node.type}{node.arraySize != null ? `, ${node.arraySize}건` : ''})</span>
        {node.sampleValue != null && (
          <span className={styles.treeSample}>
            {node.sampleValue.length > 40 ? `"${node.sampleValue.substring(0, 40)}..."` : `"${node.sampleValue}"`}
          </span>
        )}
        {node.type === 'array' && (
          <button type="button" onClick={() => onSelectRoot(currentPath || 'root')}
            className={`krds-btn xsmall ${isSelected ? '' : 'secondary'}`}>
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
