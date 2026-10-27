# LearnifyEnglish — Integrated Project

Spring Boot microservices (Eureka + API Gateway) with an Angular frontend. This branch combines everyone's LLM + RAG work into one codebase.

## Where each AI feature lives

| Service | Port | Author | LLM / RAG feature | Provider |
|---|---|---|---|---|
| `preevaluation-service` | 8089 | Wiem | CEFR pre-evaluation assistant, RAG over a pedagogy knowledge base | Groq |
| `course-service` | 8086 | Sofien | "Ask the course" (cited answers from PDFs + YouTube transcripts), lesson-grounded quiz | Gemini |
| `job-service` | 8088 | Asma | Job RAG chat, AI candidate ranking, code audit / multi-agent security review, Whisper speech | Groq, Ollama |
| `event-service` | 8081 | Alaa | Event recommendation & analysis RAG (`/api/events/rag/*`) | Gemini |
| `ai-service` | 8085 | (shared base) | Chatbot, study plans, quiz generator, feedback analysis | Gemini |

Other services: `eureka-server` (8761), `api-gateway` (8080), `user-service` (8087), `payment-service` (8082), `certificate-service` (8083), `quiz-feedback-service` (8084).

## Setup

1. Copy `.env.example` to `.env` and fill in your keys (`GEMINI_API_KEY`, `GROQ_API_KEY`, …). Never commit `.env`.
2. Create the MySQL database (local run): every service uses `learnify_english` on `localhost:3306`, user `root`, empty password (edit each `application.yml/.properties` if yours differs).
3. Optional, for job-service: [Ollama](https://ollama.com) running locally with `ollama pull mistral`, Tesseract for OCR, and Python + `openai-whisper` for speech.

### Run locally (Windows)

```powershell
.\start_all.ps1          # loads .env, starts Eureka first, then every service
cd frontend; npm install; npm start   # http://localhost:4200
```

### Run with Docker

```sh
./build.sh               # builds every service jar
docker compose up -d     # reads .env automatically; each service gets its own MySQL container
```

## Integration notes

- All API keys, the JWT secret and the Gmail/Google settings come from environment variables. `JWT_SECRET` must be the same for every service (the default in `.env.example` works locally; change it in production).
- `.gitattributes` normalizes line endings so Windows and Mac/Linux clones stay in sync.
- Detailed docs: `docs/` (course RAG report, job-service guides), `preevaluation-service/RAG.md`, `event-service/README.md`.
