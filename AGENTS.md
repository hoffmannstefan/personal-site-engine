# Instructions for coding agents

Personal Site Engine: a Spring Boot 3.5 / Java 21 site with two Maven modules, `backend` and `frontend`. Read this
file first; the documents at the bottom are read only when the task needs them.

## Commands

Needs a full JDK 21. Run everything from the project folder.

```bash
./mvnw -B verify                              # build and run all tests; must stay green
./mvnw -pl backend test                       # the tests of one module (or: -pl frontend)
./mvnw -pl backend test -Dtest=PostServiceTest   # one test class
docker compose up -d --build                  # the site on http://localhost:8080
docker compose down
```

## Rules

- New behaviour comes with tests, and `./mvnw -B verify` is green before a task counts as done.
- Dependencies are declared only in the `pom.xml` files. Ask before adding one.
- Content (the site, posts, publications, images) lives in `info/`, never in `src/`. The tests use their own content
  under `backend/src/test/resources/`.
- Never commit or print secrets: `.env` files, tokens, passwords, SSH keys and `server.txt`.
- **Do not touch the server, push or deploy unless the owner asks for it in this conversation.** A push to `main` or
  `develop` deploys automatically (`.github/workflows/ci.yml`), to production and development.
- Commit small, one change at a time, with an English message in the style of `git log`. Do not rewrite history that
  is pushed.
- Keep the documentation true: a change that makes `README.md`, `docs/spec.md` or `deploy/README.md` wrong updates
  them in the same commit.

## Team

Larger work goes through the team in `.claude/agents/` (product manager, software engineer, QA engineer). The main
session only coordinates them. The process is in [docs/process.md](docs/process.md); the backlog is
[docs/tasks.md](docs/tasks.md), finished tasks are in [docs/tasks-done.md](docs/tasks-done.md).

## Documents

- [docs/spec.md](docs/spec.md): what the system does and why; read it before changing behaviour.
- [docs/process.md](docs/process.md): the roles and the life of a task.
- [README.md](README.md): usage, content folder, REST API, project layout.
- [deploy/README.md](deploy/README.md): the vServer, Caddy and GitHub setup; read it before touching deployment.
- [observability/README.md](observability/README.md): telemetry and the Grafana dashboard.
