# Running CareFlow locally

Two ways to run the backend locally, both already verified end-to-end. They
never mix: pick one connection path per run, never point a container backend
at `localhost` or a native backend at `mysql`.

```
LOCAL WINDOWS (native)                DOCKER (containerized)
─────────────────────                 ──────────────────────
Spring Boot (mvnw)                    Spring Boot container
      │                                     │
      │ localhost:3307                      │ mysql:3306
      ▼                                     ▼
Windows MySQL service                 MySQL container
(careflow database)                   (careflow database, separate data)
```

The two `careflow` databases are entirely separate — a native MySQL install on
your Windows machine, and a MySQL container with its own named volume. Data in
one is invisible to the other. Neither setup touches the other's storage.

---

## Option A — Native Windows (no Docker)

Backend and frontend run as normal OS processes; MySQL is the Windows MySQL
service already installed on this machine, listening on **port 3307** (3306
was already taken by another local service, so this is specific to this
machine, not assumed anywhere in the code).

**One-time setup** — create the database and a non-root app user:

```bash
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -h 127.0.0.1 -P 3307 -u root -p < deployment\local-mysql-setup.sql
```

(Edit the placeholder password in that file first, or run its statements
manually in MySQL Workbench, then restore the placeholder — never commit a
real password.)

**Run the backend:**

```bash
cd backend
cp .env.local.example .env.local   # fill in the real DB_PASSWORD
set -a; source .env.local; set +a  # or export the variables directly
./mvnw spring-boot:run
```

```bash
cd frontend
npm install
NEXT_PUBLIC_API_URL=http://localhost:8080 npm run dev
```

Verified 2026-08-17: `Started CareFlowApplication`, Flyway applied migration
v1, Hikari connected, `/actuator/health` → `{"status":"UP"}`, a patient created
via the API round-tripped through MySQL and back.

---

## Option B — Docker Compose (backend + MySQL only)

MySQL and the backend run as containers on a private Docker network; the
frontend still runs natively with `npm run dev`, pointed at the containerized
backend's published port. This is the target shape for GCE later, minus Nginx
and TLS.

```
Frontend (native npm run dev, :3000)
        │
        │ HTTP → localhost:8080 (published by the backend container)
        ▼
Spring Boot container ── mysql:3306 (Docker DNS, private network) ──▶ MySQL container
```

```bash
cp .env.example .env       # fill in DB_PASSWORD, MYSQL_ROOT_PASSWORD, JWT_SECRET
cd deployment
docker compose up -d mysql backend     # only these two — frontend stays native
docker compose ps
docker compose logs -f backend
```

- API → <http://localhost:8080>
- Swagger → <http://localhost:8080/swagger-ui.html>
- `/actuator/health` → <http://localhost:8080/actuator/health>

Then point the native frontend at it exactly as in Option A:

```bash
cd frontend
NEXT_PUBLIC_API_URL=http://localhost:8080 npm run dev
```

**Inside the `careflow` Docker network**, the backend reaches MySQL as
`mysql:3306` — Compose's built-in DNS resolves the service name `mysql` to the
MySQL container's address, so this is never `localhost` and never a hardcoded
IP. MySQL itself publishes **no port to the host** by default (see
`docker-compose.yml`), matching production, where MySQL is never reachable
outside the private network. Uncomment the `ports:` block under the `mysql`
service only if you need a host tool (Workbench, mysql CLI) to inspect the
containerized database directly.

Bring the stack down (keeps the named volume, so data survives):

```bash
docker compose down
```

Add `-v` only if you intentionally want to wipe the container's MySQL data:

```bash
docker compose down -v
```

Set `SEED_ENABLED=true` in `.env` to load the fictional demo dataset on first
start. The seeder refuses to run if any patient already exists, so it can
never overwrite real data.

---

## Running the tests

```bash
cd backend && ./mvnw test        # 76 tests, no database container required
cd frontend && npm run lint && npx tsc --noEmit
```

Integration tests boot the full application context against H2 in
MySQL-compatibility mode, configured by `src/test/resources/application-test.yml`.
That file is test-scoped and never packaged into the runtime image — neither
Option A nor Option B above use it.

`JWT_SECRET` must supply at least 32 bytes of key material — the application
refuses to start otherwise rather than signing tokens with a weak key. Generate
one with `openssl rand -base64 48`.
