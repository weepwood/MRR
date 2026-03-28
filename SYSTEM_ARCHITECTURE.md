# System Architecture

## Goals

- Keep the medical record imaging system modular, testable, and deployable.
- Separate business reads from operational tooling.
- Make log retention safe by default: no automatic deletion unless explicitly enabled.
- Make manual cleanup traceable: export first, then delete.

## Layers

### Frontend

- Vue 3 + Vite + Element Plus.
- `AdminDashboard` is the operational shell for users, records, testing, logs, monitoring, and settings.
- Admin settings are persisted in local storage and can be promoted to backend-backed config later.

### Backend

- Spring Boot API service.
- MyBatis for SQL access.
- PostgreSQL as the primary data store.
- Controllers stay thin; services own the business logic.
- Scheduler handles retention jobs and temp artifact cleanup.

### Data

- `app.mr_scan`: record metadata and logical delete state.
- `app.access_log`: immutable access audit trail.
- `app.mr_statistics`, `app.mr_patient`, `app.mr_user`: supporting business tables.

## Operational Model

### Log Retention

- Default retention window: 3 years.
- Automatic cleanup is disabled by default.
- Manual cleanup flow:
  1. Export expired logs to CSV.
  2. Run the cleanup with the same cutoff.
  3. Show counts and cutoff in the admin panel.

### Monitoring

- Pressure test module captures request latency, throughput, and memory snapshot.
- History is kept in memory for lightweight validation.
- API endpoints remain under `/v1/monitoring-api`.

## Deployment Topology

- PostgreSQL container
- Backend container
- Frontend container serving static assets and proxying API calls

The front door is the frontend container. It proxies API, Swagger, and actuator paths to the backend so the browser can use one origin.

## Conventions

- No destructive operation should be exposed in the system log browser.
- Use explicit service methods for side effects.
- Keep cross-cutting defaults in one place:
  - frontend admin settings defaults
  - backend retention settings
  - deployment env vars

## Next Phases

1. Move admin settings from browser storage to backend storage.
2. Split large backend modules into domain packages.
3. Add paginated log export and archive download jobs.
4. Introduce object storage for exported artifacts and cold archives.
