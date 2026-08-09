# Cinema Booking API

Spring Boot (4.0.7, Java 21) backend for the Cinema Booking system.

## Prerequisites

- Java 21 (JDK)
- Maven (or use the bundled `mvnw` / `mvnw.cmd` wrapper)
- PostgreSQL and Redis — either installed locally, or run via Docker (see [Docker](#docker) below)

## Docker

The easiest way to get PostgreSQL and Redis running locally is with the bundled `docker-compose.yml`.

Start the dependencies only (app still runs locally via `mvnw`):

```bash
docker compose up -d
```

This starts:

- `postgres` on `localhost:5432` (db `cinema_booking_db`, user `cinema_booking_user`, password `123456` — matching `application.example.yaml`)
- `redis` on `localhost:6379`

Data persists in named volumes (`postgres_data`, `redis_data`) across restarts. Stop the containers with `docker compose down` (add `-v` to also wipe the volumes).

To also run the app itself in Docker (build image + full stack):

```bash
docker compose --profile app up --build
```

The `app` service builds from the bundled `Dockerfile` (multi-stage Maven build, JRE 21 runtime), runs migrations automatically on startup, and is reachable on `localhost:8080`. It connects to the `postgres`/`redis` containers over the Docker network, so no local Postgres/Redis install or `application-local.yaml` is needed for this mode.

### Overriding defaults (e.g. for production)

`docker-compose.yml` reads `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `LIQUIBASE_CONTEXTS` from the environment, falling back to the local dev defaults shown above if unset. To use different values:

1. Copy `.env.example` to `.env` and fill in real values:

   ```bash
   cp .env.example .env
   ```

2. Alternatively, point at a different env file explicitly (handy for keeping separate dev/prod files):

   ```bash
   docker compose --env-file .env.prod up -d
   ```

3. Or set the variables directly in the shell/CI environment before running `docker compose` — env vars take precedence over `.env`.

For a real production deploy, also set `SPRING_PROFILES_ACTIVE=prod` on the `app` service (and provide `application-prod.yaml`, per `.gitignore`) rather than reusing this compose file as-is — it's set up for local development (bind-mounted volumes, no secrets management, `restart: unless-stopped` instead of an orchestrator). Prefer injecting `SPRING_DATASOURCE_PASSWORD` etc. via your platform's secret store instead of a plain `.env` file when deploying for real.

### Changing ports (e.g. to avoid local conflicts)

If `5432`, `6379`, or `8080` are already in use on your machine, override the host-side port via `.env` (or the shell/CI environment) — the containers keep listening on the standard port internally, only the host mapping changes:

```dotenv
POSTGRES_PORT=5433
REDIS_PORT=6380
APP_PORT=8081
```

If you change `POSTGRES_PORT` or `REDIS_PORT` while running the app locally with `mvnw` (not the containerized `app` service), update the port in your `application-local.yaml` / `liquibase.properties` to match, since those connect to the host-mapped port.

## Setup

1. Copy the example configuration and fill in your local credentials:

   ```bash
   cp src/main/resources/application.example.yaml src/main/resources/application-local.yaml
   ```

   On Windows PowerShell:

   ```powershell
   Copy-Item src/main/resources/application.example.yml src/main/resources/application-local.yaml
   ```

2. Edit `src/main/resources/application-local.yaml` with your PostgreSQL username/password. This file is gitignored and must not be committed.

3. Create the database (if it doesn't exist yet):

   ```bash
   createdb cinema_booking_db
   ```

## Build

```bash
./mvnw clean install
```

On Windows:

```powershell
mvnw.cmd clean install
```

Compile only (skip tests):

```bash
./mvnw clean install -DskipTests
```

## Run

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
mvnw.cmd spring-boot:run
```

The app runs with the `local` Spring profile active by default (see `application.yaml`), loading `application-local.yaml` for datasource credentials.

## Test

```bash
./mvnw test
```

On Windows:

```powershell
mvnw.cmd test
```

Tests use Testcontainers to spin up ephemeral PostgreSQL and Redis containers (`TestcontainersConfiguration`), so **Docker must be running** locally to execute the test suite.

Run a single test class:

```bash
./mvnw test -Dtest=CinemaBookingApplicationTests
```

## Code formatting (Spotless)

Code style is enforced with the [Spotless Maven plugin](https://github.com/diffplug/spotless) using `google-java-format`. The `verify` phase (and CI) fails if any file isn't formatted.

Format all files:

```bash
./mvnw spotless:apply
```

Check formatting without modifying files:

```bash
./mvnw spotless:check
```

On Windows:

```powershell
mvnw.cmd spotless:apply
mvnw.cmd spotless:check
```

Run `./mvnw spotless:apply` before committing to avoid CI failures.

## Database migrations (Liquibase)

Migrations live under `src/main/resources/db/changelog/`. They run automatically on application startup via `spring-boot-starter-liquibase` — no manual step is needed for normal development.

To run migrations standalone (without starting the app), first create a `liquibase.properties` file in the project root (this file is gitignored and must not be committed) with your local database credentials:

```properties
url=jdbc:postgresql://<host>:<port>/<database>
username=<username>
password=<password>
driver=org.postgresql.Driver
contexts=dev
```

Then use the `liquibase-maven-plugin`:

```bash
./mvnw liquibase:update            # apply pending migrations
./mvnw liquibase:status            # show pending changesets
./mvnw liquibase:rollback -Dliquibase.rollbackCount=1   # roll back the last changeset
./mvnw liquibase:validate          # validate the changelog
```

On Windows:

```powershell
mvnw.cmd liquibase:update
mvnw.cmd liquibase:status
```

## Other useful commands

Clean build artifacts:

```bash
./mvnw clean
```

Package into an executable JAR:

```bash
./mvnw clean package
java -jar target/cinema-booking-0.0.1-SNAPSHOT.jar
```

Check for dependency updates:

```bash
./mvnw versions:display-dependency-updates
```
