# CurrencyHub — Exchange Rates Aggregator

A backend service built with **Spring Boot 4 / Java 25** that fetches currency exchange rates from
multiple external APIs every 5 minutes, keeps the latest values in **Redis**, stores the full history
in **MongoDB**, and exposes everything through a REST API.

---

## Table of Contents

- [Stack](#stack)
- [Architecture](#architecture)
- [Databases](#databases)
- [Configuration (.env)](#configuration-env)
- [Running the Project](#running-the-project)
- [Application Settings](#application-settings)
- [Git Strategy](#git-strategy)

---

## Stack

| Component     | Version / Technology                       |
|---------------|--------------------------------------------|
| Language      | Java 25 (LTS, virtual threads)             |
| Framework     | Spring Boot 4.0.6 (Spring MVC)             |
| Build         | Gradle **Kotlin DSL** (`build.gradle.kts`) |
| Cache         | Redis 7                                    |
| History store | MongoDB (local or Atlas)                   |
| Reverse proxy | Traefik v3                                 |
| Resilience    | Spring Retry + HttpClient timeouts         |

### Rate Providers

| Source                | Current | Historical (backfill) | API key required |
|-----------------------|:-------:|:---------------------:|:-----------------|
| `NBU`                 | ✅      | ✅                    | —                |
| `PRIVATBANK`          | ✅      | ✅                    | —                |
| `FRANKFURTER`         | ✅      | ✅                    | —                |
| `OPEN_EXCHANGE_RATES` | ✅      | ✅                    | `OXR_API_KEY`    |
| `MONOBANK`            | ✅      | ❌                    | —                |
| `ECB`                 | ✅      | ❌                    | —                |

---

## Architecture

```
config/                     — AppProperties, HttpClient, Redis, Async (virtual threads)
common/
  api/                      — provider clients + ProviderHttpExecutor (retry/timeout)
  exception/                — ProviderException, GlobalExceptionHandler
logic/
  job/                      — backfill jobs and the scheduled refresh job
  provider/                 — provider metadata, parallel rate fetching
  rate/                     — current/historical rates, Redis cache, Mongo storage
util/                       — helpers
```

---

## Databases

The service uses two storages with different roles and different deployment models.

| Storage     | Role                                    | Where it runs                                     | Configured by              |
|-------------|-----------------------------------------|---------------------------------------------------|----------------------------|
| **Redis 7** | Latest rates only, hot cache with TTL   | Inside `docker-compose.yml` (`currencyhub-redis`) | `REDIS_HOST/PORT/PASSWORD` |
| **MongoDB** | Full append-only history of every fetch | **External** — MongoDB Atlas or a local container | `MONGODB_URI`              |

### Redis — current rates

Comes up with `docker compose up`, data lives in the named volume `redis-data`.

- Key format: `rates:current:<SOURCE>:<BASE>:<CURRENCY>` — e.g. `rates:current:NBU:UAH:USD`
- Value: serialized `ExchangeRateDto` (JSON)
- TTL: `app.redis.current-rates-ttl-minutes` (10 min by default), so a stale rate expires on its own
  if the scheduler stops producing

### MongoDB — historical rates

**There is no MongoDB service in `docker-compose.yml`** — the application expects an external
cluster reachable at `MONGODB_URI`. Pick one of:

**A. MongoDB Atlas (cloud)** — the deployment option; put the SRV connection string into `.env`:

```dotenv
MONGODB_URI=mongodb+srv://<user>:<password>@<cluster>.mongodb.net/currencyhub?retryWrites=true&w=majority
```

**B. Local container** — for development:

```bash
docker run -d --name currencyhub-mongo -p 27017:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=secret \
  mongo:7
```

```dotenv
# app running on the host
MONGODB_URI=mongodb://admin:secret@localhost:27017/currencyhub?authSource=admin
# app running inside compose
MONGODB_URI=mongodb://admin:secret@host.docker.internal:27017/currencyhub?authSource=admin
```

## Configuration (.env)

Create a `.env` file in the project root based on `.env.example`:

```dotenv
# MongoDB — local or Atlas
MONGODB_URI=mongodb://admin:secret@localhost:27017/currencyhub?authSource=admin

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redissecret

# Open Exchange Rates (optional, disabled by default)
OXR_ENABLED=false
OXR_API_KEY=
```

> ⚠️ `.env` is listed in `.gitignore` — **never commit real credentials**.
> If a secret ever lands in a commit, rotate it instead of just deleting the file.

---

## Running the Project

```bash
docker compose up --build
```

Three services come up:

| Service   | Container             | Ports                            |
|-----------|-----------------------|----------------------------------|
| `app`     | `currencyhub-app`     | `8081` (API), `8082` (actuator)  |
| `redis`   | `currencyhub-redis`   | `6379`                           |
| `traefik` | `currencyhub-traefik` | `80` (proxy), `8090` (dashboard) |

Smoke check:

```bash
curl http://localhost:8081/api/v1/providers
curl -H "Host: currencyhub.localhost" http://localhost/api/v1/providers   # through Traefik
curl http://localhost:8082/actuator/info
```

Shut down (and drop the Redis volume):

```bash
docker compose down -v
```

> MongoDB is **not** part of compose — make sure `MONGODB_URI` points at a reachable cluster
> before starting. See [Databases](#databases).

---

## Application Settings

Key properties in `src/main/resources/application.yaml` (the `app` block):

| Property                                  | Default         | Description                             |
|-------------------------------------------|-----------------|-----------------------------------------|
| `app.scheduler.refresh-rate`              | `0 */5 * * * *` | Scheduled refresh cron                  |
| `app.external-api.timeout-seconds`        | `5`             | HTTP timeout per provider call          |
| `app.external-api.concurrency-limit`      | `10`            | Parallel requests while fetching rates  |
| `app.external-api.retry-attempts`         | `3`             | Retry attempts                          |
| `app.external-api.retry-initial-delay-ms` | `500`           | Initial backoff delay, multiplier ×2    |
| `app.external-api.retry-max-delay-ms`     | `10000`         | Exponential backoff ceiling             |
| `app.backfill.default-concurrency-limit`  | `10`            | Default backfill parallelism            |
| `app.backfill.max-concurrency-limit`      | `30`            | Maximum the API will accept             |
| `app.redis.current-rates-ttl-minutes`     | `10`            | TTL for `rates:current:*` keys          |
| `app.providers.<name>.enabled`            | `true`          | Enable/disable an individual provider   |

Retry fires on `IOException` and `5xx`/`429` responses; any other `4xx` is treated as
non-retryable and fails the request immediately.

---

## Git Strategy

The project is small and ships continuously, so no long-lived branches are needed —
we use **trunk-based development** with short-lived branches.

### Branches

| Branch                | Purpose                                                                |
|-----------------------|------------------------------------------------------------------------|
| `master`              | The only long-lived branch. Always green and deployable.               |
| `feat/<scope>-<desc>` | New functionality                                                      |
| `fix/<scope>-<desc>`  | Bug fix                                                                |
| `refactor/<desc>`     | Refactoring with no behavior change                                    |
| `docs/<desc>`         | Documentation only — README, ADRs, Postman collection                  |
| `hotfix/<desc>`       | Urgent production fix (branched off a tag, then merged back to master) |

Examples: `feat/provider-bestobmin`, `fix/backfill-date-range`, `refactor/rate-cache-keys`,
`docs/readme-and-git-strategy`.

### Repository Hygiene

- `.env` and any credentials stay in `.gitignore`; only `.env.example` is committed.
- CI/CD secrets live in GitHub Actions Secrets, not in `docker-compose.yml`.
- `build/`, `.gradle/` and `.idea/` are not committed; `gradle/wrapper/gradle-wrapper.jar` is.
- The Postman collection (`postman/`) is versioned alongside the API: changing an endpoint means
  updating the collection in the same PR.