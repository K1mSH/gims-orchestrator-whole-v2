import axios from 'axios';
import {
  ApiEndpointListItem,
  ApiEndpointDetail,
  ApiEndpointCreateRequest,
  ApiEndpointUpdateRequest,
  ApiParam,
  ApiParamRequest,
  ApiFieldMapping,
  ApiFieldMappingRequest,
  ApiScheduleItem,
  ApiExecutionHistoryItem,
} from '@/types/api-collect';

const collectorApi = axios.create({
  baseURL: '/collector-api',
  headers: { 'Content-Type': 'application/json' },
});

// --- Endpoint CRUD ---

export const endpointApi = {
  getAll: () =>
    collectorApi.get<ApiEndpointListItem[]>('/endpoints').then(r => r.data),

  getById: (id: number) =>
    collectorApi.get<ApiEndpointDetail>(`/endpoints/${id}`).then(r => r.data),

  create: (data: ApiEndpointCreateRequest) =>
    collectorApi.post<ApiEndpointDetail>('/endpoints', data).then(r => r.data),

  update: (id: number, data: ApiEndpointUpdateRequest) =>
    collectorApi.put<ApiEndpointDetail>(`/endpoints/${id}`, data).then(r => r.data),

  delete: (id: number) =>
    collectorApi.delete(`/endpoints/${id}`),
};

// --- Params ---

export const paramApi = {
  save: (endpointId: number, params: ApiParamRequest[]) =>
    collectorApi.put<ApiParam[]>(`/endpoints/${endpointId}/params`, params).then(r => r.data),
};

// --- Test Call ---

export interface TestCallResponse {
  httpStatusCode: number;
  success: boolean;
  errorMessage: string | null;
  responseTree: TreeNode | null;
  dataRootPath: string | null;
  fields: FieldInfo[] | null;
  resolvedParams: Record<string, string>;
}

export interface TreeNode {
  name: string;
  type: string;       // "object" | "array" | "string" | "integer" | "number" | "boolean" | "null"
  sampleValue: string | null;
  arraySize: number | null;
  children: TreeNode[] | null;
}

export interface FieldInfo {
  fieldPath: string;
  fieldType: string;
  sampleValue: string | null;
}

export interface InlineTestRequest {
  url: string;
  httpMethod: string;
  contentType?: string;
  headers?: string;
  authType: string;
  authConfig?: string;
  params?: {
    paramName: string;
    paramType: string;
    valueType: string;
    staticValue?: string;
    isApiKeyRef?: boolean;
    dynamicType?: string;
    dynamicFormat?: string;
    dynamicOffset?: number;
  }[];
}

export const testApi = {
  call: (endpointId: number, paramOverrides?: Record<string, string>) =>
    collectorApi.post<TestCallResponse>(`/endpoints/${endpointId}/test`, {
      paramOverrides: paramOverrides || null,
    }).then(r => r.data),

  /** 저장 없이 인라인 테스트 (등록 전 검증용) */
  callInline: (request: InlineTestRequest) =>
    collectorApi.post<TestCallResponse>('/endpoints/test-inline', request).then(r => r.data),
};

// --- Field Mappings ---

export const mappingApi = {
  get: (endpointId: number) =>
    collectorApi.get<ApiFieldMapping[]>(`/endpoints/${endpointId}/mappings`).then(r => r.data),

  save: (endpointId: number, mappings: ApiFieldMappingRequest[]) =>
    collectorApi.put<ApiFieldMapping[]>(`/endpoints/${endpointId}/mappings`, mappings).then(r => r.data),
};

// --- API Keys (GIMS 본체 프록시) ---

export interface ApiKeyItem {
  id: number;
  serviceName: string;
  apiKey: string;
  useAt: string;
  expiryDate: string;
  expiryType: string;
  dday: number;
}

export const apiKeyApi = {
  getAll: async (): Promise<ApiKeyItem[]> => {
    const res = await collectorApi.get<any>('/endpoints/api-keys');
    return res.data?.data?.apis?.content || [];
  },
};

// --- Custom Executors ---

export interface CustomExecutorItem {
  id: string;
  displayName: string;
}

export const customExecutorApi = {
  getAll: () =>
    collectorApi.get<CustomExecutorItem[]>('/endpoints/custom-executors').then(r => r.data),
};

// --- Schedules ---

export const scheduleApi = {
  getAll: (endpointId: number) =>
    collectorApi.get<ApiScheduleItem[]>(`/endpoints/${endpointId}/schedules`).then(r => r.data),

  create: (endpointId: number, cronExpression: string) =>
    collectorApi.post<ApiScheduleItem>(`/endpoints/${endpointId}/schedules`, { cronExpression }).then(r => r.data),

  update: (scheduleId: number, cronExpression: string) =>
    collectorApi.put<ApiScheduleItem>(`/schedules/${scheduleId}`, { cronExpression }).then(r => r.data),

  toggle: (scheduleId: number) =>
    collectorApi.put<ApiScheduleItem>(`/schedules/${scheduleId}/toggle`).then(r => r.data),

  delete: (scheduleId: number) =>
    collectorApi.delete(`/schedules/${scheduleId}`),
};

// --- History ---

export const historyApi = {
  get: (endpointId: number, page: number = 0, size: number = 15, startDate?: string, endDate?: string) =>
    collectorApi.get<{
      content: ApiExecutionHistoryItem[];
      totalElements: number;
      totalPages: number;
      number: number;
    }>(`/endpoints/${endpointId}/history`, {
      params: { page, size, ...(startDate && endDate ? { startDate, endDate } : {}) },
    }).then(r => r.data),
};
