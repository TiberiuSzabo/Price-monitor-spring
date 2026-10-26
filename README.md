# Price Monitor

[![CI](https://github.com/TiberiuSzabo/Price-monitor-spring/actions/workflows/ci.yml/badge.svg)](https://github.com/TiberiuSzabo/Price-monitor-spring/actions/workflows/ci.yml)

Spring Boot service that tracks product prices on eMAG.ro. You give it a product link (or a whole
search/category page), it scrapes the title and price with Jsoup, saves them, and re-checks every
product on a schedule so you get a price history over time.

## Stack

Java 21, Spring Boot 4, Spring Data JPA, H2 (file-based), Jsoup, JUnit 5 + Mockito, Docker, GitHub Actions.

## How it works

- **Scraping:** `ScraperService` reads `h1.page-title` and the main price (`[data-test=main-price]`,
  falling back to `.product-new-price` and then the JSON-LD `offers.price`). eMAG prices look like
  `6.199,00 Lei`, so `.` is the thousands separator and `,` the decimal one.
- **Discovery:** on a listing page it collects every `a.card-v2-title` link and strips the tracking
  query params, so the same product isn't stored twice.
- **History:** every check adds a `PricePoint` row; `Product` keeps the latest price.
- **Scheduler:** `PriceScheduler` re-checks all products every hour (configurable), with a pause
  between requests so the site isn't hammered. If a page no longer has a title/price the error is
  logged instead of saving bad data.
- Only `emag.ro` URLs are accepted, so the API can't be used to fetch arbitrary sites.

## Run

```bash
cd priceMonitor
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

or with Docker:

```bash
docker build -t price-monitor priceMonitor
docker run -p 8081:8081 -v pricedata:/app/data price-monitor
```

Data is stored in `priceMonitor/data/` (H2 file). The H2 console is at http://localhost:8081/h2-console
(JDBC URL `jdbc:h2:file:./data/pricedb`, user `sa`, empty password).

## API

| Method | Path | Body | Description |
| --- | --- | --- | --- |
| `POST` | `/api/products` | `{"url": "<product link>"}` | Track a product (scrapes it right away) |
| `POST` | `/api/products/discover` | `{"url": "<search/category link>"}` | Track every product on a listing page |
| `GET` | `/api/products` | | Tracked products with current price |
| `GET` | `/api/products/{id}/history` | | Price history of one product |
| `DELETE` | `/api/products/{id}` | | Stop tracking a product |

Example:

```bash
curl -X POST localhost:8081/api/products -H "Content-Type: application/json" \
  -d '{"url":"https://www.emag.ro/telefon-mobil-apple-iphone-17-pro-256gb-5g-cosmic-orange-mg8h4zd-a/pd/D099FV3BM/"}'
```

Errors come back as `application/problem+json`: `400` bad URL, `404` unknown product,
`502` when eMAG can't be read.

## Tests

```bash
cd priceMonitor
./mvnw test
```

Parser tests run on small HTML snippets (no network), service tests mock the scraper and use an
in-memory H2. CI runs them on every push and builds the Docker image.

## Next

- Email alert when a price drops under a target (JavaMailSender)
- PostgreSQL instead of H2 for a real deployment
