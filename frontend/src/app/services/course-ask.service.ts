import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type AskSourceType = 'course_desc' | 'lesson_desc' | 'transcript' | 'pdf';

export interface AskCitation {
  /** As written in the answer, without brackets, e.g. "Leçon 3 – 04:12". */
  label: string;
  lessonId?: number;
  lessonTitle?: string;
  lessonOrder?: number;
  sourceType: AskSourceType;
  timestamp?: string;
  startSeconds?: number;
  page?: number;
  documentId?: number;
  url: string;
}

export interface AskResponse {
  answer: string;
  answered: boolean;
  citations: AskCitation[];
}

export interface LessonQuizRequest {
  lessonId: number;
  questionCount: number;
  level?: string;
  questionType: 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'MIXED';
  /** Optional: only the lesson passages about this topic are used (422 if the lesson does not cover it). */
  topic?: string;
}

export interface LessonQuizQuestion {
  text: string;
  type: 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
  options: string[];
  correctAnswer: string;
  explanation: string;
  points: number;
  source: AskCitation;
  sourceExcerpt: string;
}

export interface LessonQuizResponse {
  courseId: number;
  lessonId: number;
  lessonOrder: number;
  lessonTitle: string;
  level?: string;
  title: string;
  description: string;
  suggestedPassingScore: number;
  suggestedTimeLimit: number;
  questions: LessonQuizQuestion[];
  requested: number;
  generated: number;
  selectionMode: 'topic' | 'mmr' | 'all';
  rejected: { reason: string; question: string }[];
}

/** "Ask the course" — RAG assistant answering only from the course content. */
@Injectable({ providedIn: 'root' })
export class CourseAskService {
  private readonly base = environment.courseApiBase;

  constructor(private http: HttpClient) {}

  ask(courseId: number, question: string, lessonId?: number): Observable<AskResponse> {
    return this.http.post<AskResponse>(`${this.base}/${courseId}/ask`, { question, lessonId });
  }

  /** Admin: quiz generated from the indexed passages of one lesson, each question with its source. */
  generateLessonQuiz(courseId: number, request: LessonQuizRequest): Observable<LessonQuizResponse> {
    return this.http.post<LessonQuizResponse>(`${this.base}/${courseId}/admin/rag/quiz`, request);
  }

  /** PDF cited in an answer (protected: fetched with the JWT, then opened as a blob URL). */
  documentFile(courseId: number, documentId: number): Observable<Blob> {
    return this.http.get(`${this.base}/${courseId}/documents/${documentId}/file`, { responseType: 'blob' });
  }
}
