'use client';

import { useCallback, useEffect, useState } from 'react';
import { testApi, endpointApi, mappingApi, TreeNode, TestCallResponse } from '@/lib/collectorApi';
import { datasourceApi } from '@/lib/api';
import { ApiEndpointDetail, ApiFieldMappingRequest, TransformType } from '@/types/api-collect';
import { DatasourceSimple, ColumnSearchResult } from '@/types';
import axios from 'axios';

interface MappingTabProps {
  endpoint: ApiEndpointDetail;
  onUpdate: () => void;
}

export default function MappingTab({ endpoint, onUpdate }: MappingTabProps) {
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<TestCallResponse | null>(null);
  const [selectedRoot, setSelectedRoot] = useState<string>(endpoint.dataRootPath || '');

  // datasource 선택
  const [datasources, setDatasources] = useState<DatasourceSimple[]>([]);
  const [selectedDatasourceId, setSelectedDatasourceId] = useState<string>(endpoint.targetDatasourceId || '');

  // target 테이블/컬럼
  const [tables, setTables] = useState<{ tableName: string; tableType: string }[]>([]);
  const [targetTable, setTargetTable] = useState<string>(endpoint.targetTableName || '');
  const [targetColumns, setTargetColumns] = useState<ColumnSearchResult[]>([]);

  // 매핑 행
  const [mappingRows, setMappingRows] = useState<MappingRow[]>([]);
  const [saving, setSaving] = useState(false);
  const [running, setRunning] = useState(false);

  // Orchestrator datasource 목록 로드
  useEffect(() => {
    datasourceApi.getSimple().then(setDatasources).catch(() => {});
  }, []);

  // datasource 선택 시 테이블 목록 로드
  useEffect(() => {
    if (selectedDatasourceId) {
      datasourceApi.searchTables(selectedDatasourceId).then(setTables).catch(() => setTables([]));
    } else {
      setTables([]);
    }
  }, [selectedDatasourceId]);

  // target 테이블 변경 시 컬럼 로드
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

  // 기존 매핑 로드
  useEffect(() => {
    if (endpoint.fieldMappings.length > 0) {
      setMappingRows(endpoint.fieldMappings.map(m => ({
        sourceFieldPath: m.sourceFieldPath,
        targetColumnName: m.targetColumnName,
        isPk: m.isPk,
        transformType: m.transformType,
        transformConfig: m.transformConfig || '',
        excluded: false,
      })));
    }
  }, [endpoint.fieldMappings]);

  const handleTestCall = async () => {
    try {
      setTesting(true);
      const result = await testApi.call(endpoint.id);
      setTestResult(result);
      if (result.dataRootPath) setSelectedRoot(result.dataRootPath);
    } catch (e: any) {
      alert('테스트 호출 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setTesting(false);
    }
  };

  const handleSelectRoot = async (path: string) => {
    setSelectedRoot(path);
    try {
      await endpointApi.update(endpoint.id, {
        apiName: endpoint.apiName, url: endpoint.url,
        httpMethod: endpoint.httpMethod, authType: endpoint.authType,
        dataRootPath: path,
      });
      // 필드 추출 → 매핑 행 초기화
      if (testResult?.responseTree) {
        const fields = findFieldsFromTree(testResult.responseTree, path);
        setMappingRows(fields.map(f => ({
          sourceFieldPath: f.path,
          targetColumnName: '',
          isPk: false,
          transformType: 'NONE' as TransformType,
          transformConfig: '',
          excluded: false,
        })));
      }
      onUpdate();
    } catch (e: any) {
      alert('저장 실패: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleDatasourceChange = async (dsId: string) => {
    setSelectedDatasourceId(dsId);
    setTargetTable('');
    setTargetColumns([]);
    try {
      await endpointApi.update(endpoint.id, {
        apiName: endpoint.apiName, url: endpoint.url,
        httpMethod: endpoint.httpMethod, authType: endpoint.authType,
        targetDatasourceId: dsId || undefined,
        targetTableName: '',
      });
      onUpdate();
    } catch (e: any) {
      alert('Datasource 저장 실패');
    }
  };

  const handleTargetTableChange = async (tableName: string) => {
    setTargetTable(tableName);
    try {
      await endpointApi.update(endpoint.id, {
        apiName: endpoint.apiName, url: endpoint.url,
        httpMethod: endpoint.httpMethod, authType: endpoint.authType,
        targetDatasourceId: selectedDatasourceId || undefined,
        targetTableName: tableName,
      });
      onUpdate();
    } catch (e: any) {
      alert('테이블 저장 실패');
    }
  };

  const handleSaveMappings = async () => {
    const activeRows = mappingRows.filter(r => !r.excluded && r.targetColumnName);
    if (activeRows.length === 0) { alert('매핑된 필드가 없습니다.'); return; }

    try {
      setSaving(true);
      const requests: ApiFieldMappingRequest[] = activeRows.map((r, i) => ({
        sourceFieldPath: r.sourceFieldPath,
        targetColumnName: r.targetColumnName,
        isPk: r.isPk,
        transformType: r.transformType,
        transformConfig: r.transformConfig || undefined,
        displayOrder: i,
      }));
      await mappingApi.save(endpoint.id, requests);
      alert('매핑이 저장되었습니다.');
      onUpdate();
    } catch (e: any) {
      alert('매핑 저장 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  const handleRun = async () => {
    if (!confirm('수동 실행하시겠습니까?')) return;
    try {
      setRunning(true);
      const result = await axios.post(`/collector-api/endpoints/${endpoint.id}/run`);
      const h = result.data;
      alert(`실행 완료: ${h.status}\n적재: ${h.insertCount}건, 스킵: ${h.skipCount}건${h.errorMessage ? '\n에러: ' + h.errorMessage : ''}`);
      onUpdate();
    } catch (e: any) {
      alert('실행 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setRunning(false);
    }
  };

  const updateRow = (index: number, field: string, value: any) => {
    const updated = [...mappingRows];
    updated[index] = { ...updated[index], [field]: value };
    setMappingRows(updated);
  };

  // --- 렌더링 ---

  // 테스트 호출 전 + data_root 미설정
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
            {/* Datasource 선택 */}
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
            {/* 테이블 선택 */}
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
              <label className="form-label" style={{ minWidth: '100px', margin: 0 }}>Target 테이블</label>
              <select className="form-select" value={targetTable} style={{ maxWidth: '300px' }}
                onChange={e => handleTargetTableChange(e.target.value)}
                disabled={!selectedDatasourceId}>
                <option value="">-- 테이블 선택 --</option>
                {tables.map(t => <option key={t.tableName} value={t.tableName}>{t.tableName}</option>)}
              </select>
              <input className="form-input" value={targetTable} style={{ maxWidth: '200px' }}
                onChange={e => setTargetTable(e.target.value)}
                placeholder="또는 직접 입력" />
              <label style={{ fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                <input type="checkbox" checked={endpoint.upsertEnabled}
                  onChange={async (e) => {
                    await endpointApi.update(endpoint.id, {
                      apiName: endpoint.apiName, url: endpoint.url,
                      httpMethod: endpoint.httpMethod, authType: endpoint.authType,
                      upsertEnabled: e.target.checked,
                    });
                    onUpdate();
                  }} />
                UPSERT
              </label>
            </div>
          </div>
        </div>
      )}

      {/* 필드 매핑 */}
      {selectedRoot && mappingRows.length > 0 && (
        <div className="card" style={{ marginBottom: '1rem' }}>
          <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 className="card-title">필드 매핑 ({mappingRows.filter(r => !r.excluded && r.targetColumnName).length}/{mappingRows.length})</h3>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button className="btn btn-primary btn-sm" onClick={handleSaveMappings} disabled={saving}>
                {saving ? '저장 중...' : '매핑 저장'}
              </button>
              <button className="btn btn-sm" onClick={handleRun} disabled={running || !endpoint.targetTableName}
                style={{ background: 'var(--success)', color: 'white' }}>
                {running ? '실행 중...' : '수동 실행'}
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
                  <th style={{ padding: '0.5rem', textAlign: 'center', width: '40px' }}>PK</th>
                  <th style={{ padding: '0.5rem', textAlign: 'left', width: '100px' }}>변환</th>
                  <th style={{ padding: '0.5rem', textAlign: 'center', width: '50px' }}>제외</th>
                </tr>
              </thead>
              <tbody>
                {mappingRows.map((row, i) => (
                  <tr key={i} style={{ opacity: row.excluded ? 0.4 : 1, borderBottom: '1px solid var(--gray-100)' }}>
                    <td style={{ padding: '0.35rem 0.5rem' }}>
                      <code style={{ fontSize: '0.8rem' }}>{row.sourceFieldPath}</code>
                    </td>
                    <td style={{ padding: '0.35rem 0.5rem', color: 'var(--gray-500)' }}>
                      {getFieldType(testResult?.responseTree, selectedRoot, row.sourceFieldPath)}
                    </td>
                    <td style={{ padding: '0.35rem', textAlign: 'center', color: 'var(--gray-400)' }}>→</td>
                    <td style={{ padding: '0.35rem 0.5rem' }}>
                      {targetColumns.length > 0 ? (
                        <select className="form-select" value={row.targetColumnName} disabled={row.excluded}
                          style={{ fontSize: '0.8rem' }}
                          onChange={e => updateRow(i, 'targetColumnName', e.target.value)}>
                          <option value="">-- 선택 --</option>
                          {targetColumns.map(c => (
                            <option key={c.columnName} value={c.columnName}>
                              {c.columnName} ({c.dataType}){c.isPrimaryKey ? ' [PK]' : ''}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <input className="form-input" value={row.targetColumnName} disabled={row.excluded}
                          style={{ fontSize: '0.8rem' }}
                          onChange={e => updateRow(i, 'targetColumnName', e.target.value)}
                          placeholder="컬럼명 입력" />
                      )}
                    </td>
                    <td style={{ padding: '0.35rem', textAlign: 'center' }}>
                      <input type="checkbox" checked={row.isPk} disabled={row.excluded}
                        onChange={e => updateRow(i, 'isPk', e.target.checked)} />
                    </td>
                    <td style={{ padding: '0.35rem 0.5rem' }}>
                      <select className="form-select" value={row.transformType} disabled={row.excluded}
                        style={{ fontSize: '0.75rem' }}
                        onChange={e => updateRow(i, 'transformType', e.target.value)}>
                        <option value="NONE">없음</option>
                        <option value="DATE_FORMAT">날짜변환</option>
                        <option value="NUMBER">숫자</option>
                      </select>
                    </td>
                    <td style={{ padding: '0.35rem', textAlign: 'center' }}>
                      <input type="checkbox" checked={row.excluded}
                        onChange={e => updateRow(i, 'excluded', e.target.checked)} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
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
  isPk: boolean;
  transformType: TransformType;
  transformConfig: string;
  excluded: boolean;
}

// --- JSON 트리 뷰 ---

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

function getFieldType(tree: TreeNode | null | undefined, rootPath: string, fieldPath: string): string {
  if (!tree) return '';
  const node = navigateTree(tree, rootPath);
  if (!node?.children) return '';
  const fields: { path: string; type: string; sample: string | null }[] = [];
  collectLeafFields(node.children, '', fields);
  const found = fields.find(f => f.path === fieldPath);
  return found?.type || '';
}
