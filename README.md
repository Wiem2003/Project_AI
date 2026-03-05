# Learnify — Backend (Microservices)

A Spring Boot microservices backend for the **Learnify** English-learning platform. Each microservice lives on its own branch. The `main` branch contains shared infrastructure (Docker Compose, build scripts, and this README).

---

## Architecture Overview

```
                        ┌─────────────────┐
                        │   Eureka Server  │  :8761
                        │  Service Registry│
                        └────────┬────────┘
                                 │ (all services register here)
                        ┌────────▼────────┐
Client (Angular :4200)──►   API Gateway   │  :8080
                        │ Spring Cloud GW │
                        └────────┬────────┘
          ┌──────────────────────┼──────────────────────┐
          │          │           │          │            │
    ┌─────▼──┐ ┌─────▼──┐ ┌─────▼──┐ ┌────▼───┐ ┌─────▼──┐ ...
    │ event  │ │ course │ │  user  │ │  job   │ │  ai    │
    │ :8081  │ │ :8086  │ │ :8087  │ │ :8088  │ │ :8085  │
    └────────┘ └────────┘ └────────┘ └────────┘ └────────┘
```

All inter-service communication is load-balanced via Eureka (`lb://service-name`). CORS is handled centrally at the gateway — no `@CrossOrigin` on individual controllers.

---

## Branches — One Per Microservice

| Branch | Service | Port | Database |
|---|---|---|---|
| `main` | Shared infra (Docker Compose, build scripts) | — | — |
| `eureka-server` | Service registry | 8761 | — |
| `api-gateway` | Routing & CORS | 8080 | — |
| `user-service` | Auth, users, JWT | 8087 | `user_db` (3306) |
| `event-service` | Events & reservations | 8081 | `event_db` (3306) |
| `course-service` | Courses, modules, lessons | 8086 | `course_db` (3311) |
| `job-service` | Jobs, applications, meetings | 8088 | `job_db` (3313) |
| `payment-service` | Payments & notifications | 8082 | `payment_db` (3306) |
| `quiz-feedback-service` | Quizzes, questions, attempts, feedback | 8084 | `quiz_db` (3306) |
| `certificate-service` | Course completion certificates | 8083 | `certificate_db` (3306) |
| `ai-service` | Gemini AI features (study plans, event AI) | 8085 | `ai_db` (3306) |

---

## Microservice Details

### eureka-server
**Branch:** `eureka-server`

Netflix Eureka service registry. Every microservice registers itself on startup and uses Eureka for load-balanced discovery (`lb://service-name` URIs). The gateway and all services point to `http://eureka-server:8761/eureka/`.

No business logic — pure infrastructure. Dashboard available at `http://localhost:8761`.

---

### api-gateway
**Branch:** `api-gateway`

Spring Cloud Gateway acting as the single entry point for all client requests on port **8080**. Responsibilities:
- **Path-based routing** — routes each URL prefix to the correct microservice via Eureka load balancing.
- **Global CORS** — `allowedOrigins: "*"` for all methods and headers, so no service needs `@CrossOrigin`.

Route table:

| Path prefix | Target service |
|---|---|
| `/api/events/**`, `/api/reservations/**` | event-service |
| `/api/payments/**`, `/api/notifications/**` | payment-service |
| `/api/certificates/**` | certificate-service |
| `/api/quizzes/**`, `/api/questions/**`, `/api/attempts/**`, `/api/feedbacks/**` | quiz-feedback-service |
| `/api/ai/**`, `/api/chat/**`, `/api/quiz-generator/**`, `/api/feedback-analysis/**` | ai-service |
| `/api/courses/**` | course-service |
| `/api/auth/**`, `/api/users/**`, `/api/admin/**`, `/api/app-pin/**`, `/api/webauthn/**`, `/oauth2/**` | user-service |
| `/api/jobs/**`, `/api/applications/**`, `/api/meetings/**`, `/api/ratings/**`, `/api/job-notifications/**`, `/api/saved-jobs/**`, `/api/cv-profiles/**` | job-service |

---

### user-service
**Branch:** `user-service`  
**Package:** `learnifyapp.userandpreevaluation`

Handles everything related to identity and authentication:
- **JWT authentication** — issues tokens with claims `sub` (email), `role`, `userId`.
- **Registration / login** — standard email+password flow with SendGrid email verification.
- **OAuth2** — Google social login via Spring Security OAuth2.
- **WebAuthn** — passkey/biometric authentication.
- **App PIN** — secondary PIN lock for the mobile-style UI.
- **Pre-evaluation** — onboarding quiz to determine initial English level.
- **Admin user management** — CRUD on users at `/api/admin/**`.

JWT claims decoded on the frontend via `atob(token.split('.')[1])`. `userId` (Long) is embedded in the token — it is **not** stored in `localStorage`.

---

### event-service
**Branch:** `event-service`

Manages English-learning events (workshops, webinars, conferences, etc.) and seat reservations:
- **Events CRUD** — create/read/update/delete events with image upload support (`/uploads/` directory, 10 MB limit).
- **Reservations** — users reserve seats; tracks `reservedPlaces` vs `placesLimit`.
- **Event categories:** `WORKSHOP`, `WEBINAR`, `CONFERENCE`, `TRAINING`, `EXAM_PREPARATION`, `BUSINESS_ENGLISH`, `CULTURAL_EVENT`.
- File uploads served as static resources from the `uploads/` directory.

Key endpoints: `GET/POST /api/events`, `GET/PUT/DELETE /api/events/{id}`, `POST/DELETE /api/reservations`.

---

### course-service
**Branch:** `course-service`

Three-tier content hierarchy: **Course → Module → Lesson**.
- Courses have category, level (A1–C2), duration, price, teacher, and image.
- Modules belong to a course (`@ManyToOne`) with ordered lessons.
- Lessons belong to a module with optional video URL.
- Delete is transactional: deleting a course first removes all its modules (which cascade-delete their lessons), then removes the course.
- No Spring Security — all endpoints are open; the gateway handles auth at the route level.

Key endpoints:
- `GET /api/courses`, `POST /api/courses/admin`, `PUT/DELETE /api/courses/admin/{id}`
- `GET/POST /api/courses/{id}/modules`, `PUT/DELETE /api/courses/{courseId}/modules/admin/{moduleId}`
- `GET/POST /api/courses/{courseId}/modules/{moduleId}/lessons`
- `GET /api/courses/category/{cat}`, `/level/{level}`, `/search?keyword=`

---

### job-service
**Branch:** `job-service`

Full job board for English-related employment opportunities:
- **Jobs** — post/search/filter job listings.
- **Applications** — candidates apply to jobs; status tracking.
- **Meetings** — schedule interviews between employers and candidates.
- **Ratings** — rate employers/candidates post-interview.
- **Saved jobs** — bookmark jobs for later.
- **CV profiles** — structured CV data stored and served via the API.
- **Job notifications** — in-app notifications for application status changes.

---

### payment-service
**Branch:** `payment-service`

Handles course/event payment processing and transactional notifications:
- Integrates with a payment provider (Stripe or similar) for checkout flows.
- Stores payment records with status (pending, completed, failed, refunded).
- Sends email notifications via Spring Mail on payment events.
- Exposes `/api/payments/**` and `/api/notifications/**`.

---

### quiz-feedback-service
**Branch:** `quiz-feedback-service`

Assessment and feedback engine:
- **Quizzes** — create quizzes with multiple questions, assign to courses or standalone.
- **Questions** — multiple-choice or open-ended question bank.
- **Attempts** — records student quiz attempts with answers and scores.
- **Feedback** — teachers/AI provide written feedback on attempts or open submissions.

Endpoints under `/api/quizzes/**`, `/api/questions/**`, `/api/attempts/**`, `/api/feedbacks/**`.

---

### certificate-service
**Branch:** `certificate-service`

Issues and validates course completion certificates:
- Generates a certificate record when a student completes a course (triggered by other services or manually by admin).
- Stores certificate metadata (student, course, issue date, unique certificate ID).
- Provides verification endpoint so third parties can validate a certificate by ID.

Endpoints under `/api/certificates/**`.

---

### ai-service
**Branch:** `ai-service`

Google Gemini-powered AI features — wraps the Gemini REST API via a central `GeminiService` (RestTemplate-based, `systemPrompt + userMessage` pattern).

Three feature groups:

1. **Study Plan generation** (`POST /api/ai/study-plan`)  
   Given a user's current level, target level, available hours/week, and focus areas → returns a structured multi-week study plan with weekly goals and daily tasks.

2. **Event Completion Prediction** (`POST /api/ai/events/predict`)  
   Given `likes`, `reservations`, and `placesRestantes` for an event → returns `{ result: "RISQUE_ELEVE" | "RISQUE_FAIBLE", reason: string }`. Used to show a warning banner on event detail pages.

3. **Event Recommendation** (`POST /api/ai/events/recommend`)  
   Given a user's `categoriesLiked[]` and the list of `availableEvents[]` (passed from the frontend) → returns the top 5 recommended events. The event list is passed in the request rather than fetching from event-service, avoiding inter-service HTTP coupling.

All features have JSON-parse fallbacks using rule-based logic in case Gemini is unavailable.

---

## Running Locally

### Prerequisites
- Docker & Docker Compose
- Java 17+, Maven 3.8+

### Start everything with Docker Compose

```bash
docker-compose up --build
```

This starts all services, MySQL instances, and Eureka. The gateway is available at `http://localhost:8080`.

### Start a single service (development)

```bash
cd <service-directory>
./mvnw spring-boot:run
```

Make sure Eureka is already running, and update the datasource URL in `application.properties` / `application.yml` to point to `localhost` instead of the Docker service name.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3 |
| Service discovery | Spring Cloud Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Persistence | Spring Data JPA + MySQL 8 |
| Auth | Spring Security + JWT + OAuth2 + WebAuthn |
| AI | Google Gemini REST API |
| Containerization | Docker + Docker Compose |
| Build | Maven (per-service `pom.xml`) |
