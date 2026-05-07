'use client';

import { ApiPrvOperation } from '@/types/api-provide';

interface Props {
  operation: ApiPrvOperation;
}

const codeStyle: React.CSSProperties = {
  fontSize: '0.8rem', background: '#1e1e2e', color: '#cdd6f4', padding: '1rem',
  borderRadius: '6px', overflow: 'auto', whiteSpace: 'pre-wrap', lineHeight: 1.6,
  fontFamily: "'Consolas', 'Monaco', monospace",
};
const sectionStyle = { padding: '0.75rem 1rem', borderBottom: '1px solid var(--gray-100)' };
const labelStyle: React.CSSProperties = { fontSize: '0.8rem', color: 'var(--gray-500)', marginBottom: '0.25rem', fontWeight: 500 };
const thStyle: React.CSSProperties = { fontSize: '0.75rem', textAlign: 'left' as const, padding: '0.4rem 0.5rem', borderBottom: '1px solid var(--gray-100)' };
const tdStyle: React.CSSProperties = { fontSize: '0.8rem', padding: '0.4rem 0.5rem', borderBottom: '1px solid var(--gray-50)' };

function operatorLabel(op: string) {
  switch (op) {
    case 'EQ': return '= (같음)';
    case 'GT': return '> (초과)';
    case 'GTE': return '>= (이상)';
    case 'LT': return '< (미만)';
    case 'LTE': return '<= (이하)';
    case 'LIKE': return '%포함%';
    case 'LIKE_START': return '시작%';
    case 'LIKE_END': return '%끝';
    case 'IN': return 'IN (콤마구분)';
    case 'BETWEEN': return 'BETWEEN (콤마구분)';
    default: return op;
  }
}

function transformLabel(type: string, param: string | null) {
  if (!type || type === 'NONE') return '-';
  switch (type) {
    case 'ROUND': return `소수점 ${param || 0}자리 반올림`;
    case 'DATE_FORMAT': return `날짜형식 (${param || 'YYYY-MM-DD'})`;
    case 'COALESCE': return `NULL → '${param || ''}'`;
    case 'SUBSTRING': return `앞 ${param || '?'}자`;
    default: return type;
  }
}

export default function SpecTab({ operation }: Props) {
  const visibleParams = operation.params.filter(p => !p.isHidden);
  const host = 'http://localhost:8095';
  const baseUrl = `/api/provide/${operation.operationId}`;
  const fullUrl = `${host}${baseUrl}`;

  // 호출 예시 URL 생성
  const exampleParams = visibleParams.map(p => {
    const val = p.defaultValue || (p.dataType === 'NUMBER' ? '100' : p.dataType === 'DATE' ? '2026-01-01' : 'value');
    return `${p.paramName}=${val}`;
  });
  const allParams = ['apiKey=<발급받은 키>', ...exampleParams];
  const exampleUrl = `GET ${fullUrl}?${allParams.join('&')}`;

  // 응답 JSON 샘플
  const sampleRow: Record<string, any> = {};
  if (operation.columns.length > 0) {
    operation.columns
      .sort((a, b) => a.displayOrder - b.displayOrder)
      .forEach(col => {
        const key = col.aliasName || col.columnName;
        sampleRow[key] = col.transformType === 'ROUND' ? 7.23
          : col.transformType === 'DATE_FORMAT' ? '2026-03-15'
          : col.transformType === 'COALESCE' ? (col.transformParam || '0')
          : 'sample_value';
      });
  } else {
    sampleRow['column1'] = 'value1';
    sampleRow['column2'] = 123;
  }

  const sampleResponse = JSON.stringify({
    data: [sampleRow],
    pagination: {
      page: 1,
      pageSize: operation.pageSize,
      totalCount: 100,
      totalPages: Math.ceil(100 / operation.pageSize),
    },
  }, null, 2);

  return (
    <div>
      {/* 기본 정보 */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div style={sectionStyle}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>API 명세서</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)' }}>
            오퍼레이션 설정 기반 자동 생성
          </div>
        </div>
        <div style={sectionStyle}>
          <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: '0.5rem', fontSize: '0.85rem' }}>
            <div style={labelStyle}>엔드포인트</div>
            <div><code style={{ background: 'var(--gray-50)', padding: '0.2rem 0.5rem', borderRadius: '4px' }}>GET {fullUrl}</code></div>
            <div style={labelStyle}>인증</div>
            <div><code style={{ background: 'var(--gray-50)', padding: '0.2rem 0.5rem', borderRadius: '4px' }}>?apiKey={'<발급받은 키>'}</code></div>
            <div style={labelStyle}>응답 포맷</div>
            <div>{operation.responseFormat}</div>
            <div style={labelStyle}>상태</div>
            <div>
              <span style={{
                padding: '2px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
                background: operation.isPublished ? '#dcfce7' : '#f3f4f6',
                color: operation.isPublished ? '#166534' : '#6b7280',
              }}>
                {operation.isPublished ? '활성' : '비활성'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* 호출 예시 */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div style={sectionStyle}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>호출 예시</div>
        </div>
        <div style={{ padding: '0.75rem 1rem' }}>
          <pre style={codeStyle}>{exampleUrl}</pre>
        </div>
      </div>

      {/* 요청 파라미터 */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div style={sectionStyle}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>요청 파라미터</div>
        </div>
        <div style={{ padding: '0 1rem' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th style={thStyle}>파라미터</th>
                <th style={thStyle}>타입</th>
                <th style={thStyle}>필수</th>
                <th style={thStyle}>조건</th>
                <th style={thStyle}>기본값</th>
                <th style={thStyle}>설명</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td style={tdStyle}><code>apiKey</code></td>
                <td style={tdStyle}>STRING</td>
                <td style={tdStyle}>Y</td>
                <td style={tdStyle}>-</td>
                <td style={tdStyle}>-</td>
                <td style={tdStyle}>발급받은 API Key</td>
              </tr>
              <tr>
                <td style={tdStyle}><code>page</code></td>
                <td style={tdStyle}>NUMBER</td>
                <td style={tdStyle}>N</td>
                <td style={tdStyle}>-</td>
                <td style={tdStyle}>1</td>
                <td style={tdStyle}>페이지 번호 (1부터)</td>
              </tr>
              <tr>
                <td style={tdStyle}><code>pageSize</code></td>
                <td style={tdStyle}>NUMBER</td>
                <td style={tdStyle}>N</td>
                <td style={tdStyle}>-</td>
                <td style={tdStyle}>{operation.pageSize}</td>
                <td style={tdStyle}>페이지 크기 (최대 {operation.maxPageSize})</td>
              </tr>
              {visibleParams.map(p => (
                <tr key={p.paramName}>
                  <td style={tdStyle}><code>{p.paramName}</code></td>
                  <td style={tdStyle}>{p.dataType}</td>
                  <td style={tdStyle}>{p.isRequired ? 'Y' : 'N'}</td>
                  <td style={tdStyle}>{operatorLabel(p.operator)}</td>
                  <td style={tdStyle}>{p.defaultValue || '-'}</td>
                  <td style={tdStyle}>{p.columnName} 기준 필터</td>
                </tr>
              ))}
              {visibleParams.length === 0 && (
                <tr>
                  <td colSpan={6} style={{ ...tdStyle, textAlign: 'center', color: 'var(--gray-400)' }}>
                    사용자 정의 파라미터 없음
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* 응답 필드 */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div style={sectionStyle}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>응답 필드</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--gray-400)' }}>
            {operation.columns.length > 0 ? `${operation.columns.length}개 컬럼` : '전체 컬럼 (SELECT *)'}
            {operation.orderByColumn && ` / 정렬: ${operation.orderByColumn} ${operation.orderByDirection}`}
          </div>
        </div>
        {operation.columns.length > 0 ? (
          <div style={{ padding: '0 1rem' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr>
                  <th style={thStyle}>#</th>
                  <th style={thStyle}>필드명</th>
                  <th style={thStyle}>원본 컬럼</th>
                  <th style={thStyle}>가공</th>
                </tr>
              </thead>
              <tbody>
                {operation.columns
                  .sort((a, b) => a.displayOrder - b.displayOrder)
                  .map((col, i) => (
                    <tr key={col.columnName}>
                      <td style={tdStyle}>{i + 1}</td>
                      <td style={tdStyle}><code>{col.aliasName || col.columnName}</code></td>
                      <td style={tdStyle}>{col.aliasName ? col.columnName : '-'}</td>
                      <td style={tdStyle}>{transformLabel(col.transformType, col.transformParam)}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--gray-400)', fontSize: '0.85rem' }}>
            전체 컬럼 제공 (SELECT *)
          </div>
        )}
      </div>

      {/* 응답 예시 */}
      <div className="card">
        <div style={sectionStyle}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>응답 예시</div>
        </div>
        <div style={{ padding: '0.75rem 1rem' }}>
          <pre style={codeStyle}>{sampleResponse}</pre>
        </div>
      </div>
    </div>
  );
}
