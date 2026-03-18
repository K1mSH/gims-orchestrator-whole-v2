'use client';

import { useState } from 'react';
import { endpointApi, paramApi } from '@/lib/collectorApi';
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

// JSON string → key-value 배열
function parseHeaders(json: string | null): { key: string; value: string }[] {
  if (!json) return [];
  try {
    const obj = JSON.parse(json);
    return Object.entries(obj).map(([key, value]) => ({ key, value: String(value) }));
  } catch {
    return json ? [{ key: json, value: '' }] : [];
  }
}

// key-value 배열 → JSON string
function headersToJson(rows: { key: string; value: string }[]): string {
  const obj: Record<string, string> = {};
  rows.filter(r => r.key.trim()).forEach(r => { obj[r.key.trim()] = r.value; });
  return Object.keys(obj).length > 0 ? JSON.stringify(obj) : '';
}

export default function InfoTab({ endpoint, onUpdate }: InfoTabProps) {
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
  const [headerRows, setHeaderRows] = useState<{ key: string; value: string }[]>(
    parseHeaders(endpoint.headers)
  );
  const [saving, setSaving] = useState(false);

  // --- 파라미터 ---
  const [params, setParams] = useState<ApiParamRequest[]>(
    endpoint.params.map(p => ({
      paramName: p.paramName,
      paramType: p.paramType,
      valueType: p.valueType,
      staticValue: p.staticValue || '',
      dynamicType: p.dynamicType || undefined,
      dynamicFormat: p.dynamicFormat || '',
      dynamicOffset: p.dynamicOffset ?? 0,
      description: p.description || '',
      displayOrder: p.displayOrder,
    }))
  );
  const [paramsSaving, setParamsSaving] = useState(false);

  const handleSaveInfo = async () => {
    try {
      setSaving(true);
      await endpointApi.update(endpoint.id, { ...form, headers: headersToJson(headerRows) });
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
      await paramApi.save(endpoint.id, params);
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
          <div style={sectionLabel}>요청</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '0.75rem', alignItems: 'start' }}>
            <div>
              <div style={labelStyle}>API명</div>
              <input className="form-input" value={form.apiName}
                onChange={e => setForm({ ...form, apiName: e.target.value })} />
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
          </div>
        </div>

        {/* 헤더 설정 */}
        <div style={sectionStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <div style={sectionLabel}>헤더</div>
            <button className="btn btn-sm btn-primary"
              onClick={() => setHeaderRows([...headerRows, { key: '', value: '' }])}
              style={{ fontSize: '0.75rem', padding: '2px 8px' }}>+ 추가</button>
          </div>
          {headerRows.length === 0 ? (
            <div style={{ fontSize: '0.85rem', color: 'var(--gray-400)', padding: '0.25rem 0' }}>
              커스텀 헤더 없음 (필요 시 추가)
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
              {headerRows.map((h, i) => (
                <div key={i} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                  <input className="form-input" value={h.key}
                    style={{ fontSize: '0.85rem', flex: 1 }}
                    onChange={e => { const u = [...headerRows]; u[i] = { ...u[i], key: e.target.value }; setHeaderRows(u); }}
                    placeholder="Header Name" />
                  <input className="form-input" value={h.value}
                    style={{ fontSize: '0.85rem', flex: 2 }}
                    onChange={e => { const u = [...headerRows]; u[i] = { ...u[i], value: e.target.value }; setHeaderRows(u); }}
                    placeholder="Value" />
                  <button className="btn btn-danger btn-sm" onClick={() => setHeaderRows(headerRows.filter((_, idx) => idx !== i))}
                    style={{ fontSize: '0.75rem' }}>X</button>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 인증 설정 */}
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

        {/* 기타 */}
        <div style={{ ...sectionStyle, borderBottom: 'none' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: '0.75rem', alignItems: 'end' }}>
            <div>
              <div style={labelStyle}>설명</div>
              <input className="form-input" value={form.description || ''}
                onChange={e => setForm({ ...form, description: e.target.value })}
                placeholder="API 설명 (선택)" />
            </div>
            <button className="btn btn-primary" onClick={handleSaveInfo} disabled={saving}>
              {saving ? '저장 중...' : '기본정보 저장'}
            </button>
          </div>
        </div>
      </div>

      {/* 파라미터 섹션 */}
      <div className="card">
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 className="card-title">호출 파라미터</h3>
          <button className="btn btn-sm btn-primary" onClick={addParam}>+ 추가</button>
        </div>
        <div style={{ padding: '1rem' }}>
          {params.length === 0 ? (
            <div style={{ textAlign: 'center', color: 'var(--gray-500)', padding: '1rem' }}>
              파라미터가 없습니다.
            </div>
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
                          onChange={e => updateParam(i, 'staticValue', e.target.value)}
                          placeholder="고정값 입력" />
                      ) : (
                        <div style={{ display: 'flex', gap: '0.25rem' }}>
                          <select className="form-select" value={p.dynamicType || 'TODAY'}
                            style={{ fontSize: '0.85rem', width: '80px' }}
                            onChange={e => updateParam(i, 'dynamicType', e.target.value)}>
                            <option value="TODAY">날짜</option>
                            <option value="NOW">시간</option>
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
                ))}
              </tbody>
            </table>
          )}
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
