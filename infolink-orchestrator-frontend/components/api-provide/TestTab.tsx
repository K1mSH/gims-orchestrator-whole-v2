'use client';

import { useState, useCallback } from 'react';
import { testApi } from '@/lib/providerApi';
import { ApiPrvOperation, DynamicQueryResult } from '@/types/api-provide';
import styles from './TestTab.module.css';

function formatSql(sql: string) {
  let formatted = sql
    .replace(/\bSELECT\b/gi, '\nSELECT')
    .replace(/\bFROM\b/gi, '\nFROM')
    .replace(/\bWHERE\b/gi, '\nWHERE')
    .replace(/\bAND\b/gi, '\n  AND')
    .replace(/\bOR\b/gi, '\n  OR')
    .replace(/\bORDER BY\b/gi, '\nORDER BY')
    .replace(/\bLIMIT\b/gi, '\nLIMIT')
    .replace(/\bOFFSET\b/gi, ' OFFSET')
    .trim();

  const parts: { text: string; kind: 'keyword' | 'string' | 'number' | 'text' }[] = [];
  const regex = /('(?:[^']|'')*'|\b\d+(?:\.\d+)?\b|\b(?:SELECT|FROM|WHERE|AND|OR|ORDER\s+BY|LIMIT|OFFSET|AS|IN|BETWEEN|LIKE|NOT|NULL|IS|COUNT|ROUND|TO_CHAR|COALESCE|SUBSTRING|ASC|DESC)\b)/gi;

  let lastIndex = 0;
  let match;
  while ((match = regex.exec(formatted)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ text: formatted.slice(lastIndex, match.index), kind: 'text' });
    }
    const val = match[0];
    if (val.startsWith("'")) {
      parts.push({ text: val, kind: 'string' });
    } else if (/^\d/.test(val)) {
      parts.push({ text: val, kind: 'number' });
    } else {
      parts.push({ text: val.toUpperCase(), kind: 'keyword' });
    }
    lastIndex = regex.lastIndex;
  }
  if (lastIndex < formatted.length) {
    parts.push({ text: formatted.slice(lastIndex), kind: 'text' });
  }
  return parts;
}

interface Props {
  operation: ApiPrvOperation;
}

export default function TestTab({ operation }: Props) {
  const [paramValues, setParamValues] = useState<Record<string, string>>({});
  const [pageSize, setPageSize] = useState(operation.pageSize || 100);
  const [result, setResult] = useState<DynamicQueryResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const executeTest = useCallback(async (targetPage: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await testApi.call(operation.id, paramValues, targetPage, pageSize);
      setResult(data);
    } catch (e: any) {
      setError(e.response?.data?.error || e.message);
    } finally {
      setLoading(false);
    }
  }, [operation.id, paramValues, pageSize]);

  const goPage = (targetPage: number) => {
    executeTest(targetPage);
  };

  const kindClass = (kind: 'keyword' | 'string' | 'number' | 'text') => {
    switch (kind) {
      case 'keyword': return styles.sqlKeyword;
      case 'string': return styles.sqlString;
      case 'number': return styles.sqlNumber;
      default: return styles.sqlText;
    }
  };

  return (
    <div className={styles.wrapper}>
      {/* 파라미터 입력 + 실행 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">테스트 호출</h2>
        </div>

        {operation.params.filter(p => !p.isHidden).length > 0 && (
          <div className={styles.paramsBlock}>
            <div className={styles.paramsLabel}>파라미터</div>
            <div className={styles.paramsGrid}>
              {operation.params.filter(p => !p.isHidden).map(p => (
                <div key={p.paramName} className={styles.paramField}>
                  <div className={styles.paramHead}>
                    {p.paramName} ({p.operator}, {p.dataType})
                    {p.isRequired && <span className={styles.paramRequired}> *</span>}
                  </div>
                  <input
                    className="krds-input small"
                    placeholder={p.defaultValue || '값 입력'}
                    value={paramValues[p.paramName] || ''}
                    onChange={e => setParamValues({ ...paramValues, [p.paramName]: e.target.value })}
                  />
                </div>
              ))}
            </div>
          </div>
        )}

        <div className={styles.runRow}>
          <div className={`${styles.paramField} ${styles.pageSizeField}`}>
            <div className={styles.paramHead}>페이지 크기 (최대 {operation.maxPageSize})</div>
            <input
              className="krds-input small"
              type="number"
              min={1}
              max={operation.maxPageSize}
              value={pageSize}
              onChange={e => setPageSize(Math.min(parseInt(e.target.value) || 10, operation.maxPageSize))}
            />
          </div>
          <button type="button" className={`krds-btn small ${styles.runButton}`} onClick={() => goPage(1)} disabled={loading}>
            {loading ? '실행 중...' : '테스트 실행'}
          </button>
        </div>
      </div>

      {/* 에러 */}
      {error && (
        <div className="app-alert app-alert--danger">
          <strong>실행 실패</strong>
          <div>{error}</div>
        </div>
      )}

      {/* 결과 */}
      {result && (
        <>
          {/* SQL 미리보기 */}
          {result.executedSql ? (
            <div className="app-card">
              <div className="app-card__header">
                <h2 className="app-card__title">실행된 SQL</h2>
              </div>
              <pre className={styles.sqlPre}>
                {formatSql(result.executedSql).map((part, i) => (
                  <span key={i} className={kindClass(part.kind)}>{part.text}</span>
                ))}
              </pre>
              <div className={styles.resultStats}>총 {result.pagination.totalCount}건 / {result.durationMs}ms</div>
            </div>
          ) : (
            <div className="app-card">
              <div className={styles.resultStats}>총 {result.pagination.totalCount}건 / {result.durationMs}ms</div>
            </div>
          )}

          {/* 데이터 테이블 */}
          <div className="app-card">
            <div className="app-card__header">
              <h2 className="app-card__title">결과 ({result.data.length}건)</h2>
              {result.pagination.totalPages > 1 && (
                <div className={styles.pager}>
                  <button
                    type="button"
                    className="krds-btn small secondary"
                    disabled={loading || result.pagination.page <= 1}
                    onClick={() => goPage(result.pagination.page - 1)}
                  >
                    이전
                  </button>
                  <span className={styles.pagerLabel}>
                    {result.pagination.page} / {result.pagination.totalPages}
                  </span>
                  <button
                    type="button"
                    className="krds-btn small secondary"
                    disabled={loading || result.pagination.page >= result.pagination.totalPages}
                    onClick={() => goPage(result.pagination.page + 1)}
                  >
                    다음
                  </button>
                </div>
              )}
            </div>

            {result.data.length === 0 ? (
              <div className="app-empty">데이터 없음</div>
            ) : (
              <div className={styles.dataTableWrap}>
                <table className={`app-table ${styles.dataTable}`}>
                  <thead>
                    <tr>
                      {Object.keys(result.data[0]).map(key => (
                        <th key={key}>{key}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {result.data.map((row, i) => (
                      <tr key={i}>
                        {Object.values(row).map((val, j) => (
                          <td key={j}>
                            {val === null ? <span className={styles.nullValue}>NULL</span> : String(val)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* 하단 페이징 */}
            {result.pagination.totalPages > 1 && (
              <div className={styles.pagerBottom}>
                <button
                  type="button"
                  className="krds-btn small secondary"
                  disabled={loading || result.pagination.page <= 1}
                  onClick={() => goPage(1)}
                >
                  처음
                </button>
                <button
                  type="button"
                  className="krds-btn small secondary"
                  disabled={loading || result.pagination.page <= 1}
                  onClick={() => goPage(result.pagination.page - 1)}
                >
                  이전
                </button>
                <span className={styles.pagerLabel}>
                  {result.pagination.page} / {result.pagination.totalPages} 페이지
                </span>
                <button
                  type="button"
                  className="krds-btn small secondary"
                  disabled={loading || result.pagination.page >= result.pagination.totalPages}
                  onClick={() => goPage(result.pagination.page + 1)}
                >
                  다음
                </button>
                <button
                  type="button"
                  className="krds-btn small secondary"
                  disabled={loading || result.pagination.page >= result.pagination.totalPages}
                  onClick={() => goPage(result.pagination.totalPages)}
                >
                  마지막
                </button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
