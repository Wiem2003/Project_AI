import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Application {
  id: number;
  jobId: number;
  jobTitle?: string;
  teacherId: number;
  teacherName: string;
  motivation: string;
  cvPath: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  appliedAt: string;
  matchScore: number;
}

export interface AiCandidateRanking {
  applicationId: number;
  teacherId: number;
  teacherName: string;
  rank: number;
  aiScore: number;
  recommendation: string;
  summary: string;
  keyStrengths: string[];
  missingSkills: string[];
  justification: string;
  cvSnippet?: string;
}

export interface JobAiRankingResponse {
  jobId: number;
  jobTitle: string;
  totalCandidates: number;
  globalAnalysis: string;
  modelUsed: string;
  rankings: AiCandidateRanking[];
}

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private base = `${environment.apiBase}/applications`;

  constructor(private http: HttpClient) {}

  private authHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }

  /** TUTOR / CANDIDATE: Apply to a job. motivation + cv as multipart form */
  apply(jobId: number, motivation: string, cvFile?: File, certificat?: File): Observable<Application> {
    const form = new FormData();
    form.append('jobId', String(jobId));
    if (motivation) form.append('motivation', motivation);
    if (cvFile) form.append('cv', cvFile);
    if (certificat) form.append('certificat', certificat);

    const email = localStorage.getItem('email') || '';
    if (email) form.append('candidateEmail', email);

    const sessionRaw = localStorage.getItem('session_user');
    if (sessionRaw) {
      try {
        const u = JSON.parse(sessionRaw);
        const name = `${u.firstName || ''} ${u.lastName || ''}`.trim();
        if (name) form.append('candidateName', name);
      } catch (e) {}
    }

    return this.http.post<Application>(this.base, form, { headers: this.authHeaders() });
  }

  /** ADMIN: Get all applications */
  getAllApplications(): Observable<Application[]> {
    return this.http.get<Application[]>(this.base, { headers: this.authHeaders() });
  }

  /** TUTOR: Get my applications */
  getMyApplications(): Observable<Application[]> {
    return this.http.get<Application[]>(`${this.base}/my`, { headers: this.authHeaders() });
  }

  /** ADMIN: Get all applications for a job */
  getApplicationsByJob(jobId: number): Observable<Application[]> {
    return this.http.get<Application[]>(`${this.base}/job/${jobId}`, { headers: this.authHeaders() });
  }

  /** ADMIN: Get AI ranking with Groq for a job offer */
  getAiRankingForJob(jobId: number): Observable<JobAiRankingResponse> {
    return this.http.get<JobAiRankingResponse>(`${this.base}/job/${jobId}/ai-ranking`, { headers: this.authHeaders() });
  }

  /** ADMIN: Update application status (ACCEPTED/REJECTED) */
  updateStatus(applicationId: number, status: 'ACCEPTED' | 'REJECTED'): Observable<Application> {
    return this.http.put<Application>(`${this.base}/${applicationId}/status`, { status }, { headers: this.authHeaders() });
  }

  /** Delete an application */
  deleteApplication(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { headers: this.authHeaders() });
  }
}
