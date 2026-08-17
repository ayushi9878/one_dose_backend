# Running CareFlow locally

The supported path is Docker Compose, which starts MySQL 8, the backend and the
frontend together with the same MySQL engine used in production.

```bash
cp .env.example .env      # edit the values first
cd deployment
docker compose up --build
```

- Frontend → <http://localhost:3000>
- API → <http://localhost:8080>
- Swagger → <http://localhost:8080/swagger-ui.html>

Set `SEED_ENABLED=true` in `.env` to load the fictional demo dataset on first
start. The seeder refuses to run if any patient already exists, so it can never
overwrite real data.

## Without Docker

The backend reads every database setting from the environment, so point it at
any reachable MySQL 8 instance:

```bash
cd backend
DB_HOST=localhost DB_PORT=3306 DB_NAME=careflow \
DB_USERNAME=careflow_app DB_PASSWORD=<password> \
JWT_SECRET=<at least 32 bytes> SEED_ENABLED=true \
./mvnw spring-boot:run
```

```bash
cd frontend
npm install
NEXT_PUBLIC_API_URL=http://localhost:8080 npm run dev
```

`JWT_SECRET` must supply at least 32 bytes of key material — the application
refuses to start otherwise rather than signing tokens with a weak key. Generate
one with `openssl rand -base64 48`.

## Running the tests

```bash
cd backend && ./mvnw test        # 74 tests, no database container required
cd frontend && npm run lint && npx tsc --noEmit
```

Integration tests boot the full application context against H2 in
MySQL-compatibility mode, configured by `src/test/resources/application-test.yml`.
That file is test-scoped and never packaged into the runtime image.
