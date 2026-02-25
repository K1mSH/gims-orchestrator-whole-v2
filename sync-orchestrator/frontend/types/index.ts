// Agent 관련 타입
export type AgentStatus = 'ONLINE' | 'OFFLINE' | 'RUNNING';
export type AgentType = 'RCV' | 'SND' | 'LOADER';

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
  executionParams: ExecutionParamResponse[];
  stepDefinitions: StepDefinitionResponse[];
}

export type Zone = 'EXTERNAL' | 'DMZ' | 'INTERNAL' | 'INTERNAL_COMMON' | 'INTERNAL_SERVICE';

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
  executionParams?: ExecutionParamInput[];
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
  error?: string;
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

// Step 정의 (Agent가 제공하는 Step 메타데이터)
export interface StepDefinitionResponse {
  id: number;
  stepId: string;
  stepName: string;
  description: string | null;
  displayOrder: number;
  enabledByDefault: boolean;
}

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
  chainId: string;
  chainName: string;
  description: string | null;
  triggerType: 'INDIVIDUAL' | 'SEQUENTIAL';
  isActive: boolean;
  members: AgentChainMember[];
  createdAt: string;
  updatedAt: string;
}

export interface AgentChainMember {
  id: number;
  agentId: number;
  agentCode: string;
  agentName: string;
  zone: string;
  seqOrder: number;
}

export interface AgentChainCreateRequest {
  chainId: string;
  chainName: string;
  description?: string;
  triggerType: 'INDIVIDUAL' | 'SEQUENTIAL';
  members: { agentId: number; seqOrder: number }[];
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
