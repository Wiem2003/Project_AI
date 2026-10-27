import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  EventRagService,
  EventRagStatus,
  EventRagMetrics,
  EventRagAnalyzeResponse
} from '../../services/event-rag.service';
import { simpleMarkdown } from '../../shared/utils/simple-markdown';

interface Stat { label: string; value: string; icon: string; hint?: string; }

/**
 * Admin view of the event-service RAG pipeline:
 * index status (/status), catalog trend analysis (/analyze) and performance metrics (/metrics).
 */
@Component({
  selector: 'app-admin-event-ai-insights',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="insights-page">
      <div class="page-header">
        <div>
          <div class="title-row">
            <h1 class="page-title">Event AI Insights</h1>
            <span class="pill"><i class="ti ti-sparkles"></i> RAG + Gemini</span>
          </div>
          <p class="page-subtitle">Ask questions about the event catalog and monitor the AI recommendation pipeline.</p>
        </div>
        <div class="header-actions">
          <a routerLink="/admin/events" class="btn outline"><i class="ti ti-arrow-left"></i> Events</a>
          <button class="btn primary" (click)="refresh()" [disabled]="loadingStatus">
            <i class="ti ti-refresh" [class.spin]="loadingStatus"></i> Refresh
          </button>
        </div>
      </div>

      <!-- Status -->
      <div class="status-bar" [class.ok]="status?.ready" [class.down]="statusError || (status && !status.ready)">
        <i class="ti" [class.ti-circle-check]="status?.ready" [class.ti-alert-triangle]="!status?.ready"></i>
        <div>
          <strong *ngIf="statusError">Event AI service unreachable</strong>
          <strong *ngIf="!statusError && status">{{ status.ready ? 'Index ready' : 'Index not ready yet' }}</strong>
          <strong *ngIf="!statusError && !status">Checking status…</strong>
          <span *ngIf="statusError">Start event-service (port 8081) and make sure it is registered with Eureka.</span>
          <span *ngIf="!statusError && status">{{ statusMessage() }}</span>
        </div>
      </div>

      <div class="grid">
        <!-- Trend analysis -->
        <section class="card analysis">
          <div class="card-head">
            <h2><i class="ti ti-chart-dots"></i> Catalog trend analysis</h2>
            <p>The AI answers using the indexed event data only.</p>
          </div>
          <form class="ask" (ngSubmit)="analyze()">
            <textarea name="q" rows="3" maxlength="800" [(ngModel)]="question" [disabled]="analyzing"
                      placeholder="e.g. Which categories have the most events, and where are we missing offers?"></textarea>
            <div class="ask-actions">
              <div class="chips">
                <button type="button" *ngFor="let ex of examples" (click)="useExample(ex)" [disabled]="analyzing">{{ ex }}</button>
              </div>
              <button type="submit" class="btn primary" [disabled]="analyzing || !question.trim()">
                <i class="ti" [class.ti-sparkles]="!analyzing" [class.ti-loader-2]="analyzing" [class.spin]="analyzing"></i>
                {{ analyzing ? 'Analyzing…' : 'Analyze' }}
              </button>
            </div>
          </form>
          <div class="error" *ngIf="analyzeError"><i class="ti ti-alert-circle"></i> {{ analyzeError }}</div>
          <div class="result" *ngIf="analysis">
            <div class="result-body" [innerHTML]="analysisHtml"></div>
            <div class="meta">
              <span><i class="ti ti-database"></i> {{ analysis.dataPointsAnalyzed }} data points analyzed</span>
              <span><i class="ti ti-clock"></i> {{ (analysis.responseTimeMs / 1000) | number:'1.1-1' }}s</span>
            </div>
          </div>
        </section>

        <!-- Metrics -->
        <section class="card metrics">
          <div class="card-head">
            <h2><i class="ti ti-gauge"></i> Performance</h2>
            <p>Since the service started.</p>
          </div>
          <div class="empty" *ngIf="!metrics && !statusError">Loading metrics…</div>
          <div class="empty" *ngIf="statusError">No data while the service is unreachable.</div>
          <ng-container *ngIf="metrics">
            <div class="stats">
              <div class="stat" *ngFor="let s of usageStats">
                <i class="ti" [ngClass]="s.icon"></i>
                <div><span class="v">{{ s.value }}</span><span class="l">{{ s.label }}</span></div>
              </div>
            </div>
            <h3>Latency</h3>
            <div class="empty small" *ngIf="!latencyStats.length">No requests yet. Ask something on the Events page or above.</div>
            <div class="rows" *ngIf="latencyStats.length">
              <div class="kv" *ngFor="let s of latencyStats"><span>{{ s.label }}</span><strong>{{ s.value }}</strong></div>
            </div>
            <h3>Cache</h3>
            <div class="rows">
              <div class="kv" *ngFor="let s of cacheStats"><span>{{ s.label }}</span><strong>{{ s.value }}</strong></div>
            </div>
            <h3>Configuration</h3>
            <div class="rows">
              <div class="kv" *ngFor="let s of configStats"><span>{{ s.label }}</span><strong>{{ s.value }}</strong></div>
            </div>
          </ng-container>
        </section>
      </div>
    </div>
  `,
  styles: [`
    $primary: #2D5757; $secondary: #3D3D60; $accent: #F6BD60; $cta: #C84630; $border: rgba(45,87,87,0.08); $muted: rgba(61,61,96,0.55);
    .insights-page { padding: 4px; }
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; margin-bottom: 20px; }
    .title-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .page-title { font-family: 'Playfair Display', serif; font-size: 24px; font-weight: 700; color: $secondary; margin: 0; }
    .page-subtitle { font-size: 14px; color: $muted; margin: 6px 0 0; }
    .pill { display: inline-flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 999px; background: rgba($accent, 0.2); color: #8a5a00; font-size: 12px; font-weight: 700; }
    .header-actions { display: flex; gap: 10px; }
    .btn { display: inline-flex; align-items: center; gap: 8px; padding: 10px 18px; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; text-decoration: none; border: 1px solid transparent; transition: all 0.2s; font-family: inherit;
      &.primary { background: $primary; color: #fff; &:hover:not(:disabled) { background: darken($primary, 6%); } }
      &.outline { background: #fff; color: $primary; border-color: rgba($primary, 0.3); &:hover { background: rgba($primary, 0.06); } }
      &:disabled { opacity: 0.6; cursor: not-allowed; } }
    .spin { animation: spin 1s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }

    .status-bar { display: flex; gap: 14px; align-items: center; padding: 14px 18px; border-radius: 14px; background: #fff; border: 1px solid $border; margin-bottom: 20px;
      > i { font-size: 26px; color: $muted; }
      strong { display: block; color: $secondary; font-size: 15px; }
      span { font-size: 13px; color: $muted; }
      &.ok { border-color: rgba(16,185,129,0.35); background: rgba(16,185,129,0.06); > i { color: #10b981; } }
      &.down { border-color: rgba($cta, 0.35); background: rgba($cta, 0.05); > i { color: $cta; } } }

    .grid { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: 20px; align-items: start; }
    .card { background: #fff; border: 1px solid $border; border-radius: 16px; padding: 22px; box-shadow: 0 2px 10px rgba(61,61,96,0.04); }
    .card-head { margin-bottom: 16px;
      h2 { display: flex; align-items: center; gap: 8px; font-size: 17px; font-weight: 700; color: $secondary; margin: 0;
        i { color: $primary; font-size: 20px; } }
      p { font-size: 13px; color: $muted; margin: 4px 0 0; } }

    .ask textarea { width: 100%; box-sizing: border-box; padding: 12px 14px; border: 1.5px solid rgba($primary, 0.15); border-radius: 12px; font-family: inherit; font-size: 14px; color: $secondary; resize: vertical; outline: none; background: #FDFAF6;
      &:focus { border-color: $primary; } }
    .ask-actions { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-top: 10px; }
    .chips { display: flex; flex-wrap: wrap; gap: 6px;
      button { padding: 5px 12px; border-radius: 999px; border: 1px solid rgba($primary, 0.25); background: transparent; color: $primary; font-size: 12px; cursor: pointer; font-family: inherit;
        &:hover:not(:disabled) { background: $primary; color: #fff; } } }
    .error { margin-top: 14px; padding: 12px 14px; border-radius: 10px; background: rgba($cta, 0.08); color: $cta; font-size: 13px; display: flex; gap: 8px; align-items: center; }
    .result { margin-top: 16px; padding: 18px; border-radius: 12px; background: #F7EDE2; }
    .result-body { color: $secondary; font-size: 14px; line-height: 1.65;
      ::ng-deep p { margin: 0 0 10px; color: $secondary; }
      ::ng-deep ul, ::ng-deep ol { margin: 0 0 10px; padding-left: 20px; }
      ::ng-deep li { margin-bottom: 5px; }
      ::ng-deep h4 { font-size: 15px; margin: 12px 0 6px; } }
    .meta { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 10px; font-size: 12px; color: $muted;
      span { display: inline-flex; align-items: center; gap: 4px; } }

    .stats { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 8px; }
    .stat { display: flex; gap: 10px; align-items: center; padding: 12px; border-radius: 12px; background: #FDFAF6; border: 1px solid $border;
      > i { font-size: 20px; color: $primary; }
      .v { display: block; font-size: 18px; font-weight: 800; color: $secondary; line-height: 1.2; }
      .l { font-size: 11px; color: $muted; text-transform: uppercase; letter-spacing: 0.04em; } }
    h3 { font-size: 12px; font-weight: 700; color: $muted; text-transform: uppercase; letter-spacing: 0.05em; margin: 18px 0 8px; }
    .rows { border: 1px solid $border; border-radius: 10px; overflow: hidden; }
    .kv { display: flex; flex-wrap: nowrap; align-items: center; justify-content: space-between; gap: 12px; margin: 0; padding: 9px 12px; font-size: 13px; color: $muted;
      & + .kv { border-top: 1px solid $border; }
      strong { color: $secondary; font-weight: 600; text-align: right; } }
    .empty { font-size: 13px; color: $muted; padding: 8px 0; &.small { padding: 4px 0; } }

    @media (max-width: 1024px) { .grid { grid-template-columns: 1fr; } }
    @media (max-width: 600px) { .ask-actions { flex-direction: column; .btn { width: 100%; justify-content: center; } } .stats { grid-template-columns: 1fr; } }
  `]
})
export class AdminEventAiInsightsComponent implements OnInit {
  private rag = inject(EventRagService);

  readonly examples = [
    'Which event categories are most represented?',
    'What kinds of events are missing for exam preparation?',
    'Summarize the upcoming events for business English learners',
  ];

  status: EventRagStatus | null = null;
  statusError = false;
  loadingStatus = false;
  metrics: EventRagMetrics | null = null;
  usageStats: Stat[] = [];
  latencyStats: Stat[] = [];
  cacheStats: Stat[] = [];
  configStats: Stat[] = [];

  question = '';
  analyzing = false;
  analyzeError = '';
  analysis: EventRagAnalyzeResponse | null = null;
  analysisHtml = '';

  ngOnInit(): void {
    this.refresh();
  }

  statusMessage(): string {
    const msg = this.status?.['message'];
    return typeof msg === 'string' ? msg : `${this.status?.totalChunks ?? 0} indexed event chunks.`;
  }

  refresh(): void {
    this.loadingStatus = true;
    forkJoin({
      status: this.rag.status().pipe(catchError(() => of(null))),
      metrics: this.rag.metrics().pipe(catchError(() => of(null))),
    }).subscribe(({ status, metrics }) => {
      this.loadingStatus = false;
      this.statusError = status === null;
      this.status = status;
      this.metrics = metrics;
      if (metrics) this.buildStats(metrics);
    });
  }

  useExample(ex: string): void {
    this.question = ex;
    this.analyze();
  }

  analyze(): void {
    const q = this.question.trim();
    if (!q || this.analyzing) return;
    this.analyzing = true;
    this.analyzeError = '';
    this.analysis = null;
    this.rag.analyze(q).subscribe({
      next: res => {
        this.analyzing = false;
        if (res.error) { this.analyzeError = res.error; return; }
        this.analysis = res;
        this.analysisHtml = simpleMarkdown(res.analysis);
        this.refresh(); // metrics changed
      },
      error: err => {
        this.analyzing = false;
        this.analyzeError = err?.status === 0 || err?.status === 503 || err?.status === 504
          ? 'The event AI service is not reachable right now.'
          : (err?.error?.error || 'The analysis failed. Check the event-service logs (GEMINI_API_KEY set?).');
      }
    });
  }

  private buildStats(m: EventRagMetrics): void {
    const perf = (m['performance'] ?? {}) as Record<string, any>;
    const cache = (m['cache'] ?? {}) as Record<string, any>;
    const index = (m['index'] ?? {}) as Record<string, any>;
    const latency = (perf['latency'] ?? {}) as Record<string, any>;
    const config = (perf['configuration'] ?? {}) as Record<string, any>;
    const ms = (v: unknown) => (v === undefined || v === null ? '—' : v === 'N/A' ? 'N/A' : `${v} ms`);
    const val = (v: unknown) => (v === undefined || v === null || v === '' ? '—' : String(v));

    this.usageStats = [
      { label: 'Requests', value: val(perf['totalRequests'] ?? 0), icon: 'ti-message-2' },
      { label: 'LLM calls', value: val(perf['totalLlmCalls'] ?? 0), icon: 'ti-sparkles' },
      { label: 'Cache hit rate', value: val(perf['cacheHitRate'] ?? '0.0%'), icon: 'ti-bolt' },
      { label: 'Est. cost', value: val(perf['estimatedCostUSD'] ?? '$0.0000'), icon: 'ti-coin' },
    ];
    this.latencyStats = Object.keys(latency).length ? [
      { label: 'Average total', value: ms(latency['totalAvgMs']), icon: '' },
      { label: 'Min / Max', value: `${val(latency['totalMinMs'])} / ${val(latency['totalMaxMs'])} ms`, icon: '' },
      { label: 'Retrieval (avg)', value: ms(latency['retrievalAvgMs']), icon: '' },
      { label: 'LLM (avg)', value: ms(latency['llmAvgMs']), icon: '' },
    ] : [];
    this.cacheStats = [
      { label: 'Entries', value: `${val(cache['totalEntries'] ?? 0)} / ${val(cache['maxSize'])}`, icon: '' },
      { label: 'Hits', value: val(cache['totalHits'] ?? 0), icon: '' },
      { label: 'Time to live', value: cache['ttlSeconds'] != null ? `${Math.round(Number(cache['ttlSeconds']) / 60)} min` : '—', icon: '' },
      { label: 'Indexed chunks', value: val(index['totalChunks']), icon: '' },
    ];
    this.configStats = [
      { label: 'Model', value: val(config['model']), icon: '' },
      { label: 'Temperature', value: val(config['temperature']), icon: '' },
      { label: 'Max output tokens', value: val(config['maxOutputTokens']), icon: '' },
      { label: 'Embeddings', value: `${val(config['embeddingType'])}${config['embeddingDimension'] ? ' · ' + config['embeddingDimension'] + 'd' : ''}`, icon: '' },
    ];
  }
}
