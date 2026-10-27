import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AiService } from '../../services/ai.service';
import { QuizService, QuestionService } from '../../../quiz-feedback/services/quiz-feedback.services';
import { GeneratedQuiz, QuizGenerationRequest } from '../../models/ai.models';
import { CourseDto, CourseService } from '../../../services/course.service';
import { CourseAskService, LessonQuizRequest } from '../../../services/course-ask.service';

/** 'topic': free quiz from a subject (Gemini). 'lesson': quiz grounded in the indexed passages of a lesson (RAG). */
type GeneratorMode = 'topic' | 'lesson';

@Component({
  selector: 'app-quiz-generator',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './quiz-generator.component.html',
  styleUrls: ['./quiz-generator.component.scss']
})
export class QuizGeneratorComponent implements OnInit {
  request: QuizGenerationRequest = {
    topic: '',
    difficulty: 'MEDIUM',
    questionCount: 5,
    questionType: 'MULTIPLE_CHOICE'
  };

  generatedQuiz: GeneratedQuiz | null = null;
  loading = false;
  error = '';
  successMessage = '';
  courseId = 1;
  suggestedTopics: string[] = [];

  // Lesson mode (RAG)
  mode: GeneratorMode = 'topic';
  courses: CourseDto[] = [];
  lessonOptions: { id: number; label: string }[] = [];
  lessonRequest: Omit<LessonQuizRequest, 'lessonId'> & { lessonId: number | null } = {
    lessonId: null,
    questionCount: 5,
    level: '',
    questionType: 'MULTIPLE_CHOICE',
    topic: ''
  };
  /** Result of the server-side checks of the last lesson quiz. */
  lessonStats: { kept: number; rejected: number; mode: string } | null = null;
  readonly cefrLevels = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

  constructor(
    private aiService: AiService,
    private quizService: QuizService,
    private questionService: QuestionService,
    private courseService: CourseService,
    private courseAskService: CourseAskService
  ) {}

  ngOnInit() {
    this.loadSuggestedTopics();
    this.courseService.list().subscribe({
      next: courses => {
        this.courses = courses;
        if (!courses.some(c => c.id === this.courseId) && courses.length > 0) {
          this.courseId = courses[0].id;
        }
        this.onCourseChange();
      },
      error: () => this.courses = []
    });
  }

  setMode(mode: GeneratorMode) {
    this.mode = mode;
    this.error = '';
  }

  /** "Leçon N – title" numbering = position in the whole course, as in the RAG citations. */
  onCourseChange() {
    const course = this.courses.find(c => c.id === Number(this.courseId));
    const modules = [...(course?.modules ?? [])].sort((a, b) => a.orderIndex - b.orderIndex);
    this.lessonOptions = [];
    for (const mod of modules) {
      for (const lesson of [...(mod.lessons ?? [])].sort((a, b) => a.orderIndex - b.orderIndex)) {
        this.lessonOptions.push({ id: lesson.id, label: `Leçon ${this.lessonOptions.length + 1} – ${lesson.title}` });
      }
    }
    this.lessonRequest.lessonId = this.lessonOptions[0]?.id ?? null;
    const level = (course?.level || '').toUpperCase();
    this.lessonRequest.level = this.cefrLevels.includes(level) ? level : '';
  }

  generateFromLesson() {
    if (!this.lessonRequest.lessonId) {
      this.error = 'Choisissez un cours et une leçon';
      return;
    }
    this.loading = true;
    this.error = '';
    this.successMessage = '';
    this.generatedQuiz = null;
    this.lessonStats = null;

    const request: LessonQuizRequest = {
      lessonId: this.lessonRequest.lessonId,
      questionCount: this.lessonRequest.questionCount,
      level: this.lessonRequest.level || undefined,
      questionType: this.lessonRequest.questionType,
      topic: this.lessonRequest.topic?.trim() || undefined
    };
    this.courseAskService.generateLessonQuiz(Number(this.courseId), request).subscribe({
      next: quiz => {
        const modes: Record<string, string> = {
          topic: 'passages proches du thème',
          mmr: 'passages variés couvrant la leçon (MMR)',
          all: 'tous les passages de la leçon'
        };
        this.lessonStats = {
          kept: quiz.questions.length,
          rejected: quiz.rejected?.length ?? 0,
          mode: modes[quiz.selectionMode] ?? quiz.selectionMode
        };
        this.generatedQuiz = {
          title: quiz.title,
          description: quiz.description,
          suggestedPassingScore: quiz.suggestedPassingScore,
          suggestedTimeLimit: quiz.suggestedTimeLimit,
          questions: quiz.questions.map(q => ({
            text: q.text,
            type: q.type,
            options: q.options,
            correctAnswer: q.correctAnswer,
            points: q.points,
            explanation: q.explanation,
            sourceLabel: q.source?.label,
            sourceUrl: q.source?.url,
            sourceExcerpt: q.sourceExcerpt
          }))
        };
        this.loading = false;
      },
      error: err => {
        const msg: string | undefined = err?.error?.message;
        switch (err?.status) {
          case 422: this.error = msg || "Ce thème n'est pas traité dans cette leçon."; break;
          case 409: this.error = 'Cette leçon n\'est pas encore indexée : lancez d\'abord la réindexation du cours.'; break;
          case 401:
          case 403: this.error = 'Réservé aux administrateurs (reconnectez-vous en admin).'; break;
          case 429: this.error = 'Quota Gemini atteint, réessayez plus tard.'; break;
          default: this.error = 'Échec de la génération : ' + (msg || err?.message || 'erreur inconnue');
        }
        this.loading = false;
      }
    });
  }

  loadSuggestedTopics() {
    this.aiService.getSuggestedTopics().subscribe({
      next: (topics) => {
        this.suggestedTopics = topics;
      },
      error: () => {
        this.suggestedTopics = [];
      }
    });
  }

  selectTopic(topic: string) {
    this.request.topic = topic;
  }

  generateQuiz() {
    if (!this.request.topic.trim()) {
      this.error = 'Veuillez entrer un sujet';
      return;
    }

    this.loading = true;
    this.error = '';
    this.successMessage = '';
    this.generatedQuiz = null;
    this.lessonStats = null;

    this.aiService.generateQuiz(this.request).subscribe({
      next: (quiz) => {
        this.generatedQuiz = quiz;
        this.loading = false;
      },
      error: (err) => {
        if (err.status === 0) {
          this.error = 'Impossible de se connecter au service IA. Vérifiez que le service est démarré sur le port 8082.';
        } else if (err.status === 500) {
          this.error = 'Erreur lors de la génération. Vérifiez que la clé API Gemini est configurée.';
        } else {
          this.error = 'Échec de la génération du quiz: ' + (err.error?.message || err.message);
        }
        this.loading = false;
      }
    });
  }

  saveQuiz() {
    if (!this.generatedQuiz) return;

    this.loading = true;
    this.error = '';

    const quiz = {
      title: this.generatedQuiz.title,
      description: this.generatedQuiz.description,
      courseId: this.courseId,
      passingScore: this.generatedQuiz.suggestedPassingScore || 70,
      timeLimit: this.generatedQuiz.suggestedTimeLimit || 15,
      status: 'PUBLISHED' as const
    };

    this.quizService.create(quiz).subscribe({
      next: (savedQuiz) => {
        if (savedQuiz.id) {
          this.saveQuestions(savedQuiz.id);
        }
      },
      error: (err) => {
        this.error = 'Échec de la sauvegarde du quiz: ' + err.message;
        this.loading = false;
      }
    });
  }

  private saveQuestions(quizId: number) {
    if (!this.generatedQuiz) return;

    let saved = 0;
    const total = this.generatedQuiz.questions.length;

    this.generatedQuiz.questions.forEach((q, index) => {
      const question = {
        quizId,
        questionText: q.text,
        type: q.type as 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER',
        options: q.options,
        correctAnswer: q.correctAnswer,
        points: q.points,
        explanation: q.explanation,
        orderIndex: index,
        sourceLabel: q.sourceLabel,
        sourceUrl: q.sourceUrl,
        sourceExcerpt: q.sourceExcerpt
      };

      this.questionService.create(question).subscribe({
        next: () => {
          saved++;
          if (saved === total) {
            this.successMessage = 'Quiz sauvegardé avec succès!';
            this.loading = false;
            setTimeout(() => this.reset(), 2000);
          }
        },
        error: (err) => {
          this.error = 'Erreur lors de la sauvegarde des questions: ' + err.message;
          this.loading = false;
        }
      });
    });
  }

  regenerate() {
    if (this.mode === 'lesson') {
      this.generateFromLesson();
    } else {
      this.generateQuiz();
    }
  }

  reset() {
    this.generatedQuiz = null;
    this.lessonStats = null;
    this.successMessage = '';
    this.error = '';
    this.request = {
      topic: '',
      difficulty: 'MEDIUM',
      questionCount: 5,
      questionType: 'MULTIPLE_CHOICE'
    };
  }
}
