# Quality Gate (P3 Baseline)

This project now has a baseline quality gate for CI and local development.

## Frontend Gate

Working directory: `frontend-repo`

- `npm run lint` (ESLint; currently warning-first baseline)
- `npm run build` (Vite production build)

Optional:

- `npm run format:check`
- `npm run format`

## Backend Gate

Working directory: `backend-repo`

- `mvn -B -ntp -DskipTests package`

Notes:

- Current backend tests depend on runtime data/environment. In this baseline, CI gates compile/package first.
- After test environment stabilization, upgrade gate to `mvn test`.

## CI Workflow

File: `.github/workflows/quality-gate.yml`

- `frontend-gate`: install -> lint -> build
- `backend-gate`: maven package (skip tests)
