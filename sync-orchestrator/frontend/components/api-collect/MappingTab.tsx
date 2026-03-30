'use client';

import { useCallback, useEffect, useState, useMemo } from 'react';
import { testApi, endpointApi, mappingApi, TreeNode, TestCallResponse } from '@/lib/collectorApi';
import { datasourceApi } from '@/lib/api';
import { ApiEndpointDetail, ApiFieldMappingRequest, TransformType } from '@/types/api-collect';
import { DatasourceSimple, ColumnSearchResult } from '@/types';

interface MappingTabProps {
  endpoint: ApiEndpointDetail;
  onUpdate: () => void;
}

// --- 정규식 프리셋 ---
const EXTRACT_PRESETS = [
  { label: '도메인', pattern: 'https?://(?:www\\.)?([^/]+)', group: 1 },
  { label: '숫자만', pattern: '(\\d+)', group: 1 },
  { label: '괄호 안', pattern: '\\(([^)]+)\\)', group: 1 },
];

export default function MappingTab({ endpoint, onUpdate }: MappingTabProps) {
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<TestCallResponse | null>(null);
  const [selectedRoot, setSelectedRoot] = useState<string>(endpoint.dataRootPath || '');

  const [datasources, setDatasources] = useState<DatasourceSimple[]>([]);
  const [selectedDatasourceId, setSelectedDatasourceId] = useState<string>(endpoint.targetDatasourceId || '');

  const [tables, setTables] = useState<{ tableName: string; tableType: string; remarks?: string | null }[]>([]);
  const [targetTable, setTargetTable] = useState<string>(endpoint.targetTableName || '');
  const [targetColumns, setTargetColumns] = useState<ColumnSearchResult[]>([]);
  const [savingLoadSettings, setSavingLoadSettings] = useState(false);

  // 1:1 매핑 행
  const [mappingRows, setMappingRows] = useState<MappingRow[]>([]);
  // 파생 컬럼 행
  const [derivedRows, setDerivedRows] = useState<DerivedRow[]>([]);
  // LOOKUP 설정 펼침 상태
  const [expandedDerived, setExpandedDerived] = useState<Set<number>>(new Set());

  const [saving, setSaving] = useState(false);

  // 소스 필드 목록 (파생 컬럼 소스 선택용)
  const sourceFields = useMemo(() =>
    mappingRows.filter(r => r.targetColumnName).map(r => r.sourceFieldPath),
    [mappingRows]
  );

  // 테스트 호출 응답에서 소스 필드별 샘플값 추출
  const sampleValues = useMemo(() => {
    if (!testResult?.responseTree || !selectedRoot) return {};
    const node = navigateTree(testResult.responseTree, selectedRoot);
    if (!node?.children) return {};

    // 첫 번째 배열 요소의 children에서 leaf 값 수집
    const samples: Record<string, string[]> = {};
    // responseTree에서 배열 노드 찾기
    const arrayNode = node;
    if (arrayNode.children) {
      // 배열 요소들에서 샘플 수집 (최대 5건)
      for (let ci = 0; ci < Math.min(arrayNode.children.length, 5); ci++) {
        const child = arrayNode.children[ci];
        if (child.children) {
          collectSampleValues(child.children, '', samples);
        }
      }
    }
    return samples;
  }, [testResult, selectedRoot]);

  useEffect(() => {
    datasourceApi.getSimple().then(setDatasources).catch(() => {});
  }, []);

  useEffect(() => {
    if (selectedDatasourceId) {
      datasourceApi.searchTables(selectedDatasourceId).then(setTables).catch(() => setTables([]));
    } else {
      setTables([]);
    }
  }, [selectedDatasourceId]);

  const loadColumns = useCallback(async (tableName: string) => {
    if (!tableName || !selectedDatasourceId) { setTargetColumns([]); return; }
    try {
      const cols = await datasourceApi.searchColumns(selectedDatasourceId, tableName);
      setTargetColumns(cols);
    } catch { setTargetColumns([]); }
  }, [selectedDatasourceId]);

  useEffect(() => {
    if (targetTable) loadColumns(targetTable);
  }, [targetTable, loadColumns]);

  // 기존 매핑 로드 (1:1 + 파생 분리)
  useEffect(() => {
    if (endpoint.fieldMappings.length > 0) {
      const normal = endpoint.fieldMappings.filter(m => !m.isDerived);
      const derived = endpoint.fieldMappings.filter(m => m.isDerived);

      setMappingRows(normal.map(m => ({
        sourceFieldPath: m.sourceFieldPath,
        targetColumnName: m.targetColumnName,
        isConflictKey: m.isConflictKey,
        transformType: m.transformType,
        transformConfig: m.transformConfig || '',
        excluded: false,
      })));

      setDerivedRows(derived.map(m => ({
        sourceFieldPath: m.sourceFieldPath,
        targetColumnName: m.targetColumnName,
        isConflictKey: m.isConflictKey,
        transformType: m.transformType,
        extractPattern: m.extractPattern || '',
        extractGroup: m.extractGroup ?? 1,
        lookupParam: m.lookupParam || '',
        lookupKeyField: m.lookupKeyField || '',
        lookupValueField: m.lookupValueField || '',
        lookupDataRootPath: m.lookupDataRootPath || '',
        lookupMatchType: m.lookupMatchType || 'EXACT',
        defaultValue: m.defaultValue || '',
      })));
    }
  }, [endpoint.fieldMappings]);

  const handleTestCall = async () => {
    try {
      setTesting(true);
      const result = await testApi.call(endpoint.id);
      setTestResult(result);
      if (result.dataRootPath) setSelectedRoot(result.dataRootPath);

      // 응답 필드와 기존 매핑 머지 — 매핑 안 된 필드도 행으로 추가
      if (result.responseTree && (result.dataRootPath || selectedRoot)) {
        const root = result.dataRootPath || selectedRoot;
        const allFields = findFieldsFromTree(result.responseTree, root);
        const existingPaths = new Set(mappingRows.map(r => r.sourceFieldPath));
        const newRows = allFields
          .filter(f => !existingPaths.has(f.path))
          .map(f => ({
            sourceFieldPath: f.path,
            targetColumnName: '',
            isConflictKey: false,
            transformType: 'NONE' as TransformType,
            transformConfig: '',
            excluded: false,
          }));
        if (newRows.length > 0) {
          setMappingRows(prev => [...prev, ...newRows]);
        }
      }
    } catch (e: any) {
      alert('테스트 호출 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setTesting(false);
    }
  };

  // endpoint 기존값 베이스 (부분 업데이트 시 다른 필드가 null로 덮어씌워지는 것 방지)
  const baseUpdateFields = () => ({
    apiName: endpoint.apiName, url: endpoint.url,
    httpMethod: endpoint.httpMethod, authType: endpoint.authType,
    dataRootPath: endpoint.dataRootPath || undefined,
    targetDatasourceId: endpoint.targetDatasourceId || undefined,
    targetTableName: endpoint.targetTableName || undefined,
    description: endpoint.description || '',
  });

  const handleSelectRoot = async (path: string) => {
    setSelectedRoot(path);
    try {
      await endpointApi.update(endpoint.id, {
        ...baseUpdateFields(),
        dataRootPath: path,
      });
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
      }
      onUpdate();
    } catch (e: any) {
      alert('저장 실패: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleDatasourceChange = (dsId: string) => {
    setSelectedDatasourceId(dsId);
    setTargetTable('');
    setTargetColumns([]);
  };

  const handleTargetTableChange = (tableName: string) => {
    setTargetTable(tableName);
  };

  const handleSaveLoadSettings = async () => {
    try {
      setSavingLoadSettings(true);
      await endpointApi.update(endpoint.id, {
        ...baseUpdateFields(),
        targetDatasourceId: selectedDatasourceId || undefined,
        targetTableName: targetTable || undefined,
      });
      onUpdate();
    } catch (e: any) {
      alert('적재 설정 저장 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setSavingLoadSettings(false);
    }
  };

  const handleSaveMappings = async () => {
    const activeNormal = mappingRows.filter(r => r.targetColumnName);
    const activeDerived = derivedRows.filter(r => r.targetColumnName);
    if (activeNormal.length === 0 && activeDerived.length === 0) {
      alert('매핑된 필드가 없습니다.'); return;
    }

    try {
      setSaving(true);
      const requests: ApiFieldMappingRequest[] = [
        // 1:1 매핑
        ...activeNormal.map((r, i) => ({
          sourceFieldPath: r.sourceFieldPath,
          targetColumnName: r.targetColumnName,
          isConflictKey: r.isConflictKey,
          transformType: r.transformType,
          transformConfig: r.transformConfig || undefined,
          displayOrder: i,
          isDerived: false,
        })),
        // 파생 컬럼
        ...activeDerived.map((r, i) => ({
          sourceFieldPath: r.sourceFieldPath,
          targetColumnName: r.targetColumnName,
          isConflictKey: r.isConflictKey,
          transformType: r.transformType as TransformType,
          displayOrder: activeNormal.length + i,
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
      await mappingApi.save(endpoint.id, requests);
      alert('매핑이 저장되었습니다.');
      onUpdate();
    } catch (e: any) {
      alert('매핑 저장 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  const updateRow = (index: number, field: string, value: any) => {
    const updated = [...mappingRows];
    updated[index] = { ...updated[index], [field]: value };
    setMappingRows(updated);
  };

  const updateDerivedRow = (index: number, field: string, value: any) => {
    const updated = [...derivedRows];
    updated[index] = { ...updated[index], [field]: value };
    setDerivedRows(updated);
  };

  const addDerivedRow = () => {
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
  };

  const removeDerivedRow = (index: number) => {
    setDerivedRows(derivedRows.filter((_, i) => i !== index));
    setExpandedDerived(prev => {
      const next = new Set<number>();
      prev.forEach(v => { if (v < index) next.add(v); else if (v > index) next.add(v - 1); });
      return next;
    });
  };

  const toggleDerivedExpand = (index: number) => {
    setExpandedDerived(prev => {
      const next = new Set(prev);
      next.has(index) ? next.delete(index) : next.add(index);
      return next;
    });
  };

  // 정규식 미리보기 계산
  const getExtractPreview = (sourceField: string, pattern: string, group: number): { original: string; extracted: string }[] => {
    if (!pattern || !sourceField) return [];
    const values = sampleValues[sourceField] || [];
    try {
      const regex = new RegExp(pattern);
      return values.slice(0, 5).map(v => {
        const match = v.match(regex);
        return { original: v, extracted: match ? (match[group] || v) : v };
      });
    } catch {
      return values.slice(0, 3).map(v => ({ original: v, extracted: '(정규식 오류)' }));
    }
  };

  // --- 렌더링 ---

  if (!testResult && !endpoint.dataRootPath) {
    return (
      <div className="card" style={{ padding: '2rem', textAlign: 'center' }}>
        <p style={{ color: 'var(--gray-500)', marginBottom: '1rem' }}>
          매핑을 설정하려면 먼저 테스트 호출을 실행하세요.
        </p>
        <button className="btn btn-primary" onClick={handleTestCall} disabled={testing}>
          {testing ? '호출 중...' : '테스트 호출'}
        </button>
      </div>
    );
  }

  return (
    <div>
      {/* 테스트 호출 */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 className="card-title">테스트 호출</h3>
          <button className="btn btn-sm btn-primary" onClick={handleTestCall} disabled={testing}>
            {testing ? '호출 중...' : '다시 호출'}
          </button>
        </div>
        {testResult && (
          <div style={{ padding: '0.75rem 1rem' }}>
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', fontSize: '0.85rem' }}>
              <span style={{
                padding: '2px 8px', borderRadius: '4px', fontWeight: 600,
                background: testResult.success ? '#dcfce7' : '#fee2e2',
                color: testResult.success ? '#166534' : '#991b1b',
              }}>
                {testResult.success ? 'SUCCESS' : 'FAILED'} {testResult.httpStatusCode > 0 && `(${testResult.httpStatusCode})`}
              </span>
            </div>
            {testResult.resolvedParams && Object.keys(testResult.resolvedParams).length > 0 && (
              <div style={{ marginTop: '0.5rem', fontSize: '0.8rem', color: 'var(--gray-600)' }}>
                파라미터: {Object.entries(testResult.resolvedParams).map(([k, v]) => (
                  <span key={k} style={{ display: 'inline-block', margin: '2px 4px', padding: '1px 6px', background: 'var(--gray-100)', borderRadius: '3px' }}>
                    {k}={v.length > 30 ? v.substring(0, 30) + '...' : v}
                  </span>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* 응답 트리 */}
      {testResult?.success && testResult.responseTree && (
        <div className="card" style={{ marginBottom: '1rem' }}>
          <div className="card-header"><h3 className="card-title">응답 구조</h3></div>
          <div style={{ padding: '1rem', fontFamily: 'monospace', fontSize: '0.8rem', maxHeight: '300px', overflow: 'auto' }}>
            <JsonTreeView node={testResult.responseTree} path="" selectedRoot={selectedRoot} onSelectRoot={handleSelectRoot} />
          </div>
          {selectedRoot && (
            <div style={{ padding: '0.5rem 1rem', borderTop: '1px solid var(--gray-200)', fontSize: '0.85rem', background: '#f0fdf4' }}>
              data_root_path: <code style={{ fontWeight: 600 }}>{selectedRoot}</code>
            </div>
          )}
        </div>
      )}

      {/* Target Datasource + 테이블 선택 */}
      {selectedRoot && (
        <div className="card" style={{ marginBottom: '1rem' }}>
          <div className="card-header"><h3 className="card-title">적재 설정</h3></div>
          <div style={{ padding: '1rem', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
              <label className="form-label" style={{ minWidth: '100px', margin: 0 }}>Target DB</label>
              <select className="form-select" value={selectedDatasourceId} style={{ maxWidth: '400px' }}
                onChange={e => handleDatasourceChange(e.target.value)}>
                <option value="">-- Datasource 선택 --</option>
                {datasources.map(ds => (
                  <option key={ds.datasourceId} value={ds.datasourceId}>
                    {ds.datasourceName} ({ds.dbType})
                  </option>
                ))}
              </select>
            </div>
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
              <label className="form-label" style={{ minWidth: '100px', margin: 0 }}>Target 테이블</label>
              <select className="form-select" value={targetTable} style={{ maxWidth: '300px' }}
                onChange={e => handleTargetTableChange(e.target.value)}
                disabled={!selectedDatasourceId}>
                <option value="">-- 테이블 선택 --</option>
                {tables.map(t => <option key={t.tableName} value={t.tableName}>{t.tableName}{t.remarks ? ` (${t.remarks})` : ''}</option>)}
              </select>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button className="btn btn-primary btn-sm" onClick={handleSaveLoadSettings} disabled={savingLoadSettings}>
                {savingLoadSettings ? '저장 중...' : '적재 설정 저장'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 필드 매핑 (1:1 + 파생) */}
      {selectedRoot && (mappingRows.length > 0 || derivedRows.length > 0) && (
        <div className="card" style={{ marginBottom: '1rem' }}>
          <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 className="card-title">
              필드 매핑 ({mappingRows.filter(r => r.targetColumnName).length + derivedRows.filter(r => r.targetColumnName).length}
              /{mappingRows.length + derivedRows.length})
            </h3>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button className="btn btn-primary btn-sm" onClick={handleSaveMappings} disabled={saving}>
                {saving ? '저장 중...' : '매핑 저장'}
              </button>
            </div>
          </div>
          <div style={{ padding: '0.5rem', overflowX: 'auto' }}>
            <table style={{ width: '100%', fontSize: '0.8rem' }}>
              <thead>
                <tr style={{ background: 'var(--gray-50)' }}>
                  <th style={{ padding: '0.5rem', textAlign: 'left' }}>API 필드</th>
                  <th style={{ padding: '0.5rem', textAlign: 'left', width: '80px' }}>타입</th>
                  <th style={{ padding: '0.5rem', textAlign: 'center', width: '16px' }}></th>
                  <th style={{ padding: '0.5rem', textAlign: 'left' }}>Target 컬럼</th>
                  <th style={{ padding: '0.5rem', textAlign: 'center', width: '60px' }}>중복키</th>
                  <th style={{ padding: '0.5rem', textAlign: 'left', width: '120px' }}>변환</th>
                </tr>
              </thead>
              <tbody>
                {/* 1:1 매핑 행 */}
                {mappingRows.map((row, i) => (
                  <tr key={`n-${i}`} style={{ opacity: row.targetColumnName ? 1 : 0.4, borderBottom: '1px solid var(--gray-100)' }}>
                    <td style={{ padding: '0.35rem 0.5rem' }}>
                      <code style={{ fontSize: '0.8rem' }}>{row.sourceFieldPath}</code>
                    </td>
                    <td style={{ padding: '0.35rem 0.5rem', color: 'var(--gray-500)' }}>
                      {getFieldType(testResult?.responseTree, selectedRoot, row.sourceFieldPath)}
                    </td>
                    <td style={{ padding: '0.35rem', textAlign: 'center', color: 'var(--gray-400)' }}>→</td>
                    <td style={{ padding: '0.35rem 0.5rem' }}>
                      {targetColumns.length > 0 ? (
                        <select className="form-select" value={row.targetColumnName}
                          style={{ fontSize: '0.8rem' }}
                          onChange={e => updateRow(i, 'targetColumnName', e.target.value)}>
                          <option value="">-- 선택 --</option>
                          {targetColumns.map(c => (
                            <option key={c.columnName} value={c.columnName}>
                              {c.columnName} ({c.dataType}){c.isPrimaryKey ? ' [PK]' : ''}{c.remarks ? ` - ${c.remarks}` : ''}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <input className="form-input" value={row.targetColumnName}
                          style={{ fontSize: '0.8rem' }}
                          onChange={e => updateRow(i, 'targetColumnName', e.target.value)}
                          placeholder="컬럼명 입력" />
                      )}
                    </td>
                    <td style={{ padding: '0.35rem', textAlign: 'center' }}>
                      <input type="checkbox" checked={row.isConflictKey} disabled={!row.targetColumnName}
                        onChange={e => updateRow(i, 'isConflictKey', e.target.checked)} />
                    </td>
                    <td style={{ padding: '0.35rem 0.5rem' }}>
                      <select className="form-select" value={row.transformType} disabled={!row.targetColumnName}
                        style={{ fontSize: '0.75rem' }}
                        onChange={e => updateRow(i, 'transformType', e.target.value)}>
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

                {/* 구분선 */}
                {derivedRows.length > 0 && (
                  <tr>
                    <td colSpan={6} style={{ padding: '0.25rem 0.5rem', background: 'var(--gray-100)', fontSize: '0.75rem', color: 'var(--gray-500)', fontWeight: 600 }}>
                      파생 컬럼 (LOOKUP / 고정값)
                    </td>
                  </tr>
                )}

                {/* 파생 컬럼 행 */}
                {derivedRows.map((row, i) => {
                  const isExpanded = expandedDerived.has(i);
                  const isFixed = row.transformType === 'DEFAULT_VALUE';
                  const preview = isExpanded ? getExtractPreview(row.sourceFieldPath, row.extractPattern, row.extractGroup) : [];

                  return (
                    <React.Fragment key={`d-${i}`}>
                      <tr style={{ borderBottom: isExpanded ? 'none' : '1px solid var(--gray-100)', background: isFixed ? '#f0fdf4' : '#fefce8' }}>
                        <td style={{ padding: '0.35rem 0.5rem' }}>
                          {isFixed ? (
                            <span style={{ fontSize: '0.8rem', color: 'var(--gray-500)' }}>고정값</span>
                          ) : (
                            <select className="form-select" value={row.sourceFieldPath}
                              style={{ fontSize: '0.8rem' }}
                              onChange={e => updateDerivedRow(i, 'sourceFieldPath', e.target.value)}>
                              <option value="">-- 소스 필드 --</option>
                              {sourceFields.map(f => <option key={f} value={f}>{f}</option>)}
                            </select>
                          )}
                        </td>
                        <td style={{ padding: '0.35rem 0.5rem', color: 'var(--gray-500)', fontSize: '0.75rem' }}>
                          {isFixed ? '고정' : '파생'}
                        </td>
                        <td style={{ padding: '0.35rem', textAlign: 'center', color: 'var(--gray-400)' }}>→</td>
                        <td style={{ padding: '0.35rem 0.5rem' }}>
                          {targetColumns.length > 0 ? (
                            <select className="form-select" value={row.targetColumnName}
                              style={{ fontSize: '0.8rem' }}
                              onChange={e => updateDerivedRow(i, 'targetColumnName', e.target.value)}>
                              <option value="">-- 선택 --</option>
                              {targetColumns.map(c => (
                                <option key={c.columnName} value={c.columnName}>
                                  {c.columnName} ({c.dataType}){c.isPrimaryKey ? ' [PK]' : ''}
                                </option>
                              ))}
                            </select>
                          ) : (
                            <input className="form-input" value={row.targetColumnName}
                              style={{ fontSize: '0.8rem' }}
                              onChange={e => updateDerivedRow(i, 'targetColumnName', e.target.value)}
                              placeholder="컬럼명 입력" />
                          )}
                        </td>
                        <td style={{ padding: '0.35rem', textAlign: 'center' }}>
                          <input type="checkbox" checked={row.isConflictKey}
                            onChange={e => updateDerivedRow(i, 'isConflictKey', e.target.checked)} />
                        </td>
                        <td style={{ padding: '0.35rem 0.5rem', display: 'flex', gap: '0.25rem', alignItems: 'center' }}>
                          {isFixed ? (
                            <input className="form-input" value={row.defaultValue || ''} style={{ fontSize: '0.8rem' }}
                              onChange={e => updateDerivedRow(i, 'defaultValue', e.target.value)}
                              placeholder="고정값 입력" />
                          ) : (
                            <button className="btn btn-sm" onClick={() => toggleDerivedExpand(i)}
                              style={{ fontSize: '0.7rem', padding: '1px 6px', background: isExpanded ? 'var(--primary)' : 'var(--gray-200)', color: isExpanded ? 'white' : 'var(--gray-700)' }}>
                              {isExpanded ? '설정 ▴' : '설정 ▾'}
                            </button>
                          )}
                          <button onClick={() => removeDerivedRow(i)}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--danger)', fontSize: '1rem', padding: '0 4px' }}>
                            ✕
                          </button>
                        </td>
                      </tr>

                      {/* LOOKUP 설정 패널 (인라인 확장) */}
                      {isExpanded && (
                        <tr style={{ background: '#fefce8' }}>
                          <td colSpan={7} style={{ padding: '0.5rem 1rem 1rem 2rem' }}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', fontSize: '0.8rem' }}>
                              {/* 추출 정규식 */}
                              <div style={{ gridColumn: '1 / -1' }}>
                                <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>추출 정규식</label>
                                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginTop: '0.25rem' }}>
                                  <input className="form-input" value={row.extractPattern}
                                    style={{ flex: 1, fontSize: '0.8rem', fontFamily: 'monospace' }}
                                    onChange={e => updateDerivedRow(i, 'extractPattern', e.target.value)}
                                    placeholder="정규식 입력 (예: https?://(?:www\.)?([^/]+))" />
                                  <label style={{ fontSize: '0.75rem', whiteSpace: 'nowrap' }}>그룹:</label>
                                  <input className="form-input" type="number" min={0} value={row.extractGroup}
                                    style={{ width: '50px', fontSize: '0.8rem' }}
                                    onChange={e => updateDerivedRow(i, 'extractGroup', parseInt(e.target.value) || 1)} />
                                </div>
                                <div style={{ marginTop: '0.25rem', display: 'flex', gap: '0.25rem' }}>
                                  {EXTRACT_PRESETS.map(p => (
                                    <button key={p.label} className="btn btn-sm"
                                      style={{ fontSize: '0.65rem', padding: '1px 6px', background: 'var(--gray-100)' }}
                                      onClick={() => {
                                        const u = [...derivedRows];
                                        u[i] = { ...u[i], extractPattern: p.pattern, extractGroup: p.group };
                                        setDerivedRows(u);
                                      }}>
                                      {p.label}
                                    </button>
                                  ))}
                                </div>
                              </div>

                              {/* 미리보기 */}
                              {preview.length > 0 && (
                                <div style={{ gridColumn: '1 / -1', background: 'white', borderRadius: '4px', padding: '0.5rem', border: '1px solid var(--gray-200)' }}>
                                  <div style={{ fontSize: '0.7rem', color: 'var(--gray-500)', marginBottom: '0.25rem', fontWeight: 600 }}>추출 미리보기</div>
                                  <table style={{ width: '100%', fontSize: '0.75rem' }}>
                                    <tbody>
                                      {preview.map((p, pi) => (
                                        <tr key={pi}>
                                          <td style={{ padding: '1px 4px', color: 'var(--gray-500)', maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                            {p.original.length > 50 ? p.original.substring(0, 50) + '...' : p.original}
                                          </td>
                                          <td style={{ padding: '1px 4px', color: 'var(--gray-400)' }}>→</td>
                                          <td style={{ padding: '1px 4px', fontWeight: 600 }}>{p.extracted}</td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                </div>
                              )}

                              {/* LOOKUP 설정 */}
                              <div>
                                <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>코드 파라미터</label>
                                <input className="form-input" value={row.lookupParam}
                                  style={{ fontSize: '0.8rem', marginTop: '0.15rem' }}
                                  onChange={e => updateDerivedRow(i, 'lookupParam', e.target.value)}
                                  placeholder="NGW_0118" />
                              </div>
                              <div>
                                <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>데이터 루트</label>
                                <input className="form-input" value={row.lookupDataRootPath}
                                  style={{ fontSize: '0.8rem', marginTop: '0.15rem' }}
                                  onChange={e => updateDerivedRow(i, 'lookupDataRootPath', e.target.value)}
                                  placeholder="data.common" />
                              </div>
                              <div>
                                <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>기본값 (매칭 실패 시)</label>
                                <input className="form-input" value={row.defaultValue}
                                  style={{ fontSize: '0.8rem', marginTop: '0.15rem' }}
                                  onChange={e => updateDerivedRow(i, 'defaultValue', e.target.value)}
                                  placeholder="기타" />
                              </div>
                              <div>
                                <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>키 필드</label>
                                <input className="form-input" value={row.lookupKeyField}
                                  style={{ fontSize: '0.8rem', marginTop: '0.15rem' }}
                                  onChange={e => updateDerivedRow(i, 'lookupKeyField', e.target.value)}
                                  placeholder="detailCode" />
                              </div>
                              <div>
                                <label style={{ fontWeight: 600, fontSize: '0.75rem', color: 'var(--gray-600)' }}>값 필드</label>
                                <input className="form-input" value={row.lookupValueField}
                                  style={{ fontSize: '0.8rem', marginTop: '0.15rem' }}
                                  onChange={e => updateDerivedRow(i, 'lookupValueField', e.target.value)}
                                  placeholder="detailCodeName" />
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
              <button className="btn btn-sm" onClick={addDerivedRow}
                style={{ fontSize: '0.8rem', background: 'var(--gray-50)', border: '1px dashed var(--gray-300)', width: '49%', padding: '0.4rem' }}>
                + 파생 컬럼 추가
              </button>
              <button className="btn btn-sm" onClick={() => {
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
              }} style={{ fontSize: '0.8rem', background: '#f0fdf4', border: '1px dashed var(--gray-300)', width: '49%', padding: '0.4rem' }}>
                + 고정값 컬럼 추가
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// --- 타입 ---

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

// --- JSON 트리 뷰 ---

import React from 'react';

function JsonTreeView({ node, path, selectedRoot, onSelectRoot, depth = 0 }: {
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
            {isSelected ? '선택됨' : '데이터 루트로 선택'}
          </button>
        )}
      </div>
      {expanded && hasChildren && node.children!.map((child, i) => (
        <JsonTreeView key={`${child.name}-${i}`} node={child} path={currentPath} selectedRoot={selectedRoot} onSelectRoot={onSelectRoot} depth={depth + 1} />
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

function collectSampleValues(children: TreeNode[], prefix: string, samples: Record<string, string[]>) {
  for (const child of children) {
    const path = prefix ? `${prefix}.${child.name}` : child.name;
    if (child.type === 'object' && child.children) {
      collectSampleValues(child.children, path, samples);
    } else if (child.sampleValue != null) {
      if (!samples[path]) samples[path] = [];
      if (!samples[path].includes(child.sampleValue)) {
        samples[path].push(child.sampleValue);
      }
    }
  }
}

function getFieldType(tree: TreeNode | null | undefined, rootPath: string, fieldPath: string): string {
  if (!tree) return '';
  const node = navigateTree(tree, rootPath);
  if (!node?.children) return '';
  const fields: { path: string; type: string; sample: string | null }[] = [];
  collectLeafFields(node.children, '', fields);
  const found = fields.find(f => f.path === fieldPath);
  return found?.type || '';
}
