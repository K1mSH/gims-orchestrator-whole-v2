'use client';

import { useState, useCallback } from 'react';
import { testApi } from '@/lib/providerApi';
import { ApiPrvOperation, DynamicQueryResult } from '@/types/api-provide';

const fieldLabel: React.CSSProperties = { fontSize: '0.8rem', color: 'var(--gray-500)', marginBottom: '0.25rem', fontWeight: 500 };

const SQL_KEYWORDS = ['SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'ORDER BY', 'LIMIT', 'OFFSET', 'AS', 'IN', 'BETWEEN', 'LIKE', 'NOT', 'NULL', 'IS', 'COUNT', 'ROUND', 'TO_CHAR', 'COALESCE', 'SUBSTRING'];

function formatSql(sql: string) {
  // 줄바꿈 삽입
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

  // 키워드 하이라이팅
  const parts: { text: string; isKeyword: boolean; isString: boolean; isNumber: boolean }[] = [];
  const regex = /('(?:[^']|'')*'|\b\d+(?:\.\d+)?\b|\b(?:SELECT|FROM|WHERE|AND|OR|ORDER\s+BY|LIMIT|OFFSET|AS|IN|BETWEEN|LIKE|NOT|NULL|IS|COUNT|ROUND|TO_CHAR|COALESCE|SUBSTRING|ASC|DESC)\b)/gi;

  let lastIndex = 0;
  let match;
  while ((match = regex.exec(formatted)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ text: formatted.slice(lastIndex, match.index), isKeyword: false, isString: false, isNumber: false });
    }
    const val = match[0];
    if (val.startsWith("'")) {
      parts.push({ text: val, isKeyword: false, isString: true, isNumber: false });
    } else if (/^\d/.test(val)) {
      parts.push({ text: val, isKeyword: false, isString: false, isNumber: true });
    } else {
      parts.push({ text: val.toUpperCase(), isKeyword: true, isString: false, isNumber: false });
    }
    lastIndex = regex.lastIndex;
  }
  if (lastIndex < formatted.length) {
    parts.push({ text: formatted.slice(lastIndex), isKeyword: false, isString: false, isNumber: false });
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

  return (
    <div>
      {/* 파라미터 입력 */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>테스트 호출</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)' }}>
            10건 단위 페이징 조회
          </div>
        </div>

        {operation.params.filter(p => !p.isHidden).length > 0 && (
          <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)' }}>
            <div style={fieldLabel}>파라미터</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '0.5rem' }}>
              {operation.params.filter(p => !p.isHidden).map(p => (
                <div key={p.paramName}>
                  <div style={{ fontSize: '0.75rem', color: 'var(--gray-500)' }}>
                    {p.paramName} ({p.operator}, {p.dataType})
                    {p.isRequired && <span style={{ color: '#ef4444' }}> *</span>}
                  </div>
                  <input className="form-input" placeholder={p.defaultValue || '값 입력'}
                    value={paramValues[p.paramName] || ''}
                    onChange={e => setParamValues({ ...paramValues, [p.paramName]: e.target.value })} />
                </div>
              ))}
            </div>
          </div>
        )}

        <div style={{ padding: '0.75rem 1rem', display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <button className="btn btn-primary" onClick={() => goPage(1)} disabled={loading}>
            {loading ? '실행 중...' : '테스트 실행'}
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--gray-500)' }}>페이지 크기</span>
            <input className="form-input" type="number" min={1} max={operation.maxPageSize}
              value={pageSize}
              onChange={e => setPageSize(Math.min(parseInt(e.target.value) || 10, operation.maxPageSize))}
              style={{ width: '80px', fontSize: '0.8rem' }} />
            <span style={{ fontSize: '0.7rem', color: 'var(--gray-400)' }}>최대 {operation.maxPageSize}</span>
          </div>
        </div>
      </div>

      {/* 에러 */}
      {error && (
        <div className="card" style={{ marginBottom: '1rem', padding: '1rem', background: '#fef2f2', border: '1px solid #fecaca' }}>
          <div style={{ fontSize: '0.85rem', color: '#991b1b', fontWeight: 600 }}>실행 실패</div>
          <div style={{ fontSize: '0.8rem', color: '#991b1b', marginTop: '0.25rem' }}>{error}</div>
        </div>
      )}

      {/* 결과 */}
      {result && (
        <>
          {/* SQL 미리보기 (executedSql 이 있을 때만) */}
          {result.executedSql ? (
            <div className="card" style={{ marginBottom: '1rem' }}>
              <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)' }}>
                <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>실행된 SQL</div>
              </div>
              <div style={{ padding: '0.75rem 1rem' }}>
                <pre style={{ fontSize: '0.8rem', background: '#1e1e2e', color: '#cdd6f4', padding: '1rem', borderRadius: '6px', overflow: 'auto', whiteSpace: 'pre-wrap', lineHeight: 1.6, fontFamily: "'Consolas', 'Monaco', monospace" }}>
                  {formatSql(result.executedSql).map((part, i) => (
                    <span key={i} style={{
                      color: part.isKeyword ? '#89b4fa' : part.isString ? '#a6e3a1' : part.isNumber ? '#fab387' : '#cdd6f4',
                      fontWeight: part.isKeyword ? 700 : 400,
                    }}>{part.text}</span>
                  ))}
                </pre>
                <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)', marginTop: '0.5rem' }}>
                  총 {result.pagination.totalCount}건 / {result.durationMs}ms
                </div>
              </div>
            </div>
          ) : (
            <div className="card" style={{ marginBottom: '1rem', padding: '0.75rem 1rem', fontSize: '0.8rem', color: 'var(--gray-500)' }}>
              총 {result.pagination.totalCount}건 / {result.durationMs}ms
            </div>
          )}

          {/* 데이터 테이블 */}
          <div className="card">
            <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>
                결과 ({result.data.length}건)
              </div>
              {/* 페이징 */}
              {result.pagination.totalPages > 1 && (
                <div style={{ display: 'flex', gap: '0.25rem', alignItems: 'center' }}>
                  <button className="btn btn-sm" disabled={loading || result.pagination.page <= 1}
                    onClick={() => goPage(result.pagination.page - 1)}>이전</button>
                  <span style={{ fontSize: '0.8rem', color: 'var(--gray-500)', padding: '0 0.5rem' }}>
                    {result.pagination.page} / {result.pagination.totalPages}
                  </span>
                  <button className="btn btn-sm" disabled={loading || result.pagination.page >= result.pagination.totalPages}
                    onClick={() => goPage(result.pagination.page + 1)}>다음</button>
                </div>
              )}
            </div>
            {result.data.length === 0 ? (
              <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--gray-400)' }}>데이터 없음</div>
            ) : (
              <div style={{ overflow: 'auto', maxHeight: '500px' }}>
                <table>
                  <thead>
                    <tr>
                      {Object.keys(result.data[0]).map(key => (
                        <th key={key} style={{ fontSize: '0.75rem', whiteSpace: 'nowrap' }}>{key}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {result.data.map((row, i) => (
                      <tr key={i}>
                        {Object.values(row).map((val, j) => (
                          <td key={j} style={{ fontSize: '0.8rem', maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {val === null ? <span style={{ color: 'var(--gray-300)' }}>NULL</span> : String(val)}
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
              <div style={{ padding: '0.75rem 1rem', borderTop: '1px solid var(--gray-100)', display: 'flex', justifyContent: 'center', gap: '0.25rem', alignItems: 'center' }}>
                <button className="btn btn-sm" disabled={loading || result.pagination.page <= 1}
                  onClick={() => goPage(1)}>처음</button>
                <button className="btn btn-sm" disabled={loading || result.pagination.page <= 1}
                  onClick={() => goPage(result.pagination.page - 1)}>이전</button>
                <span style={{ fontSize: '0.8rem', color: 'var(--gray-500)', padding: '0 0.75rem' }}>
                  {result.pagination.page} / {result.pagination.totalPages} 페이지
                </span>
                <button className="btn btn-sm" disabled={loading || result.pagination.page >= result.pagination.totalPages}
                  onClick={() => goPage(result.pagination.page + 1)}>다음</button>
                <button className="btn btn-sm" disabled={loading || result.pagination.page >= result.pagination.totalPages}
                  onClick={() => goPage(result.pagination.totalPages)}>마지막</button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
