import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApplicationService, Application, JobAiRankingResponse, AiCandidateRanking } from '../../services/application.service';
import { JobService, Job } from '../../services/job.service';
import { MeetingService, ScheduleMeetingRequest } from '../../services/meeting.service';

@Component({
  selector: 'app-admin-applications',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="crud-page">
      <!-- Page Header -->
      <div class="page-header">
        <div>
          <div class="title-with-badge">
            <h1 class="page-title">Applications & Candidatures</h1>
            <span class="ai-powered-pill"><i class="ti ti-sparkles"></i> Propulsé par Groq AI</span>
          </div>
          <p class="page-subtitle">{{ jobId ? 'Évaluation et classement intelligent des candidatures pour cette offre' : 'Gérez et classez les candidatures avec analyse IA des CVs et lettres de motivation' }}</p>
        </div>
        <div class="header-actions">
          <a routerLink="/admin/jobs" class="btn-admin outline" *ngIf="jobId">
            <i class="ti ti-arrow-left"></i> Retour aux Offres
          </a>
          <a routerLink="/admin/jobs" class="btn-admin primary" *ngIf="!jobId">
            <i class="ti ti-briefcase"></i> Toutes les Offres
          </a>
        </div>
      </div>

      <!-- Controls & Filter Bar -->
      <div class="controls-card">
        <div class="selectors-row">
          <!-- Job Selection for AI Ranking -->
          <div class="job-selector-group">
            <label><i class="ti ti-briefcase"></i> Poste / Offre d'emploi :</label>
            <select [(ngModel)]="selectedJobId" (change)="onJobSelected()" class="select-job-control">
              <option [value]="0">Toutes les offres d'emploi ({{ applications.length }} candidatures)</option>
              <option *ngFor="let j of jobs" [value]="j.id">{{ j.title }} ({{ j.location || 'Tunisie' }})</option>
            </select>
          </div>

          <!-- AI Ranking Trigger Button -->
          <div class="ai-button-wrapper">
            <button class="btn-ai-rank" (click)="generateAiRanking()" [disabled]="aiLoading || (!selectedJobId && applications.length === 0)">
              <span *ngIf="!aiLoading" class="btn-content">
                <i class="ti ti-sparkles"></i> Classer les candidats avec l'IA (Groq)
              </span>
              <span *ngIf="aiLoading" class="btn-content">
                <i class="ti ti-loader-2 spin"></i> Analyse IA en cours...
              </span>
            </button>
          </div>
        </div>

        <div class="search-and-view-row">
          <div class="search-box">
            <i class="ti ti-search"></i>
            <input type="text" placeholder="Rechercher par candidat, poste, compétences..." [(ngModel)]="searchTerm" (input)="applyFilter()">
          </div>

          <div class="right-filters">
            <select [(ngModel)]="statusFilter" (change)="applyFilter()" class="filter-select">
              <option value="">Tous les statuts</option>
              <option value="PENDING">En attente (PENDING)</option>
              <option value="ACCEPTED">Accepté (ACCEPTED)</option>
              <option value="REJECTED">Refusé (REJECTED)</option>
            </select>

            <div class="view-toggle">
              <button [class.active]="activeTab === 'table'" (click)="activeTab = 'table'" title="Vue Tableau">
                <i class="ti ti-table"></i>
              </button>
              <button [class.active]="activeTab === 'ai'" (click)="activeTab = 'ai'" [disabled]="!aiRanking" title="Vue Classement IA">
                <i class="ti ti-sparkles"></i> Classement IA
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- AI Loading State -->
      @if (aiLoading) {
        <div class="ai-loading-card">
          <div class="loading-spinner-ring">
            <i class="ti ti-sparkles pulse-icon"></i>
          </div>
          <h3>Analyse et classement par l'IA Groq en cours...</h3>
          <p>Lecture des CVs (PDF), analyse des lettres de motivation et comparaison avec les exigences du poste.</p>
          <div class="progress-bar-indeterminate"></div>
        </div>
      }

      <!-- AI Ranking Results View -->
      @if (activeTab === 'ai' && aiRanking && !aiLoading) {
        <div class="ai-results-container">
          <!-- Global AI Executive Summary -->
          <div class="ai-overview-card">
            <div class="overview-header">
              <div class="ai-badge-header">
                <i class="ti ti-brain"></i> Synthèse Stratégique du Recrutement
              </div>
              <span class="model-tag"><i class="ti ti-cpu"></i> {{ aiRanking.modelUsed || 'Groq Llama-3' }}</span>
            </div>
            <p class="overview-text">{{ aiRanking.globalAnalysis }}</p>
            <div class="overview-stats">
              <div class="stat-item">
                <span class="stat-number">{{ aiRanking.totalCandidates }}</span>
                <span class="stat-label">Candidats Évalués</span>
              </div>
              <div class="stat-item" *ngIf="aiRanking.rankings.length > 0">
                <span class="stat-number top-score">{{ aiRanking.rankings[0].aiScore }}%</span>
                <span class="stat-label">Meilleur Score Match</span>
              </div>
              <div class="stat-item" *ngIf="aiRanking.rankings.length > 0">
                <span class="stat-number top-name">{{ aiRanking.rankings[0].teacherName }}</span>
                <span class="stat-label">Candidat N°1 Recommandé</span>
              </div>
            </div>
          </div>

          <!-- AI Ranking Cards List -->
          <div class="rankings-grid">
            <div *ngFor="let item of aiRanking.rankings; let idx = index" class="ranking-card" [class.rank-gold]="item.rank === 1" [class.rank-silver]="item.rank === 2" [class.rank-bronze]="item.rank === 3">
              <div class="card-rank-badge">
                <span *ngIf="item.rank === 1">🏆 #1</span>
                <span *ngIf="item.rank === 2">🥈 #2</span>
                <span *ngIf="item.rank === 3">🥉 #3</span>
                <span *ngIf="item.rank > 3">#{{ item.rank }}</span>
              </div>

              <div class="card-main">
                <div class="candidate-header-row">
                  <div>
                    <h3 class="candidate-name">{{ item.teacherName || 'Candidat' }}</h3>
                    <p class="candidate-summary" *ngIf="item.summary">{{ item.summary }}</p>
                  </div>
                  <div class="score-pill-box">
                    <div class="score-circle" [style.--score-pct]="(item.aiScore || 0) + '%'">
                      <span>{{ item.aiScore }}%</span>
                    </div>
                    <span class="recommendation-badge" [attr.data-rec]="item.recommendation">
                      {{ item.recommendation }}
                    </span>
                  </div>
                </div>

                <!-- Justification -->
                <div class="justification-box">
                  <div class="box-title"><i class="ti ti-bulb"></i> Analyse de l'IA (CV & Motivation) :</div>
                  <p class="justification-text">{{ item.justification }}</p>
                </div>

                <!-- Strengths and Missing Skills -->
                <div class="skills-row">
                  <div class="skills-col" *ngIf="item.keyStrengths?.length">
                    <span class="skills-title text-success"><i class="ti ti-circle-check"></i> Points Forts :</span>
                    <div class="tags-wrap">
                      <span *ngFor="let s of item.keyStrengths" class="tag strength">{{ s }}</span>
                    </div>
                  </div>
                  <div class="skills-col" *ngIf="item.missingSkills?.length">
                    <span class="skills-title text-warning"><i class="ti ti-alert-triangle"></i> Compétences à vérifier :</span>
                    <div class="tags-wrap">
                      <span *ngFor="let m of item.missingSkills" class="tag missing">{{ m }}</span>
                    </div>
                  </div>
                </div>

                <!-- Footer Quick Actions -->
                <div class="card-actions">
                  <ng-container *ngIf="findApp(item.applicationId) as app">
                    <a [href]="'/uploads/' + app.cvPath" target="_blank" class="btn-action-round cv" title="Télécharger le CV" *ngIf="app.cvPath">
                      <i class="ti ti-file-download"></i> Télécharger CV
                    </a>
                    <div class="decision-buttons">
                      <button class="btn-decision accept" (click)="updateStatus(app, 'ACCEPTED')" [disabled]="app.status === 'ACCEPTED'">
                        <i class="ti ti-check"></i> {{ app.status === 'ACCEPTED' ? 'Accepté' : 'Accepter' }}
                      </button>
                      <button class="btn-decision reject" (click)="updateStatus(app, 'REJECTED')" [disabled]="app.status === 'REJECTED'">
                        <i class="ti ti-x"></i> Refuser
                      </button>
                      <button class="btn-decision schedule" (click)="openSchedule(app)">
                        <i class="ti ti-calendar-plus"></i> Planifier Entretien
                      </button>
                    </div>
                  </ng-container>
                </div>
              </div>
            </div>
          </div>
        </div>
      }

      <!-- Standard Table View -->
      @if (activeTab === 'table' || (!aiRanking && !aiLoading)) {
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th *ngIf="!selectedJobId">Offre d'emploi</th>
                <th>Candidat / Tuteur</th>
                <th>Date de candidature</th>
                <th>Score de Base</th>
                <th>Statut</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let app of filteredApplications">
                <td *ngIf="!selectedJobId">
                  <strong>{{ app.jobTitle || 'Offre #' + app.jobId }}</strong>
                </td>
                <td>
                  <div class="candidate-info">
                    <strong>{{ app.teacherName || 'Candidat #' + app.teacherId }}</strong>
                    <div class="sub">{{ app.motivation | slice:0:90 }}{{ (app.motivation?.length || 0) > 90 ? '...' : '' }}</div>
                  </div>
                </td>
                <td>{{ app.appliedAt | date:'mediumDate' }}</td>
                <td>
                  <div class="score-bar">
                    <div class="score-fill" [style.width.%]="app.matchScore || 0"></div>
                    <span>{{ app.matchScore || 0 }}%</span>
                  </div>
                </td>
                <td>
                  <span class="badge" [attr.data-status]="app.status">{{ app.status }}</span>
                </td>
                <td>
                  <div class="action-buttons">
                    <a [href]="'/uploads/' + app.cvPath" target="_blank" class="btn-action cv" title="Télécharger le CV" *ngIf="app.cvPath">
                      <i class="ti ti-file-download"></i>
                    </a>
                    <button class="btn-action accept" (click)="updateStatus(app, 'ACCEPTED')" title="Accepter" [disabled]="app.status === 'ACCEPTED'">
                      <i class="ti ti-check"></i>
                    </button>
                    <button class="btn-action reject" (click)="updateStatus(app, 'REJECTED')" title="Refuser" [disabled]="app.status === 'REJECTED'">
                      <i class="ti ti-x"></i>
                    </button>
                    <button class="btn-action schedule" (click)="openSchedule(app)" title="Planifier Entretien">
                      <i class="ti ti-calendar-plus"></i>
                    </button>
                  </div>
                </td>
              </tr>
              <tr *ngIf="filteredApplications.length === 0">
                <td [attr.colspan]="selectedJobId ? 5 : 6" class="empty-state">
                  <i class="ti ti-users"></i>
                  <p>Aucune candidature trouvée</p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      }

      <!-- Schedule Meeting Modal -->
      @if (showScheduleModal && selectedApp) {
        <div class="modal-overlay" (click)="closeSchedule()">
          <div class="modal-content" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h3><i class="ti ti-calendar-plus"></i> Planifier un Entretien</h3>
              <button class="modal-close" (click)="closeSchedule()"><i class="ti ti-x"></i></button>
            </div>
            <div class="modal-body">
              <p class="mb-3">Entretien pour <strong>{{ selectedApp.teacherName }}</strong></p>
              <div class="form-group">
                <label>Date & Heure de l'entretien</label>
                <input type="datetime-local" [(ngModel)]="meetingForm.scheduledAt" class="form-control">
              </div>
              <div class="form-group">
                <label>Durée estimée (minutes)</label>
                <input type="number" [(ngModel)]="meetingForm.durationMinutes" min="15" class="form-control">
              </div>
              <div class="form-group">
                <label>Lien Visio (Google Meet / Zoom / Teams)</label>
                <input type="text" [(ngModel)]="meetingForm.meetingLink" placeholder="https://meet.google.com/abc-defg-hij" class="form-control">
              </div>
              <div class="form-group">
                <label>Notes ou Consignes pour le candidat</label>
                <textarea [(ngModel)]="meetingForm.notes" rows="2" class="form-control" placeholder="Préparer une démonstration de cours de 10 min..."></textarea>
              </div>

              @if (scheduleSuccess) {
                <div class="alert success"><i class="ti ti-check"></i> Entretien planifié avec succès ! Le candidat a été notifié.</div>
              }
              @if (scheduleError) {
                <div class="alert error"><i class="ti ti-alert-circle"></i> {{ scheduleError }}</div>
              }
            </div>
            <div class="modal-footer">
              <button class="btn-admin outline" (click)="closeSchedule()">Fermer</button>
              <button class="btn-admin primary" (click)="submitSchedule()" [disabled]="scheduling || !meetingForm.scheduledAt">
                {{ scheduling ? 'Enregistrement...' : 'Confirmer l\'entretien' }}
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .crud-page { animation: fadeIn 0.3s ease; }
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .title-with-badge { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
    .page-title { font-family: var(--font-family); font-size: 28px; font-weight: 700; color: var(--color-primary); margin: 0; }
    .ai-powered-pill { display: inline-flex; align-items: center; gap: 6px; background: linear-gradient(135deg, rgba(99,102,241,0.15), rgba(168,85,247,0.15)); color: #7c3aed; padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 700; border: 1px solid rgba(124,58,237,0.25); }
    .page-subtitle { font-size: 15px; color: var(--color-gray-500); margin: 6px 0 0; }
    .header-actions { display: flex; gap: 12px; }

    /* Controls card */
    .controls-card { background: var(--color-white); border-radius: 20px; padding: 20px 24px; box-shadow: var(--shadow-card); margin-bottom: 24px; display: flex; flex-direction: column; gap: 18px; }
    .selectors-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; }
    .job-selector-group { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 280px; label { font-size: 14px; font-weight: 700; color: var(--color-primary); white-space: nowrap; display: flex; align-items: center; gap: 6px; } }
    .select-job-control { flex: 1; padding: 12px 18px; border: 2px solid rgba(61,61,96,0.12); border-radius: 14px; font-size: 14px; font-weight: 600; color: var(--color-primary); background: #fdfdfd; transition: all 0.2s; &:focus { outline: none; border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,0.15); } }

    /* AI Button */
    .btn-ai-rank { background: linear-gradient(135deg, #6366f1, #8b5cf6, #d946ef); color: #fff; border: none; padding: 13px 26px; border-radius: 14px; font-size: 15px; font-weight: 700; cursor: pointer; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); box-shadow: 0 6px 20px rgba(124,58,237,0.35); display: inline-flex; align-items: center; justify-content: center; min-width: 280px; &:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 10px 25px rgba(124,58,237,0.45); } &:disabled { opacity: 0.55; cursor: not-allowed; } .btn-content { display: flex; align-items: center; gap: 10px; } i { font-size: 19px; } }
    .spin { animation: spin 1s linear infinite; }
    @keyframes spin { 100% { transform: rotate(360deg); } }

    .search-and-view-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
    .search-box { position: relative; flex: 1; min-width: 260px; i { position: absolute; left: 16px; top: 50%; transform: translateY(-50%); color: var(--color-gray-400); font-size: 18px; } input { width: 100%; padding: 12px 16px 12px 46px; border: 2px solid rgba(61,61,96,0.1); border-radius: 14px; font-size: 14px; background: #fff; &:focus { outline: none; border-color: var(--color-primary); } } }
    .right-filters { display: flex; align-items: center; gap: 12px; }
    .filter-select { padding: 0 16px; border: 2px solid rgba(61,61,96,0.1); border-radius: 14px; font-size: 14px; background: #fff; height: 46px; color: var(--color-primary); &:focus { outline: none; border-color: var(--color-primary); } }
    .view-toggle { display: flex; background: rgba(61,61,96,0.06); padding: 4px; border-radius: 12px; gap: 4px; button { border: none; background: transparent; padding: 8px 14px; border-radius: 8px; font-size: 13px; font-weight: 600; color: var(--color-gray-500); cursor: pointer; display: flex; align-items: center; gap: 6px; transition: all 0.2s; &.active { background: #fff; color: var(--color-primary); box-shadow: 0 2px 8px rgba(0,0,0,0.08); } &:disabled { opacity: 0.4; cursor: not-allowed; } } }

    /* AI Loading Card */
    .ai-loading-card { background: var(--color-white); border-radius: 20px; padding: 48px 30px; text-align: center; box-shadow: var(--shadow-card); margin-bottom: 24px; animation: fadeIn 0.3s ease; h3 { font-size: 20px; font-weight: 700; color: var(--color-primary); margin: 16px 0 8px; } p { color: var(--color-gray-500); max-width: 500px; margin: 0 auto 24px; font-size: 14px; } }
    .loading-spinner-ring { width: 70px; height: 70px; margin: 0 auto; border-radius: 50%; background: linear-gradient(135deg, rgba(99,102,241,0.2), rgba(217,70,239,0.2)); display: flex; align-items: center; justify-content: center; position: relative; }
    .pulse-icon { font-size: 32px; color: #8b5cf6; animation: pulse 1.5s ease-in-out infinite; }
    @keyframes pulse { 0%, 100% { transform: scale(1); opacity: 1; } 50% { transform: scale(1.2); opacity: 0.7; } }
    .progress-bar-indeterminate { width: 220px; height: 6px; background: rgba(99,102,241,0.15); border-radius: 3px; margin: 0 auto; overflow: hidden; position: relative; &::after { content: ''; position: absolute; top: 0; left: -100%; width: 100%; height: 100%; background: linear-gradient(90deg, #6366f1, #d946ef); border-radius: 3px; animation: progressAnim 1.6s infinite ease-in-out; } }
    @keyframes progressAnim { 0% { left: -100%; } 100% { left: 100%; } }

    /* AI Overview Card */
    .ai-overview-card { background: linear-gradient(135deg, #1e1b4b, #312e81); color: #fff; border-radius: 20px; padding: 26px 30px; margin-bottom: 24px; box-shadow: 0 10px 30px rgba(49,46,129,0.25); position: relative; overflow: hidden; }
    .overview-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .ai-badge-header { font-size: 17px; font-weight: 700; display: flex; align-items: center; gap: 8px; color: #a5b4fc; }
    .model-tag { font-size: 12px; background: rgba(255,255,255,0.12); padding: 4px 10px; border-radius: 8px; color: #e0e7ff; }
    .overview-text { font-size: 15px; line-height: 1.6; color: #f1f5f9; margin-bottom: 20px; }
    .overview-stats { display: flex; gap: 30px; flex-wrap: wrap; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 16px; }
    .stat-item { display: flex; flex-direction: column; }
    .stat-number { font-size: 24px; font-weight: 800; color: #fff; }
    .top-score { color: #34d399; }
    .top-name { color: #fde047; }
    .stat-label { font-size: 12px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.5px; }

    /* Rankings Grid */
    .rankings-grid { display: flex; flex-direction: column; gap: 20px; margin-bottom: 30px; }
    .ranking-card { background: #fff; border-radius: 20px; box-shadow: var(--shadow-card); display: flex; overflow: hidden; border: 2px solid transparent; transition: all 0.25s ease; &:hover { transform: translateY(-3px); box-shadow: var(--shadow-xl); } &.rank-gold { border-color: #f59e0b; background: linear-gradient(to right, rgba(245,158,11,0.03), #fff); } &.rank-silver { border-color: #94a3b8; } &.rank-bronze { border-color: #b45309; } }
    .card-rank-badge { width: 75px; background: rgba(61,61,96,0.04); display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 800; color: var(--color-primary); border-right: 1px solid rgba(61,61,96,0.06); flex-shrink: 0; }
    .rank-gold .card-rank-badge { background: linear-gradient(180deg, #fef3c7, #fde68a); color: #b45309; }
    .rank-silver .card-rank-badge { background: linear-gradient(180deg, #f1f5f9, #e2e8f0); color: #475569; }
    .rank-bronze .card-rank-badge { background: linear-gradient(180deg, #ffedd5, #fed7aa); color: #9a3412; }

    .card-main { flex: 1; padding: 22px 26px; display: flex; flex-direction: column; gap: 14px; }
    .candidate-header-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; }
    .candidate-name { font-size: 20px; font-weight: 700; color: var(--color-primary); margin: 0 0 4px; }
    .candidate-summary { font-size: 14px; color: var(--color-gray-500); margin: 0; }
    .score-pill-box { display: flex; align-items: center; gap: 12px; }
    .score-circle { width: 50px; height: 50px; border-radius: 50%; background: conic-gradient(#8b5cf6 var(--score-pct), #e2e8f0 0); display: flex; align-items: center; justify-content: center; position: relative; span { width: 40px; height: 40px; border-radius: 50%; background: #fff; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 800; color: #6366f1; } }
    .recommendation-badge { padding: 6px 14px; border-radius: 20px; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; &[data-rec="Fortement recommandé"] { background: rgba(16,185,129,0.15); color: #059669; } &[data-rec="Recommandé"] { background: rgba(59,130,246,0.15); color: #2563eb; } &[data-rec="À évaluer"] { background: rgba(245,158,11,0.15); color: #d97706; } &[data-rec="Non adapté"] { background: rgba(239,68,68,0.15); color: #dc2626; } }

    .justification-box { background: rgba(99,102,241,0.04); border-left: 4px solid #6366f1; padding: 12px 16px; border-radius: 0 12px 12px 0; }
    .box-title { font-size: 12px; font-weight: 700; color: #4338ca; margin-bottom: 4px; display: flex; align-items: center; gap: 6px; }
    .justification-text { font-size: 14px; line-height: 1.5; color: var(--color-primary); margin: 0; }

    .skills-row { display: flex; gap: 24px; flex-wrap: wrap; }
    .skills-col { flex: 1; min-width: 220px; }
    .skills-title { font-size: 12px; font-weight: 700; display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
    .tags-wrap { display: flex; flex-wrap: wrap; gap: 6px; }
    .tag { font-size: 12px; padding: 3px 10px; border-radius: 8px; font-weight: 600; &.strength { background: rgba(16,185,129,0.1); color: #047857; } &.missing { background: rgba(245,158,11,0.1); color: #b45309; } }

    .card-actions { display: flex; justify-content: space-between; align-items: center; border-top: 1px solid rgba(61,61,96,0.06); padding-top: 14px; flex-wrap: wrap; gap: 12px; }
    .decision-buttons { display: flex; gap: 10px; flex-wrap: wrap; }
    .btn-action-round { display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 10px; font-size: 13px; font-weight: 600; text-decoration: none; cursor: pointer; transition: all 0.2s; &.cv { background: rgba(99,102,241,0.1); color: #6366f1; &:hover { background: rgba(99,102,241,0.2); } } }
    .btn-decision { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 10px; font-size: 13px; font-weight: 600; border: none; cursor: pointer; transition: all 0.2s; &.accept { background: rgba(16,185,129,0.12); color: #059669; &:hover:not(:disabled) { background: #10b981; color: #fff; } &:disabled { opacity: 0.5; cursor: not-allowed; } } &.reject { background: rgba(239,68,68,0.12); color: #dc2626; &:hover:not(:disabled) { background: #ef4444; color: #fff; } &:disabled { opacity: 0.5; cursor: not-allowed; } } &.schedule { background: rgba(99,102,241,0.12); color: #6366f1; &:hover { background: #6366f1; color: #fff; } } }

    /* Classic Table Styles */
    .table-container { background: var(--color-white); border-radius: 20px; box-shadow: var(--shadow-card); overflow: hidden; }
    .data-table { width: 100%; border-collapse: collapse; th, td { padding: 16px 20px; text-align: left; vertical-align: middle; } th { background: rgba(61,61,96,0.03); font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: var(--color-gray-500); } td { border-bottom: 1px solid rgba(61,61,96,0.06); font-size: 14px; color: var(--color-primary); } tr:last-child td { border-bottom: none; } }
    .candidate-info { display: flex; flex-direction: column; }
    .sub { font-size: 12px; color: var(--color-gray-500); margin-top: 4px; }
    .score-bar { display: flex; align-items: center; gap: 8px; width: 120px; }
    .score-fill { height: 6px; border-radius: 3px; background: linear-gradient(90deg, var(--color-primary), var(--color-secondary)); flex-shrink: 0; min-width: 4px; }
    .score-bar span { font-size: 12px; font-weight: 600; color: var(--color-primary); }
    .badge { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; &[data-status="PENDING"] { background: rgba(245,158,11,0.1); color: #d97706; } &[data-status="ACCEPTED"] { background: rgba(16,185,129,0.1); color: #10b981; } &[data-status="REJECTED"] { background: rgba(200,70,48,0.1); color: var(--color-cta); } }
    .action-buttons { display: flex; gap: 8px; }
    .btn-action { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; border-radius: 10px; border: none; cursor: pointer; transition: all 0.2s; text-decoration: none; i { font-size: 18px; } &.accept { background: rgba(16,185,129,0.1); color: #10b981; &:hover { background: rgba(16,185,129,0.2); } &:disabled { opacity: 0.4; cursor: not-allowed; } } &.reject { background: rgba(200,70,48,0.1); color: var(--color-cta); &:hover { background: rgba(200,70,48,0.2); } &:disabled { opacity: 0.4; cursor: not-allowed; } } &.schedule { background: rgba(59,130,246,0.1); color: #3b82f6; &:hover { background: rgba(59,130,246,0.2); } } &.cv { background: rgba(99,102,241,0.1); color: #6366f1; &:hover { background: rgba(99,102,241,0.2); } } }
    .empty-state { text-align: center; padding: 60px 20px !important; color: var(--color-gray-400); i { font-size: 48px; margin-bottom: 16px; display: block; } }

    /* Modals & Helpers */
    .btn-admin { display: inline-flex; align-items: center; gap: 8px; padding: 10px 20px; border-radius: 12px; font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.25s ease; border: none; text-decoration: none; i { font-size: 18px; } &.primary { background: linear-gradient(135deg, var(--color-primary), var(--color-secondary)); color: #fff; box-shadow: 0 4px 15px rgba(61,61,96,0.3); &:hover { box-shadow: 0 8px 25px rgba(61,61,96,0.4); transform: translateY(-2px); } } &.outline { background: var(--color-white); color: var(--color-primary); border: 2px solid rgba(61,61,96,0.1); &:hover { border-color: rgba(61,61,96,0.25); background: rgba(61,61,96,0.04); } } }
    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 2000; }
    .modal-content { background: var(--color-white); border-radius: 20px; width: 100%; max-width: 500px; box-shadow: var(--shadow-2xl); }
    .modal-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px; border-bottom: 1px solid rgba(61,61,96,0.08); h3 { font-size: 18px; font-weight: 700; color: var(--color-primary); margin: 0; display: flex; align-items: center; gap: 8px; } }
    .modal-close { width: 32px; height: 32px; border: none; background: rgba(61,61,96,0.06); border-radius: 8px; cursor: pointer; }
    .modal-body { padding: 24px; }
    .modal-footer { display: flex; gap: 12px; justify-content: flex-end; padding: 16px 24px; border-top: 1px solid rgba(61,61,96,0.08); }
    .form-group { margin-bottom: 16px; label { display: block; font-size: 14px; font-weight: 600; color: var(--color-primary); margin-bottom: 8px; } }
    .form-control { width: 100%; padding: 12px 16px; border: 2px solid rgba(61,61,96,0.1); border-radius: 12px; font-size: 14px; &:focus { outline: none; border-color: var(--color-primary); } }
    .alert { display: flex; align-items: center; gap: 10px; padding: 14px 18px; border-radius: 12px; margin-top: 16px; font-size: 14px; i { font-size: 20px; } &.success { background: rgba(16,185,129,0.1); color: #10b981; } &.error { background: rgba(200,70,48,0.1); color: var(--color-cta); } }
    .mb-3 { margin-bottom: 16px; }
    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
  `]
})
export class AdminApplicationsComponent implements OnInit {
  private appService = inject(ApplicationService);
  private jobService = inject(JobService);
  private meetingService = inject(MeetingService);
  private route = inject(ActivatedRoute);

  jobId!: number;
  jobs: Job[] = [];
  selectedJobId: number = 0;

  applications: Application[] = [];
  filteredApplications: Application[] = [];
  searchTerm = '';
  statusFilter = '';

  activeTab: 'table' | 'ai' = 'table';
  aiRanking: JobAiRankingResponse | null = null;
  aiLoading = false;

  showScheduleModal = false;
  selectedApp: Application | null = null;
  scheduling = false;
  scheduleSuccess = false;
  scheduleError = '';

  meetingForm = {
    scheduledAt: '',
    durationMinutes: 60,
    meetingLink: '',
    notes: '',
  };

  ngOnInit(): void {
    this.jobId = +(this.route.snapshot.paramMap.get('jobId') || '0');
    this.selectedJobId = this.jobId;

    // Charger les offres d'emploi pour le sélecteur
    this.jobService.getAllJobs().subscribe({
      next: (jobs: Job[]) => this.jobs = jobs ?? [],
      error: () => this.jobs = []
    });

    this.loadApplications();
  }

  loadApplications(): void {
    if (this.selectedJobId && this.selectedJobId > 0) {
      this.appService.getApplicationsByJob(this.selectedJobId).subscribe({
        next: (apps: Application[]) => {
          this.applications = apps ?? [];
          this.applyFilter();
        },
        error: () => {
          this.applications = [];
          this.filteredApplications = [];
        }
      });
    } else {
      this.appService.getAllApplications().subscribe({
        next: (apps: Application[]) => {
          this.applications = apps ?? [];
          this.applyFilter();
        },
        error: () => {
          this.applications = [];
          this.filteredApplications = [];
        }
      });
    }
  }

  onJobSelected(): void {
    this.aiRanking = null;
    this.activeTab = 'table';
    this.loadApplications();
  }

  generateAiRanking(): void {
    // Si aucun job sélectionné, on prend le premier job ayant des candidatures
    let targetJobId = this.selectedJobId;
    if (!targetJobId && this.applications.length > 0) {
      targetJobId = this.applications[0].jobId;
      this.selectedJobId = targetJobId;
    }

    if (!targetJobId) return;

    this.aiLoading = true;
    this.appService.getAiRankingForJob(targetJobId).subscribe({
      next: (res: JobAiRankingResponse) => {
        this.aiRanking = res;
        this.aiLoading = false;
        this.activeTab = 'ai';
      },
      error: (err: any) => {
        console.error('Erreur classement IA:', err);
        this.aiLoading = false;
        alert('Erreur lors du classement IA Groq: ' + (err?.error?.message || err?.message || 'Vérifiez la connexion API.'));
      }
    });
  }

  findApp(appId: number): Application | undefined {
    return this.applications.find(a => a.id === appId);
  }

  applyFilter(): void {
    const term = (this.searchTerm || '').toLowerCase().trim();
    this.filteredApplications = this.applications.filter(app => {
      const matchSearch = !term ||
        (app.teacherName && app.teacherName.toLowerCase().includes(term)) ||
        (app.jobTitle && app.jobTitle.toLowerCase().includes(term)) ||
        (app.motivation && app.motivation.toLowerCase().includes(term));
      const matchStatus = !this.statusFilter || app.status === this.statusFilter;
      return matchSearch && matchStatus;
    });
  }

  updateStatus(app: Application, status: 'ACCEPTED' | 'REJECTED'): void {
    this.appService.updateStatus(app.id, status).subscribe({
      next: (updated: Application) => {
        app.status = updated.status;
        this.applyFilter();
      }
    });
  }

  openSchedule(app: Application): void {
    this.selectedApp = app;
    this.showScheduleModal = true;
    this.scheduleSuccess = false;
    this.scheduleError = '';
    this.meetingForm = { scheduledAt: '', durationMinutes: 60, meetingLink: '', notes: '' };
  }

  closeSchedule(): void {
    this.showScheduleModal = false;
    this.selectedApp = null;
  }

  submitSchedule(): void {
    if (!this.selectedApp) return;
    this.scheduling = true;
    this.scheduleError = '';
    const userId = Number(localStorage.getItem('userId') || 0);
    const userName = localStorage.getItem('userName') || 'Admin';

    const req: ScheduleMeetingRequest = {
      applicationId: this.selectedApp.id,
      scheduledAt: this.meetingForm.scheduledAt,
      durationMinutes: this.meetingForm.durationMinutes,
      meetingLink: this.meetingForm.meetingLink,
      notes: this.meetingForm.notes,
      assignedToId: userId,
      assignedToName: userName,
    };

    this.meetingService.scheduleMeeting(req).subscribe({
      next: () => { this.scheduling = false; this.scheduleSuccess = true; },
      error: (e: any) => { this.scheduling = false; this.scheduleError = e?.error?.message || 'Failed to schedule meeting.'; }
    });
  }
}
