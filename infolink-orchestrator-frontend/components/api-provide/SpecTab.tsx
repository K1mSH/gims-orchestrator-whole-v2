'use client';

import { ApiPrvOperation } from '@/types/api-provide';
import styles from './SpecTab.module.css';

interface Props {
  operation: ApiPrvOperation;
}

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
      {/* API 명세서 (메타) */}
      <div className="app-card">
        <div className="app-card__header">
          <div>
            <h2 className="app-card__title">API 명세서</h2>
            <div className={styles.cardSubLine}>오퍼레이션 설정 기반 자동 생성</div>
          </div>
        </div>
        <div className={styles.metaGrid}>
          <div className={styles.metaLabel}>엔드포인트</div>
          <div><span className={styles.inlineCode}>GET {fullUrl}</span></div>
          <div className={styles.metaLabel}>인증</div>
          <div><span className={styles.inlineCode}>apiKey={'<발급받은 키>'}</span> (Query 파라미터)</div>
          <div className={styles.metaLabel}>응답 포맷</div>
          <div>{operation.responseFormat}</div>
          <div className={styles.metaLabel}>상태</div>
          <div>
            <span className={`krds-badge ${operation.isPublished ? 'bg-light-success' : 'bg-light-gray'}`}>
              {operation.isPublished ? '활성' : '비활성'}
            </span>
          </div>
        </div>
      </div>

      {/* 호출 예시 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">호출 예시</h2>
        </div>
        <pre className={styles.codePre}>{exampleUrl}</pre>
      </div>

      {/* 요청 파라미터 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">요청 파라미터</h2>
        </div>
        <table className="app-table">
          <thead>
            <tr>
              <th>파라미터</th>
              <th>타입</th>
              <th>필수</th>
              <th>조건</th>
              <th>기본값</th>
              <th>설명</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><span className={styles.paramCode}>apiKey</span></td>
              <td>STRING</td>
              <td>Y</td>
              <td>-</td>
              <td>-</td>
              <td>발급받은 API Key</td>
            </tr>
            <tr>
              <td><span className={styles.paramCode}>page</span></td>
              <td>NUMBER</td>
              <td>N</td>
              <td>-</td>
              <td>1</td>
              <td>페이지 번호 (1부터)</td>
            </tr>
            <tr>
              <td><span className={styles.paramCode}>pageSize</span></td>
              <td>NUMBER</td>
              <td>N</td>
              <td>-</td>
              <td>{operation.pageSize}</td>
              <td>페이지 크기 (최대 {operation.maxPageSize})</td>
            </tr>
            {visibleParams.map(p => (
              <tr key={p.paramName}>
                <td><span className={styles.paramCode}>{p.paramName}</span></td>
                <td>{p.dataType}</td>
                <td>{p.isRequired ? 'Y' : 'N'}</td>
                <td>{operatorLabel(p.operator)}</td>
                <td>{p.defaultValue || '-'}</td>
                <td>{p.columnName} 기준 필터</td>
              </tr>
            ))}
            {visibleParams.length === 0 && (
              <tr>
                <td colSpan={6} className="app-empty">사용자 정의 파라미터 없음</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* 응답 필드 */}
      <div className="app-card">
        <div className="app-card__header">
          <div>
            <h2 className="app-card__title">응답 필드</h2>
            <div className={styles.cardSubLine}>
              {operation.columns.length > 0 ? `${operation.columns.length}개 컬럼` : '전체 컬럼 (SELECT *)'}
              {operation.orderByColumn && ` / 정렬: ${operation.orderByColumn} ${operation.orderByDirection}`}
            </div>
          </div>
        </div>
        {operation.columns.length > 0 ? (
          <table className="app-table">
            <thead>
              <tr>
                <th>#</th>
                <th>필드명</th>
                <th>원본 컬럼</th>
                <th>가공</th>
              </tr>
            </thead>
            <tbody>
              {operation.columns
                .sort((a, b) => a.displayOrder - b.displayOrder)
                .map((col, i) => (
                  <tr key={col.columnName}>
                    <td>{i + 1}</td>
                    <td><span className={styles.paramCode}>{col.aliasName || col.columnName}</span></td>
                    <td>{col.aliasName ? col.columnName : '-'}</td>
                    <td>{transformLabel(col.transformType, col.transformParam)}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        ) : (
          <div className="app-empty">전체 컬럼 제공 (SELECT *)</div>
        )}
      </div>

      {/* 응답 예시 */}
      <div className="app-card">
        <div className="app-card__header">
          <h2 className="app-card__title">응답 예시</h2>
        </div>
        <pre className={styles.codePre}>{sampleResponse}</pre>
      </div>
    </div>
  );
}
