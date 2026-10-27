import { Component, ElementRef, inject, NgZone, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { combineLatest, Subscription } from 'rxjs';
import { DataService, Course, Lesson } from '../../../core/data.service';
import { NavbarComponent } from '../../../components/navbar/navbar.component';
import { FooterComponent } from '../../../components/footer/footer.component';
import { AskCitation, AskResponse, CourseAskService } from '../../../services/course-ask.service';

declare global {
  interface Window {
    YT?: any;
    onYouTubeIframeAPIReady?: () => void;
  }
}

let youTubeApi: Promise<any> | null = null;

/** Loads the YouTube IFrame API once (needed to seek the player from a citation). */
function loadYouTubeApi(): Promise<any> {
  if (window.YT?.Player) {
    return Promise.resolve(window.YT);
  }
  if (!youTubeApi) {
    youTubeApi = new Promise(resolve => {
      const previous = window.onYouTubeIframeAPIReady;
      window.onYouTubeIframeAPIReady = () => {
        previous?.();
        resolve(window.YT);
      };
      const script = document.createElement('script');
      script.src = 'https://www.youtube.com/iframe_api';
      document.head.appendChild(script);
    });
  }
  return youTubeApi;
}

/** Same rule as the backend (YoutubeUrls): watch?v=, youtu.be/, embed/, shorts/, live/. */
export function extractYoutubeId(url?: string): string | null {
  if (!url) return null;
  const m = url.match(/(?:youtube(?:-nocookie)?\.com\/(?:watch\?(?:.*&)?v=|embed\/|shorts\/|live\/|v\/)|youtu\.be\/)([A-Za-z0-9_-]{11})/);
  return m ? m[1] : null;
}

type AnswerPart = { text: string } | { citation: AskCitation };

interface AskExchange {
  question: string;
  response: AskResponse;
  parts: AnswerPart[];
}

interface OrderedLesson {
  lesson: Lesson;
  number: number; // "Leçon N": position in the whole course, as in backend citations
}

@Component({
  selector: 'app-lesson-player',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, NavbarComponent, FooterComponent],
  template: `
    <app-navbar></app-navbar>
    <div class="lesson-page">
      <div class="container">
        @if (loadError) {
          <div class="state-box"><i class="ti ti-alert-circle"></i> {{ loadError }}</div>
        } @else if (!course || !current) {
          <div class="state-box"><i class="ti ti-loader-2 spin"></i> Loading lesson…</div>
        } @else {
          <div class="breadcrumb">
            <a routerLink="/courses">Courses</a>
            <i class="ti ti-chevron-right"></i>
            <a [routerLink]="['/courses', course.id]">{{ course.title }}</a>
            <i class="ti ti-chevron-right"></i>
            <span>Lesson {{ current.number }}</span>
          </div>

          <div class="lesson-layout">
            <div class="lesson-main">
              <h1 class="lesson-title">Lesson {{ current.number }} – {{ current.lesson.title }}</h1>

              @if (videoId) {
                <div class="video-wrapper" #videoWrapper>
                  <div id="lesson-yt-player"></div>
                </div>
              } @else {
                <div class="no-video"><i class="ti ti-video-off"></i> No video for this lesson.</div>
              }

              @if (current.lesson.description) {
                <p class="lesson-description">{{ current.lesson.description }}</p>
              }

              <div class="lesson-nav">
                @if (previous) {
                  <a class="nav-btn" [routerLink]="['/courses', course.id, 'lessons', previous.lesson.id]">
                    <i class="ti ti-chevron-left"></i> Lesson {{ previous.number }}
                  </a>
                }
                <span class="spacer"></span>
                @if (next) {
                  <a class="nav-btn" [routerLink]="['/courses', course.id, 'lessons', next.lesson.id]">
                    Lesson {{ next.number }} <i class="ti ti-chevron-right"></i>
                  </a>
                }
              </div>
            </div>

            <aside class="ask-panel">
              <div class="ask-header">
                <i class="ti ti-message-question"></i>
                <div>
                  <h2>Ask the course</h2>
                  <p>Answers come only from this course, with links to the exact moment in the videos.</p>
                </div>
              </div>

              <div class="ask-history">
                @for (ex of history; track $index) {
                  <div class="bubble question">{{ ex.question }}</div>
                  <div class="bubble answer" [class.not-found]="!ex.response.answered">
                    @for (part of ex.parts; track $index) {
                      @if (isCitation(part)) {
                        <button type="button" class="citation" (click)="openCitation(part.citation)"
                                [title]="citationTitle(part.citation)">
                          <i class="ti" [class.ti-player-play]="part.citation.sourceType === 'transcript'"
                             [class.ti-file-text]="part.citation.sourceType === 'pdf'"
                             [class.ti-book]="part.citation.sourceType === 'lesson_desc' || part.citation.sourceType === 'course_desc'"></i>
                          {{ part.citation.label }}
                        </button>
                      } @else {
                        <span>{{ part.text }}</span>
                      }
                    }
                  </div>
                } @empty {
                  <p class="ask-empty">Example: “What is the difference between for and since?”</p>
                }
                @if (asking) {
                  <div class="bubble answer pending"><i class="ti ti-loader-2 spin"></i> Searching the course…</div>
                }
              </div>

              @if (askError) {
                <div class="ask-error"><i class="ti ti-alert-circle"></i> {{ askError }}</div>
              }

              <form class="ask-form" (ngSubmit)="ask()">
                <textarea name="question" [(ngModel)]="question" rows="3" maxlength="1000"
                          placeholder="Ask a question about this course…"
                          (keydown.enter)="onEnter($event)" [disabled]="asking"></textarea>
                <button type="submit" class="btn-ask" [disabled]="asking || !question.trim()">
                  <i class="ti ti-send"></i> Ask
                </button>
              </form>
            </aside>
          </div>
        }
      </div>
    </div>
    <app-footer></app-footer>
  `,
  styles: [`
    .lesson-page { min-height: 100vh; background: var(--color-background); padding: 32px 0 60px; }
    .container { max-width: 1280px; margin: 0 auto; padding: 0 24px; }
    .breadcrumb { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--color-gray-500); margin-bottom: 16px;
      a { color: var(--color-secondary); text-decoration: none; } i { font-size: 14px; } }
    .state-box { padding: 60px 0; text-align: center; color: var(--color-gray-500); font-size: 16px; }
    .lesson-layout { display: grid; grid-template-columns: minmax(0, 1fr) 400px; gap: 32px; align-items: start; }
    .lesson-title { font-size: 26px; color: var(--color-primary); margin: 0 0 18px; }
    .video-wrapper { position: relative; width: 100%; aspect-ratio: 16 / 9; background: #000; border-radius: 14px; overflow: hidden;
      ::ng-deep iframe, #lesson-yt-player { position: absolute; inset: 0; width: 100%; height: 100%; } }
    .no-video { padding: 40px; text-align: center; border: 1.5px dashed rgba(61,61,96,0.2); border-radius: 14px; color: var(--color-gray-500); }
    .lesson-description { margin: 20px 0 0; font-size: 15px; line-height: 1.7; color: var(--color-gray-600); }
    .lesson-nav { display: flex; align-items: center; margin-top: 24px; .spacer { flex: 1; } }
    .nav-btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 16px; border-radius: 10px; border: 1.5px solid rgba(61,61,96,0.15);
      color: var(--color-primary); text-decoration: none; font-weight: 600; font-size: 14px; &:hover { border-color: var(--color-secondary); } }
    .ask-panel { position: sticky; top: 90px; display: flex; flex-direction: column; max-height: calc(100vh - 120px);
      background: #fff; border: 1.5px solid rgba(61,61,96,0.12); border-radius: 16px; overflow: hidden; }
    .ask-header { display: flex; gap: 12px; padding: 18px 20px; border-bottom: 1px solid rgba(61,61,96,0.1);
      background: linear-gradient(135deg, rgba(61,61,96,0.06), rgba(61,61,96,0.02));
      > i { font-size: 26px; color: var(--color-secondary); }
      h2 { margin: 0; font-size: 17px; color: var(--color-primary); } p { margin: 4px 0 0; font-size: 13px; color: var(--color-gray-500); line-height: 1.5; } }
    .ask-history { flex: 1; overflow-y: auto; padding: 16px 20px; display: flex; flex-direction: column; gap: 10px; min-height: 160px; }
    .ask-empty { font-size: 13px; color: var(--color-gray-500); font-style: italic; margin: 0; }
    .bubble { padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
    .bubble.question { align-self: flex-end; max-width: 85%; background: var(--color-primary); color: #fff; }
    .bubble.answer { align-self: flex-start; background: rgba(61,61,96,0.06); color: var(--color-gray-700, #333); }
    .bubble.answer.not-found { background: #fff7ed; color: #9a3412; }
    .bubble.pending { color: var(--color-gray-500); }
    .citation { display: inline-flex; align-items: center; gap: 4px; margin: 0 2px; padding: 1px 8px; border-radius: 999px; cursor: pointer;
      border: 1px solid var(--color-secondary); background: #fff; color: var(--color-secondary); font-size: 12.5px; font-weight: 600; vertical-align: baseline;
      i { font-size: 13px; } &:hover { background: var(--color-secondary); color: #fff; } }
    .ask-error { margin: 0 20px 10px; padding: 10px 12px; border-radius: 10px; background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; font-size: 13px; }
    .ask-form { display: flex; flex-direction: column; gap: 8px; padding: 14px 20px 18px; border-top: 1px solid rgba(61,61,96,0.1);
      textarea { resize: none; padding: 10px 12px; border: 1.5px solid rgba(61,61,96,0.15); border-radius: 10px; font: inherit; font-size: 14px; outline: none;
        &:focus { border-color: var(--color-secondary); } } }
    .btn-ask { align-self: flex-end; display: inline-flex; align-items: center; gap: 6px; padding: 9px 18px; border: none; border-radius: 10px; cursor: pointer;
      background: linear-gradient(135deg, var(--color-secondary), var(--color-primary)); color: #fff; font-weight: 700; font-size: 14px;
      &:disabled { opacity: 0.6; cursor: not-allowed; } }
    .spin { animation: spin 1s linear infinite; display: inline-block; }
    @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
    @media (max-width: 992px) { .lesson-layout { grid-template-columns: 1fr; } .ask-panel { position: static; max-height: none; } }
  `]
})
export class LessonPlayerComponent implements OnInit, OnDestroy {
  private dataService = inject(DataService);
  private askService = inject(CourseAskService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private zone = inject(NgZone);

  @ViewChild('videoWrapper') videoWrapper?: ElementRef<HTMLElement>;

  course?: Course;
  lessons: OrderedLesson[] = [];
  current?: OrderedLesson;
  previous?: OrderedLesson;
  next?: OrderedLesson;
  videoId: string | null = null;
  loadError: string | null = null;

  question = '';
  asking = false;
  askError: string | null = null;
  history: AskExchange[] = [];

  private player: any = null;
  private playerVideoId: string | null = null;
  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = combineLatest([this.route.paramMap, this.route.queryParamMap]).subscribe(([params, query]) => {
      const courseId = Number(params.get('id'));
      const lessonId = Number(params.get('lessonId'));
      const t = Number(query.get('t'));
      const startSeconds = Number.isFinite(t) && t > 0 ? Math.floor(t) : 0;

      if (this.course?.id === courseId) {
        this.selectLesson(lessonId, startSeconds);
        return;
      }
      this.course = undefined;
      this.history = [];
      this.dataService.getCourseById(courseId).subscribe({
        next: course => {
          this.course = course;
          this.lessons = this.flattenLessons(course);
          this.selectLesson(lessonId, startSeconds);
        },
        error: () => this.loadError = 'Course not found.'
      });
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.player?.destroy?.();
  }

  // ── lessons & player ──────────────────────────────────────────────────────

  private flattenLessons(course: Course): OrderedLesson[] {
    const modules = [...(course.modules ?? [])].sort((a, b) => a.orderIndex - b.orderIndex);
    const out: OrderedLesson[] = [];
    for (const mod of modules) {
      for (const lesson of [...(mod.lessons ?? [])].sort((a, b) => a.orderIndex - b.orderIndex)) {
        out.push({ lesson, number: out.length + 1 });
      }
    }
    return out;
  }

  private selectLesson(lessonId: number, startSeconds: number): void {
    const index = this.lessons.findIndex(l => l.lesson.id === lessonId);
    if (index < 0) {
      this.loadError = 'Lesson not found in this course.';
      return;
    }
    this.loadError = null;
    const changed = this.current?.lesson.id !== lessonId;
    this.current = this.lessons[index];
    this.previous = this.lessons[index - 1];
    this.next = this.lessons[index + 1];
    this.videoId = extractYoutubeId(this.current.lesson.videoUrl);

    if (!this.videoId) {
      this.player?.destroy?.();
      this.player = null;
      this.playerVideoId = null;
      return;
    }
    if (changed || this.playerVideoId !== this.videoId) {
      // Let Angular render the #lesson-yt-player container first.
      setTimeout(() => this.showVideo(this.videoId!, startSeconds));
    } else {
      this.seek(startSeconds);
    }
  }

  private async showVideo(videoId: string, startSeconds: number): Promise<void> {
    const YT = await loadYouTubeApi();
    this.zone.runOutsideAngular(() => {
      if (this.player && document.getElementById('lesson-yt-player')?.tagName === 'IFRAME') {
        this.player.loadVideoById({ videoId, startSeconds });
      } else {
        this.player?.destroy?.();
        this.player = new YT.Player('lesson-yt-player', {
          videoId,
          playerVars: { start: startSeconds, rel: 0, modestbranding: 1 }
        });
      }
    });
    this.playerVideoId = videoId;
  }

  private seek(seconds: number): void {
    if (!this.player?.seekTo) return;
    this.zone.runOutsideAngular(() => {
      this.player.seekTo(seconds, true);
      this.player.playVideo?.();
    });
    this.videoWrapper?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  // ── ask ───────────────────────────────────────────────────────────────────

  onEnter(event: Event): void {
    const e = event as KeyboardEvent;
    if (!e.shiftKey) {
      e.preventDefault();
      this.ask();
    }
  }

  ask(): void {
    const question = this.question.trim();
    if (!question || this.asking || !this.course) return;
    this.asking = true;
    this.askError = null;

    this.askService.ask(this.course.id, question, this.current?.lesson.id).subscribe({
      next: response => {
        this.history.push({ question, response, parts: this.parseAnswer(response) });
        this.question = '';
        this.asking = false;
      },
      error: err => {
        this.askError = this.errorMessage(err);
        this.asking = false;
      }
    });
  }

  private errorMessage(err: any): string {
    const serverMsg: string | undefined = err?.error?.message;
    switch (err?.status) {
      case 401: return 'Please sign in to ask questions about this course.';
      case 403: return serverMsg || 'This assistant is reserved to students enrolled in this course.';
      case 429: return serverMsg || 'Too many questions, please wait a moment.';
      case 503: return serverMsg || 'The course assistant is temporarily unavailable.';
      default: return 'Something went wrong, please try again.';
    }
  }

  /** Splits the answer so that every [label] matching a citation becomes a clickable chip. */
  private parseAnswer(response: AskResponse): AnswerPart[] {
    const byLabel = new Map(response.citations.map(c => [c.label, c]));
    const parts: AnswerPart[] = [];
    const re = /\[([^\[\]]+)\]/g;
    let last = 0;
    let m: RegExpExecArray | null;
    while ((m = re.exec(response.answer)) !== null) {
      const citation = byLabel.get(m[1]);
      if (!citation) continue;
      if (m.index > last) parts.push({ text: response.answer.slice(last, m.index) });
      parts.push({ citation });
      last = m.index + m[0].length;
    }
    if (last < response.answer.length) parts.push({ text: response.answer.slice(last) });
    return parts;
  }

  isCitation(part: AnswerPart): part is { citation: AskCitation } {
    return 'citation' in part;
  }

  citationTitle(c: AskCitation): string {
    if (c.sourceType === 'pdf') return `Open the course PDF at page ${c.page}`;
    if (c.sourceType === 'transcript') return `Play “${c.lessonTitle}” at ${c.timestamp}`;
    return c.lessonTitle ? `Go to “${c.lessonTitle}”` : 'Course description';
  }

  openCitation(c: AskCitation): void {
    if (!this.course) return;
    const courseId = this.course.id;

    if (c.sourceType === 'pdf' && c.documentId != null) {
      // Open the tab synchronously (popup blockers), then load the protected PDF into it.
      const tab = window.open('', '_blank');
      this.askService.documentFile(courseId, c.documentId).subscribe({
        next: blob => {
          const url = URL.createObjectURL(blob) + (c.page ? `#page=${c.page}` : '');
          if (tab) tab.location.href = url; else window.open(url, '_blank');
        },
        error: () => {
          tab?.close();
          this.askError = 'Could not open the PDF.';
        }
      });
      return;
    }

    if (c.sourceType === 'transcript' && c.lessonId != null) {
      const seconds = c.startSeconds ?? 0;
      if (c.lessonId === this.current?.lesson.id && this.player) {
        this.seek(seconds);
      } else {
        this.router.navigate(['/courses', courseId, 'lessons', c.lessonId], { queryParams: { t: seconds } });
      }
      return;
    }

    if (c.lessonId != null) {
      this.router.navigate(['/courses', courseId, 'lessons', c.lessonId]);
    } else {
      this.router.navigate(['/courses', courseId]);
    }
  }
}
