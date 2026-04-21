import axios from 'axios';
import {
  ApiPrvOperation,
  ApiPrvKeyInfo,
  DynamicQueryResult,
} from '@/types/api-provide';

const providerApi = axios.create({
  baseURL: '/provider-api',
  headers: { 'Content-Type': 'application/json' },
});

// --- Operation CRUD ---

export const operationApi = {
  getAll: () =>
    providerApi.get<ApiPrvOperation[]>('/manage/operations').then(r => r.data),

  getById: (id: number) =>
    providerApi.get<ApiPrvOperation>(`/manage/operations/${id}`).then(r => r.data),

  create: (data: Partial<ApiPrvOperation>) =>
    providerApi.post<ApiPrvOperation>('/manage/operations', data).then(r => r.data),

  update: (id: number, data: Partial<ApiPrvOperation>) =>
    providerApi.put<ApiPrvOperation>(`/manage/operations/${id}`, data).then(r => r.data),

  delete: (id: number) =>
    providerApi.delete(`/manage/operations/${id}`),

  togglePublish: (id: number) =>
    providerApi.put<ApiPrvOperation>(`/manage/operations/${id}/publish`).then(r => r.data),
};

// --- Columns & Params ---

export const columnApi = {
  save: (operationId: number, columns: {
    columnName: string; aliasName?: string; displayOrder: number;
    transformType?: string; transformParam?: string;
  }[]) =>
    providerApi.put(`/manage/operations/${operationId}/columns`, columns).then(r => r.data),
};

export const paramApi = {
  save: (operationId: number, params: {
    paramName: string; columnName: string; operator: string;
    isRequired: boolean; defaultValue?: string; dataType: string;
    isHidden?: boolean;
  }[]) =>
    providerApi.put(`/manage/operations/${operationId}/params`, params).then(r => r.data),
};

// --- Test Call ---

export const testApi = {
  call: (operationId: number, params?: Record<string, string>, page?: number, pageSize?: number) =>
    providerApi.post<DynamicQueryResult>(`/manage/operations/${operationId}/test`, {
      params: params || {},
      page: page || 1,
      pageSize: pageSize || 10,
    }).then(r => r.data),
};

// --- DB Meta ---

export const metaApi = {
  getTables: (datasourceId: string, query?: string) =>
    providerApi.get('/manage/meta/tables', { params: { datasourceId, query: query || '' } }).then(r => r.data),

  getColumns: (datasourceId: string, tableName: string) =>
    providerApi.get(`/manage/meta/tables/${tableName}/columns`, { params: { datasourceId } }).then(r => r.data),
};

// --- API Keys ---

export const apiKeyApi = {
  getAll: () =>
    providerApi.get<ApiPrvKeyInfo[]>('/manage/api-keys').then(r => r.data),

  create: (data: Partial<ApiPrvKeyInfo>) =>
    providerApi.post<ApiPrvKeyInfo>('/manage/api-keys', data).then(r => r.data),

  delete: (id: number) =>
    providerApi.delete(`/manage/api-keys/${id}`),
};

// --- History ---

export const historyApi = {
  get: (operationId: number, page: number = 0, size: number = 20) =>
    providerApi.get(`/manage/operations/${operationId}/history`, {
      params: { page, size },
    }).then(r => r.data),
};
