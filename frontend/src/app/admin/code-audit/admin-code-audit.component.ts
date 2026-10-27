import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CodeAuditService,
  CodeAuditResponse,
  PresetFile,
  VulnerabilityReport,
  CodeBenchmarkResponse,
  MultiAgentAuditResponse
} from '../../services/code-audit.service';

@Component({
  selector: 'app-admin-code-audit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="audit-page">
      <!-- Header -->
      <div class="page-header">
        <div>
          <div class="title-with-badges">
            <h1 class="page-title">Détection d'Anomalies & Tests IA</h1>
            <span class="pill-badge groq"><i class="ti ti-sparkles"></i> Groq LPU Powered</span>
            <span class="pill-badge devsecops"><i class="ti ti-shield-check"></i> DevSecOps QA</span>
            <span class="pill-badge rag"><i class="ti ti-database"></i> RAG Augmenté</span>
          </div>
          <p class="page-subtitle">
            Analyse intelligente de vulnérabilités, génération automatisée de tests unitaires, refactoring Clean Code et optimisation des modèles (Prompt Tuning, RAG, Cache, Hyperparamètres).
          </p>
        </div>
      </div>

      <!-- AI Optimizations & Hyperparameters Bar -->
      <div class="opt-card">
        <div class="opt-header" (click)="toggleOptPanel()">
          <div class="opt-title">
            <i class="ti ti-adjustments-horizontal"></i>
            <span>Techniques d'Adaptation & Optimisation IA (Hyperparamètres, RAG, Cache & Benchmarking)</span>
          </div>
          <button class="opt-toggle-btn">
            <i class="ti" [class.ti-chevron-down]="!showOptPanel" [class.ti-chevron-up]="showOptPanel"></i>
          </button>
        </div>

        <div class="opt-body" *ngIf="showOptPanel">
          <div class="opt-grid">
            <!-- Model Selection -->
            <div class="opt-item">
              <label><i class="ti ti-cpu"></i> Modèle LLM :</label>
              <select [(ngModel)]="selectedModel" class="opt-select">
                <option value="openai/gpt-oss-120b">openai/gpt-oss-120b (Recommandé - Précision maximale)</option>
                <option value="openai/gpt-oss-20b">openai/gpt-oss-20b (Compact - Ultra-rapide & économe)</option>
              </select>
              <span class="opt-hint">Choix et comparaison de modèles pour arbitrage latence vs précision</span>
            </div>

            <!-- Temperature Slider -->
            <div class="opt-item">
              <div class="slider-label">
                <label><i class="ti ti-temperature"></i> Température : <strong>{{ temperature }}</strong></label>
                <span class="badge-hint" [class.cold]="temperature <= 0.2">
                  {{ temperature <= 0.2 ? 'Audit Déterministe' : 'Génération Créative' }}
                </span>
              </div>
              <input type="range" [(ngModel)]="temperature" min="0.0" max="1.0" step="0.05" class="opt-slider" />
              <span class="opt-hint">0.1 recommandé pour éviter les hallucinations sur la sécurité</span>
            </div>

            <!-- Max Tokens -->
            <div class="opt-item">
              <label><i class="ti ti-coin"></i> Fenêtre Max Tokens : <strong>{{ maxTokens }}</strong></label>
              <input type="range" [(ngModel)]="maxTokens" min="1000" max="4000" step="250" class="opt-slider" />
              <span class="opt-hint">Réduction du coût et du temps de transfert mémoire</span>
            </div>

            <!-- Switches: Cache & RAG -->
            <div class="opt-item switches-group">
              <div class="switch-row">
                <label class="switch-container">
                  <input type="checkbox" [(ngModel)]="useCache" />
                  <span class="switch-slider"></span>
                </label>
                <div class="switch-text">
                  <strong>Cache Intelligent (SHA-256)</strong>
                  <small>Latence &lt; 5ms et 0 token consommé sur code déjà audité</small>
                </div>
              </div>

              <div class="switch-row">
                <label class="switch-container">
                  <input type="checkbox" [(ngModel)]="enableRag" />
                  <span class="switch-slider"></span>
                </label>
                <div class="switch-text">
                  <strong>RAG (Base de règles OWASP & Clean Code)</strong>
                  <small>Injection dynamique des règles de conformité dans le prompt</small>
                </div>
              </div>
            </div>
          </div>

          <!-- Benchmark CTA Bar -->
          <div class="opt-actions">
            <button class="btn-benchmark" (click)="runBenchmark()" [disabled]="benchmarking || !sourceCode.trim()">
              <i class="ti" [class.ti-scale]="!benchmarking" [class.ti-loader-2]="benchmarking" [class.spin]="benchmarking"></i>
              {{ benchmarking ? 'Benchmark comparatif en cours...' : '⚡ Lancer le Benchmark Comparatif (120B vs 20B)' }}
            </button>
            <span class="bench-note">Compare la latence, le nombre de tokens et le coût financier des deux modèles</span>
          </div>
        </div>
      </div>

      <!-- Benchmark Result Modal/Card -->
      <div *ngIf="benchmarkResult" class="benchmark-card">
        <div class="bench-header">
          <div class="bench-title">
            <i class="ti ti-trophy"></i>
            <h3>Résultats du Benchmark Comparatif A/B</h3>
          </div>
          <button class="btn-close-bench" (click)="benchmarkResult = null"><i class="ti ti-x"></i></button>
        </div>

        <div class="bench-grid">
          <!-- Model A Card -->
          <div class="model-card">
            <div class="model-badge">Modèle Étendu (120B)</div>
            <h4>openai/gpt-oss-120b</h4>
            <div class="metric-row"><span>⏱️ Latence :</span> <strong>{{ benchmarkResult.modelA.executionTimeMs }} ms</strong></div>
            <div class="metric-row"><span>🪙 Tokens :</span> <strong>{{ benchmarkResult.modelA.totalTokens }} tokens</strong></div>
            <div class="metric-row"><span>💰 Coût :</span> <strong>\${{ benchmarkResult.modelA.estimatedCostUsd }}</strong></div>
            <div class="metric-row"><span>🛡️ Note :</span> <strong class="badge-grade">{{ benchmarkResult.modelA.qualityGrade }} ({{ benchmarkResult.modelA.overallScore }}/100)</strong></div>
          </div>

          <!-- Model B Card -->
          <div class="model-card highlight">
            <div class="model-badge compact">Modèle Compact (20B)</div>
            <h4>openai/gpt-oss-20b</h4>
            <div class="metric-row"><span>⏱️ Latence :</span> <strong>{{ benchmarkResult.modelB.executionTimeMs }} ms</strong></div>
            <div class="metric-row"><span>🪙 Tokens :</span> <strong>{{ benchmarkResult.modelB.totalTokens }} tokens</strong></div>
            <div class="metric-row"><span>💰 Coût :</span> <strong>\${{ benchmarkResult.modelB.estimatedCostUsd }}</strong></div>
            <div class="metric-row"><span>🛡️ Note :</span> <strong class="badge-grade">{{ benchmarkResult.modelB.qualityGrade }} ({{ benchmarkResult.modelB.overallScore }}/100)</strong></div>
          </div>

          <!-- Optimization Gains -->
          <div class="gains-card">
            <h4>Gains d'Optimisation Mesurés :</h4>
            <div class="gain-item">
              <span class="gain-val">⚡ {{ benchmarkResult.latencySpeedupRatio }}x</span>
              <span class="gain-lbl">Plus rapide</span>
            </div>
            <div class="gain-item">
              <span class="gain-val">🪙 -{{ benchmarkResult.tokenSavingsPercent }}%</span>
              <span class="gain-lbl">De tokens consommés</span>
            </div>
            <div class="gain-item">
              <span class="gain-val">💰 -{{ benchmarkResult.costSavingsPercent }}%</span>
              <span class="gain-lbl">D'économie financière</span>
            </div>
          </div>
        </div>

        <div class="bench-reco">
          <i class="ti ti-bulb"></i>
          <p><strong>Synthèse d'arbitrage :</strong> {{ benchmarkResult.recommendation }}</p>
        </div>
      </div>

      <!-- Main Grid: Code Input & Action Panel -->
      <div class="editor-card">
        <div class="editor-header">
          <div class="left-controls">
            <label class="control-label"><i class="ti ti-files"></i> Fichier à auditer :</label>
            <select [(ngModel)]="selectedPresetId" (change)="onPresetChange()" class="preset-select">
              <option value="custom">✍️ Code Personnalisé (Saisie libre)</option>
              <option *ngFor="let p of presetFiles" [value]="p.id">{{ p.name }} - {{ p.description }}</option>
            </select>
          </div>

          <div class="right-controls">
            <!-- Bouton Upload de fichier -->
            <input #fileInput type="file" (change)="onFileSelected($event)" accept=".java,.ts,.js,.py,.sql,.html,.css" style="display: none;" />
            <button class="btn-import" (click)="fileInput.click()" title="Importer un fichier depuis votre PC">
              <i class="ti ti-upload"></i> Importer un fichier...
            </button>

            <div class="lang-selector">
              <label>Langage :</label>
              <select [(ngModel)]="language" class="lang-select">
                <option value="java">Java (Spring Boot)</option>
                <option value="typescript">TypeScript (Angular)</option>
                <option value="sql">SQL (MySQL / DDL)</option>
                <option value="python">Python</option>
              </select>
            </div>
            <button class="btn-clear" (click)="clearCode()" title="Effacer le code">
              <i class="ti ti-trash"></i>
            </button>
          </div>
        </div>

        <!-- Quick Presets Bar (1-click selection) -->
        <div class="quick-presets-bar">
          <span class="quick-label"><i class="ti ti-bolt"></i> Accès rapide :</span>
          <button
            *ngFor="let p of presetFiles"
            type="button"
            class="preset-pill"
            [class.active]="selectedPresetId === p.id"
            (click)="selectPreset(p.id)"
          >
            {{ p.name }}
          </button>
          <button type="button" class="preset-pill upload-pill" (click)="fileInput.click()">
            <i class="ti ti-file-upload"></i> Ouvrir un fichier .java / .ts
          </button>
        </div>

        <!-- Code Editor Area with Drag & Drop -->
        <div
          class="code-editor-container"
          [class.drag-over]="isDragging"
          (dragover)="onDragOver($event)"
          (dragleave)="onDragLeave($event)"
          (drop)="onFileDrop($event)"
        >
          <div *ngIf="isDragging" class="drag-overlay">
            <i class="ti ti-cloud-upload"></i>
            <span>Déposez votre fichier source ici pour l'auditer</span>
          </div>
          <textarea
            [(ngModel)]="sourceCode"
            rows="14"
            placeholder="// Cliquez sur un fichier ci-dessus, importez un fichier de votre projet, ou glissez-déposez un fichier .java / .ts ici..."
            class="code-textarea"
            spellcheck="false"
          ></textarea>
        </div>

        <div class="editor-footer">
          <div class="stats-counter">
            <span><i class="ti ti-code"></i> {{ getLineCount() }} lignes</span>
            <span><i class="ti ti-letter-case"></i> {{ sourceCode.length }} caractères</span>
            <span *ngIf="useCache" class="badge-cached-pill"><i class="ti ti-bolt"></i> Cache SHA-256 Actif</span>
            <span *ngIf="enableRag" class="badge-rag-pill"><i class="ti ti-shield-check"></i> RAG Actif</span>
          </div>
          <div class="footer-actions">
            <button class="btn-audit-cta" (click)="runAudit()" [disabled]="loading || multiAgentRunning || !sourceCode.trim()">
              <span *ngIf="!loading" class="btn-inner">
                <i class="ti ti-sparkles"></i> Audit Standard
              </span>
              <span *ngIf="loading" class="btn-inner">
                <i class="ti ti-loader-2 spin"></i> Analyse...
              </span>
            </button>
            <button class="btn-multiagent-cta" (click)="runMultiAgentAudit()" [disabled]="loading || multiAgentRunning || !sourceCode.trim()">
              <span *ngIf="!multiAgentRunning" class="btn-inner">
                <i class="ti ti-users"></i> 🛡️ Délibération Multi-Agents (Threat vs Defense vs QA)
              </span>
              <span *ngIf="multiAgentRunning" class="btn-inner">
                <i class="ti ti-loader-2 spin"></i> Délibération des 3 Agents...
              </span>
            </button>
          </div>
        </div>
      </div>

      <!-- Loading State -->
      @if (loading) {
        <div class="audit-loading-card">
          <div class="scanner-orbit">
            <i class="ti ti-shield-code pulse-icon"></i>
          </div>
          <h3>Analyse DevSecOps & Génération par l'IA en cours...</h3>
          <p>Recherche de vulnérabilités OWASP, détection de récursion ou null pointers, rédaction des tests JUnit 5 et optimisation du code.</p>
          <div class="scanner-bar"></div>
        </div>
      }

      <!-- Results View -->
      @if (auditResult && !loading) {
        <div class="results-container">
          <!-- Score Banner -->
          <div class="score-banner-card">
            <div class="grade-box" [attr.data-grade]="auditResult.qualityGrade">
              <span class="grade-letter">{{ auditResult.qualityGrade }}</span>
              <span class="grade-label">Indice Qualité</span>
            </div>

            <div class="summary-content">
              <div class="summary-top">
                <h2>Rapport d'Audit : {{ auditResult.fileName }}</h2>
                <div class="summary-chips">
                  <span class="chip-score"><i class="ti ti-gauge"></i> {{ auditResult.overallScore }}/100</span>
                  <span class="chip-critical" *ngIf="auditResult.criticalCount > 0">
                    <i class="ti ti-alert-circle"></i> {{ auditResult.criticalCount }} Vulnérabilité(s) majeure(s)
                  </span>
                  <span class="chip-safe" *ngIf="auditResult.criticalCount === 0">
                    <i class="ti ti-shield-check"></i> Aucune faille critique
                  </span>
                  <span class="chip-model"><i class="ti ti-cpu"></i> {{ auditResult.modelUsed }}</span>
                </div>
              </div>
              <p class="summary-text">{{ auditResult.summary }}</p>

              <!-- Live Optimization Metrics Bar -->
              <div class="metrics-bar">
                <div class="metric-pill" [class.cache-hit]="auditResult.cached">
                  <i class="ti" [class.ti-bolt]="auditResult.cached" [class.ti-clock]="!auditResult.cached"></i>
                  <span>Latence : <strong>{{ auditResult.executionTimeMs }} ms</strong></span>
                  <small *ngIf="auditResult.cached">(Cache HIT ⚡ 100% économisé)</small>
                </div>
                <div class="metric-pill">
                  <i class="ti ti-coin"></i>
                  <span>Tokens : <strong>{{ auditResult.totalTokens }}</strong></span>
                  <small>(Prompt: {{ auditResult.promptTokens }}, Sortie: {{ auditResult.completionTokens }})</small>
                </div>
                <div class="metric-pill">
                  <i class="ti ti-cash"></i>
                  <span>Coût : <strong>\${{ auditResult.estimatedCostUsd }}</strong></span>
                </div>
                <div class="metric-pill" *ngIf="auditResult.ragRulesApplied && auditResult.ragRulesApplied.length > 0">
                  <i class="ti ti-shield"></i>
                  <span>RAG : <strong>{{ auditResult.ragRulesApplied.length }} règles injectées</strong></span>
                </div>
              </div>
            </div>
          </div>

          <!-- Tabs Navigation -->
          <div class="tabs-nav">
            <button class="tab-btn" [class.active]="activeTab === 'vulns'" (click)="activeTab = 'vulns'">
              <i class="ti ti-alert-triangle"></i>
              <span>Anomalies & Vulnérabilités</span>
              <span class="tab-badge" [class.red]="auditResult.criticalCount > 0">{{ auditResult.vulnerabilities.length }}</span>
            </button>
            <button class="tab-btn" [class.active]="activeTab === 'tests'" (click)="activeTab = 'tests'">
              <i class="ti ti-test-pipe"></i>
              <span>Tests Assistés par IA (JUnit 5)</span>
            </button>
            <button class="tab-btn" [class.active]="activeTab === 'refactor'" (click)="activeTab = 'refactor'">
              <i class="ti ti-wand"></i>
              <span>Recommandations de Refactoring</span>
            </button>
            <button class="tab-btn" [class.active]="activeTab === 'academic'" (click)="activeTab = 'academic'">
              <i class="ti ti-school"></i>
              <span>Fiche Synthèse Académique (Optimisations)</span>
            </button>
          </div>

          <!-- Tab 1: Vulnerabilities -->
          @if (activeTab === 'vulns') {
            <div class="tab-content">
              <div *ngIf="auditResult.vulnerabilities.length === 0" class="empty-vuln-state">
                <i class="ti ti-shield-check-filled text-success"></i>
                <h3>Code Robuste et Sécurisé</h3>
                <p>Aucune anomalie ou vulnérabilité critique n'a été détectée dans cet extrait de code.</p>
              </div>

              <div class="vulns-list" *ngIf="auditResult.vulnerabilities.length > 0">
                <div *ngFor="let v of auditResult.vulnerabilities" class="vuln-card" [attr.data-severity]="v.severity">
                  <div class="vuln-header">
                    <div class="vuln-title-group">
                      <span class="severity-badge" [attr.data-severity]="v.severity">{{ v.severity }}</span>
                      <h4 class="vuln-type">{{ v.type }}</h4>
                    </div>
                    <span class="line-badge"><i class="ti ti-map-pin"></i> {{ v.lineRange }}</span>
                  </div>

                  <p class="vuln-desc">{{ v.description }}</p>

                  <div class="remediation-box">
                    <div class="remed-title"><i class="ti ti-tool"></i> Recommandation de correction :</div>
                    <p class="remed-text">{{ v.remediation }}</p>
                    <div class="patch-snippet" *ngIf="v.suggestedPatch">
                      <code>{{ v.suggestedPatch }}</code>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          }

          <!-- Tab 2: AI Generated Unit Tests -->
          @if (activeTab === 'tests') {
            <div class="tab-content">
              <div class="code-action-bar">
                <div class="action-info">
                  <i class="ti ti-check-circle"></i>
                  <span>Suite de tests unitaires prête à être intégrée dans <code>src/test/java</code></span>
                </div>
                <button class="btn-copy" (click)="copyCode(auditResult.generatedTests)">
                  <i class="ti" [class.ti-copy]="!copiedTest" [class.ti-check]="copiedTest"></i>
                  {{ copiedTest ? 'Copié !' : 'Copier le Test' }}
                </button>
              </div>
              <div class="code-box-wrapper">
                <pre class="code-block"><code>{{ auditResult.generatedTests }}</code></pre>
              </div>
            </div>
          }

          <!-- Tab 3: Refactored Code -->
          @if (activeTab === 'refactor') {
            <div class="tab-content">
              <!-- Explanation Box -->
              <div class="refactor-explanation-card">
                <div class="box-icon"><i class="ti ti-sparkles"></i></div>
                <div>
                  <h4>Gains Architecturaux & Clean Code apportés :</h4>
                  <p>{{ auditResult.refactoringExplanation }}</p>
                </div>
              </div>

              <div class="code-action-bar">
                <div class="action-info">
                  <i class="ti ti-code"></i>
                  <span>Version optimisée respectant les principes SOLID</span>
                </div>
                <button class="btn-copy" (click)="copyCode(auditResult.refactoredCode)">
                  <i class="ti" [class.ti-copy]="!copiedRefactor" [class.ti-check]="copiedRefactor"></i>
                  {{ copiedRefactor ? 'Copié !' : 'Copier le Code Refactorisé' }}
                </button>
              </div>
              <div class="code-box-wrapper">
                <pre class="code-block"><code>{{ auditResult.refactoredCode }}</code></pre>
              </div>
            </div>
          }

          <!-- Tab 4: Academic Synthesis / Professor's Requirements -->
          @if (activeTab === 'academic') {
            <div class="tab-content academic-tab">
              <div class="academic-intro">
                <h3><i class="ti ti-certificate"></i> Synthèse des Techniques d'Adaptation & Optimisation Mises en Œuvre</h3>
                <p>Ce volet détaille les techniques appliquées dans le projet conformément aux exigences d'optimisation et d'adaptation des modèles de langage (LLM).</p>
              </div>

              <div class="tech-grid">
                <!-- 1. Prompt Tuning -->
                <div class="tech-card">
                  <div class="tech-icon"><i class="ti ti-terminal-2"></i></div>
                  <h4>1. Prompt Tuning & Engineering</h4>
                  <ul>
                    <li><strong>Persona & Rôle :</strong> Injonction système définissant un rôle d'expert Principal DevSecOps.</li>
                    <li><strong>Few-Shot & In-Context :</strong> Structuration d'exemples de vulnérabilités pour calibrer la réponse.</li>
                    <li><strong>Contraintes Négatives & JSON Schema :</strong> Forçage strict d'un format JSON sans texte libre pour éliminer le besoin de post-parsing lourd.</li>
                  </ul>
                </div>

                <!-- 2. RAG -->
                <div class="tech-card">
                  <div class="tech-icon"><i class="ti ti-database"></i></div>
                  <h4>2. RAG (Retrieval-Augmented Generation)</h4>
                  <ul>
                    <li><strong>Base de Connaissances Vectorielle/Contextuelle :</strong> Injection dynamique des règles OWASP Top 10 (A01 à A09) et des patterns Clean Architecture.</li>
                    <li><strong>Avantage :</strong> Aucune hallucination sur les standards de sécurité sans avoir à ré-entraîner le modèle.</li>
                    <li><strong>Dans le projet :</strong> Module RAG actif pour le matching des offres et l'audit de sécurité.</li>
                  </ul>
                </div>

                <!-- 3. Fine-Tuning vs RAG -->
                <div class="tech-card">
                  <div class="tech-icon"><i class="ti ti-adjustments"></i></div>
                  <h4>3. Fine-Tuning vs RAG (Arbitrage)</h4>
                  <ul>
                    <li><strong>Pourquoi RAG + Prompting ici ?</strong> Les règles de sécurité évoluent rapidement ; RAG permet une mise à jour instantanée sans coût d'entraînement GPU (LoRA/QLoRA).</li>
                    <li><strong>Cas du Fine-Tuning :</strong> Pertinent uniquement si l'on souhaitait modifier la syntaxe native du modèle pour un DSL propriétaire.</li>
                  </ul>
                </div>

                <!-- 4. Hyperparamètres -->
                <div class="tech-card">
                  <div class="tech-icon"><i class="ti ti-sliders"></i></div>
                  <h4>4. Réglage des Hyperparamètres</h4>
                  <ul>
                    <li><strong>Température (0.1) :</strong> Faible entropie assurant un audit déterministe et reproductible des failles de sécurité.</li>
                    <li><strong>Top-P (0.95) :</strong> Échantillonnage par noyau restreint pour exclure les tokens atypiques.</li>
                    <li><strong>Max Tokens (3500) :</strong> Calibrage précis pour autoriser le code refactorisé sans gaspillage mémoire.</li>
                  </ul>
                </div>

                <!-- 5. Cache SHA-256 -->
                <div class="tech-card">
                  <div class="tech-icon"><i class="ti ti-bolt"></i></div>
                  <h4>5. Cache Déterministe SHA-256</h4>
                  <ul>
                    <li><strong>Latence réduite de 3 500 ms à 2 ms</strong> (&gt;1 700x plus rapide).</li>
                    <li><strong>Consommation de tokens :</strong> 0 token consommé (100% d'économie).</li>
                    <li><strong>Coût financier :</strong> 0.00 $ sur les ré-analyses.</li>
                  </ul>
                </div>

                <!-- 6. Réduction des Coûts & Tokens -->
                <div class="tech-card">
                  <div class="tech-icon"><i class="ti ti-currency-dollar"></i></div>
                  <h4>6. Réduction des Coûts & Inférence LPU</h4>
                  <ul>
                    <li><strong>Requête Tripartite Unifiée :</strong> 1 seul appel API pour Vulnérabilités + Tests + Refactoring (divise par 3 les tokens d'entrée).</li>
                    <li><strong>Architecture LPU Groq :</strong> Débit supérieur à 300 tokens/sec contre 30 tokens/sec sur GPU conventionnel.</li>
                  </ul>
                </div>
              </div>
            </div>
          }
        </div>
      }

      <!-- Multi-Agent Loading State -->
      <div *ngIf="multiAgentRunning" class="audit-loading-card multiagent-loading">
        <div class="scanner-orbit agents-orbit">
          <i class="ti ti-users-group pulse-icon"></i>
        </div>
        <h3>Délibération du Système Multi-Agents DevSecOps en cours...</h3>
        <p>1. L'Agent Threat Modeler (Red Team) audite les failles &rarr; 2. L'Agent Defense Architect (Blue Team) durcit et patche &rarr; 3. L'Agent QA Verifier (Green Team) certifie avec JUnit 5.</p>
        <div class="agent-step-pills">
          <span class="step-pill active"><i class="ti ti-shield-alert"></i> 1. Threat Modeling</span>
          <span class="step-arrow">&rarr;</span>
          <span class="step-pill active"><i class="ti ti-shield-code"></i> 2. Defense Hardening</span>
          <span class="step-arrow">&rarr;</span>
          <span class="step-pill active"><i class="ti ti-certificate"></i> 3. QA Certification</span>
        </div>
      </div>

      <!-- Multi-Agent Deliberation Result View -->
      <div *ngIf="multiAgentResult && !multiAgentRunning" class="multiagent-results-card">
        <!-- Top Certification Banner -->
        <div class="certif-banner">
          <div class="certif-badge-group">
            <div class="certif-seal">
              <i class="ti ti-rosette-discount-check"></i>
            </div>
            <div>
              <div class="certif-status">{{ multiAgentResult.certificationStatus }}</div>
              <div class="certif-title">Certification de Sécurité DevSecOps par Consensus Multi-Agents</div>
              <div class="certif-metrics">
                <span><i class="ti ti-clock"></i> Délibération : {{ multiAgentResult.executionTimeMs }} ms</span>
                <span><i class="ti ti-coin"></i> Tokens : {{ multiAgentResult.totalTokens }}</span>
                <span><i class="ti ti-cpu"></i> Modèle : {{ multiAgentResult.modelUsed }}</span>
              </div>
            </div>
          </div>
          <div class="final-score-box">
            <span class="score-val">{{ multiAgentResult.finalSecurityScore }}/100</span>
            <span class="score-lbl">Score Post-Patch</span>
          </div>
        </div>

        <!-- Consensus Callout -->
        <div class="consensus-box">
          <div class="consensus-icon"><i class="ti ti-speakerphone"></i></div>
          <div>
            <h4>Synthèse du Consensus des 3 Agents :</h4>
            <p>{{ multiAgentResult.consensusSummary }}</p>
          </div>
        </div>

        <!-- 3 Agents Tabs -->
        <div class="agents-tabs">
          <button class="agent-tab red" [class.active]="activeAgentSubTab === 'attacker'" (click)="activeAgentSubTab = 'attacker'">
            <i class="ti ti-shield-alert"></i>
            <span>Agent 1 : Threat Modeler (Audit Offensif)</span>
            <span class="agent-badge red">{{ multiAgentResult.threatAssessment.threatLevel }}</span>
          </button>
          <button class="agent-tab blue" [class.active]="activeAgentSubTab === 'defender'" (click)="activeAgentSubTab = 'defender'">
            <i class="ti ti-shield-code"></i>
            <span>Agent 2 : Defense Architect (Durcissement & Patch)</span>
            <span class="agent-badge blue">HARDENED</span>
          </button>
          <button class="agent-tab green" [class.active]="activeAgentSubTab === 'verifier'" (click)="activeAgentSubTab = 'verifier'">
            <i class="ti ti-test-pipe"></i>
            <span>Agent 3 : QA Verifier (Tests & Certification)</span>
            <span class="agent-badge green">{{ multiAgentResult.qaVerification.robustnessScore }}/100</span>
          </button>
        </div>

        <!-- Agent 1 Content -->
        <div *ngIf="activeAgentSubTab === 'attacker'" class="agent-content-box">
          <div class="agent-role-intro red">
            <i class="ti ti-target"></i>
            <div>
              <strong>Rôle de l'Agent 1 (Red Team / Threat Modeler) :</strong>
              <p>{{ multiAgentResult.threatAssessment.agentRationale }}</p>
            </div>
          </div>

          <div class="meta-row">
            <div><strong>Niveau de Risque :</strong> <span class="tag-alert">{{ multiAgentResult.threatAssessment.threatLevel }}</span></div>
            <div><strong>Classification STRIDE :</strong> <span class="tag-stride">{{ multiAgentResult.threatAssessment.strideCategory }}</span></div>
          </div>

          <h4 class="sub-h4">Vecteurs de Vulnérabilités Identifiés :</h4>
          <ul class="vectors-list">
            <li *ngFor="let vec of multiAgentResult.threatAssessment.threatVectors">
              <i class="ti ti-alert-circle text-danger"></i> {{ vec }}
            </li>
          </ul>

          <div class="impact-card">
            <strong>Impact Théorique Évalué :</strong>
            <p>{{ multiAgentResult.threatAssessment.theoreticalImpact }}</p>
          </div>
        </div>

        <!-- Agent 2 Content -->
        <div *ngIf="activeAgentSubTab === 'defender'" class="agent-content-box">
          <div class="agent-role-intro blue">
            <i class="ti ti-shield"></i>
            <div>
              <strong>Rôle de l'Agent 2 (Blue Team / Defense Architect) :</strong>
              <p>{{ multiAgentResult.remediationDefense.agentRationale }}</p>
            </div>
          </div>

          <div class="strategy-banner">
            <strong>Stratégie de Défense & Durcissement :</strong>
            <p>{{ multiAgentResult.remediationDefense.defenseStrategy }}</p>
          </div>

          <div class="tags-group">
            <span class="harden-tag" *ngFor="let hm of multiAgentResult.remediationDefense.hardeningMeasures"><i class="ti ti-check"></i> {{ hm }}</span>
            <span class="solid-tag" *ngFor="let sp of multiAgentResult.remediationDefense.solidPrinciples"><i class="ti ti-sparkles"></i> {{ sp }}</span>
          </div>

          <div class="code-action-bar">
            <div class="action-info"><i class="ti ti-code"></i><span>Code Source Durci & Patché par l'Agent 2</span></div>
            <button class="btn-copy" (click)="copyCustomCode(multiAgentResult.remediationDefense.patchedCode, 'patch')">
              <i class="ti" [class.ti-copy]="!copiedPatchCode" [class.ti-check]="copiedPatchCode"></i>
              {{ copiedPatchCode ? 'Copié !' : 'Copier le Patch' }}
            </button>
          </div>
          <div class="code-box-wrapper">
            <pre class="code-block"><code>{{ multiAgentResult.remediationDefense.patchedCode }}</code></pre>
          </div>
        </div>

        <!-- Agent 3 Content -->
        <div *ngIf="activeAgentSubTab === 'verifier'" class="agent-content-box">
          <div class="agent-role-intro green">
            <i class="ti ti-rosette"></i>
            <div>
              <strong>Rôle de l'Agent 3 (Green Team / QA & Compliance Verifier) :</strong>
              <p>{{ multiAgentResult.qaVerification.agentRationale }}</p>
            </div>
          </div>

          <div class="verdict-banner">
            <div>
              <strong>Verdict de Robustesse :</strong> <span class="verdict-tag">{{ multiAgentResult.qaVerification.verificationVerdict }}</span>
            </div>
            <div>
              <strong>Risque Résiduel :</strong> {{ multiAgentResult.qaVerification.residualRiskAssessment }}
            </div>
          </div>

          <div class="code-action-bar">
            <div class="action-info"><i class="ti ti-check-circle"></i><span>Suite de Tests Unitaires JUnit 5 pour certifier le patch</span></div>
            <button class="btn-copy" (click)="copyCustomCode(multiAgentResult.qaVerification.verificationTests, 'tests')">
              <i class="ti" [class.ti-copy]="!copiedVerifierTests" [class.ti-check]="copiedVerifierTests"></i>
              {{ copiedVerifierTests ? 'Copié !' : 'Copier les Tests' }}
            </button>
          </div>
          <div class="code-box-wrapper">
            <pre class="code-block"><code>{{ multiAgentResult.qaVerification.verificationTests }}</code></pre>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .audit-page { animation: fadeIn 0.3s ease; }
    .page-header { margin-bottom: 20px; }
    .title-with-badges { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
    .page-title { font-size: 28px; font-weight: 800; color: var(--color-primary); margin: 0; }
    .pill-badge { display: inline-flex; align-items: center; gap: 6px; padding: 5px 12px; border-radius: 20px; font-size: 12px; font-weight: 700; }
    .pill-badge.groq { background: linear-gradient(135deg, rgba(234,88,12,0.12), rgba(249,115,22,0.12)); color: #ea580c; border: 1px solid rgba(234,88,12,0.25); }
    .pill-badge.devsecops { background: linear-gradient(135deg, rgba(16,185,129,0.12), rgba(5,150,105,0.12)); color: #059669; border: 1px solid rgba(16,185,129,0.25); }
    .pill-badge.rag { background: linear-gradient(135deg, rgba(99,102,241,0.12), rgba(124,58,237,0.12)); color: #6366f1; border: 1px solid rgba(99,102,241,0.25); }
    .page-subtitle { font-size: 14.5px; color: var(--color-gray-500); margin: 0; max-width: 900px; line-height: 1.5; }

    /* Optimizations & Hyperparameters Bar */
    .opt-card { background: #fff; border-radius: 16px; border: 1px solid rgba(99,102,241,0.2); box-shadow: var(--shadow-card); margin-bottom: 20px; overflow: hidden; }
    .opt-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 20px; background: linear-gradient(135deg, rgba(99,102,241,0.05), rgba(168,85,247,0.05)); cursor: pointer; user-select: none; }
    .opt-title { display: flex; align-items: center; gap: 10px; font-size: 14px; font-weight: 700; color: #4338ca; i { font-size: 18px; color: #6366f1; } }
    .opt-toggle-btn { border: none; background: transparent; color: #6366f1; cursor: pointer; font-size: 18px; display: flex; align-items: center; }
    .opt-body { padding: 20px 24px; border-top: 1px solid rgba(99,102,241,0.12); background: #fafafa; }
    .opt-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 20px; margin-bottom: 18px; }
    .opt-item { display: flex; flex-direction: column; gap: 6px; }
    .opt-item label { font-size: 13px; font-weight: 700; color: #334155; display: flex; align-items: center; gap: 6px; }
    .opt-select { padding: 8px 12px; border: 1px solid rgba(61,61,96,0.2); border-radius: 8px; font-size: 13px; background: #fff; font-weight: 600; color: #1e293b; }
    .opt-slider { width: 100%; accent-color: #6366f1; cursor: pointer; }
    .slider-label { display: flex; justify-content: space-between; align-items: center; }
    .badge-hint { font-size: 11px; padding: 2px 8px; border-radius: 6px; font-weight: 700; background: #fed7aa; color: #9a3412; &.cold { background: #dbeafe; color: #1e40af; } }
    .opt-hint { font-size: 11.5px; color: #64748b; line-height: 1.3; }
    .switches-group { display: flex; flex-direction: column; gap: 12px; justify-content: center; }
    .switch-row { display: flex; align-items: center; gap: 12px; }
    .switch-container { position: relative; display: inline-block; width: 40px; height: 22px; flex-shrink: 0; input { opacity: 0; width: 0; height: 0; } }
    .switch-slider { position: absolute; cursor: pointer; inset: 0; background-color: #cbd5e1; transition: 0.3s; border-radius: 34px; &:before { position: absolute; content: ""; height: 16px; width: 16px; left: 3px; bottom: 3px; background-color: white; transition: 0.3s; border-radius: 50%; } }
    input:checked + .switch-slider { background-color: #6366f1; }
    input:checked + .switch-slider:before { transform: translateX(18px); }
    .switch-text { display: flex; flex-direction: column; font-size: 12.5px; strong { color: #1e293b; } small { color: #64748b; font-size: 11px; } }

    .opt-actions { display: flex; align-items: center; gap: 16px; padding-top: 14px; border-top: 1px solid rgba(61,61,96,0.08); flex-wrap: wrap; }
    .btn-benchmark { display: inline-flex; align-items: center; gap: 8px; padding: 9px 18px; border-radius: 10px; font-size: 13px; font-weight: 700; background: #0f172a; color: #38bdf8; border: 1px solid #38bdf8; cursor: pointer; transition: all 0.2s; &:hover:not(:disabled) { background: #1e293b; box-shadow: 0 4px 14px rgba(56,189,248,0.25); } &:disabled { opacity: 0.6; cursor: not-allowed; } }
    .bench-note { font-size: 12px; color: #64748b; font-style: italic; }

    /* Benchmark Result Card */
    .benchmark-card { background: #0f172a; color: #fff; border-radius: 18px; padding: 22px 26px; margin-bottom: 22px; box-shadow: 0 8px 30px rgba(15,23,42,0.4); border: 1px solid #334155; animation: fadeIn 0.3s ease; }
    .bench-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 12px; }
    .bench-title { display: flex; align-items: center; gap: 10px; i { font-size: 22px; color: #fbbf24; } h3 { margin: 0; font-size: 18px; font-weight: 800; color: #fff; } }
    .btn-close-bench { border: none; background: transparent; color: #94a3b8; font-size: 18px; cursor: pointer; &:hover { color: #fff; } }
    .bench-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; margin-bottom: 16px; }
    .model-card { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); border-radius: 14px; padding: 18px; position: relative; h4 { margin: 10px 0 14px; font-size: 14px; font-weight: 700; color: #94a3b8; font-family: monospace; } &.highlight { border-color: #38bdf8; background: rgba(56,189,248,0.06); } }
    .model-badge { display: inline-block; font-size: 11px; font-weight: 800; text-transform: uppercase; padding: 3px 8px; border-radius: 6px; background: #6366f1; color: #fff; &.compact { background: #0284c7; } }
    .metric-row { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 8px; span { color: #94a3b8; } strong { color: #fff; } .badge-grade { color: #34d399; } }
    .gains-card { background: linear-gradient(135deg, rgba(16,185,129,0.1), rgba(6,182,212,0.1)); border: 1px solid rgba(16,185,129,0.3); border-radius: 14px; padding: 18px; display: flex; flex-direction: column; justify-content: center; h4 { margin: 0 0 12px; font-size: 14px; font-weight: 700; color: #34d399; } }
    .gain-item { display: flex; align-items: baseline; gap: 8px; margin-bottom: 8px; }
    .gain-val { font-size: 18px; font-weight: 900; color: #38bdf8; }
    .gain-lbl { font-size: 12.5px; color: #cbd5e1; }
    .bench-reco { background: rgba(255,255,255,0.05); padding: 12px 16px; border-radius: 10px; display: flex; align-items: center; gap: 10px; font-size: 13px; color: #e2e8f0; i { color: #fbbf24; font-size: 20px; flex-shrink: 0; } p { margin: 0; line-height: 1.4; } }

    /* Editor Card */
    .editor-card { background: #fff; border-radius: 20px; box-shadow: var(--shadow-card); overflow: hidden; margin-bottom: 24px; border: 1px solid rgba(61,61,96,0.06); }
    .editor-header { display: flex; justify-content: space-between; align-items: center; padding: 18px 24px; background: #f8fafc; border-bottom: 1px solid rgba(61,61,96,0.08); flex-wrap: wrap; gap: 14px; }
    .left-controls { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 320px; }
    .control-label { font-size: 13px; font-weight: 700; color: var(--color-primary); white-space: nowrap; display: flex; align-items: center; gap: 6px; }
    .preset-select { flex: 1; padding: 10px 16px; border: 2px solid rgba(61,61,96,0.12); border-radius: 12px; font-size: 13px; font-weight: 600; background: #fff; color: var(--color-primary); &:focus { outline: none; border-color: #6366f1; } }
    .right-controls { display: flex; align-items: center; gap: 12px; }
    .lang-selector { display: flex; align-items: center; gap: 8px; label { font-size: 12px; font-weight: 600; color: var(--color-gray-500); } }
    .lang-select { padding: 8px 12px; border: 1px solid rgba(61,61,96,0.15); border-radius: 10px; font-size: 13px; font-weight: 600; background: #fff; }
    .btn-clear { width: 38px; height: 38px; border-radius: 10px; border: 1px solid rgba(61,61,96,0.15); background: #fff; cursor: pointer; color: var(--color-gray-500); display: flex; align-items: center; justify-content: center; transition: all 0.2s; &:hover { color: #ef4444; border-color: #ef4444; background: rgba(239,68,68,0.05); } }
    .btn-import { display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border: 1px solid #6366f1; background: rgba(99,102,241,0.08); color: #4338ca; border-radius: 10px; font-size: 13px; font-weight: 700; cursor: pointer; transition: all 0.2s; &:hover { background: #6366f1; color: #fff; } i { font-size: 16px; } }

    /* Quick Presets Bar */
    .quick-presets-bar { display: flex; align-items: center; gap: 10px; padding: 12px 24px; background: #f1f5f9; border-bottom: 1px solid rgba(61,61,96,0.06); flex-wrap: wrap; }
    .quick-label { font-size: 12px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; color: #64748b; display: flex; align-items: center; gap: 5px; i { color: #f59e0b; } }
    .preset-pill { border: 1px solid rgba(61,61,96,0.15); background: #fff; color: var(--color-primary); padding: 6px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s; display: inline-flex; align-items: center; gap: 5px; &:hover { background: #e0e7ff; border-color: #6366f1; color: #4338ca; } &.active { background: #4f46e5; border-color: #4f46e5; color: #fff; box-shadow: 0 2px 8px rgba(79,70,229,0.3); } }
    .preset-pill.upload-pill { border-style: dashed; border-color: #0284c7; background: rgba(2,132,199,0.06); color: #0284c7; &:hover { background: #0284c7; color: #fff; } }

    /* Code Textarea & Drag and Drop */
    .code-editor-container { position: relative; background: #1e1e2e; transition: all 0.2s; }
    .code-editor-container.drag-over { border: 3px dashed #6366f1; }
    .drag-overlay { position: absolute; inset: 0; background: rgba(30,30,46,0.92); z-index: 10; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; color: #38bdf8; i { font-size: 48px; } span { font-size: 16px; font-weight: 700; } }
    .code-textarea { width: 100%; border: none; background: #1e1e2e; color: #cdd6f4; font-family: 'Fira Code', 'Cascadia Code', Consolas, Monaco, monospace; font-size: 13.5px; line-height: 1.6; padding: 20px 24px; resize: vertical; min-height: 240px; box-sizing: border-box; &:focus { outline: none; } &::placeholder { color: #6c7086; font-style: italic; } }

    .editor-footer { display: flex; justify-content: space-between; align-items: center; padding: 14px 24px; background: #f8fafc; border-top: 1px solid rgba(61,61,96,0.08); flex-wrap: wrap; gap: 14px; }
    .stats-counter { display: flex; gap: 14px; font-size: 12px; font-weight: 600; color: var(--color-gray-500); align-items: center; span { display: flex; align-items: center; gap: 6px; } }
    .badge-cached-pill { background: #dcfce7; color: #15803d; padding: 3px 8px; border-radius: 6px; font-weight: 700; }
    .badge-rag-pill { background: #e0e7ff; color: #4338ca; padding: 3px 8px; border-radius: 6px; font-weight: 700; }
    .btn-audit-cta { background: linear-gradient(135deg, #4f46e5, #7c3aed, #c026d3); color: #fff; border: none; padding: 12px 28px; border-radius: 12px; font-size: 14px; font-weight: 700; cursor: pointer; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); box-shadow: 0 4px 18px rgba(124,58,237,0.35); display: inline-flex; align-items: center; justify-content: center; min-width: 260px; &:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(124,58,237,0.45); } &:disabled { opacity: 0.55; cursor: not-allowed; } .btn-inner { display: flex; align-items: center; gap: 8px; } i { font-size: 18px; } }
    .spin { animation: spin 1s linear infinite; }
    @keyframes spin { 100% { transform: rotate(360deg); } }

    /* Loading state */
    .audit-loading-card { background: #fff; border-radius: 20px; padding: 50px 30px; text-align: center; box-shadow: var(--shadow-card); margin-bottom: 24px; }
    .scanner-orbit { width: 80px; height: 80px; border-radius: 50%; margin: 0 auto 20px; background: rgba(99,102,241,0.1); display: flex; align-items: center; justify-content: center; border: 2px dashed #6366f1; animation: spin 8s linear infinite; }
    .pulse-icon { font-size: 38px; color: #6366f1; animation: pulse 1.5s ease-in-out infinite; }
    @keyframes pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.15); } }
    .scanner-bar { height: 4px; width: 200px; background: linear-gradient(90deg, #6366f1, #a855f7); margin: 20px auto 0; border-radius: 4px; animation: scanAnim 1.5s ease-in-out infinite alternate; }
    @keyframes scanAnim { from { transform: scaleX(0.4); opacity: 0.5; } to { transform: scaleX(1); opacity: 1; } }

    /* Results View */
    .results-container { animation: fadeIn 0.4s ease; }
    .score-banner-card { background: linear-gradient(135deg, #0f172a, #1e1b4b); color: #fff; border-radius: 20px; padding: 26px 30px; display: flex; gap: 28px; align-items: center; box-shadow: 0 10px 30px rgba(15,23,42,0.3); margin-bottom: 24px; flex-wrap: wrap; }
    .grade-box { width: 90px; height: 90px; border-radius: 20px; display: flex; flex-direction: column; align-items: center; justify-content: center; background: rgba(255,255,255,0.1); border: 2px solid rgba(255,255,255,0.2); flex-shrink: 0; &[data-grade="A"] { background: rgba(16,185,129,0.2); border-color: #10b981; .grade-letter { color: #34d399; } } &[data-grade="B"] { background: rgba(59,130,246,0.2); border-color: #3b82f6; .grade-letter { color: #60a5fa; } } &[data-grade="C"] { background: rgba(245,158,11,0.2); border-color: #f59e0b; .grade-letter { color: #fbbf24; } } &[data-grade="D"], &[data-grade="F"] { background: rgba(239,68,68,0.2); border-color: #ef4444; .grade-letter { color: #f87171; } } }
    .grade-letter { font-size: 42px; font-weight: 900; line-height: 1; }
    .grade-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; opacity: 0.8; margin-top: 4px; }
    .summary-content { flex: 1; min-width: 280px; }
    .summary-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; flex-wrap: wrap; margin-bottom: 10px; h2 { font-size: 20px; font-weight: 700; margin: 0; color: #fff; } }
    .summary-chips { display: flex; gap: 8px; flex-wrap: wrap; }
    .chip-score { background: rgba(255,255,255,0.12); padding: 4px 10px; border-radius: 8px; font-size: 12px; font-weight: 700; color: #fff; }
    .chip-critical { background: rgba(239,68,68,0.25); color: #fca5a5; padding: 4px 10px; border-radius: 8px; font-size: 12px; font-weight: 700; border: 1px solid rgba(239,68,68,0.4); }
    .chip-safe { background: rgba(16,185,129,0.25); color: #6ee7b7; padding: 4px 10px; border-radius: 8px; font-size: 12px; font-weight: 700; }
    .chip-model { background: rgba(255,255,255,0.08); padding: 4px 10px; border-radius: 8px; font-size: 12px; color: #94a3b8; }
    .summary-text { font-size: 14px; line-height: 1.6; color: #cbd5e1; margin: 0 0 16px; }

    /* Live Metrics Bar */
    .metrics-bar { display: flex; gap: 12px; flex-wrap: wrap; padding-top: 14px; border-top: 1px solid rgba(255,255,255,0.12); }
    .metric-pill { display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 8px; background: rgba(255,255,255,0.08); font-size: 12px; color: #e2e8f0; i { color: #38bdf8; font-size: 14px; } strong { color: #fff; } small { opacity: 0.75; font-size: 11px; margin-left: 2px; } &.cache-hit { background: rgba(16,185,129,0.2); border: 1px solid rgba(16,185,129,0.4); i { color: #34d399; } strong { color: #6ee7b7; } } }

    /* Tabs */
    .tabs-nav { display: flex; gap: 12px; margin-bottom: 20px; border-bottom: 2px solid rgba(61,61,96,0.06); padding-bottom: 2px; flex-wrap: wrap; }
    .tab-btn { border: none; background: transparent; padding: 12px 18px; border-radius: 12px 12px 0 0; font-size: 14px; font-weight: 700; color: var(--color-gray-500); cursor: pointer; display: inline-flex; align-items: center; gap: 8px; transition: all 0.2s; position: relative; bottom: -2px; border-bottom: 3px solid transparent; i { font-size: 18px; } &:hover { color: var(--color-primary); } &.active { color: #6366f1; border-bottom-color: #6366f1; background: rgba(99,102,241,0.04); } }
    .tab-badge { background: #e2e8f0; color: #475569; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 800; &.red { background: #fee2e2; color: #dc2626; } }

    /* Tab Content - Vulnerabilities */
    .vulns-list { display: flex; flex-direction: column; gap: 16px; margin-bottom: 30px; }
    .vuln-card { background: #fff; border-radius: 16px; padding: 20px 24px; box-shadow: var(--shadow-card); border-left: 5px solid #94a3b8; &[data-severity="CRITICAL"] { border-left-color: #dc2626; } &[data-severity="HIGH"] { border-left-color: #ea580c; } &[data-severity="MEDIUM"] { border-left-color: #d97706; } &[data-severity="LOW"] { border-left-color: #3b82f6; } }
    .vuln-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; flex-wrap: wrap; gap: 10px; }
    .vuln-title-group { display: flex; align-items: center; gap: 10px; }
    .severity-badge { padding: 4px 10px; border-radius: 8px; font-size: 11px; font-weight: 800; text-transform: uppercase; &[data-severity="CRITICAL"] { background: #fee2e2; color: #b91c1c; } &[data-severity="HIGH"] { background: #ffedd5; color: #c2410c; } &[data-severity="MEDIUM"] { background: #fef3c7; color: #b45309; } &[data-severity="LOW"] { background: #dbeafe; color: #1d4ed8; } }
    .vuln-type { font-size: 16px; font-weight: 700; color: var(--color-primary); margin: 0; }
    .line-badge { font-size: 12px; color: var(--color-gray-500); background: #f1f5f9; padding: 4px 8px; border-radius: 6px; font-family: monospace; }
    .vuln-desc { font-size: 14px; line-height: 1.5; color: var(--color-primary); margin: 0 0 14px; }
    .remediation-box { background: #f8fafc; border-radius: 12px; padding: 14px 18px; border: 1px solid rgba(61,61,96,0.06); }
    .remed-title { font-size: 12px; font-weight: 700; color: #4338ca; margin-bottom: 6px; display: flex; align-items: center; gap: 6px; }
    .remed-text { font-size: 13.5px; line-height: 1.5; color: #334155; margin: 0 0 10px; }
    .patch-snippet { background: #0f172a; padding: 10px 14px; border-radius: 8px; code { color: #38bdf8; font-family: 'Fira Code', monospace; font-size: 12.5px; white-space: pre-wrap; } }

    /* Code Action Bar & Code Block */
    .code-action-bar { display: flex; justify-content: space-between; align-items: center; background: #f8fafc; padding: 12px 20px; border-radius: 16px 16px 0 0; border: 1px solid rgba(61,61,96,0.08); border-bottom: none; }
    .action-info { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; color: #475569; i { color: #10b981; font-size: 16px; } code { background: #e2e8f0; padding: 2px 6px; border-radius: 4px; font-size: 12px; } }
    .btn-copy { display: inline-flex; align-items: center; gap: 6px; padding: 7px 14px; border-radius: 8px; font-size: 13px; font-weight: 600; border: 1px solid rgba(61,61,96,0.15); background: #fff; color: var(--color-primary); cursor: pointer; transition: all 0.2s; &:hover { background: #f1f5f9; border-color: #6366f1; color: #6366f1; } }
    .code-box-wrapper { background: #0f172a; border-radius: 0 0 16px 16px; padding: 20px; overflow-x: auto; box-shadow: var(--shadow-card); margin-bottom: 30px; }
    .code-block { margin: 0; color: #f8fafc; font-family: 'Fira Code', Consolas, monospace; font-size: 13px; line-height: 1.6; }

    /* Refactor Explanation Card */
    .refactor-explanation-card { background: linear-gradient(135deg, rgba(99,102,241,0.06), rgba(168,85,247,0.06)); border: 1px solid rgba(99,102,241,0.2); border-radius: 16px; padding: 18px 22px; display: flex; gap: 14px; align-items: flex-start; margin-bottom: 20px; .box-icon { width: 36px; height: 36px; border-radius: 10px; background: #6366f1; color: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 18px; } h4 { margin: 0 0 6px; font-size: 15px; font-weight: 700; color: var(--color-primary); } p { margin: 0; font-size: 14px; line-height: 1.5; color: #475569; } }

    /* Academic Tab */
    .academic-tab { background: #fff; border-radius: 20px; padding: 28px 32px; box-shadow: var(--shadow-card); margin-bottom: 30px; }
    .academic-intro { margin-bottom: 24px; border-bottom: 1px solid rgba(61,61,96,0.08); padding-bottom: 16px; h3 { font-size: 20px; font-weight: 800; color: #1e1b4b; display: flex; align-items: center; gap: 8px; margin: 0 0 6px; i { color: #6366f1; } } p { color: #64748b; font-size: 14px; margin: 0; } }
    .tech-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 20px; }
    .tech-card { background: #f8fafc; border-radius: 14px; padding: 20px; border: 1px solid rgba(61,61,96,0.08); transition: transform 0.2s; &:hover { transform: translateY(-2px); border-color: #6366f1; } .tech-icon { width: 38px; height: 38px; border-radius: 10px; background: #e0e7ff; color: #4338ca; display: flex; align-items: center; justify-content: center; font-size: 18px; margin-bottom: 12px; } h4 { font-size: 16px; font-weight: 800; color: #1e293b; margin: 0 0 10px; } ul { margin: 0; padding-left: 18px; font-size: 13px; color: #475569; line-height: 1.6; li { margin-bottom: 6px; } } }

    /* Multi-Agent Results and Deliberation Styles */
    .footer-actions { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
    .btn-multiagent-cta { background: linear-gradient(135deg, #0f172a, #1e293b, #334155); color: #38bdf8; border: 1px solid #38bdf8; padding: 12px 24px; border-radius: 12px; font-size: 14px; font-weight: 700; cursor: pointer; transition: all 0.3s; box-shadow: 0 4px 18px rgba(56,189,248,0.25); display: inline-flex; align-items: center; justify-content: center; &:hover:not(:disabled) { transform: translateY(-2px); background: #1e293b; box-shadow: 0 8px 24px rgba(56,189,248,0.35); } &:disabled { opacity: 0.55; cursor: not-allowed; } .btn-inner { display: flex; align-items: center; gap: 8px; } }

    .multiagent-loading { border: 2px solid #6366f1; background: #faf5ff; }
    .agents-orbit { border-color: #a855f7; background: rgba(168,85,247,0.1); i { color: #a855f7; } }
    .agent-step-pills { display: flex; align-items: center; justify-content: center; gap: 10px; margin-top: 20px; flex-wrap: wrap; }
    .step-pill { padding: 6px 14px; border-radius: 20px; font-size: 12px; font-weight: 700; background: #e2e8f0; color: #475569; display: inline-flex; align-items: center; gap: 6px; &.active { background: #6366f1; color: #fff; box-shadow: 0 2px 8px rgba(99,102,241,0.3); } }
    .step-arrow { color: #94a3b8; font-weight: bold; }

    .multiagent-results-card { background: #fff; border-radius: 20px; box-shadow: var(--shadow-card); padding: 26px; margin-bottom: 30px; border: 1px solid rgba(99,102,241,0.2); animation: fadeIn 0.4s ease; }
    .certif-banner { background: linear-gradient(135deg, #090d16, #111827); color: #fff; border-radius: 16px; padding: 24px 28px; display: flex; justify-content: space-between; align-items: center; gap: 20px; flex-wrap: wrap; margin-bottom: 20px; border: 1px solid #1f2937; }
    .certif-badge-group { display: flex; align-items: center; gap: 18px; }
    .certif-seal { width: 56px; height: 56px; border-radius: 50%; background: linear-gradient(135deg, #10b981, #059669); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 30px; box-shadow: 0 4px 16px rgba(16,185,129,0.4); flex-shrink: 0; }
    .certif-status { font-size: 12px; font-weight: 800; text-transform: uppercase; letter-spacing: 1px; color: #34d399; margin-bottom: 4px; }
    .certif-title { font-size: 18px; font-weight: 800; color: #fff; margin-bottom: 6px; }
    .certif-metrics { display: flex; gap: 14px; font-size: 12px; color: #94a3b8; span { display: flex; align-items: center; gap: 4px; } }
    .final-score-box { background: rgba(255,255,255,0.06); border: 2px solid #10b981; border-radius: 14px; padding: 12px 20px; text-align: center; }
    .score-val { font-size: 28px; font-weight: 900; color: #34d399; display: block; line-height: 1; }
    .score-lbl { font-size: 10.5px; text-transform: uppercase; letter-spacing: 0.5px; color: #94a3b8; }

    .consensus-box { background: linear-gradient(135deg, rgba(99,102,241,0.06), rgba(168,85,247,0.06)); border: 1px solid rgba(99,102,241,0.25); border-radius: 14px; padding: 18px 22px; display: flex; gap: 16px; align-items: flex-start; margin-bottom: 24px; .consensus-icon { width: 40px; height: 40px; border-radius: 10px; background: #6366f1; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 20px; flex-shrink: 0; } h4 { margin: 0 0 6px; font-size: 15px; font-weight: 800; color: #1e1b4b; } p { margin: 0; font-size: 13.5px; line-height: 1.6; color: #334155; } }

    .agents-tabs { display: flex; gap: 12px; margin-bottom: 20px; border-bottom: 2px solid #f1f5f9; padding-bottom: 2px; flex-wrap: wrap; }
    .agent-tab { border: none; background: transparent; padding: 12px 18px; border-radius: 12px 12px 0 0; font-size: 13.5px; font-weight: 700; color: #64748b; cursor: pointer; display: inline-flex; align-items: center; gap: 8px; border-bottom: 3px solid transparent; transition: all 0.2s; i { font-size: 16px; } &:hover { color: #1e293b; } }
    .agent-tab.red.active { color: #dc2626; border-bottom-color: #dc2626; background: rgba(220,38,38,0.04); }
    .agent-tab.blue.active { color: #2563eb; border-bottom-color: #2563eb; background: rgba(37,99,235,0.04); }
    .agent-tab.green.active { color: #059669; border-bottom-color: #059669; background: rgba(5,150,105,0.04); }
    .agent-badge { padding: 2px 8px; border-radius: 8px; font-size: 10.5px; font-weight: 800; &.red { background: #fee2e2; color: #dc2626; } &.blue { background: #dbeafe; color: #1d4ed8; } &.green { background: #dcfce7; color: #15803d; } }

    .agent-content-box { animation: fadeIn 0.3s ease; }
    .agent-role-intro { padding: 14px 18px; border-radius: 12px; display: flex; gap: 12px; align-items: flex-start; margin-bottom: 20px; font-size: 13.5px; line-height: 1.5; i { font-size: 20px; margin-top: 2px; flex-shrink: 0; } p { margin: 4px 0 0; } &.red { background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; i { color: #dc2626; } } &.blue { background: #eff6ff; border: 1px solid #bfdbfe; color: #1e40af; i { color: #2563eb; } } &.green { background: #f0fdf4; border: 1px solid #bbf7d0; color: #166534; i { color: #16a34a; } } }

    .meta-row { display: flex; gap: 24px; margin-bottom: 18px; font-size: 13.5px; flex-wrap: wrap; }
    .tag-alert { background: #fee2e2; color: #b91c1c; padding: 3px 10px; border-radius: 6px; font-weight: 800; }
    .tag-stride { background: #fef3c7; color: #b45309; padding: 3px 10px; border-radius: 6px; font-weight: 700; }
    .sub-h4 { font-size: 15px; font-weight: 800; color: #1e293b; margin: 0 0 10px; }
    .vectors-list { list-style: none; padding: 0; margin: 0 0 20px; display: flex; flex-direction: column; gap: 8px; li { display: flex; align-items: center; gap: 8px; font-size: 13.5px; color: #334155; font-weight: 600; } }
    .impact-card { background: #f8fafc; border-left: 4px solid #ef4444; padding: 12px 18px; border-radius: 0 10px 10px 0; font-size: 13.5px; line-height: 1.5; color: #334155; margin-bottom: 20px; }

    .strategy-banner { background: #eff6ff; border-left: 4px solid #3b82f6; padding: 12px 18px; border-radius: 0 10px 10px 0; font-size: 13.5px; line-height: 1.5; color: #1e3a8a; margin-bottom: 16px; }
    .tags-group { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 20px; }
    .harden-tag { background: #dcfce7; color: #166534; padding: 4px 10px; border-radius: 6px; font-size: 12px; font-weight: 700; display: inline-flex; align-items: center; gap: 5px; }
    .solid-tag { background: #ede9fe; color: #5b21b6; padding: 4px 10px; border-radius: 6px; font-size: 12px; font-weight: 700; display: inline-flex; align-items: center; gap: 5px; }

    .verdict-banner { background: #f0fdf4; border-left: 4px solid #10b981; padding: 14px 18px; border-radius: 0 10px 10px 0; font-size: 13.5px; line-height: 1.6; color: #064e3b; margin-bottom: 20px; display: flex; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
    .verdict-tag { background: #10b981; color: #fff; padding: 3px 10px; border-radius: 6px; font-weight: 800; font-size: 12px; }

    .empty-vuln-state { text-align: center; padding: 60px 20px; background: #fff; border-radius: 20px; box-shadow: var(--shadow-card); margin-bottom: 30px; i { font-size: 56px; margin-bottom: 14px; display: block; } h3 { font-size: 20px; font-weight: 700; color: var(--color-primary); margin: 0 0 8px; } p { color: var(--color-gray-500); font-size: 14px; margin: 0; } }
    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
  `]
})
export class AdminCodeAuditComponent implements OnInit {
  private auditService = inject(CodeAuditService);

  presetFiles: PresetFile[] = [
    {
      id: 'vulnerable-demo',
      name: 'VulnerablePaymentService.java (Démo Failles)',
      path: 'src/main/java/demo/VulnerablePaymentService.java',
      language: 'java',
      description: 'Démo avec SQL Injection, fuite de mot de passe, absence de validation et NPE',
      code: `package com.learnhub.demo;

import org.springframework.stereotype.Service;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

@Service
public class VulnerablePaymentService {

    // FAILLE 1: Identifiants en clair (Hardcoded Credentials)
    private static final String DB_USER = "root";
    private static final String DB_PASS = "admin123456";

    // FAILLE 2: Injection SQL (CWE-89 / OWASP A03)
    public boolean processPayment(String cardNumber, String amountStr, String userEmail) {
        // FAILLE 3: Exposition de données sensibles dans les logs console
        System.out.println("Processing credit card: " + cardNumber + " for user: " + userEmail);

        try {
            // FAILLE 4: Risque NullPointer (NPE) sans validation
            double amount = Double.parseDouble(amountStr);

            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/app_db", DB_USER, DB_PASS);
            Statement stmt = conn.createStatement();

            // VULNÉRABILITÉ CRITIQUE: Concaténation directe non échappée
            String query = "SELECT * FROM users WHERE email = '" + userEmail + "' AND balance >= " + amount;
            ResultSet rs = stmt.executeQuery(query);

            if (rs.next()) {
                System.out.println("Payment approved for amount: " + amount);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}`
    },
    {
      id: 'app-service',
      name: 'ApplicationService.java',
      path: 'src/main/java/pi/integrated/jobservice/service/ApplicationService.java',
      language: 'java',
      description: 'Gestion des candidatures, calcul de match score et stockage de CV',
      code: `package pi.integrated.jobservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pi.integrated.jobservice.dto.ApplicationDTO;
import pi.integrated.jobservice.model.Application;
import pi.integrated.jobservice.repository.ApplicationRepository;
import pi.integrated.jobservice.repository.JobRepository;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;

    public ApplicationDTO createApplication(Long jobId, Long teacherId, String teacherName,
                                            String candidateEmail, String motivation, MultipartFile cv) {
        if (teacherId == null) {
            teacherId = candidateEmail != null ? (Math.abs((long) candidateEmail.hashCode()) % 1000000L + 1L) : 16L;
        }
        var job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        Application app = Application.builder()
                .job(job)
                .teacherId(teacherId)
                .teacherName(teacherName != null ? teacherName : "Candidat")
                .motivation(motivation)
                .build();

        return ApplicationDTO.fromEntity(applicationRepository.save(app));
    }
}`
    },
    {
      id: 'job-ctrl',
      name: 'JobController.java',
      path: 'src/main/java/pi/integrated/jobservice/controller/JobController.java',
      language: 'java',
      description: 'Endpoints REST pour les offres d emploi et candidatures',
      code: `package pi.integrated.jobservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pi.integrated.jobservice.dto.JobDTO;
import pi.integrated.jobservice.service.JobService;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {
    private final JobService jobService;

    @GetMapping
    public ResponseEntity<List<JobDTO>> getAllJobs() {
        return ResponseEntity.ok(jobService.getAllJobs());
    }

    @PostMapping
    public ResponseEntity<JobDTO> createJob(@RequestBody JobDTO dto) {
        return ResponseEntity.ok(jobService.createJob(dto));
    }
}`
    }
  ];

  selectedPresetId = 'vulnerable-demo';
  language = 'java';
  sourceCode = '';

  // Optimisations & Hyperparamètres
  showOptPanel = true;
  selectedModel = 'openai/gpt-oss-120b';
  temperature = 0.1;
  maxTokens = 3500;
  useCache = true;
  enableRag = true;

  loading = false;
  benchmarking = false;
  multiAgentRunning = false;
  auditResult: CodeAuditResponse | null = null;
  benchmarkResult: CodeBenchmarkResponse | null = null;
  multiAgentResult: MultiAgentAuditResponse | null = null;
  activeAgentSubTab: 'attacker' | 'defender' | 'verifier' = 'attacker';
  activeTab: 'vulns' | 'tests' | 'refactor' | 'academic' = 'vulns';

  copiedTest = false;
  copiedRefactor = false;
  copiedPatchCode = false;
  copiedVerifierTests = false;
  isDragging = false;

  ngOnInit(): void {
    this.selectPreset('vulnerable-demo');

    this.auditService.getPresetFiles().subscribe({
      next: (presets) => {
        if (presets && presets.length > 0) {
          const demo = this.presetFiles[0];
          this.presetFiles = [demo, ...presets];
        }
      },
      error: () => {}
    });
  }

  toggleOptPanel(): void {
    this.showOptPanel = !this.showOptPanel;
  }

  selectPreset(id: string): void {
    this.selectedPresetId = id;
    this.onPresetChange();
  }

  onPresetChange(): void {
    if (this.selectedPresetId === 'custom') {
      return;
    }
    const found = this.presetFiles.find(p => p.id === this.selectedPresetId);
    if (found) {
      this.sourceCode = found.code;
      this.language = found.language || 'java';
    }
  }

  onFileSelected(event: any): void {
    const file: File = event.target?.files?.[0];
    if (!file) return;
    this.readFile(file);
    event.target.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
  }

  onFileDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.readFile(file);
    }
  }

  private readFile(file: File): void {
    const reader = new FileReader();
    reader.onload = () => {
      this.sourceCode = (reader.result as string) || '';
      this.selectedPresetId = 'custom';

      const name = file.name.toLowerCase();
      if (name.endsWith('.java')) this.language = 'java';
      else if (name.endsWith('.ts') || name.endsWith('.js')) this.language = 'typescript';
      else if (name.endsWith('.sql')) this.language = 'sql';
      else if (name.endsWith('.py')) this.language = 'python';
    };
    reader.readAsText(file);
  }

  clearCode(): void {
    this.sourceCode = '';
    this.selectedPresetId = 'custom';
  }

  getLineCount(): number {
    if (!this.sourceCode) return 0;
    return this.sourceCode.split('\n').length;
  }

  runAudit(): void {
    if (!this.sourceCode.trim()) return;

    this.loading = true;
    this.auditResult = null;

    let fileName = 'SourceFile.java';
    if (this.selectedPresetId !== 'custom') {
      const found = this.presetFiles.find(p => p.id === this.selectedPresetId);
      if (found) fileName = found.name;
    }

    this.auditService.analyzeCode({
      fileName,
      language: this.language,
      sourceCode: this.sourceCode,
      model: this.selectedModel,
      temperature: this.temperature,
      maxTokens: this.maxTokens,
      useCache: this.useCache,
      enableRag: this.enableRag
    }).subscribe({
      next: (res) => {
        this.auditResult = res;
        this.loading = false;
        this.activeTab = 'vulns';
      },
      error: (err) => {
        console.error('Erreur audit:', err);
        this.loading = false;
        alert('Erreur lors de l\'audit de code: ' + (err?.error?.message || err?.message || 'Erreur réseau'));
      }
    });
  }

  runBenchmark(): void {
    if (!this.sourceCode.trim()) return;

    this.benchmarking = true;
    this.benchmarkResult = null;

    let fileName = 'SourceFile.java';
    if (this.selectedPresetId !== 'custom') {
      const found = this.presetFiles.find(p => p.id === this.selectedPresetId);
      if (found) fileName = found.name;
    }

    this.auditService.benchmarkModels({
      fileName,
      language: this.language,
      sourceCode: this.sourceCode
    }).subscribe({
      next: (res) => {
        this.benchmarkResult = res;
        this.benchmarking = false;
      },
      error: (err) => {
        console.error('Erreur benchmark:', err);
        this.benchmarking = false;
        alert('Erreur lors du benchmark: ' + (err?.error?.message || err?.message || 'Erreur réseau'));
      }
    });
  }

  runMultiAgentAudit(): void {
    if (!this.sourceCode.trim()) return;

    this.multiAgentRunning = true;
    this.multiAgentResult = null;
    this.auditResult = null;

    let fileName = 'SourceFile.java';
    if (this.selectedPresetId !== 'custom') {
      const found = this.presetFiles.find(p => p.id === this.selectedPresetId);
      if (found) fileName = found.name;
    }

    this.auditService.runMultiAgentAudit({
      fileName,
      language: this.language,
      sourceCode: this.sourceCode,
      model: this.selectedModel,
      temperature: this.temperature
    }).subscribe({
      next: (res) => {
        this.multiAgentResult = res;
        this.multiAgentRunning = false;
        this.activeAgentSubTab = 'attacker';
      },
      error: (err) => {
        console.error('Erreur multi-agents:', err);
        this.multiAgentRunning = false;
        alert('Erreur lors de la délibération multi-agents: ' + (err?.error?.message || err?.message || 'Erreur réseau'));
      }
    });
  }

  copyCustomCode(text: string, type: 'patch' | 'tests'): void {
    if (!text) return;
    navigator.clipboard.writeText(text);
    if (type === 'patch') {
      this.copiedPatchCode = true;
      setTimeout(() => this.copiedPatchCode = false, 2000);
    } else {
      this.copiedVerifierTests = true;
      setTimeout(() => this.copiedVerifierTests = false, 2000);
    }
  }

  copyCode(text: string): void {
    if (!text) return;
    navigator.clipboard.writeText(text);
    if (this.activeTab === 'tests') {
      this.copiedTest = true;
      setTimeout(() => this.copiedTest = false, 2000);
    } else {
      this.copiedRefactor = true;
      setTimeout(() => this.copiedRefactor = false, 2000);
    }
  }
}
