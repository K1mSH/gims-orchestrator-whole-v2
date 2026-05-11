'use client';

import React, { useCallback, useEffect, useState, useMemo } from 'react';
import { testApi, endpointApi, mappingApi, TreeNode, TestCallResponse } from '@/lib/collectorApi';
import { datasourceApi } from '@/lib/api';
import { ApiEndpointDetail, ApiFieldMappingRequest, TransformType } from '@/types/api-collect';
import { DatasourceSimple, ColumnSearchResult } from '@/types';
import styles from './MappingTab.module.css';

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

interface MappingRow {
  sourceFieldPath: string;
  targetColumnName: string;
  sourceFieldType: string;
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

export default function MappingTab({ endpoint, onUpdate }: MappingTabProps) {
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<TestCallResponse | null>(null);
  const [selectedRoot, setSelectedRoot] = useState<string>(endpoint.dataRootPath || '');

  const [datasources, setDatasources] = useState<DatasourceSimple[]>([]);
  const [selectedDatasourceId, setSelectedDatasourceId] = useState<string>(endpoint.targetDatasourceId || '');

  const [tables, setTables] = useState<{ tableName: string; tableType: string; remarks?: string | null }[]>([]);
  const [targetTable, setTargetTable] = useState<string>(endpoint.targetTableName || '');
  const [targetColumns, setTargetColumns] = useState<ColumnSearchResult[]>([]);
  const [upsertEnabled, setUpsertEnabled] = useState<boolean>(endpoint.upsertEnabled ?? true);
  const [savingLoadSettings, setSavingLoadSettings] = useState(false);

  const [mappingRows, setMappingRows] = useState<MappingRow[]>([]);
  const [derivedRows, setDerivedRows] = useState<DerivedRow[]>([]);
  const [expandedDerived, setExpandedDerived] = useState<Set<number>>(new Set());

  const [saving, setSaving] = useState(false);

  const sourceFields = useMemo(() =>
    mappingRows.filter(r => r.targetColumnName).map(r => r.sourceFieldPath),
    [mappingRows]
  );

  const sampleValues = useMemo(() => {
    if (!testResult?.responseTree || !selectedRoot) return {};
    const node = navigateTree(testResult.responseTree, selectedRoot);
    if (!node?.children) return {};

    const samples: Record<string, string[]> = {};
    const arrayNode = node;
    if (arrayNode.children) {
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

  // endpoint 변경 시 (다른 엔드포인트 진입 또는 onUpdate 후) 적재 설정 동기화
  useEffect(() => {
    setSelectedDatasourceId(endpoint.targetDatasourceId || '');
    setTargetTable(endpoint.targetTableName || '');
    setUpsertEnabled(endpoint.upsertEnabled ?? true);
    setSelectedRoot(endpoint.dataRootPath || '');
  }, [endpoint.id, endpoint.targetDatasourceId, endpoint.targetTableName, endpoint.upsertEnabled, endpoint.dataRootPath]);

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

  useEffect(() => {
    if (endpoint.fieldMappings.length > 0) {
      const normal = endpoint.fieldMappings.filter(m => !m.isDerived);
      const derived = endpoint.fieldMappings.filter(m => m.isDerived);

      setMappingRows(normal.map(m => ({
        sourceFieldPath: m.sourceFieldPath,
        targetColumnName: m.targetColumnName,
        sourceFieldType: m.sourceFieldType || '',
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

      if (result.responseTree && (result.dataRootPath || selectedRoot)) {
        const root = result.dataRootPath || selectedRoot;
        const allFields = findFieldsFromTree(result.responseTree, root);
        // null/unknown 은 의미없는 타입이라 빈 값으로 처리
        const normType = (t: string) => (t === 'null' || t === 'unknown') ? '' : t;
        setMappingRows(prev => {
          const updated = prev.map(row => {
            let matched = allFields.find(f => f.path === row.sourceFieldPath);
            if (!matched) {
              matched = allFields.find(f =>
                f.path.endsWith('.' + row.sourceFieldPath) ||
                row.sourceFieldPath.endsWith('.' + f.path)
              );
            }
            if (!matched) return row;
            const t = normType(matched.type);
            // 새 타입이 의미있으면 갱신. null/unknown 만 받아왔으면 기존값 유지
            return t ? { ...row, sourceFieldType: t } : row;
          });
          const existingPaths = new Set(updated.map(r => r.sourceFieldPath));
          const newRows = allFields
            .filter(f => !existingPaths.has(f.path) && !updated.some(r =>
              r.sourceFieldPath.endsWith('.' + f.path) || f.path.endsWith('.' + r.sourceFieldPath)
            ))
            .map(f => ({
              sourceFieldPath: f.path,
              targetColumnName: '',
              sourceFieldType: normType(f.type),
              isConflictKey: false,
              transformType: 'NONE' as TransformType,
              transformConfig: '',
              excluded: false,
            }));
          return [...updated, ...newRows];
        });
      }
    } catch (e: any) {
      alert('테스트 호출 실패: ' + (e.response?.data?.message || e.message));
    } finally {
      setTesting(false);
    }
  };

  const baseUpdateFields = () => ({
    apiName: endpoint.apiName, url: endpoint.url,
    httpMethod: endpoint.httpMethod, authType: endpoint.authType,
    dataRootPath: endpoint.dataRootPath || undefined,
    targetDatasourceId: endpoint.targetDatasourceId || undefined,
    targetTableName: endpoint.targetTableName || undefined,
    upsertEnabled: endpoint.upsertEnabled,
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
          sourceFieldType: (f.type === 'null' || f.type === 'unknown') ? '' : f.type,
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
        upsertEnabled,
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
        ...activeNormal.map((r, i) => ({
          sourceFieldPath: r.sourceFieldPath,
          targetColumnName: r.targetColumnName,
          sourceFieldType: r.sourceFieldType || undefined,
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

  const addFixedRow = () => {
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
      <div className="app-card">
        <div className={styles.emptyWrap}>
          <p className={styles.emptyText}>매핑을 설정하려면 먼저 테스트 호출을 실행하세요.</p>
          <button type="button" className="krds-btn small" onClick={handleTestCall} disabled={testing}>
            {testing ? '호출 중...' : '테스트 호출'}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      {/* 테스트 호출 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">테스트 호출</h2>
          <button type="button" className="krds-btn small" onClick={handleTestCall} disabled={testing}>
            {testing ? '호출 중...' : '호출'}
          </button>
        </div>
        {testResult && (
          <>
            <div className={styles.testStatusRow}>
              <span className={`krds-badge ${testResult.success ? 'bg-light-success' : 'bg-light-danger'}`}>
                {testResult.success ? 'SUCCESS' : 'FAILED'} {testResult.httpStatusCode > 0 && `(${testResult.httpStatusCode})`}
              </span>
            </div>
            {testResult.resolvedParams && Object.keys(testResult.resolvedParams).length > 0 && (
              <div className={styles.paramsLine}>
                파라미터:
                {Object.entries(testResult.resolvedParams).map(([k, v]) => (
                  <span key={k} className={styles.paramChip}>
                    {k}={v.length > 30 ? v.substring(0, 30) + '...' : v}
                  </span>
                ))}
              </div>
            )}
          </>
        )}
      </div>

      {/* 응답 트리 */}
      {testResult?.success && testResult.responseTree && (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">응답 구조</h2>
          </div>
          <div className={styles.treeWrap}>
            <JsonTreeView node={testResult.responseTree} path="" selectedRoot={selectedRoot} onSelectRoot={handleSelectRoot} />
          </div>
          {selectedRoot && (
            <div className={styles.dataRootFoot}>
              data_root_path:<code className={styles.dataRootCode}>{selectedRoot}</code>
            </div>
          )}
        </div>
      )}

      {/* Target Datasource + 테이블 선택 */}
      {selectedRoot && (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">적재 설정</h2>
            <button type="button" className="krds-btn small" onClick={handleSaveLoadSettings} disabled={savingLoadSettings}>
              {savingLoadSettings ? '저장 중...' : '저장'}
            </button>
          </div>
          <div className={styles.loadSettings}>
            <div className={styles.loadRow}>
              <div className={styles.loadLabel}>Target DB</div>
              <select className={`krds-input small ${styles.loadSelect}`} value={selectedDatasourceId}
                onChange={e => handleDatasourceChange(e.target.value)}>
                <option value="">-- Datasource 선택 --</option>
                {datasources.map(ds => (
                  <option key={ds.datasourceId} value={ds.datasourceId}>
                    {ds.datasourceName} ({ds.dbType})
                  </option>
                ))}
              </select>
            </div>
            <div className={styles.loadRow}>
              <div className={styles.loadLabel}>Target 테이블</div>
              <select className={`krds-input small ${styles.loadSelect}`} value={targetTable}
                onChange={e => handleTargetTableChange(e.target.value)}
                disabled={!selectedDatasourceId}>
                <option value="">-- 테이블 선택 --</option>
                {tables.map(t => <option key={t.tableName} value={t.tableName}>{t.tableName}{t.remarks ? ` (${t.remarks})` : ''}</option>)}
              </select>
            </div>
            <div className={styles.loadRow}>
              <div className={styles.loadLabel}>UPSERT</div>
              <div className={styles.loadCheckbox}>
                <div className="krds-form-check medium">
                  <input type="checkbox" id="upsert-enabled"
                    checked={upsertEnabled}
                    onChange={e => setUpsertEnabled(e.target.checked)} />
                  <label htmlFor="upsert-enabled" aria-label="UPSERT 활성"></label>
                </div>
                <label htmlFor="upsert-enabled" className={styles.loadCheckboxLabel}>중복키 충돌 시 갱신 (UPDATE)</label>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 필드 매핑 */}
      {selectedRoot && (mappingRows.length > 0 || derivedRows.length > 0) && (
        <div className="app-card">
          <div className="app-card__header">
            <h2 className="app-card__title">
              필드 매핑 ({mappingRows.filter(r => r.targetColumnName).length + derivedRows.filter(r => r.targetColumnName).length}
              /{mappingRows.length + derivedRows.length})
            </h2>
            <button type="button" className="krds-btn small" onClick={handleSaveMappings} disabled={saving}>
              {saving ? '저장 중...' : '저장'}
            </button>
          </div>
          <div className={styles.mappingTableWrap}>
            <table className={styles.mappingTable}>
              <thead>
                <tr>
                  <th>API 필드</th>
                  <th className={styles.thWidth80}>타입</th>
                  <th className={styles.thArrow}></th>
                  <th>Target 컬럼</th>
                  <th className={`${styles.thCenter} ${styles.thWidth60}`}>중복키</th>
                  <th className={styles.thWidth120}>변환</th>
                </tr>
              </thead>
              <tbody>
                {mappingRows.map((row, i) => (
                  <tr key={`n-${i}`} className={row.targetColumnName ? '' : styles.mappingRowDimmed}>
                    <td>
                      <code className={styles.sourceCode}>{row.sourceFieldPath}</code>
                    </td>
                    <td className={styles.muted}>
                      {(() => {
                        const t = getFieldType(testResult?.responseTree, selectedRoot, row.sourceFieldPath);
                        const norm = (t === 'null' || t === 'unknown') ? '' : t;
                        return norm || row.sourceFieldType || '-';
                      })()}
                    </td>
                    <td className={styles.muted + ' ' + styles.thCenter}>→</td>
                    <td>
                      {targetColumns.length > 0 ? (
                        <select className="krds-input small" value={row.targetColumnName}
                          onChange={e => updateRow(i, 'targetColumnName', e.target.value)}>
                          <option value="">-- 선택 --</option>
                          {targetColumns.map(c => (
                            <option key={c.columnName} value={c.columnName}>
                              {c.columnName} ({c.dataType}){c.isPrimaryKey ? ' [PK]' : ''}{c.remarks ? ` - ${c.remarks}` : ''}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <input className="krds-input small" value={row.targetColumnName}
                          onChange={e => updateRow(i, 'targetColumnName', e.target.value)}
                          placeholder="컬럼명 입력" />
                      )}
                    </td>
                    <td className={styles.checkCell}>
                      <div className="krds-form-check medium">
                        <input type="checkbox" id={`mapping-key-${i}`}
                          checked={row.isConflictKey} disabled={!row.targetColumnName}
                          onChange={e => updateRow(i, 'isConflictKey', e.target.checked)} />
                        <label htmlFor={`mapping-key-${i}`} aria-label="중복키"></label>
                      </div>
                    </td>
                    <td>
                      <select className="krds-input small" value={row.transformType} disabled={!row.targetColumnName}
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

                {derivedRows.length > 0 && (
                  <tr className={styles.sectionDivider}>
                    <td colSpan={6}>파생 컬럼 (LOOKUP / 고정값)</td>
                  </tr>
                )}

                {derivedRows.map((row, i) => {
                  const isExpanded = expandedDerived.has(i);
                  const isFixed = row.transformType === 'DEFAULT_VALUE';
                  const preview = isExpanded ? getExtractPreview(row.sourceFieldPath, row.extractPattern, row.extractGroup) : [];

                  return (
                    <React.Fragment key={`d-${i}`}>
                      <tr className={isFixed ? styles.derivedFixedRow : styles.derivedDerivedRow}>
                        <td>
                          {isFixed ? (
                            <span className={styles.muted}>고정값</span>
                          ) : (
                            <select className="krds-input small" value={row.sourceFieldPath}
                              onChange={e => updateDerivedRow(i, 'sourceFieldPath', e.target.value)}>
                              <option value="">-- 소스 필드 --</option>
                              {sourceFields.map(f => <option key={f} value={f}>{f}</option>)}
                            </select>
                          )}
                        </td>
                        <td className={styles.muted}>{isFixed ? '고정' : '파생'}</td>
                        <td className={styles.muted + ' ' + styles.thCenter}>→</td>
                        <td>
                          {targetColumns.length > 0 ? (
                            <select className="krds-input small" value={row.targetColumnName}
                              onChange={e => updateDerivedRow(i, 'targetColumnName', e.target.value)}>
                              <option value="">-- 선택 --</option>
                              {targetColumns.map(c => (
                                <option key={c.columnName} value={c.columnName}>
                                  {c.columnName} ({c.dataType}){c.isPrimaryKey ? ' [PK]' : ''}
                                </option>
                              ))}
                            </select>
                          ) : (
                            <input className="krds-input small" value={row.targetColumnName}
                              onChange={e => updateDerivedRow(i, 'targetColumnName', e.target.value)}
                              placeholder="컬럼명 입력" />
                          )}
                        </td>
                        <td className={styles.checkCell}>
                          <div className="krds-form-check medium">
                            <input type="checkbox" id={`derived-key-${i}`}
                              checked={row.isConflictKey}
                              onChange={e => updateDerivedRow(i, 'isConflictKey', e.target.checked)} />
                            <label htmlFor={`derived-key-${i}`} aria-label="중복키"></label>
                          </div>
                        </td>
                        <td>
                          <div className={styles.derivedActionCell}>
                            {isFixed ? (
                              <input className="krds-input small" value={row.defaultValue || ''}
                                onChange={e => updateDerivedRow(i, 'defaultValue', e.target.value)}
                                placeholder="고정값 입력" />
                            ) : (
                              <button type="button"
                                className={`krds-btn xsmall ${isExpanded ? '' : 'secondary'}`}
                                onClick={() => toggleDerivedExpand(i)}>
                                {isExpanded ? '설정 ▴' : '설정 ▾'}
                              </button>
                            )}
                            <button type="button" className={styles.removeBtn} onClick={() => removeDerivedRow(i)}>✕</button>
                          </div>
                        </td>
                      </tr>

                      {isExpanded && (
                        <tr className={styles.derivedDerivedRow}>
                          <td colSpan={6} className={styles.lookupPanel}>
                            <div className={styles.lookupGrid}>
                              <div className={styles.lookupFull}>
                                <label className="app-form-label">추출 정규식</label>
                                <div className={styles.lookupExtractRow}>
                                  <input className={`krds-input small ${styles.lookupExtractInput}`} value={row.extractPattern}
                                    onChange={e => updateDerivedRow(i, 'extractPattern', e.target.value)}
                                    placeholder="정규식 입력 (예: https?://(?:www\.)?([^/]+))" />
                                  <span className={styles.lookupExtractGroupLabel}>그룹:</span>
                                  <input className={`krds-input small ${styles.lookupExtractGroup}`} type="number" min={0} value={row.extractGroup}
                                    onChange={e => updateDerivedRow(i, 'extractGroup', parseInt(e.target.value) || 1)} />
                                </div>
                                <div className={styles.presetRow}>
                                  {EXTRACT_PRESETS.map(p => (
                                    <button key={p.label} type="button" className="krds-btn xsmall secondary"
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

                              {preview.length > 0 && (
                                <div className={`${styles.lookupFull} ${styles.previewBox}`}>
                                  <div className={styles.previewLabel}>추출 미리보기</div>
                                  <table className={styles.previewTable}>
                                    <tbody>
                                      {preview.map((p, pi) => (
                                        <tr key={pi}>
                                          <td className={styles.previewOriginal}>
                                            {p.original.length > 50 ? p.original.substring(0, 50) + '...' : p.original}
                                          </td>
                                          <td className={styles.previewArrow}>→</td>
                                          <td className={styles.previewExtracted}>{p.extracted}</td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                </div>
                              )}

                              <div className="app-form-field">
                                <label className="app-form-label">코드 파라미터</label>
                                <input className="krds-input small" value={row.lookupParam}
                                  onChange={e => updateDerivedRow(i, 'lookupParam', e.target.value)}
                                  placeholder="NGW_0118" />
                              </div>
                              <div className="app-form-field">
                                <label className="app-form-label">데이터 루트</label>
                                <input className="krds-input small" value={row.lookupDataRootPath}
                                  onChange={e => updateDerivedRow(i, 'lookupDataRootPath', e.target.value)}
                                  placeholder="data.common" />
                              </div>
                              <div className="app-form-field">
                                <label className="app-form-label">기본값 (매칭 실패 시)</label>
                                <input className="krds-input small" value={row.defaultValue}
                                  onChange={e => updateDerivedRow(i, 'defaultValue', e.target.value)}
                                  placeholder="기타" />
                              </div>
                              <div className="app-form-field">
                                <label className="app-form-label">키 필드</label>
                                <input className="krds-input small" value={row.lookupKeyField}
                                  onChange={e => updateDerivedRow(i, 'lookupKeyField', e.target.value)}
                                  placeholder="detailCode" />
                              </div>
                              <div className="app-form-field">
                                <label className="app-form-label">값 필드</label>
                                <input className="krds-input small" value={row.lookupValueField}
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
          </div>

          <div className={styles.addDerivedRow}>
            <button type="button" className={styles.addDerivedBtn} onClick={addDerivedRow}>+ 파생 컬럼 추가</button>
            <button type="button" className={`${styles.addDerivedBtn} ${styles.addFixedBtn}`} onClick={addFixedRow}>+ 고정값 컬럼 추가</button>
          </div>
        </div>
      )}
    </div>
  );
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
