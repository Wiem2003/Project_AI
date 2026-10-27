import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** A catalog passage the event-service RAG used to build its answer. */
export interface EventRagSource {
  /** "db-event-12" for a real event (links to /events/12), other ids for knowledge-base documents. */
  id: string;
  title: string;
  category?: string | null;
  score?: number;
}

export interface EventRagRecommendResponse {
  recommendation: string;
  query: string;
  category?: string | null;
  sourcesUsed: number;
  sources: EventRagSource[];
  outOfScope: boolean;
  fromCache: boolean;
  responseTimeMs: number;
  error?: string;
}

export interface EventRagAnalyzeResponse {
  analysis: string;
  query: string;
  dataPointsAnalyzed: number;
  responseTimeMs: number;
  error?: string;
}

export interface EventRagStatus {
  ready: boolean;
  totalChunks: number;
  [key: string]: unknown;
}

/** Shape is open-ended (cache/index/latency/configuration blocks); rendered generically. */
export type EventRagMetrics = Record<string, unknown>;

/** Alaa's event-service RAG + LLM endpoints (/api/events/rag/*). */
@Injectable({ providedIn: 'root' })
export class EventRagService {
  private readonly base = `${environment.apiBase}/events/rag`;

  constructor(private http: HttpClient) {}

  recommend(query: string, category?: string): Observable<EventRagRecommendResponse> {
    const body: Record<string, string> = { query };
    if (category) body['category'] = category;
    return this.http.post<EventRagRecommendResponse>(`${this.base}/recommend`, body);
  }

  analyze(query: string): Observable<EventRagAnalyzeResponse> {
    return this.http.post<EventRagAnalyzeResponse>(`${this.base}/analyze`, { query });
  }

  status(): Observable<EventRagStatus> {
    return this.http.get<EventRagStatus>(`${this.base}/status`);
  }

  metrics(): Observable<EventRagMetrics> {
    return this.http.get<EventRagMetrics>(`${this.base}/metrics`);
  }

  /** Event id for a source that is a real catalog event, otherwise null. */
  static eventIdOf(source: EventRagSource): number | null {
    const m = /^db-event-(\d+)$/.exec(source.id ?? '');
    return m ? Number(m[1]) : null;
  }
}
