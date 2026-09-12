# Deployment

## Environments

| | Dev | Production |
|---|---|---|
| Deploy unit | `docker compose up` | backend image + frontend image (or CDN SPA) + managed PG/Redis |
| Database | container | managed PostgreSQL 16 (RDS/Cloud SQL/Neon) |
| Redis | container | managed Redis |
| SMTP | MailHog | SES SMTP / Postmark / provider SMTP |
| File storage | local volume | S3-compatible bucket (`StorageProvider` interface, local impl default) |

## Environment variables

| Variable | Required | Default (dev) | Purpose |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | – | `dev` | `dev` enables seeding |
| `CRM_DB_URL` | ✔ prod | `jdbc:postgresql://localhost:5432/crm` | JDBC url |
| `CRM_DB_USERNAME` / `CRM_DB_PASSWORD` | ✔ prod | `crm` / `crm` | DB credentials |
| `CRM_JWT_SECRET` | ✔ prod | dev value + warning | ≥64-char random secret |
| `CRM_CORS_ORIGINS` | ✔ prod | `http://localhost:5173` | comma-separated SPA origins |
| `CRM_ENCRYPTION_KEY` | ✔ prod | dev value + warning | 32-byte base64 key for credential encryption |
| `CRM_ADMIN_EMAIL` / `CRM_ADMIN_PASSWORD` | first boot | `admin@nexuscrm.local` / `Admin123!` | seeded org admin |
| `CRM_STORAGE_DIR` | – | `./data/storage` | local document storage root |
| `CRM_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` | sending | MailHog | outbound SMTP |
| `CRM_REDIS_URL` | – | `redis://localhost:6379` | Redis |
| `CRM_AI_API_KEY` | – | – | enables OpenAI-compatible provider |
| `CRM_APP_URL` | – | `http://localhost:5173` | used in links (tracking, unsubscribe) |

## Production checklist

1. Set all ✔ env vars from a secret manager (docker secrets / K8s secrets / vault).
2. `SPRING_PROFILES_ACTIVE=prod` (seeding off except first boot with admin bootstrap).
3. Run behind TLS (nginx/Caddy/ALB). HSTS is sent by the app; terminate TLS at the proxy.
4. Health: `/actuator/health` for LB readiness; `/actuator/prometheus` for metrics scrape.
5. Backups: nightly `pg_dump` + WAL archiving; restore drills quarterly.
6. Logs: JSON to stdout → collector; ship trace ids.
7. Scale: API is stateless — run N replicas behind the LB; Flyway runs on one node
   (`spring.flyway.enabled=true` on a single deployer or use a migration job).
8. Background workers: same image, profile `worker` (disables web port) for horizontal scaling of
   imports/campaigns when needed.

## CI/CD

GitHub Actions (`.github/workflows/`): backend job = JDK 21 + Maven build, unit tests, and a
Docker-enabled job running Testcontainers integration tests against real PostgreSQL; frontend job =
`npm ci`, type-check, production build. Images can be built and pushed from the same pipeline
(Dockerfiles are multi-stage).
