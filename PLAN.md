# DevOps setup plan

Branch: `devops-setup` (cut from `fix/price-monitor`, which is 3 commits ahead of `master`).

## Audit — what is there today

| Area | Finding |
| --- | --- |
| Layout | Git root is the repo folder; the Spring app lives in `priceMonitor/` |
| Build | Maven (wrapper 3.9.11), Java 21, Spring Boot 4.0.0 |
| Dependencies | webmvc, data-jpa, H2, Jsoup 1.17.2, Lombok |
| Database | File-based H2 (`./data/pricedb`), schema managed by `ddl-auto=update`, H2 console servlet registered in `H2Config` |
| Entities | `Product` (id, title, current_price, last_checked, unique url) and `PricePoint` (id, product_id, price, checked_at) |
| Scheduling | `PriceScheduler.checkAll()` — `@Scheduled` fixed delay, default 1 h, 5 s pause between requests, single thread |
| Scraping | `ScraperService` — Jsoup fetch + CSS selectors, JSON-LD fallback; every failure is a plain `ScrapeException` |
| Config | One `application.properties` with hardcoded values; the test copy shadows it completely |
| Tests | 3 classes, 11 tests: parser on HTML snippets, service with a mocked scraper on in-memory H2, context load. None touch the network |
| Docker | Two-stage Dockerfile, but runs as root, fat jar (no layers), no JVM flags, no healthcheck |
| CI | Runs `mvnw verify` and a throwaway `docker build`; nothing is published |
| Observability | None (no Actuator, no metrics, plain-text logs) |

**Build status:** `./mvnw -B verify` passes as-is (exit 0, 11 tests green). Nothing was broken.

Things noticed that affect the work:

- The repo's default branch is `master`, not `main`. The publish job will accept either.
- `mvnw` shows as modified only because Windows dropped the executable bit; it is left out of commits.
- Scrape failures carry no cause, so a `reason` tag needs a small change to `ScrapeException`.
- Nothing can start a scrape cycle on demand, which makes the pipeline hard to demo or verify.
- Prices are stored as `double`. Money should be `NUMERIC`/`BigDecimal`; left out of the DevOps phases
  because it is a domain change. Done afterwards in its own commit (`V2__prices_as_numeric.sql`).

## What will be done

1. **Containerization** — three-stage Dockerfile (Maven build → layer extraction → Alpine JRE), non-root user,
   layered jar, container-aware JVM flags, healthcheck, `.dockerignore`. Config moved to environment variables
   with `dev` / `prod` profiles and a `.env.example`.
2. **Database** — PostgreSQL 17. Flyway owns the schema (`V1__baseline_schema.sql` written from the current
   entities); Hibernate drops to `ddl-auto=validate`. H2 stays as a test-only dependency in PostgreSQL mode, and
   tests run the real migration, so entity/migration drift fails the build. `H2Config` is removed.
3. **docker-compose** (repo root) — `app`, `postgres`, `prometheus`, `grafana`; a healthcheck on each, the app
   waits for a healthy Postgres, named volumes, working defaults so `docker compose up -d` needs no `.env`.
4. **Observability** — Actuator on a separate management port (9001), Prometheus registry, four scraper metrics
   plus a tracked-products gauge, an actuator endpoint to trigger a cycle, Prometheus scrape config and alert
   rules, Grafana datasource + dashboard provisioned from the repo, ECS JSON logs in `prod`.
5. **CI/CD** — test job with Maven caching; image build on every run with layer caching, pushed to `ghcr.io`
   (`<sha>` and `latest`) only from the default branch. New tests: WireMock for the HTTP layer and failure
   classification, saved HTML fixtures for the parser, price-change detection, scheduler cycle.
6. **Docs** — README rewrite with a Mermaid architecture diagram, one-command run, URLs and dev credentials,
   metrics reference, CI/CD description, screenshot placeholders.
7. **Verification** — bring the whole stack up, check container health, app health, Prometheus target, custom
   metrics after a triggered cycle, Grafana dashboard, and the full test suite.

## Changes to existing code (kept minimal)

- `ScrapeException` gains a failure reason (`timeout` / `blocked` / `parse_error` / `other`).
- `ScraperService` classifies failures and counts checks/failures.
- `PriceService` counts detected price changes.
- `PriceScheduler` times each cycle.
- `H2Config` deleted; `application.properties` split into base + `dev` + `prod`.
- Tests switch to a `test` profile instead of shadowing the main properties file.

Public REST API and scraping behaviour stay the same.
