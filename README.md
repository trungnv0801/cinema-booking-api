# Cinema Booking API

Backend API for the Cinema Booking system, built with Spring Boot.

## Tech Stack

- **Java 21** / **Spring Boot 4.0.7**
- **Spring Web MVC** — REST API
- **Spring Data JPA** + **PostgreSQL** — persistence
- **Spring Data Redis** — caching / session data
- **Liquibase** — database schema migrations (`src/main/resources/db/changelog`)
- **Spring Security** — stateless JWT authentication/authorization (BCrypt password hashing, custom auth entry point / access-denied handling)
- **JJWT** — JWT issuing and verification
- **springdoc-openapi** — OpenAPI 3 / Swagger UI
- **Lombok**
- **Spotless (Google Java Format)** — code formatting, enforced in CI and on `mvn verify`
- **Testcontainers** (PostgreSQL + Redis) — integration tests
- Packaged as a Docker image (`eclipse-temurin` Alpine, multi-stage build)

## Prerequisites

- **Java 21 JDK** (only if you plan to run outside Docker)
- **Docker** and **Docker Compose v2** (recommended — no local Java/Postgres/Redis install needed)
- The Maven wrapper (`mvnw` / `mvnw.cmd`) is committed to the repo, so a local Maven install is not required

## Configuration

Environment variables are read from a `.env` file at the project root (used by Docker Compose) and/or exported in your shell (used when running the jar directly).

1. Copy the example file:
   - Linux/macOS: `cp .env.example .env`
   - Windows (PowerShell): `Copy-Item .env.example .env`
2. Generate a JWT secret and set it (**required** — the app fails to start without it):
   - Linux/macOS: `openssl rand -base64 48`
   - Windows (PowerShell): `[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))`

   Put the result in `.env` as `JWT_SECRET=...`.
3. Adjust other values as needed.

| Variable | Default | Description |
|---|---|---|
| `APP_PORT` | `8080` | Host port the API is published on |
| `POSTGRES_PORT` | `5432` | Host port for PostgreSQL |
| `POSTGRES_DB` | `database` | Database name |
| `POSTGRES_USER` | `username` | Database user |
| `POSTGRES_PASSWORD` | `password` | Database password |
| `REDIS_PORT` | `6379` | Host port for Redis |
| `LIQUIBASE_CONTEXTS` | `dev` | Liquibase changelog contexts to apply |
| `SPRING_PROFILES_ACTIVE` | `local` | Active Spring profile: `local`, `stg`, or `prod` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Allowed CORS origin(s) |
| `APP_SERVER_URL` | `http://localhost:8080` | Public base URL advertised in the OpenAPI docs |
| `JWT_SECRET` | *(none — required)* | Secret key used to sign/verify access & refresh tokens |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | `900` | Access token lifetime |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | `30` | Refresh token lifetime (set as an `httpOnly` cookie) |
| `COOKIE_SECURE` | `true` | Whether the refresh-token cookie requires HTTPS (set `false` for local HTTP dev) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | *(empty)* | If both are set, an admin account is seeded on startup — see [Authentication](#authentication) |
| `ADMIN_FULL_NAME` | `Administrator` | Display name for the seeded admin account |

### Profile files

The active profile (`local`, `stg`, `prod`) loads `src/main/resources/application-{profile}.yaml`, which is **git-ignored** because it typically holds environment-specific secrets (DB credentials, etc.). A template is provided at `src/main/resources/application.example.yaml`.

For running **directly on the host** (outside Docker), create the profile file you need:

```bash
cp src/main/resources/application.example.yaml src/main/resources/application-local.yaml
# edit application-local.yaml with your local Postgres connection details
```

`application.example.yaml` (and therefore the file you just copied) leaves `app.jwt.secret` as `${JWT_SECRET}` — a placeholder, not an actual value. **Spring Boot does not read the `.env` file** (that file is only consumed by Docker Compose), so when running the jar/`spring-boot:run` directly on the host, that placeholder will fail to resolve unless you either:

- replace it in `application-local.yaml` with an actual generated secret, e.g. `app.jwt.secret: <output of openssl rand -base64 48>` (same idea as how the datasource credentials are hardcoded in that file), **or**
- export `JWT_SECRET` in your shell before running the app, e.g. `export JWT_SECRET=$(openssl rand -base64 48)` (Linux/macOS) or `$env:JWT_SECRET = "..."` (Windows PowerShell).

Do the same (`application-stg.yaml`, `application-prod.yaml`) on staging/production hosts if you deploy the jar directly rather than via Docker Compose (see below). When running via Docker Compose, database/Redis/JWT settings are instead injected as environment variables from `.env`, so these files are not required inside the container.

## Authentication

Login is JWT-based:

- `POST {api.prefix}/auth/login` — body `{ "identifier": "<email>", "password": "<password>" }`. On success, returns an access token (`accessToken`, `tokenType`, `expiresIn`, and the authenticated `user`) and sets an `httpOnly` refresh-token cookie scoped to `{api.prefix}/auth`.
- Protected endpoints require `Authorization: Bearer <accessToken>`; `{api.prefix}/auth/admin/**` additionally requires the `ADMIN` authority.
- `{api.prefix}/auth/login`, `/api-docs/**`, `/swagger-ui/**`, and `/actuator/**` are the only publicly accessible paths — everything else requires authentication (see `SecurityConfig`).

**Bootstrap admin account**: if both `ADMIN_EMAIL` and `ADMIN_PASSWORD` are set, `AdminAccountSeeder` creates (or leaves untouched, if it already exists) an admin user with the `ADMIN` role on application startup — this is the way to get your first login on a fresh database. Leave them blank to skip seeding.

## Running with Docker

This is the recommended way to get a full stack (API + PostgreSQL + Redis) running. Works the same on Windows (with Docker Desktop / WSL2), Linux, and macOS.

### Local development (with live reload)

`docker-compose.local.yml` overrides the `app` service to recompile on file changes (via `entr`) and run `spring-boot:run`, with your source tree and the local Maven cache mounted as volumes.

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml --profile app up
```

- API available at `http://localhost:${APP_PORT:-8080}`
- Editing any file under `src/` or `pom.xml` triggers an automatic recompile + restart
- Stop with `Ctrl+C`, or `docker compose -f docker-compose.yml -f docker-compose.local.yml down`

### Only start infrastructure (Postgres + Redis)

Useful if you want to run the Spring Boot app yourself (from an IDE or `mvnw`) while letting Docker manage the databases:

```bash
docker compose up -d
```

The `app` service is behind the `app` Compose profile and will **not** start with this command — only `postgres` and `redis` do.

### Production-style image (no live reload)

Builds the multi-stage `Dockerfile` (compiles the jar, then runs it on a minimal JRE image) and starts the full stack with `SPRING_PROFILES_ACTIVE=prod`:

```bash
docker compose --profile app up -d --build
```

Useful commands:

```bash
docker compose --profile app ps          # status
docker compose --profile app logs -f app # follow app logs
docker compose --profile app down        # stop and remove containers
docker compose --profile app down -v     # also remove volumes (wipes DB data)
```

## Running without Docker

Requires a local **Java 21 JDK**, plus a running **PostgreSQL** and **Redis** instance reachable from your machine (installed locally, or started via `docker compose up -d` as above).

Make sure `src/main/resources/application-local.yaml` exists and points at your Postgres instance (see [Profile files](#profile-files)).

**Linux/macOS:**

```bash
./mvnw spring-boot:run
```

**Windows:**

```cmd
mvnw.cmd spring-boot:run
```

By default the app runs with `SPRING_PROFILES_ACTIVE=local` (see `application.yaml`). Override it, e.g. to test staging config locally:

```bash
SPRING_PROFILES_ACTIVE=stg ./mvnw spring-boot:run       # Linux/macOS
set SPRING_PROFILES_ACTIVE=stg && mvnw.cmd spring-boot:run   # Windows
```

## Common Development Commands

Run these from the project root; use `mvnw.cmd` instead of `./mvnw` on Windows.

```bash
./mvnw spring-boot:run          # run the app
./mvnw test                     # run unit/integration tests
./mvnw verify                   # run tests + Spotless format check (same as CI)
./mvnw clean package            # build the executable jar (target/cinema-booking-*.jar)
./mvnw clean package -DskipTests   # build without running tests

./mvnw spotless:check           # check code formatting only
./mvnw spotless:apply           # auto-format the code

./mvnw liquibase:update         # apply DB migrations manually via the Liquibase Maven plugin
                                 # (normally not needed — Spring Boot runs migrations automatically on startup)
```

Run a single test class:

```bash
./mvnw test -Dtest=GlobalExceptionHandlerTest
```

Integration tests that require Docker (Testcontainers-backed Postgres/Redis) are excluded from the default `test`/`verify` run via the `docker` Surefire group; a Docker daemon must be available if you enable/run them explicitly.

## API Documentation

Once the app is running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- Health check: `http://localhost:8080/actuator/health`

All business endpoints are served under the `/api/v1` prefix (`app.api.prefix` in `application.yaml`).

## Deploying to Staging / Production

The app is deployed as a Docker image built from the provided `Dockerfile` (multi-stage: builds the jar with Maven, then runs it on a minimal `eclipse-temurin` JRE Alpine image as a non-root `spring` user, exposing port `8080`).

1. **Build the image** on/for the target environment:

   ```bash
   docker build -t cinema-booking-api:latest .
   ```

2. **Provide configuration via environment variables** (no need to bake secrets into the image or commit `application-{profile}.yaml`):

   | Variable | Purpose |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `stg` or `prod` |
   | `SPRING_DATASOURCE_URL` | e.g. `jdbc:postgresql://<host>:5432/<db>` |
   | `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | DB credentials |
   | `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | Redis connection |
   | `LIQUIBASE_CONTEXTS` | changelog contexts to apply (e.g. `prod`) |
   | `CORS_ALLOWED_ORIGINS` | allowed frontend origin(s) |
   | `APP_SERVER_URL` | public base URL, shown in Swagger docs |
   | `JWT_SECRET` | **required** — secret used to sign/verify JWTs |
   | `COOKIE_SECURE` | should be `true` (default) so the refresh-token cookie is HTTPS-only |
   | `ADMIN_EMAIL` / `ADMIN_PASSWORD` | set once to seed the initial admin account; safe to leave blank afterwards |

   These map directly onto the equivalent `spring.*` / `app.*` YAML keys via Spring Boot's relaxed environment-variable binding, mirroring what `docker-compose.yml` does for the `app` service.

3. **Run it**, pointing at your managed/staging/production Postgres and Redis instances, e.g.:

   ```bash
   docker run -d \
     -p 8080:8080 \
     -e SPRING_PROFILES_ACTIVE=prod \
     -e SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/cinema_booking \
     -e SPRING_DATASOURCE_USERNAME=... \
     -e SPRING_DATASOURCE_PASSWORD=... \
     -e SPRING_DATA_REDIS_HOST=redis-host \
     -e SPRING_DATA_REDIS_PORT=6379 \
     -e LIQUIBASE_CONTEXTS=prod \
     --name cinema-booking-api \
     cinema-booking-api:latest
   ```

   Alternatively, reuse `docker-compose.yml` on the target host with a production `.env` file (set `SPRING_PROFILES_ACTIVE=prod` and real credentials) and run `docker compose --profile app up -d --build`.

4. **Logging**: under the `prod` profile, logs are written to rolling files (`logs/app.log`), and HTTP request logs to `logs/requests/request.log` (see `logback-spring.xml`). Any other profile logs to the console only. Mount `/app/logs` as a volume if you need logs to persist outside the container.

5. **Database migrations** run automatically on application startup via Liquibase — no manual step required, as long as `SPRING_DATASOURCE_*` and `LIQUIBASE_CONTEXTS` are set correctly for the target environment.

## CI

GitHub Actions runs on every push/PR to `develop`:

- **Build** (`.github/workflows/build.yml`): `./mvnw verify` (tests, Spotless check skipped here and enforced separately)
- **Format Check** (`.github/workflows/format-check.yml`): `./mvnw spotless:check`
