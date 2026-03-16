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

export default function InfoTab({ endpoint, onUpdate }: InfoTabProps) {
  // --- 기본정보 ---
  const [form, setForm] = useState<ApiEndpointUpdateRequest>({
    apiName: endpoint.apiName,
    url: endpoint.url,
    httpMethod: endpoint.httpMethod,
    contentType: endpoint.contentType || '',
    headers: endpoint.headers || '',
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
      await endpointApi.update(endpoint.id, form);
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
    // STATIC↔DYNAMIC 전환 시 관련 필드 초기화
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

  return (
    <div>
      {/* 기본정보 섹션 */}
      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <div className="card-header"><h3 className="card-title">기본정보</h3></div>
        <div style={{ padding: '1rem', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">API명</label>
            <input className="form-input" value={form.apiName}
              onChange={e => setForm({ ...form, apiName: e.target.value })} />
          </div>
          <div className="form-group">
            <label className="form-label">API 코드</label>
            <input className="form-input" value={endpoint.apiCode} disabled
              style={{ background: 'var(--gray-100)' }} />
          </div>
          <div className="form-group" style={{ gridColumn: '1 / -1' }}>
            <label className="form-label">URL</label>
            <input className="form-input" value={form.url}
              onChange={e => setForm({ ...form, url: e.target.value })} />
          </div>
          <div className="form-group">
            <label className="form-label">HTTP Method</label>
            <select className="form-select" value={form.httpMethod}
              onChange={e => setForm({ ...form, httpMethod: e.target.value })}>
              <option value="GET">GET</option>
              <option value="POST">POST</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">인증 유형</label>
            <select className="form-select" value={form.authType}
              onChange={e => setForm({ ...form, authType: e.target.value as AuthType })}>
              <option value="NONE">없음</option>
              <option value="BASIC">Basic Auth</option>
              <option value="BEARER">Bearer Token</option>
            </select>
          </div>
          {form.authType === 'BASIC' && (
            <div className="form-group" style={{ gridColumn: '1 / -1' }}>
              <label className="form-label">인증 설정 (JSON)</label>
              <input className="form-input" value={form.authConfig || ''}
                onChange={e => setForm({ ...form, authConfig: e.target.value })}
                placeholder='{"username": "...", "password": "..."}' />
            </div>
          )}
          {form.authType === 'BEARER' && (
            <div className="form-group" style={{ gridColumn: '1 / -1' }}>
              <label className="form-label">Bearer Token</label>
              <input className="form-input" value={form.authConfig || ''}
                onChange={e => setForm({ ...form, authConfig: e.target.value })}
                placeholder='{"token": "..."}' />
            </div>
          )}
          <div className="form-group" style={{ gridColumn: '1 / -1' }}>
            <label className="form-label">설명</label>
            <input className="form-input" value={form.description || ''}
              onChange={e => setForm({ ...form, description: e.target.value })} />
          </div>
          <div style={{ gridColumn: '1 / -1', textAlign: 'right' }}>
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
