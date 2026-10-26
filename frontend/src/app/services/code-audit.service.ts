import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface VulnerabilityReport {
  type: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  lineRange: string;
  description: string;
  remediation: string;
  suggestedPatch: string;
}

export interface CodeAuditResponse {
  fileName: string;
  overallScore: number;
  qualityGrade: string;
  summary: string;
  criticalCount: number;
  warningCount: number;
  vulnerabilities: VulnerabilityReport[];
  generatedTests: string;
  refactoredCode: string;
  refactoringExplanation: string;
  modelUsed: string;

  // Métriques d'optimisation
  executionTimeMs?: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  estimatedCostUsd?: number;
  cached?: boolean;
  temperature?: number;
  ragRulesApplied?: string[];
}

export interface CodeAuditRequest {
  fileName?: string;
  language?: string;
  sourceCode: string;

  // Hyperparamètres & Optimisations
  model?: string;
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  useCache?: boolean;
  enableRag?: boolean;
}

export interface CodeBenchmarkResponse {
  fileName: string;
  modelA: CodeAuditResponse;
  modelB: CodeAuditResponse;
  latencySpeedupRatio: number;
  tokenSavingsPercent: number;
  costSavingsPercent: number;
  recommendation: string;
}

export interface ThreatModelAgent {
  threatLevel: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  strideCategory: string;
  threatVectors: string[];
  theoreticalImpact: string;
  agentRationale: string;
}

export interface DefenseAgent {
  defenseStrategy: string;
  patchedCode: string;
  hardeningMeasures: string[];
  solidPrinciples: string[];
  agentRationale: string;
}

export interface QaVerifierAgent {
  verificationVerdict: string;
  robustnessScore: number;
  verificationTests: string;
  residualRiskAssessment: string;
  agentRationale: string;
}

export interface MultiAgentAuditResponse {
  fileName: string;
  language: string;
  threatAssessment: ThreatModelAgent;
  remediationDefense: DefenseAgent;
  qaVerification: QaVerifierAgent;
  consensusSummary: string;
  finalSecurityScore: number;
  certificationStatus: string;
  executionTimeMs?: number;
  totalTokens?: number;
  estimatedCostUsd?: number;
  modelUsed?: string;
}

export interface PresetFile {
  id: string;
  name: string;
  path: string;
  language: string;
  description: string;
  code: string;
}

@Injectable({ providedIn: 'root' })
export class CodeAuditService {
  private base = `${environment.apiBase}/jobs/code-audit`;

  constructor(private http: HttpClient) {}

  private authHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }

  analyzeCode(request: CodeAuditRequest): Observable<CodeAuditResponse> {
    return this.http.post<CodeAuditResponse>(`${this.base}/analyze`, request, { headers: this.authHeaders() });
  }

  benchmarkModels(request: CodeAuditRequest): Observable<CodeBenchmarkResponse> {
    return this.http.post<CodeBenchmarkResponse>(`${this.base}/benchmark`, request, { headers: this.authHeaders() });
  }

  runMultiAgentAudit(request: CodeAuditRequest): Observable<MultiAgentAuditResponse> {
    return this.http.post<MultiAgentAuditResponse>(`${this.base}/multi-agent`, request, { headers: this.authHeaders() });
  }

  getPresetFiles(): Observable<PresetFile[]> {
    return this.http.get<PresetFile[]>(`${this.base}/preset-files`, { headers: this.authHeaders() });
  }
}
