import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Job {
  id: number;
  title: string;
  description: string;
  location: string;
  subject: string;
  salaryMin: number;
  salaryMax: number;
  status: 'OPEN' | 'EXPIRED' | 'CLOSED';
  createdAt: string;
  expiresAt: string;
}

export interface JobWithScore extends Job {
  matchScore: number;
}

export interface CreateJobRequest {
  title: string;
  description: string;
  location: string;
  subject: string;
  salaryMin: number;
  salaryMax: number;
  expiresAt: string;
}

@Injectable({ providedIn: 'root' })
export class JobService {
  private base = `${environment.apiBase}/jobs`;

  constructor(private http: HttpClient) {}

  private authHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }

  getAllJobs(): Observable<Job[]> {
    return this.http.get<Job[]>(this.base, { headers: this.authHeaders() });
  }

  getJobById(id: number): Observable<Job> {
    return this.http.get<Job>(`${this.base}/${id}`, { headers: this.authHeaders() });
  }

  getRankedJobs(): Observable<JobWithScore[]> {
    return this.http.get<JobWithScore[]>(`${this.base}/ranked`, { headers: this.authHeaders() });
  }

  searchJobs(keyword?: string, location?: string, subject?: string): Observable<Job[]> {
    let params = new HttpParams();
    if (keyword) params = params.set('keyword', keyword);
    if (location) params = params.set('location', location);
    if (subject) params = params.set('subject', subject);
    return this.http.get<Job[]>(`${this.base}/search`, { headers: this.authHeaders(), params });
  }

  createJob(req: CreateJobRequest): Observable<Job> {
    return this.http.post<Job>(this.base, req, { headers: this.authHeaders() });
  }

  updateJob(id: number, req: Partial<CreateJobRequest>): Observable<Job> {
    return this.http.put<Job>(`${this.base}/${id}`, req, { headers: this.authHeaders() });
  }

  deleteJob(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { headers: this.authHeaders() });
  }

  saveJob(jobId: number): Observable<any> {
    return this.http.post(`${this.base}/${jobId}/save`, {}, { headers: this.authHeaders() });
  }

  unsaveJob(jobId: number): Observable<any> {
    return this.http.delete(`${this.base}/${jobId}/save`, { headers: this.authHeaders() });
  }

  getSavedJobs(): Observable<Job[]> {
    return this.http.get<Job[]>(`${this.base}/saved`, { headers: this.authHeaders() });
  }

  askJobRag(
    question: string,
    profile?: { skills?: string; experience?: string; city?: string; targetRole?: string },
    attachment?: File
  ): Observable<{ answer: string; matchedJobs: any[]; modelUsed: string; ocrExtractedText?: string }> {
    if (attachment) {
      const formData = new FormData();
      if (question) formData.append('question', question);
      if (profile) formData.append('profile', JSON.stringify(profile));
      formData.append('file', attachment);

      return this.http.post<{ answer: string; matchedJobs: any[]; modelUsed: string; ocrExtractedText?: string }>(
        `${this.base}/rag/chat-with-file`,
        formData
      );
    }

    return this.http.post<{ answer: string; matchedJobs: any[]; modelUsed: string; ocrExtractedText?: string }>(
      `${this.base}/rag/chat`,
      { question, profile }
    );
  }

  transcribeAudioWithWhisper(audioBlob: Blob): Observable<{ text: string }> {
    const formData = new FormData();
    formData.append('audio', audioBlob, 'voice_query.wav');
    return this.http.post<{ text: string }>(`${this.base}/rag/transcribe-audio`, formData);
  }
}
