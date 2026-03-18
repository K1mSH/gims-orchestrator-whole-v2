'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { endpointApi, testApi, paramApi, TestCallResponse, TreeNode, InlineTestRequest } from '@/lib/collectorApi';
import {
  ApiEndpointListItem,
  ApiEndpointCreateRequest,
  ApiParamRequest,
  AuthType,
  CollectorZone,
  ParamType,
  ValueType,
} from '@/types/api-collect';

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

  // 등록 후 상태 (테스트 호출용)
  const [createdId, setCreatedId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);

  // 테스트 호출
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<TestCallResponse | null>(null);

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

  useEffect(() => {
    fetchEndpoints();
  }, [fetchEndpoints]);

  const resetForm = () => {
    setForm({ apiName: '', url: '', httpMethod: 'GET', authType: 'NONE', zone: 'DMZ' });
    setHeaderRows([]);
    setParams([]);
    setCreatedId(null);
    setTestResult(null);
    setShowCreateForm(false);
  };

  const headersToJson = (rows: { key: string; value: string }[]): string => {
    const obj: Record<string, string> = {};
    rows.filter(r => r.key.trim()).forEach(r => { obj[r.key.trim()] = r.value; });
    return Object.keys(obj).length > 0 ? JSON.stringify(obj) : '';
  };

  // 테스트 호출 (저장 없이 인라인)
  const handleTestCall = async () => {
    if (!form.url) { alert('URL을 입력하세요.'); return; }
    try {
      setTesting(true);
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

  // 등록 (테스트 성공 후에만 가능)
  const handleCreate = async () => {
    if (!form.apiName || !form.url) {
      alert('API명과 URL은 필수입니다.');
      return;
    }
    try {
      setSaving(true);
      const submitForm = { ...form, headers: headersToJson(headerRows) };
      const created = await endpointApi.create(submitForm);
      setCreatedId(created.id);
      if (params.length > 0) {
        await paramApi.save(created.id, params);
      }
      fetchEndpoints();
    } catch (e: any) {
      alert('등록 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  // 파라미터 관리
  const addParam = () => {
    setParams([...params, {
      paramName: '', paramType: 'QUERY', valueType: 'STATIC', staticValue: '', displayOrder: params.length,
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
                <input className="form-input" value={form.apiName} disabled={!!createdId}
                  onChange={e => setForm({ ...form, apiName: e.target.value })}
                  placeholder="예: 지하수 수위 관측" />
              </div>
              <div>
                <div style={fieldLabel}>URL *</div>
                <input className="form-input" value={form.url} disabled={!!createdId}
                  onChange={e => setForm({ ...form, url: e.target.value })}
                  placeholder="https://apis.data.go.kr/..." />
              </div>
              <div>
                <div style={fieldLabel}>HTTP Method</div>
                <select className="form-select" value={form.httpMethod} disabled={!!createdId}
                  onChange={e => setForm({ ...form, httpMethod: e.target.value })}>
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                </select>
              </div>
              <div>
                <div style={fieldLabel}>Content-Type</div>
                <select className="form-select" value={form.contentType || ''} disabled={!!createdId}
                  onChange={e => setForm({ ...form, contentType: e.target.value })}>
                  <option value="">기본 (없음)</option>
                  <option value="application/json">application/json</option>
                  <option value="application/x-www-form-urlencoded">application/x-www-form-urlencoded</option>
                  <option value="multipart/form-data">multipart/form-data</option>
                </select>
              </div>
            </div>
          </div>

          {/* 헤더 */}
          <div style={sectionStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <div style={sectionLabel}>헤더</div>
              {!createdId && (
                <button className="btn btn-sm btn-primary" onClick={() => setHeaderRows([...headerRows, { key: '', value: '' }])}
                  style={{ fontSize: '0.75rem', padding: '2px 8px' }}>+ 추가</button>
              )}
            </div>
            {headerRows.length === 0 ? (
              <div style={{ fontSize: '0.85rem', color: 'var(--gray-400)', padding: '0.25rem 0' }}>
                커스텀 헤더 없음 {!createdId && '(필요 시 추가)'}
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                {headerRows.map((h, i) => (
                  <div key={i} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                    <input className="form-input" value={h.key} disabled={!!createdId}
                      style={{ fontSize: '0.85rem', flex: 1 }}
                      onChange={e => { const u = [...headerRows]; u[i] = { ...u[i], key: e.target.value }; setHeaderRows(u); }}
                      placeholder="Header Name" />
                    <input className="form-input" value={h.value} disabled={!!createdId}
                      style={{ fontSize: '0.85rem', flex: 2 }}
                      onChange={e => { const u = [...headerRows]; u[i] = { ...u[i], value: e.target.value }; setHeaderRows(u); }}
                      placeholder="Value" />
                    {!createdId && (
                      <button className="btn btn-danger btn-sm" onClick={() => setHeaderRows(headerRows.filter((_, idx) => idx !== i))}
                        style={{ fontSize: '0.75rem' }}>X</button>
                    )}
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
                <select className="form-select" value={form.authType} disabled={!!createdId}
                  onChange={e => setForm({ ...form, authType: e.target.value as AuthType })}>
                  <option value="NONE">없음</option>
                  <option value="BASIC">Basic Auth</option>
                  <option value="BEARER">Bearer Token</option>
                </select>
              </div>
              {form.authType === 'BASIC' && (
                <div>
                  <div style={fieldLabel}>인증 설정 (JSON)</div>
                  <input className="form-input" value={form.authConfig || ''} disabled={!!createdId}
                    onChange={e => setForm({ ...form, authConfig: e.target.value })}
                    placeholder='{"username": "...", "password": "..."}' />
                </div>
              )}
              {form.authType === 'BEARER' && (
                <div>
                  <div style={fieldLabel}>Bearer Token</div>
                  <input className="form-input" value={form.authConfig || ''} disabled={!!createdId}
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
                <select className="form-select" value={form.zone} disabled={!!createdId}
                  onChange={e => setForm({ ...form, zone: e.target.value as CollectorZone })}>
                  <option value="DMZ">DMZ</option>
                  <option value="INTERNAL">INTERNAL</option>
                </select>
              </div>
              <div>
                <div style={fieldLabel}>설명</div>
                <input className="form-input" value={form.description || ''} disabled={!!createdId}
                  onChange={e => setForm({ ...form, description: e.target.value })}
                  placeholder="API 설명 (선택)" />
              </div>
            </div>
          </div>

          {/* 파라미터 */}
          <div style={sectionStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <div style={sectionLabel}>호출 파라미터</div>
              {!createdId && (
                <button className="btn btn-sm btn-primary" onClick={addParam}
                  style={{ fontSize: '0.75rem', padding: '2px 8px' }}>+ 추가</button>
              )}
            </div>
            {params.length === 0 ? (
              <div style={{ fontSize: '0.85rem', color: 'var(--gray-400)', padding: '0.25rem 0' }}>
                파라미터 없음 {!createdId && '(필요 시 추가)'}
              </div>
            ) : (
              <table style={{ width: '100%', fontSize: '0.85rem' }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left', padding: '0.4rem 0.25rem' }}>파라미터명</th>
                    <th style={{ textAlign: 'left', padding: '0.4rem 0.25rem' }}>위치</th>
                    <th style={{ textAlign: 'left', padding: '0.4rem 0.25rem' }}>값 유형</th>
                    <th style={{ textAlign: 'left', padding: '0.4rem 0.25rem' }}>값 설정</th>
                    <th style={{ textAlign: 'left', padding: '0.4rem 0.25rem' }}>설명</th>
                    {!createdId && <th style={{ width: '40px' }}></th>}
                  </tr>
                </thead>
                <tbody>
                  {params.map((p, i) => (
                    <tr key={i}>
                      <td style={{ padding: '0.25rem' }}>
                        <input className="form-input" value={p.paramName} disabled={!!createdId}
                          style={{ fontSize: '0.85rem' }}
                          onChange={e => updateParam(i, 'paramName', e.target.value)} />
                      </td>
                      <td style={{ padding: '0.25rem' }}>
                        <select className="form-select" value={p.paramType} disabled={!!createdId}
                          style={{ fontSize: '0.85rem' }}
                          onChange={e => updateParam(i, 'paramType', e.target.value)}>
                          <option value="QUERY">QUERY</option>
                          <option value="BODY">BODY</option>
                          <option value="PATH">PATH</option>
                          <option value="HEADER">HEADER</option>
                        </select>
                      </td>
                      <td style={{ padding: '0.25rem' }}>
                        <select className="form-select" value={p.valueType} disabled={!!createdId}
                          style={{ fontSize: '0.85rem' }}
                          onChange={e => updateParam(i, 'valueType', e.target.value)}>
                          <option value="STATIC">고정값</option>
                          <option value="DYNAMIC">동적</option>
                        </select>
                      </td>
                      <td style={{ padding: '0.25rem' }}>
                        {p.valueType === 'STATIC' ? (
                          <input className="form-input" value={p.staticValue || ''} disabled={!!createdId}
                            style={{ fontSize: '0.85rem' }}
                            onChange={e => updateParam(i, 'staticValue', e.target.value)}
                            placeholder="고정값 입력" />
                        ) : (
                          <div style={{ display: 'flex', gap: '0.25rem' }}>
                            <select className="form-select" value={p.dynamicType || 'TODAY'} disabled={!!createdId}
                              style={{ fontSize: '0.85rem', width: '80px' }}
                              onChange={e => updateParam(i, 'dynamicType', e.target.value)}>
                              <option value="TODAY">날짜</option>
                              <option value="NOW">시간</option>
                            </select>
                            <input className="form-input" value={p.dynamicFormat || ''} disabled={!!createdId}
                              style={{ fontSize: '0.85rem', width: '100px' }}
                              onChange={e => updateParam(i, 'dynamicFormat', e.target.value)}
                              placeholder="yyyyMMdd" />
                            <input className="form-input" type="number" value={p.dynamicOffset ?? 0} disabled={!!createdId}
                              style={{ fontSize: '0.85rem', width: '60px' }}
                              onChange={e => updateParam(i, 'dynamicOffset', parseInt(e.target.value) || 0)}
                              title="오프셋 (예: -1 = 어제)" />
                          </div>
                        )}
                      </td>
                      <td style={{ padding: '0.25rem' }}>
                        <input className="form-input" value={p.description || ''} disabled={!!createdId}
                          style={{ fontSize: '0.85rem' }}
                          onChange={e => updateParam(i, 'description', e.target.value)}
                          placeholder="설명" />
                      </td>
                      {!createdId && (
                        <td style={{ padding: '0.25rem' }}>
                          <button className="btn btn-danger btn-sm" onClick={() => removeParam(i)}
                            style={{ fontSize: '0.75rem' }}>X</button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* 액션 버튼 */}
          <div style={{ padding: '0.75rem 1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                {createdId && (
                  <span style={{ fontSize: '0.85rem', color: 'var(--success)', fontWeight: 600 }}>
                    등록 완료
                  </span>
                )}
                {!createdId && testResult?.success && (
                  <span style={{ fontSize: '0.8rem', color: 'var(--gray-500)' }}>
                    테스트 성공 — 등록 가능
                  </span>
                )}
              </div>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                {!createdId && (
                  <button className="btn btn-primary" onClick={handleTestCall} disabled={testing}>
                    {testing ? '호출 중...' : '테스트 호출'}
                  </button>
                )}
                {!createdId && (
                  <button className="btn" onClick={handleCreate}
                    disabled={saving || !testResult?.success}
                    style={{ background: testResult?.success ? 'var(--success)' : 'var(--gray-200)',
                      color: testResult?.success ? 'white' : 'var(--gray-500)' }}
                    title={!testResult?.success ? '테스트 성공 후 등록 가능' : ''}>
                    {saving ? '등록 중...' : '등록'}
                  </button>
                )}
                {createdId && (
                  <button className="btn" onClick={() => router.push(`/api-collect/${createdId}`)}
                    style={{ background: 'var(--primary)', color: 'white' }}>
                    상세 (매핑) →
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 테스트 결과 */}
      {testResult && (
        <div className="card" style={{ marginBottom: '1.5rem' }}>
          <div className="card-header">
            <h3 className="card-title">테스트 결과</h3>
          </div>

          {/* 요청 정보 */}
          <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)' }}>
            <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)', marginBottom: '0.5rem', fontWeight: 600 }}>요청 정보</div>
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', fontSize: '0.85rem', flexWrap: 'wrap' }}>
              <span style={{
                padding: '2px 8px', borderRadius: '4px', fontWeight: 600, fontSize: '0.75rem',
                background: form.httpMethod === 'GET' ? '#dbeafe' : '#fef3c7',
                color: form.httpMethod === 'GET' ? '#1d4ed8' : '#92400e',
              }}>
                {form.httpMethod}
              </span>
              <code style={{ fontSize: '0.85rem', color: 'var(--gray-700)', wordBreak: 'break-all' }}>{form.url}</code>
            </div>
            {testResult.resolvedParams && Object.keys(testResult.resolvedParams).length > 0 && (
              <div style={{ marginTop: '0.5rem' }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)', marginBottom: '0.25rem' }}>파라미터 (resolve 결과)</div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.25rem' }}>
                  {Object.entries(testResult.resolvedParams).map(([k, v]) => (
                    <span key={k} style={{
                      display: 'inline-block', padding: '2px 6px', fontSize: '0.8rem',
                      background: 'var(--gray-100)', borderRadius: '3px', fontFamily: 'monospace',
                    }}>
                      <span style={{ color: 'var(--gray-500)' }}>{k}=</span>
                      <span>{String(v).length > 40 ? String(v).substring(0, 40) + '...' : v}</span>
                    </span>
                  ))}
                </div>
              </div>
            )}
            {headerRows.length > 0 && (
              <div style={{ marginTop: '0.5rem' }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)', marginBottom: '0.25rem' }}>커스텀 헤더</div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.25rem' }}>
                  {headerRows.filter(h => h.key.trim()).map((h, i) => (
                    <span key={i} style={{
                      display: 'inline-block', padding: '2px 6px', fontSize: '0.8rem',
                      background: '#e0e7ff', borderRadius: '3px', fontFamily: 'monospace',
                    }}>
                      {h.key}: {h.value}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* 응답 */}
          <div style={{ padding: '0.75rem 1rem' }}>
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginBottom: '0.5rem' }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)', fontWeight: 600 }}>응답</div>
              <span style={{
                padding: '2px 8px', borderRadius: '4px', fontWeight: 600, fontSize: '0.8rem',
                background: testResult.success ? '#dcfce7' : '#fee2e2',
                color: testResult.success ? '#166534' : '#991b1b',
              }}>
                {testResult.success ? 'SUCCESS' : 'FAILED'}
                {testResult.httpStatusCode > 0 && ` (${testResult.httpStatusCode})`}
              </span>
              {testResult.errorMessage && (
                <span style={{ color: '#991b1b', fontSize: '0.8rem' }}>{testResult.errorMessage}</span>
              )}
            </div>
            {testResult.success && testResult.responseTree && (
              <div style={{
                fontFamily: 'monospace', fontSize: '0.8rem', maxHeight: '400px', overflow: 'auto',
                border: '1px solid var(--gray-200)', borderRadius: '4px', padding: '0.75rem',
                background: 'var(--gray-50)',
              }}>
                <JsonTreeView node={testResult.responseTree} depth={0} />
              </div>
            )}
          </div>
        </div>
      )}

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
                      }}>
                        {ep.httpMethod}
                      </span>
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
                        background: ep.isActive ? 'var(--success)' : 'var(--gray-400)',
                        marginRight: '6px'
                      }} />
                      {ep.isActive ? '활성' : '비활성'}
                    </td>
                    <td>{ep.zone}</td>
                    <td onClick={e => e.stopPropagation()}>
                      <button className="btn btn-danger btn-sm"
                        onClick={() => handleDelete(ep.id, ep.apiName)}>삭제</button>
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

// --- JSON 트리 뷰 (읽기 전용, 등록 시 응답 구조 확인용) ---

function JsonTreeView({ node, depth = 0 }: { node: TreeNode; depth?: number }) {
  const [expanded, setExpanded] = useState(depth < 2);
  const hasChildren = node.children && node.children.length > 0;
  const isExpandable = node.type === 'object' || node.type === 'array';

  return (
    <div style={{ marginLeft: depth > 0 ? '1.25rem' : 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '2px 0' }}>
        {isExpandable && hasChildren ? (
          <span onClick={() => setExpanded(!expanded)}
            style={{ cursor: 'pointer', width: '16px', textAlign: 'center', userSelect: 'none' }}>
            {expanded ? '▾' : '▸'}
          </span>
        ) : (
          <span style={{ width: '16px', textAlign: 'center' }}>-</span>
        )}
        <span style={{ fontWeight: isExpandable ? 600 : 400 }}>
          {node.name === 'root' ? '(root)' : node.name}
        </span>
        <span style={{ color: 'var(--gray-400)', fontSize: '0.75rem' }}>
          ({node.type}{node.arraySize != null ? `, ${node.arraySize}건` : ''})
        </span>
        {node.sampleValue != null && (
          <span style={{ color: 'var(--gray-500)', fontSize: '0.75rem' }}>
            {node.sampleValue.length > 40 ? `"${node.sampleValue.substring(0, 40)}..."` : `"${node.sampleValue}"`}
          </span>
        )}
      </div>
      {expanded && hasChildren && node.children!.map((child, i) => (
        <JsonTreeView key={`${child.name}-${i}`} node={child} depth={depth + 1} />
      ))}
    </div>
  );
}
