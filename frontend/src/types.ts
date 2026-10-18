export interface Ctx {
  organizationId: string;
  userId: string;
}

export interface Team {
  id: string;
  organizationId: string;
  name: string;
  description?: string | null;
  status: string;
  createdAt?: string;
  teamLeadEmployeeId?: string | null;
}

export interface Employee {
  id: string;
  name: string;
  role: string;
  gradeId?: string | null;
  teamId?: string | null;
  status: string;
  systemPrompt?: string | null;
  customInstructions?: string | null;
  completedTasks?: number | null;
  totalTasks?: number | null;
  avgRating?: number | null;
}

export interface Grade {
  id: string;
  code: string;
  name: string;
  ordinal: number;
  responsibilities?: string | null;
  taskTypes?: string[] | null;
  modelSelectionMode: string;
  status: string;
  templateDefault?: boolean;
}

export interface Provider {
  id: string;
  name: string;
  providerType: string;
  baseUrl?: string | null;
  secretRef?: string | null;
  healthStatus: string;
  enabled: boolean;
}

export interface Profile {
  id: string;
  name: string;
  providerId: string;
  provider?: { name?: string } | null;
  providerModelId: string;
  qualityTier?: number | null;
  latencyClass?: string | null;
  inputCostPerMillion?: number | null;
  outputCostPerMillion?: number | null;
  contextWindow?: number | null;
  capabilities?: string[] | null;
  localOnly?: boolean;
  enabled: boolean;
}

export interface ProjectLite {
  id: string;
  name: string;
  description?: string | null;
  status?: string;
  createdAt?: string;
}

export interface TaskRow {
  id: string;
  title?: string;
  type?: string;
  taskType?: string;
  status?: string;
  description?: string | null;
  assignee?: { id: string; name?: string } | null;
  createdAt?: string;
  attemptCount?: number | null;
  maxAttempts?: number | null;
  resultSummary?: string | null;
  blockedReason?: string | null;
  parentTaskId?: string | null;
}

export interface SessionAttachment {
  filename: string;
  parseStatus: string;
}

export interface Session {
  id: string;
  projectId: string;
  sessionNumber: number;
  status: string;
  goalText?: string | null;
  createdAt?: string;
  maxQuestions?: number;
  openedByUserId?: string | null;
  parentSessionId?: string | null;
  clientId?: string | null;
  attachments?: SessionAttachment[];
}

export interface QuestionOption {
  value: string;
  label: string;
}

export interface Question {
  id: string;
  prompt: string;
  questionType: string;
  displayOrder: number;
  answerStatus?: string;
  options?: QuestionOption[] | null;
  allowOther?: boolean;
  selectedValues?: string[] | null;
  otherText?: string | null;
  numericAnswer?: number | null;
}

export interface RequirementItem {
  title: string;
  detail?: string | null;
  source?: string;
  evidence?: string | null;
}

export interface RequirementContent {
  functional?: RequirementItem[];
  nonFunctional?: RequirementItem[];
  constraints?: RequirementItem[];
  outOfScope?: RequirementItem[];
  openRisks?: RequirementItem[];
}

export interface RequirementSet {
  version: number;
  status: string;
  content: RequirementContent;
}

export interface EvaluationReport {
  totalFeedbackCount: number;
  activeFeedbackCount: number;
  recent30dCount: number;
  overallAvgScore?: number | null;
  recent30dAvgScore?: number | null;
  distinctRaterCount: number;
  evidenceBackedCount: number;
  grade?: string | null;
  categoryAverages?: { category: string; avgScore?: number | null; count: number }[];
  recentFeedback?: {
    category: string;
    score: number;
    createdAt?: string;
    contextWeight?: number | null;
    archivedAt?: string | null;
    comment?: string | null;
    feedbackSummary?: string | null;
    evidenceRefs?: string[] | null;
  }[];
}

export interface GoalAttachmentPayload {
  filename: string;
  mediaType: string;
  sizeBytes: number;
  contentBase64: string;
}

export interface OrchestrationCycle {
  id: string;
  requirementSetId: string;
  requirementSetVersion: number;
  mode: string;
  createdTaskCount: number;
  blockedTaskCount: number;
  createdAt: string;
}

export interface OrchestrationTaskView {
  id: string;
  parentTaskId: string | null;
  type: string;
  title: string;
  status: string;
  assigneeId: string | null;
  assigneeName: string | null;
  blockedReason: string | null;
}

export interface LatestFrozen {
  requirementSetId: string;
  version: number;
  decomposed: boolean;
}

export interface OrchestrationView {
  cycle: OrchestrationCycle | null;
  tasks: OrchestrationTaskView[];
  latestFrozen: LatestFrozen | null;
}

export interface OrchestrationTriggerRequest {
  userId?: string;
}

export interface OrchestrationTriggerResponse {
  id: string;
  requirementSetId: string;
  requirementSetVersion: number;
  mode: string;
  createdTaskCount: number;
  blockedTaskCount: number;
  createdAt: string;
}

export interface TeamFeedback {
  id: string;
  organizationId: string;
  subjectTeamId: string | null;
  authorEmployeeId: string;
  authorTeamId: string | null;
  category: string;
  score: number | null;
  summary: string;
  evidenceRefs: string[];
  contextWeight: number;
  createdAt: string;
}
