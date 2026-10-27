import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { EventRagService, EventRagRecommendResponse, EventRagSource } from '../../../services/event-rag.service';
import { simpleMarkdown } from '../../../shared/utils/simple-markdown';

/**
 * Free-text event advisor backed by event-service RAG + LLM (POST /api/events/rag/recommend).
 * The student describes a goal; the answer is grounded in the event catalog and links to the matching events.
 */
@Component({
  selector: 'app-event-ai-advisor',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <section class="advisor">
      <div class="advisor-header">
        <i class="ti ti-message-chatbot"></i>
        <div>
          <h2>Ask the AI Event Advisor</h2>
          <p>Describe what you want to improve and get events from our catalog that match your goal.</p>
        </div>
      </div>

      <form class="ask-row" (ngSubmit)="ask()">
        <input type="text" name="query" [(ngModel)]="query" maxlength="500"
               placeholder="e.g. I want to prepare for job interviews in English"
               [disabled]="loading" aria-label="Your goal">
        <select name="category" [(ngModel)]="category" [disabled]="loading" aria-label="Category (optional)">
          <option value="">Any category</option>
          <option *ngFor="let c of categories" [value]="c">{{ label(c) }}</option>
        </select>
        <button type="submit" [disabled]="loading || !query.trim()">
          <i class="ti" [class.ti-sparkles]="!loading" [class.ti-loader-2]="loading" [class.spin]="loading"></i>
          {{ loading ? 'Thinking…' : 'Ask' }}
        </button>
      </form>

      <div class="examples" *ngIf="!result && !loading && !error">
        <span>Try:</span>
        <button type="button" *ngFor="let ex of examples" (click)="useExample(ex)">{{ ex }}</button>
      </div>

      <div class="error" *ngIf="error"><i class="ti ti-alert-circle"></i> {{ error }}</div>

      <div class="answer" *ngIf="result">
        <div class="answer-body" [innerHTML]="html"></div>

        <div class="sources" *ngIf="eventSources.length">
          <h4>Events mentioned</h4>
          <div class="source-list">
            <a *ngFor="let s of eventSources" class="source" [routerLink]="['/events', eventId(s)]">
              <i class="ti ti-calendar-event"></i>
              <span class="source-title">{{ s.title }}</span>
              <span class="source-cat" *ngIf="s.category">{{ label(s.category) }}</span>
              <i class="ti ti-arrow-right"></i>
            </a>
          </div>
        </div>

        <div class="meta">
          <span *ngIf="result.outOfScope"><i class="ti ti-info-circle"></i> This question is outside the event catalog.</span>
          <span><i class="ti ti-database"></i> {{ result.sourcesUsed }} catalog passages used</span>
          <span><i class="ti ti-clock"></i> {{ (result.responseTimeMs / 1000) | number:'1.1-1' }}s{{ result.fromCache ? ' (cached)' : '' }}</span>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .advisor { background: var(--color-white); border-radius: 24px; padding: 32px 40px; margin-bottom: 40px; box-shadow: var(--shadow-card); border: 1px solid rgba(246,189,96,0.35); }
    .advisor-header { display: flex; gap: 16px; align-items: center; margin-bottom: 24px;
      > i { font-size: 40px; color: var(--color-accent); flex-shrink: 0; }
      h2 { font-size: 24px; font-weight: 800; color: var(--color-primary); margin: 0 0 4px; }
      p { font-size: 15px; color: var(--color-gray-500); margin: 0; } }
    .ask-row { display: flex; gap: 12px;
      input, select { padding: 14px 16px; border: 2px solid rgba(61,61,96,0.12); border-radius: 14px; font-size: 15px; background: var(--color-background); color: var(--color-primary); outline: none; transition: border-color 0.2s;
        &:focus { border-color: var(--color-secondary); } }
      input { flex: 1; min-width: 0; }
      select { flex: 0 0 200px; cursor: pointer; }
      button { display: flex; align-items: center; gap: 8px; padding: 14px 28px; background: linear-gradient(135deg, var(--color-secondary), #1a3a3a); color: #fff; border: none; border-radius: 14px; font-size: 15px; font-weight: 700; cursor: pointer; white-space: nowrap; transition: all 0.2s;
        &:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 8px 20px rgba(45,87,87,0.3); }
        &:disabled { opacity: 0.6; cursor: not-allowed; }
        i { font-size: 18px; } } }
    .spin { animation: spin 1s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .examples { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 16px;
      span { font-size: 13px; color: var(--color-gray-500); font-weight: 600; }
      button { padding: 6px 14px; border-radius: 999px; border: 1px solid rgba(45,87,87,0.25); background: transparent; color: var(--color-secondary); font-size: 13px; cursor: pointer;
        &:hover { background: var(--color-secondary); color: #fff; } } }
    .error { margin-top: 20px; padding: 14px 18px; border-radius: 12px; background: rgba(239,68,68,0.08); color: var(--color-danger); font-size: 14px; display: flex; gap: 8px; align-items: center; }
    .answer { margin-top: 24px; padding: 24px; border-radius: 16px; background: var(--color-background); }
    .answer-body { color: var(--color-primary); font-size: 15px; line-height: 1.65;
      ::ng-deep p { margin: 0 0 10px; color: var(--color-primary); font-size: 15px; line-height: 1.65; }
      ::ng-deep ul, ::ng-deep ol { margin: 0 0 12px; padding-left: 22px; }
      ::ng-deep li { margin-bottom: 6px; }
      ::ng-deep h4 { font-size: 16px; margin: 14px 0 8px; } }
    .sources { margin-top: 20px; padding-top: 16px; border-top: 1px solid rgba(61,61,96,0.1);
      h4 { font-size: 14px; font-weight: 700; color: var(--color-gray-500); text-transform: uppercase; letter-spacing: 0.04em; margin: 0 0 12px; } }
    .source-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 10px; }
    .source { display: flex; align-items: center; gap: 10px; padding: 12px 14px; background: var(--color-white); border-radius: 12px; text-decoration: none; color: var(--color-primary); border: 1px solid rgba(61,61,96,0.08); transition: all 0.2s;
      &:hover { border-color: var(--color-accent); transform: translateY(-2px); box-shadow: 0 6px 16px rgba(61,61,96,0.1); }
      > i:first-child { color: var(--color-secondary); font-size: 18px; }
      > i:last-child { margin-left: auto; color: var(--color-gray-500); } }
    .source-title { font-weight: 600; font-size: 14px; line-height: 1.35; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .source-cat { font-size: 11px; font-weight: 700; padding: 3px 8px; border-radius: 999px; background: rgba(246,189,96,0.2); color: #8a5a00; white-space: nowrap; }
    .meta { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 16px; font-size: 12px; color: var(--color-gray-500);
      span { display: inline-flex; align-items: center; gap: 4px; } }
    @media (max-width: 768px) {
      .advisor { padding: 24px 16px; }
      .ask-row { flex-direction: column; select { flex: none; } button { justify-content: center; } }
    }
  `]
})
export class EventAiAdvisorComponent {
  private rag = inject(EventRagService);

  readonly categories = ['WORKSHOP', 'WEBINAR', 'CONFERENCE', 'TRAINING', 'EXAM_PREPARATION', 'BUSINESS_ENGLISH', 'CULTURAL_EVENT'];
  readonly examples = [
    'I want to prepare for job interviews in English',
    'Events to improve my speaking confidence',
    'Help me get ready for the IELTS exam',
  ];

  query = '';
  category = '';
  loading = false;
  error = '';
  result: EventRagRecommendResponse | null = null;
  html = '';
  eventSources: EventRagSource[] = [];

  label(c: string): string {
    return c.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, ch => ch.toUpperCase());
  }

  eventId(s: EventRagSource): number | null {
    return EventRagService.eventIdOf(s);
  }

  useExample(ex: string): void {
    this.query = ex;
    this.ask();
  }

  ask(): void {
    const q = this.query.trim();
    if (!q || this.loading) return;
    this.loading = true;
    this.error = '';
    this.result = null;

    this.rag.recommend(q, this.category || undefined).subscribe({
      next: res => {
        this.loading = false;
        if (res.error) { this.error = res.error; return; }
        this.result = res;
        this.html = simpleMarkdown(res.recommendation);
        // Only real catalog events get a link; de-duplicate (several chunks can come from one event).
        const seen = new Set<number>();
        this.eventSources = (res.sources ?? []).filter(s => {
          const id = EventRagService.eventIdOf(s);
          if (id === null || seen.has(id)) return false;
          seen.add(id);
          return true;
        });
      },
      error: err => {
        this.loading = false;
        this.error = err?.status === 0 || err?.status === 503 || err?.status === 504
          ? 'The event AI service is not reachable right now. Please try again in a moment.'
          : (err?.error?.error || 'Something went wrong while getting recommendations.');
      }
    });
  }
}
