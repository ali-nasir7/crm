# Nexus CRM — Sales Operating System

A production-grade Sales CRM / Sales Operating System: lead database, pipeline management, activity
tracking, cold-email campaigns, proposals, client management, analytics, RBAC and a full audit trail —
architected as a multi-tenant-ready platform.

> **Status:** see [ROADMAP.md](ROADMAP.md) for the honest implementation status of every module,
> including features that are intentionally scaffolded as `TODO / Integration Required`.

## Stack

| Layer     | Technology |
|-----------|------------|
| Backend   | Java 21, Spring Boot 3.3, Spring Security (JWT), Spring Data JPA / Hibernate, Flyway, PostgreSQL 16, Redis 7, OpenAPI 3 (springdoc) |
| Frontend  | React 18, TypeScript, Vite, Tailwind CSS, TanStack Query, React Hook Form + Zod, Recharts, lucide-react |
| Tests     | JUnit 5, Mockito, Testcontainers (real PostgreSQL), MockMvc |
| Infra     | Docker, Docker Compose, Mailpit (dev SMTP with web UI :8025) |

## Repository layout

```
├── backend/           Spring Boot API (modular monolith, package-per-domain)
├── frontend/          React SPA
├── docs/              ARCHITECTURE / DATABASE / API / SECURITY / DEPLOYMENT / ROADMAP + ci/
├── docker-compose.yml One-command dev stack
└── docs/ci/*.yml      CI definitions — copy to .github/workflows/ (GitHub Apps can't push workflow files)
```

## Quick start (one command)

```bash
cp .env.example .env          # defaults work for local dev
docker compose up --build
```

Seeded demo logins (dev profile only): **admin@nexuscrm.local / Admin123!** ·
manager@nexuscrm.local / Manager123! · rep@nexuscrm.local / Rep12345!

| Service   | URL                              |
|-----------|----------------------------------|
| Frontend  | http://localhost:5173            |
| API       | http://localhost:8080            |
| Swagger   | http://localhost:8080/swagger-ui.html |
| MailHog   | http://localhost:8025            |

Default seeded login (dev only, change immediately): `admin@nexuscrm.local` / `Admin123!`

The seeder creates: a default organization, the 5 system roles with their permission sets, the
default 14-stage pipeline, lead sources, tags, lead-scoring rules, clinic-niche custom fields and
starter email templates — idempotently (safe on restart).

## Running without Docker

```bash
# 1. PostgreSQL + Redis (any recent versions), then:
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# 2. Frontend
cd frontend && npm install && npm run dev
```

## Tests

```bash
cd backend
./mvnw test                       # fast unit tests (no Docker needed)
./mvnw test -Dsurefire.excludedGroups=   # + Testcontainers integration tests (needs Docker)
cd ../frontend && npm test && npm run build
```

## Documentation

* [ARCHITECTURE.md](ARCHITECTURE.md) — system architecture and every key decision + rationale
* [DATABASE.md](DATABASE.md) — ERD, schema, indexing and tenancy strategy
* [API.md](API.md) — REST API surface (also live at `/swagger-ui.html`)
* [SECURITY.md](SECURITY.md) — auth, RBAC, data visibility, hardening
* [DEPLOYMENT.md](DEPLOYMENT.md) — production deployment, env vars, scaling
* [ROADMAP.md](ROADMAP.md) — phased roadmap and implementation status
