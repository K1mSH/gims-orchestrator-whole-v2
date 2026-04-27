// API Provider 타입 정의

export interface ApiPrvOperation {
  id: number;
  operationId: string;
  operationName: string;
  description: string | null;
  datasourceId: string;
  tableName: string;
  responseFormat: string;
  pageSize: number;
  maxPageSize: number;
  orderByColumn: string | null;
  orderByDirection: string;
  isPublished: boolean;
  isActive: boolean;
  /** META=메타등록형 / CUSTOM=내장 핸들러형 */
  operationType: 'META' | 'CUSTOM';
  /** 잠금 (CUSTOM 핸들러는 부팅 자동 등록 후 운영자 수정/삭제 차단) */
  isLocked: boolean;
  columns: ApiPrvOperationColumn[];
  params: ApiPrvOperationParam[];
  createdAt: string;
  updatedAt: string;
}

export interface ApiPrvOperationColumn {
  id?: number;
  columnName: string;
  aliasName: string | null;
  displayOrder: number;
  transformType: string;
  transformParam: string | null;
}

export interface ApiPrvOperationParam {
  id?: number;
  paramName: string;
  columnName: string;
  operator: string;
  isRequired: boolean;
  defaultValue: string | null;
  dataType: string;
  isHidden: boolean;
}

export interface ApiPrvKeyInfo {
  id: number;
  apiKey: string;
  clientName: string;
  allowedIps: string | null;
  allowedOperations: string | null;
  isActive: boolean;
  expiresAt: string | null;
}

export interface ApiPrvCallHistory {
  id: number;
  operationId: number;
  apiKey: string | null;
  clientIp: string | null;
  requestParams: string | null;
  responseCount: number;
  status: string;
  errorMessage: string | null;
  durationMs: number;
  calledAt: string;
}

export interface DynamicQueryResult {
  data: Record<string, any>[];
  pagination: {
    page: number;
    pageSize: number;
    totalCount: number;
    totalPages: number;
  };
  executedSql: string;
  durationMs: number;
}

export interface ColumnMeta {
  columnName: string;
  dataType: string;
  nullable: string;
}
