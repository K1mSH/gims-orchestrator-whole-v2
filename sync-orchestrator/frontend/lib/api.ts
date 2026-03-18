import axios, { AxiosInstance } from 'axios';
import type {
  Agent,
  AgentCreateRequest,
  HealthCheckResponse,
  DiscoverResponse,
  Schedule,
  ScheduleCreateRequest,
  ScheduleUpdateRequest,
  ExecutionDetail,
  TriggerResponse,
  AgentChain,
  AgentChainCreateRequest,
  TableStats,
  SyncLog,
  Datasource,
  DatasourceCreateRequest,
  DatasourceUpdateRequest,
  DatasourceSimple,
  ConnectionTestRequest,
  ConnectionTestResponse,
  TableSearchResult,
  ColumnSearchResult,
  TableCreateRequest,
  DatasourceTable,
  ExecutionHistory,
  ExecutionStepHistory,
  ExecutionDashboardStats,
  ExecutionHistorySearchParams,
  ExecutionCondition,
  PageResponse,
} from '@/types';

const api: AxiosInstance = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Agent API
export const agentApi = {
  getAll: () => api.get<Agent[]>('/agents').then((res) => res.data),

  getById: (id: number) => api.get<Agent>(`/agents/${id}`).then((res) => res.data),

  create: (data: AgentCreateRequest) =>
    api.post<Agent>('/agents', data).then((res) => res.data),

  update: (id: number, data: Partial<AgentCreateRequest>) =>
    api.put<Agent>(`/agents/${id}`, data).then((res) => res.data),

  delete: (id: number) => api.delete(`/agents/${id}`),

  healthCheck: (id: number) =>
    api.post<HealthCheckResponse>(`/agents/${id}/health-check`).then((res) => res.data),

  discover: (endpointUrl: string) =>
    api.get<DiscoverResponse>('/agents/discover', { params: { endpointUrl } })
      .then((res) => res.data),

  generateTestData: (id: number, count: number = 1000) =>
    api.post<{ message: string; created: number; requested: number; timeRange: { from: string; to: string } }>(
      `/agents/${id}/generate-test-data?count=${count}`
    ).then((res) => res.data),

  clearTestData: (id: number) =>
    api.delete<{ message: string; deleted: number }>(`/agents/${id}/clear-test-data`).then((res) => res.data),

  // WHERE 조건 대상 테이블 (Agent YML select-tables)
  getSelectTables: (id: number) =>
    api.get<DatasourceTable[]>(`/agents/${id}/select-tables`).then((res) => res.data),

  // Retention(자동삭제) 설정 조회/수정
  getRetentionConfig: (id: number) =>
    api.get<{ enabled: boolean; targetDatasourceId?: string; targets: { table: string; dateColumn: string; retentionDays: number }[] }>(`/agents/${id}/retention`).then((res) => res.data),

  updateRetentionConfig: (id: number, config: { enabled: boolean; targetDatasourceId?: string; targets: { table: string; dateColumn: string; retentionDays: number }[] }) =>
    api.put(`/agents/${id}/retention`, config).then((res) => res.data),
};

// Schedule API
export const scheduleApi = {
  getAll: () => api.get<Schedule[]>('/schedules').then((res) => res.data),

  getById: (scheduleId: number) =>
    api.get<Schedule>(`/schedules/${scheduleId}`).then((res) => res.data),

  getByAgentId: (agentId: number) =>
    api.get<Schedule[]>('/schedules').then((res) =>
      res.data.filter((s) => s.agentId === agentId)
    ),

  create: (data: ScheduleCreateRequest) =>
    api.post<Schedule>('/schedules', data).then((res) => res.data),

  update: (scheduleId: number, data: ScheduleUpdateRequest) =>
    api.put<Schedule>(`/schedules/${scheduleId}`, data).then((res) => res.data),

  delete: (scheduleId: number) => api.delete(`/schedules/${scheduleId}`),

  toggle: (scheduleId: number) =>
    api.put<Schedule>(`/schedules/${scheduleId}/toggle`).then((res) => res.data),
};

// Execution Data Types
export interface ExecutionDataSummary {
  executionId: string;
  successCount: number;
  failedCount: number;
  totalCount: number;
}

export interface TableData {
  tableName: string;
  columns: string[];
  data: Record<string, unknown>[];
  totalCount: number;
  successCount?: number;
  failedCount?: number;
  page: number;
  size: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  fallbackMode?: boolean;
  fallbackReason?: string;
}

export interface TableDataParams {
  page?: number;
  size?: number;
  search?: string;
  searchColumn?: string;
  status?: string;
  tableName?: string;
  sortColumn?: string;
  sortDirection?: 'asc' | 'desc';
}

// Trace Result Type (Source → Target 추적 결과)
export interface TraceResult {
  pkColumn?: string;
  pkValue?: string;
  executionId?: string;
  targetTableName?: string;
  targetRecords?: Record<string, unknown>[];
  targetCount?: number;
  traceStatus?: 'SYNCED' | 'NOT_SYNCED' | 'FOUND' | 'SOURCE_NOT_FOUND' | 'FULLY_SYNCED' | 'FOUND_IN_IF';
  error?: string;
  sourceRefs?: string;
  pkValues?: string[];
  sourceTableName?: string;
  sourceRecords?: Record<string, unknown>[];
  sourceCount?: number;
}

// Agent 상태 요약 (대시보드용)
export interface AgentExecutionSummary {
  agentId: number;
  agentCode: string;
  agentName: string;
  zone: string;
  lastExecutionStatus: string | null;
  lastRunAt: string | null;
  agentStatus: string;
}

// Execution API
export const executionApi = {
  // Agent별 실행 이력 조회 (Agent DB에서)
  getByAgent: (id: number) =>
    api.get<ExecutionDetail[]>(`/executions/agent/${id}`).then((res) => res.data),

  // 전체 Agent 상태 조회 (대시보드용)
  getAgentStatuses: () =>
    api.get<AgentExecutionSummary[]>('/executions/status').then((res) => res.data),

  // 실행 트리거 - Agent에 실행 요청
  trigger: (id: number, conditions?: ExecutionCondition[]) => {
    const body = conditions && conditions.length > 0
      ? { conditions }
      : undefined;
    return api.post<TriggerResponse>(`/executions/${id}/run`, body).then((res) => res.data);
  },

  // 실행 상세 정보 조회 (Agent DB에서)
  getDetail: (executionId: string) =>
    api.get<ExecutionDetail>(`/executions/${encodeURIComponent(executionId)}/detail`).then((res) => res.data),

  // 실행 데이터 조회 API
  getSummary: (executionId: string) =>
    api.get<ExecutionDataSummary>(`/executions/${encodeURIComponent(executionId)}/data/summary`).then((res) => res.data),

  getSourceData: (executionId: string, params?: TableDataParams) =>
    api.get<TableData>(`/executions/${encodeURIComponent(executionId)}/data/source`, { params }).then((res) => res.data),

  getTargetIfData: (executionId: string, params?: TableDataParams) =>
    api.get<TableData>(`/executions/${encodeURIComponent(executionId)}/data/target-if`, { params }).then((res) => res.data),

  getTargetData: (executionId: string, params?: TableDataParams) =>
    api.get<TableData>(`/executions/${encodeURIComponent(executionId)}/data/target`, { params }).then((res) => res.data),

  getFailedData: (executionId: string) =>
    api.get<unknown[]>(`/executions/${encodeURIComponent(executionId)}/data/failed`).then((res) => res.data),

  // 테이블별 통계 조회 (Agent DB에서)
  getTableStats: (executionId: string) =>
    api.get<TableStats[]>(`/executions/${encodeURIComponent(executionId)}/tables`).then((res) => res.data),

  // 특정 테이블의 레코드 조회 (Agent DB에서)
  getTableRecords: (executionId: string, tableName: string) =>
    api.get<SyncLog[]>(`/executions/${encodeURIComponent(executionId)}/tables/${encodeURIComponent(tableName)}`).then((res) => res.data),

  // 특정 테이블의 실패 레코드 조회 (Agent DB에서)
  getTableFailedRecords: (executionId: string, tableName: string) =>
    api.get<SyncLog[]>(`/executions/${encodeURIComponent(executionId)}/tables/${encodeURIComponent(tableName)}/failed`).then((res) => res.data),

  // Source PK로 데이터 추적 (Source → Target)
  traceBySourcePk: (executionId: string, pkValue: string, pkColumn: string = 'id', sourceTable: string) =>
    api.get<TraceResult>(`/executions/${encodeURIComponent(executionId)}/trace`, {
      params: { pkValue, pkColumn, sourceTable }
    }).then((res) => res.data),

  // Target에서 Source로 역추적 (sourceRefs 기반)
  traceToSource: (executionId: string, sourceRefs: string, sourceTable?: string) =>
    api.get<TraceResult>(`/executions/${encodeURIComponent(executionId)}/trace-source`, {
      params: { sourceRefs, sourceTable }
    }).then((res) => res.data),
};

// Agent Chain API
export const chainApi = {
  getAll: () => api.get<AgentChain[]>('/chains').then((res) => res.data),

  getById: (id: number) =>
    api.get<AgentChain>(`/chains/${id}`).then((res) => res.data),

  create: (data: AgentChainCreateRequest) =>
    api.post<AgentChain>('/chains', data).then((res) => res.data),

  update: (id: number, data: Partial<AgentChainCreateRequest>) =>
    api.put<AgentChain>(`/chains/${id}`, data).then((res) => res.data),

  delete: (id: number) => api.delete(`/chains/${id}`),

  execute: (id: number) =>
    api.post<TriggerResponse[]>(`/chains/${id}/execute`).then((res) => res.data),
};

// Execution Step History API
export const executionStepApi = {
  // 실행의 Step별 결과 조회
  getByExecution: (executionId: string) =>
    api.get<ExecutionStepHistory[]>(`/executions/${encodeURIComponent(executionId)}/steps`).then((res) => res.data),
};

// Execution History API (Orchestrator 중앙 관리)
export const executionHistoryApi = {
  // 최근 실행 이력 조회 (최신 50건)
  getRecent: () =>
    api.get<ExecutionHistory[]>('/executions/history').then((res) => res.data),

  // 현재 실행 중인 이력 조회
  getRunning: () =>
    api.get<ExecutionHistory[]>('/executions/history/running').then((res) => res.data),

  // Agent별 실행 이력 조회
  getByAgent: (id: number) =>
    api.get<ExecutionHistory[]>(`/executions/history/agent/${id}`).then((res) => res.data),

  // 대시보드 통계 조회
  getDashboardStats: () =>
    api.get<ExecutionDashboardStats>('/executions/dashboard/stats').then((res) => res.data),

  // 페이징 + 필터 조회
  getPaged: (params: ExecutionHistorySearchParams) => {
    const query: Record<string, string | number> = {
      page: params.page ?? 0,
      size: params.size ?? 20,
    };
    if (params.status) query.status = params.status;
    if (params.agentCode) query.agentCode = params.agentCode;
    if (params.agentType) query.agentType = params.agentType;
    if (params.zone) query.zone = params.zone;
    if (params.startDate) query.startDate = params.startDate;
    if (params.endDate) query.endDate = params.endDate;
    if (params.search) query.search = params.search;
    return api.get<PageResponse<ExecutionHistory>>('/executions/history/paged', { params: query })
      .then((res) => res.data);
  },
};

// Datasource API
export const datasourceApi = {
  getAll: () => api.get<Datasource[]>('/datasources').then((res) => res.data),

  getActive: () => api.get<Datasource[]>('/datasources/active').then((res) => res.data),

  getSimple: () => api.get<DatasourceSimple[]>('/datasources/simple').then((res) => res.data),

  getById: (datasourceId: string) =>
    api.get<Datasource>(`/datasources/${encodeURIComponent(datasourceId)}`).then((res) => res.data),

  create: (data: DatasourceCreateRequest) =>
    api.post<Datasource>('/datasources', data).then((res) => res.data),

  update: (datasourceId: string, data: DatasourceUpdateRequest) =>
    api.put<Datasource>(`/datasources/${encodeURIComponent(datasourceId)}`, data).then((res) => res.data),

  delete: (datasourceId: string) => api.delete(`/datasources/${encodeURIComponent(datasourceId)}`),

  testConnection: (datasourceId: string) =>
    api.post<ConnectionTestResponse>(`/datasources/${encodeURIComponent(datasourceId)}/test-connection`).then((res) => res.data),

  testConnectionBeforeSave: (data: ConnectionTestRequest) =>
    api.post<ConnectionTestResponse>('/datasources/test-connection', data).then((res) => res.data),

  // 테이블/컬럼 관리
  searchTables: (datasourceId: string, query?: string) =>
    api.get<TableSearchResult[]>(`/datasources/${encodeURIComponent(datasourceId)}/search-tables`, {
      params: query ? { query } : undefined
    }).then((res) => res.data),

  searchColumns: (datasourceId: string, tableName: string, query?: string) =>
    api.get<ColumnSearchResult[]>(`/datasources/${encodeURIComponent(datasourceId)}/search-columns`, {
      params: { tableName, query }
    }).then((res) => res.data),

  getRegisteredTables: (datasourceId: string) =>
    api.get<DatasourceTable[]>(`/datasources/${encodeURIComponent(datasourceId)}/tables`).then((res) => res.data),

  registerTable: (datasourceId: string, data: TableCreateRequest) =>
    api.post<DatasourceTable>(`/datasources/${encodeURIComponent(datasourceId)}/tables`, data).then((res) => res.data),

  deleteTable: (datasourceId: string, tableId: number) =>
    api.delete(`/datasources/${encodeURIComponent(datasourceId)}/tables/${tableId}`),

  // sourceRef 해석용 lookup 데이터
  getSourceRefLookup: () =>
    api.get<{ datasources: Record<string, string>; tables: Record<string, string> }>(
      '/datasources/sourceref-lookup'
    ).then((res) => res.data),

  // 테이블 alias 전역 조회 (tableName → tableAlias)
  getTableAliasMap: () =>
    api.get<Record<string, string>>('/datasources/table-alias-map').then((res) => res.data),
};

export default api;
