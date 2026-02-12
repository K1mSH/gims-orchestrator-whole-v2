// Agent 관련 타입
export type AgentStatus = 'ONLINE' | 'OFFLINE' | 'RUNNING';
export type AgentType = 'RELAY' | 'LOADER_STANDARD' | 'LOADER_CUSTOM';

export interface Agent {
  agentId: string;
  agentName: string;
  endpointUrl: string;
  zone: string;
  isActive: boolean;
  agentType: AgentType;
  sourceDatasourceId: string | null;
  targetDatasourceId: string | null;
  description: string | null;
  status: AgentStatus;
  createdAt: string | null;
  // 마지막 실행 정보 (상세 counts는 Agent API에서 조회)
  lastExecutedAt: string | null;
  lastExecutionStatus: string | null;
  // 선택된 테이블 ID 목록
  sourceTableIds: number[];
  targetTableIds: number[];
  // 실행 파라미터
  executionParams: ExecutionParamResponse[];
}

export type Zone = 'EXTERNAL' | 'DMZ' | 'INTERNAL_COMMON' | 'INTERNAL_SERVICE';

export interface AgentCreateRequest {
  agentId: string;
  agentName: string;
  endpointUrl: string;
  zone: string;
  isActive?: boolean;
  agentType?: AgentType;
  sourceDatasourceId?: string;
  targetDatasourceId?: string;
  description?: string;
  sourceTableIds?: number[];
  targetTableIds?: number[];
  executionParams?: ExecutionParamInput[];
}

// 실행 파라미터 관련 타입
export interface ExecutionParamDefinition {
  paramId: string;
  label: string;
  description: string;
  dataType: string;
  defaultValue: string | null;
  required: boolean;
  displayOrder: number;
}

export interface ExecutionParamInput {
  paramId: string;
  label: string;
  description?: string;
  dataType?: string;
  defaultValue?: string;
  isEnabled?: boolean;
  displayOrder?: number;
}

export interface ExecutionParamResponse {
  id: number;
  paramId: string;
  label: string;
  description: string | null;
  dataType: string;
  defaultValue: string | null;
  isEnabled: boolean;
  displayOrder: number;
}

export interface ExecutionFilter {
  paramId: string;
  value: string;
}

export interface HealthCheckResponse {
  agentId: string;
  status: AgentStatus;
  message: string;
}

// Schedule 관련 타입
export interface Schedule {
  scheduleId: number;
  agentId: string;
  agentName: string;
  cronExpression: string;
  isEnabled: boolean;
  executionOptions: string | null;
  createdAt: string | null;
}

export interface ScheduleCreateRequest {
  agentId: string;
  cronExpression: string;
  isEnabled?: boolean;
  executionOptions?: string;
}

export interface ScheduleUpdateRequest {
  cronExpression?: string;
  isEnabled?: boolean;
  executionOptions?: string;
}

// Execution 관련 타입
export type ExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';

/**
 * 실행 트리거 응답
 */
export interface TriggerResponse {
  executionId: string;
  agentId: string;
  status: string;
  startTime?: string;
  endTime?: string;
}

/**
 * Agent DB에서 조회하는 실행 상세 정보
 */
export interface ExecutionDetail {
  executionId: string;
  status: string;
  totalReadCount: number | null;
  totalWriteCount: number | null;
  totalSkipCount: number | null;
  durationMs: number | null;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
}

/**
 * Step 로그 (Agent DB에서 조회)
 */
export interface StepLog {
  stepLogId: number;
  stepId: string;
  stepName: string;
  stepOrder: number;
  totalSteps: number | null;
  status: ExecutionStatus;
  readCount: number | null;
  writeCount: number | null;
  skipCount: number | null;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
}

// Table Stats (from Agent)
export interface TableStats {
  tableName: string;
  tableType: 'SOURCE' | 'TARGET_IF' | 'TARGET';
  totalCount: number;
  successCount: number;
  failedCount: number;
  skipCount: number;
}

// Sync Log (from Agent)
export interface SyncLog {
  id: number;
  executionId: string;
  stepId: string;
  tableName: string;
  recordKey: string;
  status: string;
  errorReason: string | null;
  createdAt: string;
}

// Agent Chain 관련 타입
export interface AgentChain {
  id: number;
  name: string;
  description: string | null;
  isActive: boolean;
  members: AgentChainMember[];
  createdAt: string;
  updatedAt: string;
}

export interface AgentChainMember {
  id: number;
  chainId: number;
  agentId: string;
  agent?: Agent;
  sequence: number;
  waitForCompletion: boolean;
}

export interface AgentChainCreateRequest {
  name: string;
  description?: string;
  isActive: boolean;
  memberAgentIds: string[];
}

// API Response 타입
export interface ApiResponse<T> {
  data: T;
  message?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// Dashboard 타입
export interface DashboardStats {
  totalAgents: number;
  onlineAgents: number;
  offlineAgents: number;
  errorAgents: number;
  totalSchedules: number;
  activeSchedules: number;
  todayExecutions: number;
  failedExecutions: number;
}

// 실행 이력 (Orchestrator 중앙 관리용)
export interface ExecutionHistory {
  executionId: string;
  agentId: string;
  agentName: string;
  agentType: AgentType | null;
  status: ExecutionStatus;
  totalReadCount: number | null;
  totalWriteCount: number | null;
  totalSkipCount: number | null;
  durationMs: number | null;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
  triggeredBy: 'MANUAL' | 'SCHEDULE' | 'CHAIN';
}

// 실행 대시보드 통계
export interface ExecutionDashboardStats {
  todayExecutions: number;
  todayFailed: number;
  currentlyRunning: number;
  totalAgents: number;
  onlineAgents: number;
}

// Datasource 관련 타입
export type DbType = 'POSTGRESQL' | 'ORACLE' | 'MYSQL' | 'MARIADB' | 'MSSQL';

export interface Datasource {
  datasourceId: string;
  datasourceName: string;
  dbType: DbType;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  description: string | null;
  zone: string | null;
  isActive: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface DatasourceCreateRequest {
  datasourceId: string;
  datasourceName: string;
  dbType: DbType;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  password: string;
  description?: string;
  zone?: string;
}

export interface DatasourceUpdateRequest {
  datasourceName?: string;
  dbType?: DbType;
  host?: string;
  port?: number;
  databaseName?: string;
  username?: string;
  password?: string;
  description?: string;
  zone?: string;
  isActive?: boolean;
}

export interface DatasourceSimple {
  datasourceId: string;
  datasourceName: string;
  dbType: DbType;
}

export interface ConnectionTestRequest {
  dbType: DbType;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  password: string;
  zone?: string;
}

export interface ConnectionTestResponse {
  success: boolean;
  message: string;
  responseTimeMs: number;
}

// 테이블/컬럼 검색 결과 (실제 DB에서 검색)
export interface TableSearchResult {
  tableName: string;
  tableType: string; // TABLE, VIEW
  remarks: string | null;
}

export interface ColumnSearchResult {
  columnName: string;
  dataType: string;
  columnSize: number;
  isNullable: boolean;
  isPrimaryKey: boolean;
  remarks: string | null;
}

// 테이블/컬럼 등록 요청
export interface TableCreateRequest {
  tableName: string;
  tableAlias?: string;
  description?: string;
  columns?: ColumnCreateRequest[];
}

export interface ColumnCreateRequest {
  columnName: string;
  columnAlias?: string;
  dataType?: string;
  isPrimaryKey?: boolean;
  isNullable?: boolean;
  description?: string;
}

// 등록된 테이블/컬럼 응답
export interface DatasourceTable {
  id: number;
  datasourceId: string;
  tableName: string;
  tableAlias: string | null;
  description: string | null;
  columns: DatasourceColumn[];
  createdAt: string;
}

export interface DatasourceColumn {
  id: number;
  columnName: string;
  columnAlias: string | null;
  dataType: string | null;
  isPrimaryKey: boolean;
  isNullable: boolean;
  description: string | null;
}
