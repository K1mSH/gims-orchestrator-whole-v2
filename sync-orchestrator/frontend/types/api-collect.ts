// API 수집 관리 타입 정의

export type AuthType = 'NONE' | 'BASIC' | 'BEARER';
export type CollectorZone = 'DMZ' | 'INTERNAL';
export type ParamType = 'QUERY' | 'BODY' | 'PATH' | 'HEADER';
export type ValueType = 'STATIC' | 'DYNAMIC';
export type DynamicType = 'TODAY' | 'NOW' | 'CUSTOM';
export type TransformType = 'NONE' | 'DATE_FORMAT' | 'NUMBER' | 'SUBSTRING' | 'TRIM' | 'REPLACE' | 'DEFAULT_VALUE' | 'LOOKUP';
export type ExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';
export type TriggeredBy = 'MANUAL' | 'SCHEDULE';

export interface ApiEndpointListItem {
  id: number;
  apiName: string;

  url: string;
  httpMethod: string;
  authType: AuthType;
  targetTableName: string | null;
  isActive: boolean;
  zone: CollectorZone;
  hasMappings: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ApiEndpointDetail {
  id: number;
  apiName: string;

  url: string;
  httpMethod: string;
  contentType: string | null;
  headers: string | null;
  authType: AuthType;
  authConfig: string | null;
  dataRootPath: string | null;
  targetDatasourceId: string | null;
  targetTableName: string | null;
  upsertEnabled: boolean;
  description: string | null;
  isActive: boolean;
  zone: CollectorZone;
  params: ApiParam[];
  fieldMappings: ApiFieldMapping[];
  createdAt: string;
  updatedAt: string;
}

export interface ApiEndpointCreateRequest {
  apiName: string;
  url: string;
  httpMethod: string;
  contentType?: string;
  headers?: string;
  authType: AuthType;
  authConfig?: string;
  description?: string;
  zone: CollectorZone;
  dataRootPath?: string;
  targetDatasourceId?: string;
  targetTableName?: string;
  upsertEnabled?: boolean;
}

export interface ApiEndpointUpdateRequest {
  apiName: string;
  url: string;
  httpMethod: string;
  contentType?: string;
  headers?: string;
  authType: AuthType;
  authConfig?: string;
  dataRootPath?: string;
  targetDatasourceId?: string;
  targetTableName?: string;
  upsertEnabled?: boolean;
  description?: string;
  isActive?: boolean;
}

export interface ApiParam {
  id: number;
  paramName: string;
  paramType: ParamType;
  valueType: ValueType;
  staticValue: string | null;
  isApiKeyRef: boolean;
  dynamicType: DynamicType | null;
  dynamicFormat: string | null;
  dynamicOffset: number | null;
  description: string | null;
  displayOrder: number;
}

export interface ApiParamRequest {
  paramName: string;
  paramType: ParamType;
  valueType: ValueType;
  staticValue?: string;
  isApiKeyRef?: boolean;
  dynamicType?: DynamicType;
  dynamicFormat?: string;
  dynamicOffset?: number;
  description?: string;
  displayOrder?: number;
}

export interface ApiFieldMapping {
  id: number;
  sourceFieldPath: string;
  targetColumnName: string;
  isConflictKey: boolean;
  transformType: TransformType;
  transformConfig: string | null;
  displayOrder: number;
  // 파생 컬럼
  isDerived: boolean;
  // LOOKUP 전용
  extractPattern: string | null;
  extractGroup: number | null;
  lookupParam: string | null;
  lookupKeyField: string | null;
  lookupValueField: string | null;
  lookupDataRootPath: string | null;
  lookupMatchType: string | null;
  defaultValue: string | null;
}

export interface ApiFieldMappingRequest {
  sourceFieldPath: string;
  targetColumnName: string;
  isConflictKey?: boolean;
  transformType?: TransformType;
  transformConfig?: string;
  displayOrder?: number;
  // 파생 컬럼
  isDerived?: boolean;
  // LOOKUP 전용
  extractPattern?: string;
  extractGroup?: number;
  lookupParam?: string;
  lookupKeyField?: string;
  lookupValueField?: string;
  lookupDataRootPath?: string;
  lookupMatchType?: string;
  defaultValue?: string;
}

export interface ApiScheduleItem {
  id: number;
  apiEndpointId: number;
  cronExpression: string;
  isEnabled: boolean;
  createdAt: string;
}

export interface ApiExecutionHistoryItem {
  id: number;
  executionId: string;
  status: ExecutionStatus;
  httpStatusCode: number | null;
  responseCount: number | null;
  insertCount: number | null;
  skipCount: number | null;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  triggeredBy: TriggeredBy;
}
