// Agent 관련 타입
export type AgentStatus = 'ONLINE' | 'OFFLINE' | 'RUNNING';
export type AgentType = 'RCV' | 'SND' | 'LOADER' | 'DB_CON_PROXY';

export interface Agent {
  id: number;
  agentCode: string;
  agentName: string;
  endpointUrl: string;
  zone: string;
  isActive: boolean;
  agentType: AgentType;
  datasourceTag: string | null;
  sourceDatasourceId: string | null;
  targetDatasourceId: string | null;
  description: string | null;
  status: AgentStatus;
  createdAt: string | null;
  lastExecutedAt: string | null;
  lastExecutionStatus: string | null;
  sourceTableIds: number[];
  targetTableIds: number[];
}

export type Zone = 'EXTERNAL' | 'DMZ' | 'INTERNAL_COMMON' | 'INTERNAL_SERVICE';

export interface AgentCreateRequest {
  agentCode: string;
  agentName: string;
  endpointUrl: string;
  zone: string;
  agentType: AgentType;
  datasourceTag?: string;
  isActive?: boolean;
  sourceDatasourceId?: string;
  targetDatasourceId?: string;
  description?: string;
  sourceTableIds?: number[];
  targetTableIds?: number[];
  sourceTableNames?: string[];
  targetTableNames?: string[];
}

// Agent 조회(Discover) 관련 타입
export interface DiscoverAgent {
  agentCode: string;
  type: AgentType;
  registered: boolean;
}

export interface DiscoverResponse {
  endpointUrl: string;
  zone: string;
  agents: DiscoverAgent[];
  agentInfo?: Array<{ agentCode: string; [key: string]: unknown }>;
  error?: string;
}

// 동적 WHERE 조건 (수동 실행 시 사용)
export interface ExecutionCondition {
  tableName?: string;  // 조건 대상 테이블 (select-tables 기준). 미지정 시 모든 Step에 적용
  column: string;
  operator: 'EQ' | 'NEQ' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'BETWEEN' | 'IN' | 'LIKE' | 'IS_NULL' | 'IS_NOT_NULL';
  value?: string;
  value2?: string;  // BETWEEN 전용
}

export const CONDITION_OPERATORS = [
  { value: 'EQ', label: '=' },
  { value: 'NEQ', label: '!=' },
  { value: 'GT', label: '>' },
  { value: 'GTE', label: '>=' },
  { value: 'LT', label: '<' },
  { value: 'LTE', label: '<=' },
  { value: 'BETWEEN', label: 'BETWEEN' },
  { value: 'IN', label: 'IN' },
  { value: 'LIKE', label: 'LIKE' },
  { value: 'IS_NULL', label: 'IS NULL' },
  { value: 'IS_NOT_NULL', label: 'IS NOT NULL' },
] as const;

export interface HealthCheckResponse {
  id: number;
  agentCode: string;
  status: AgentStatus;
  message: string;
}

// Schedule 관련 타입
export interface Schedule {
  scheduleId: number;
  agentId: number;
  agentCode: string;
  agentName: string;
  cronExpression: string;
  isEnabled: boolean;
  executionOptions: string | null;
  createdAt: string | null;
}

export interface ScheduleCreateRequest {
  agentId: number;
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
  agentId: number;
  agentCode: string;
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

// Table Stats (from Agent) - 매핑 단위
export interface TableStats {
  mappingName: string;
  sourceTables: string[];
  targetTables: string[];
  readCount: number;
  writeCount: number;
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

// 실행 이력 (Orchestrator 중앙 관리용)
export interface ExecutionHistory {
  executionId: string;
  agentCode: string;
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

// Step별 실행 결과 이력
export interface ExecutionStepHistory {
  id: number;
  executionId: string;
  stepId: string;
  status: string;  // SUCCESS, FAILED, SKIPPED
  readCount: number | null;
  writeCount: number | null;
  skipCount: number | null;
  durationMs: number | null;
  errorMessage: string | null;
  stepOrder: number;
}

// 실행 이력 검색 파라미터
export interface ExecutionHistorySearchParams {
  page?: number;
  size?: number;
  status?: ExecutionStatus | null;
  agentCode?: string | null;
  agentType?: AgentType | null;
  zone?: Zone | null;
  startDate?: string | null;  // yyyy-MM-dd
  endDate?: string | null;    // yyyy-MM-dd
  search?: string | null;
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
