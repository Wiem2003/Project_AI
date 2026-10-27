import { Component, OnInit, inject } from '@angular/core';
import { JobService, Job, JobWithScore } from '../../services/job.service';
import { ApplicationService } from '../../services/application.service';
import { SessionService } from '../../services/session.service';

@Component({
  selector: 'app-job-offers',
  templateUrl: './job-offers.component.html',
  styleUrl: './job-offers.component.scss',
  standalone: false,
})
export class JobOffersComponent implements OnInit {
  private jobService = inject(JobService);
  private appService = inject(ApplicationService);
  private session = inject(SessionService);

  jobs: JobWithScore[] = [];
  filtered: JobWithScore[] = [];
  savedJobIds = new Set<number>();
  loading = true;
  error = '';

  // Filters
  searchTerm = '';
  locationFilter = '';
  subjectFilter = '';

  // Apply modal
  showApplyModal = false;
  applyJob: Job | null = null;
  coverLetter = '';  // kept as local variable name for the textarea binding (maps to 'motivation' on submit)
  cvFile: File | null = null;
  applying = false;
  applySuccess = '';
  applyError = '';

  // Detail modal
  showDetailModal = false;
  detailJob: Job | null = null;

  // RAG Chatbot
  showChat = false;
  showProfileConfig = false;
  chatQuestion = '';
  chatLoading = false;
  chatAttachment: File | null = null;

  // Vocal / Whisper STT
  isRecording = false;
  private mediaRecorder: MediaRecorder | null = null;
  private audioChunks: Blob[] = [];

  // Profil personnalisé du candidat
  candidateProfile = {
    skills: '',
    experience: '',
    city: '',
    targetRole: ''
  };

  chatMessages: Array<{ sender: 'user' | 'bot'; text: string; matchedJobs?: any[] }> = [
    {
      sender: 'bot',
      text: 'Bonjour ! Je suis votre conseiller recrutement IA Learnify. Configurez votre profil ou posez-moi vos questions pour recevoir des recommandations personnalisées.'
    }
  ];

  toggleChat(): void {
    this.showChat = !this.showChat;
  }

  toggleProfileConfig(): void {
    this.showProfileConfig = !this.showProfileConfig;
  }

  hasCustomProfile(): boolean {
    return !!(
      this.candidateProfile.skills?.trim() ||
      this.candidateProfile.city?.trim() ||
      this.candidateProfile.targetRole?.trim() ||
      this.candidateProfile.experience?.trim()
    );
  }

  requestPersonalizedRecommendations(): void {
    if (!this.hasCustomProfile()) {
      this.showProfileConfig = true;
      return;
    }
    const prompt = `Recommandez-moi les meilleures offres pour mon profil (${this.candidateProfile.targetRole || 'Enseignant'} à ${this.candidateProfile.city || 'toute ville'}) et expliquez vos choix.`;
    this.chatQuestion = prompt;
    this.sendChatMessage();
  }

  // Enregistrement vocal
  async toggleVoiceRecording(): Promise<void> {
    if (this.isRecording) {
      this.stopVoiceRecording();
    } else {
      await this.startVoiceRecording();
    }
  }

  private async startVoiceRecording(): Promise<void> {
    // 1. Essai avec Web Speech API native du navigateur (temps réel, immédiat et sans délai)
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (SpeechRecognition) {
      try {
        const recognition = new SpeechRecognition();
        recognition.lang = 'fr-FR';
        recognition.interimResults = false;
        recognition.maxAlternatives = 1;

        recognition.onstart = () => {
          this.isRecording = true;
        };

        recognition.onresult = (event: any) => {
          const text = event.results[0][0].transcript;
          if (text) {
            this.chatQuestion = text;
            this.isRecording = false;
            // Remplir le champ et envoyer directement
            this.sendChatMessage();
          }
        };

        recognition.onerror = (event: any) => {
          console.warn('SpeechRecognition fallback to Whisper backend:', event.error);
          this.isRecording = false;
          this.startMediaRecorderRecording();
        };

        recognition.onend = () => {
          this.isRecording = false;
        };

        (this as any)._speechRec = recognition;
        recognition.start();
        return;
      } catch (e) {
        console.warn('SpeechRecognition failed to start, using backend Whisper', e);
      }
    }

    // 2. Si Web Speech n'est pas disponible, capture audio pour Whisper
    await this.startMediaRecorderRecording();
  }

  private async startMediaRecorderRecording(): Promise<void> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      this.audioChunks = [];
      this.mediaRecorder = new MediaRecorder(stream);

      this.mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          this.audioChunks.push(event.data);
        }
      };

      this.mediaRecorder.onstop = () => {
        const audioBlob = new Blob(this.audioChunks, { type: 'audio/wav' });
        stream.getTracks().forEach(track => track.stop());
        this.processRecordedAudio(audioBlob);
      };

      this.mediaRecorder.start();
      this.isRecording = true;
    } catch (err) {
      console.error('Erreur accès microphone:', err);
      alert('Impossible d\'accéder à votre microphone.');
    }
  }

  private stopVoiceRecording(): void {
    if ((this as any)._speechRec) {
      try { (this as any)._speechRec.stop(); } catch (e) {}
      this.isRecording = false;
      return;
    }
    if (this.mediaRecorder && this.isRecording) {
      this.mediaRecorder.stop();
      this.isRecording = false;
    }
  }

  private processRecordedAudio(audioBlob: Blob): void {
    this.chatLoading = true;

    this.jobService.transcribeAudioWithWhisper(audioBlob).subscribe({
      next: (res) => {
        const transcript = res?.text?.trim();
        this.chatLoading = false;
        if (transcript) {
          this.chatQuestion = transcript;
          // Envoie directement la question transcrite
          this.sendChatMessage();
        } else {
          this.chatMessages.push({
            sender: 'bot',
            text: 'Désolé, aucun texte n\'a pu être identifié dans votre message vocal.'
          });
        }
      },
      error: (err) => {
        this.chatLoading = false;
        console.error('Erreur transcription:', err);
        this.chatMessages.push({
          sender: 'bot',
          text: 'Erreur lors de la transcription vocale.'
        });
      }
    });
  }

  onChatFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.chatAttachment = input.files[0];
    }
  }

  removeChatAttachment(): void {
    this.chatAttachment = null;
  }

  sendChatMessage(): void {
    const q = this.chatQuestion.trim();
    const file = this.chatAttachment;

    if ((!q && !file) || this.chatLoading) return;

    let userMsgText = q;
    if (file) {
      userMsgText = q ? `📎 [Pièce jointe: ${file.name}] ${q}` : `📎 [Document joint pour analyse OCR: ${file.name}]`;
    }

    this.chatMessages.push({ sender: 'user', text: userMsgText });
    this.chatQuestion = '';
    this.chatAttachment = null;
    this.chatLoading = true;

    const profilePayload = this.hasCustomProfile() ? this.candidateProfile : undefined;

    this.jobService.askJobRag(q, profilePayload, file ?? undefined).subscribe({
      next: (res) => {
        this.chatLoading = false;
        let botText = res.answer;
        if (res.ocrExtractedText) {
          botText = `📄 *Texte analysé via Tesseract OCR :*\n"${res.ocrExtractedText}"\n\n` + botText;
        }

        this.chatMessages.push({
          sender: 'bot',
          text: botText,
          matchedJobs: res.matchedJobs
        });
      },
      error: () => {
        this.chatLoading = false;
        this.chatMessages.push({
          sender: 'bot',
          text: 'Désolé, une erreur est survenue lors de l\'analyse de votre fichier ou de la communication avec le conseiller IA.'
        });
      }
    });
  }

  get currentUser() {
    return this.session['userSubject']?.value ?? null;
  }

  get isCandidate(): boolean {
    const role = localStorage.getItem('role') || '';
    return role === 'CANDIDATE' || role === 'TUTOR';
  }

  ngOnInit(): void {
    this.loadJobs();
    if (localStorage.getItem('token')) {
      this.jobService.getSavedJobs().subscribe({
        next: (saved) => saved.forEach(j => this.savedJobIds.add(j.id)),
        error: () => {}
      });
    }
  }

  loadJobs(): void {
    this.loading = true;
    const token = localStorage.getItem('token');
    const obs = token ? this.jobService.getRankedJobs() : this.jobService.getAllJobs();
    obs.subscribe({
      next: (jobs: any[]) => {
        this.jobs = jobs ?? [];
        this.applyFilters();
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load jobs.';
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    let list = [...this.jobs];
    if (this.searchTerm) {
      const t = this.searchTerm.toLowerCase();
      list = list.filter(j => j.title.toLowerCase().includes(t) || j.description.toLowerCase().includes(t));
    }
    if (this.locationFilter) {
      const l = this.locationFilter.toLowerCase();
      list = list.filter(j => j.location.toLowerCase().includes(l));
    }
    if (this.subjectFilter) {
      const s = this.subjectFilter.toLowerCase();
      list = list.filter(j => j.subject.toLowerCase().includes(s));
    }
    this.filtered = list;
  }

  openDetail(job: Job): void {
    this.detailJob = job;
    this.showDetailModal = true;
  }

  closeDetail(): void {
    this.showDetailModal = false;
    this.detailJob = null;
  }

  openApply(job: Job): void {
    this.applyJob = job;
    this.showApplyModal = true;
    this.coverLetter = '';
    this.cvFile = null;
    this.applySuccess = '';
    this.applyError = '';
  }

  closeApply(): void {
    this.showApplyModal = false;
    this.applyJob = null;
  }

  onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.cvFile = input.files[0];
  }

  submitApply(): void {
    if (!this.applyJob) return;
    this.applying = true;
    this.applyError = '';
    this.applySuccess = '';
    this.appService.apply(this.applyJob.id, this.coverLetter, this.cvFile ?? undefined).subscribe({
      next: () => {
        this.applying = false;
        this.applySuccess = 'Candidature envoyée avec succès !';
        setTimeout(() => {
          this.closeApply();
        }, 1500);
      },
      error: (e) => {
        this.applying = false;
        const msg = e?.error?.message || e?.error?.error || e?.message;
        if (msg && typeof msg === 'string' && (msg.includes('already applied') || msg.includes('déjà postulé'))) {
          this.applyError = 'Vous avez déjà postulé à cette offre d\'emploi.';
        } else if (e?.status === 403) {
          this.applyError = 'Accès refusé. Veuillez vous connecter avec un compte Candidat ou Tuteur.';
        } else {
          this.applyError = msg || 'Échec de la soumission de la candidature.';
        }
      }
    });
  }

  toggleSave(job: Job): void {
    if (!localStorage.getItem('token')) return;
    if (this.savedJobIds.has(job.id)) {
      this.jobService.unsaveJob(job.id).subscribe({
        next: () => this.savedJobIds.delete(job.id),
        error: () => {}
      });
    } else {
      this.jobService.saveJob(job.id).subscribe({
        next: () => this.savedJobIds.add(job.id),
        error: () => {}
      });
    }
  }

  isSaved(jobId: number): boolean {
    return this.savedJobIds.has(jobId);
  }
}
