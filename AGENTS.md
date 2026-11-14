# AGENTS.md

Relay is a Spring Boot REST API (users, JWT auth, direct messages, AI-agent conversations, per-account host/event logs). Most of it is still specified, not built: `docs/api/api.yaml` is the contract, and only `GET /syn`, `POST /auth/register` and `GET /auth/verify/{token}` are implemented so far.

## Stack

- Java 27 (Gradle toolchain), Spring Boot 4.1.2, Gradle wrapper
- Spring Web MVC, Security, Data JPA, Data Redis, Flyway
- PostgreSQL (schema `relay_app`), Redis
- jjwt 0.12 (HMAC), Argon2 password hashing (Bouncy Castle)
- Jackson 3 (`tools.jackson.*`), Lombok
- Spring Mail with Thymeleaf HTML templates (`src/main/resources/templates/mail`); server-rendered pages in `templates/auth`

## Commands

```sh
./gradlew bootRun     # run locally (needs .env, Postgres, Redis)
./gradlew test        # tests; needs Docker (Testcontainers)
./gradlew check       # tests + JaCoCo gate: 85% instruction coverage, fails the build below it
```

## Configuration

`src/main/resources/application.properties` imports `.env` (gitignored, never commit it) as properties. Keys it must provide:

- `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`
- `app.jwt.secret`: Base64, at least 256 bits once decoded
- `app.cors.allowed-origins`: comma-separated
- `app.geoip.url`: base URL of the MaxMind-GeoIp service (`GET /geoip?ip=`). `app.geoip.timeout` (1s, connect and read) is set in `application.properties`.
- `spring.data.redis.host`, `spring.data.redis.port`, `spring.data.redis.username`, `spring.data.redis.password`. Use these, not `spring.data.redis.url`: when a URL is set, Spring reads credentials only from the URL and ignores the username and password keys. Add `spring.data.redis.ssl.enabled=true` if the provider requires TLS. `spring.data.redis.timeout` (500ms) is set in `application.properties`.
- `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`
- `spring.mail.username` (Gmail address, also the sender), `spring.mail.password` (Gmail App Password, needs 2-Step Verification; the account password is rejected). Host, port and STARTTLS are in `application.properties`.
- `app.base-url`: public URL that emailed links point to (`http://localhost:8080` locally)

Tests get Postgres, Redis, a Mailpit SMTP server, a stub GeoIP server and a random JWT secret from `TestcontainersConfig`; `src/test/resources/application.properties` holds the rest. Read delivered mail through Mailpit's REST API at `mailpitApiUrl()`.

## Layout (`src/main/java/com/techindna/relay`)

| Package | Holds |
|---|---|
| `controller` | REST controllers |
| `dto` | Request and response bodies named after their spec schemas (`RegisterInput`, `MessageBody`) |
| `entity` | Domain records returned by the API (`User`) and enums; `entity/email` holds `EmailDetails` and `EmailTemplate` |
| `service` | Business logic and transactions: `auth`, `mail`, `redis`, `mapper`, `event` (`HostEventService`: hosts and event logs), `event/auth` (emails sent by auth flows), `enums` |
| `repository` | Repositories |
| `repository/model` | JPA entities, prefixed `J` (`JUser`) |
| `exception`, `exception/http` | `ErrorBody`, `GlobalExceptionHandler`, one exception per HTTP status |
| `validator` | `DataValidator`: input normalization and validation; `AuthValidator` composes it per request body |
| `security`, `security/jwt` | Filter chain, JWT issue/verify, ABAC rules, client IP resolution |
| `config` | CORS, GeoIP `RestTemplate` |

## Conventions

- **Schema changes go through Flyway** (`src/main/resources/db/migration/V<n>__<name>.sql`), never `ddl-auto`. Hibernate only validates. Never edit an applied migration; add a new one.
- **GeoIP** comes from the separate MaxMind-GeoIp service over HTTP: `GeoIpService` calls `GET <app.geoip.url>/geoip?ip=<ip>` with the `RestTemplate` from `GeoIpClientConfig` and returns `null` on any `RestClientException` (down, timeout, 4xx/5xx), logged. Relay has no GeoIP endpoint or data of its own. The test stub answers `203.0.113.42` with Lisbon data and any other IP with 400.
- **Postgres enums** are named types in `relay_app`, mapped with `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`. Adding a Java enum value needs a migration too. Java and DB values are upper case; JSON writes them lower case (`spring.jackson.datatype.enum.write-enums-to-lowercase=true`, in both `application.properties` files), matching the spec.
- **Entities vs domain**: JPA entities (`J*`) stay in `repository/model`; controllers return `entity` records, never JPA entities (`JUser` carries the password hash).
- **Errors**: throw an `exception/http` exception; `GlobalExceptionHandler` turns it into `ErrorBody` JSON. Filters and security handlers write errors with `ErrorBody.send(...)`. Unknown exceptions become a generic 500, logged.
- **Validation**: use `DataValidator`. Semantic problems are 422 (`UnprocessableContentException`), malformed requests and bad pagination are 400. Username and email are trimmed and lower-cased before checks and storage; uniqueness is case-insensitive in the DB.
- **Auth**: stateless JWT in `Authorization: Bearer`. Claims: `sub` = user id, `role` (becomes `ROLE_<role>`), `ip_address`. `ABACRules.authorizeIpAddress` rejects a token used from a different IP with 403. Only `GET /syn`, `POST /auth/register`, `GET /auth/verify/*`, `POST /auth/mfa/confirm/register/*` and error dispatches are public; open new public endpoints explicitly in `SecurityConfig`.
- **Registration and verification mail**: `POST /auth/register` validates the whole body before hashing, saves the user with `verified=false`, stores a 15-minute token in Redis (`auth:verification:<uuid>` = `<VerificationFlow>:<userId>`), and sends the email synchronously inside the transaction. On any mail failure, delete the token and rethrow so the user insert rolls back and the client gets a 500. Duplicates are told apart by constraint name (`user_username_lower_key`, `user_email_lower_key`), never by the DB message, which embeds user input. Links are `app.base-url` + `/auth/verify/<token>`. The email shows the client IP's GeoIP details (continent, country, city, timezone, coordinates, accuracy radius, AS organization) from `GeoIpService`; a failed lookup is logged and those rows show `N/A`, it never fails the registration.
- **Verification page**: `GET /auth/verify/{token}` renders `templates/auth/auth-redirection.html`, a form that POSTs to `/auth/mfa/confirm/register/{token}`. It does not read Redis, consume the token or touch the account; invalid tokens get the page too and the confirm endpoint answers 401. When a login flow is added, read the token's flow here to pick the form target. It sends `Cache-Control: no-store` and `Referrer-Policy: no-referrer` because the token is in the URL.
- **Registration confirmation**: `POST /auth/mfa/confirm/register/{token}` (`VerificationService`) consumes the token atomically with `VerificationTokenService.consume` (Redis `GETDEL`), so it is gone even when the request then fails. A missing, malformed or non-`REGISTER` token, or a deleted user, is 401 `Invalid or expired token`. Otherwise it sets `verified=true`, records the host and an `INFO` "Account verified" event, and returns a JWT bound to the caller's IP plus the `User` record.
- **Hosts and event logs**: `HostEventService.recordHost` finds or creates the caller's host for (user, client IP) and sets `lastSeenAt`; `logEvent` writes an event log with the user agent (`Unknown` if missing, cut to 512 chars). Call both inside the flow's transaction so they roll back with it. `POST /auth/register` records the host and an `INFO` "Account registered" event before sending the email.
- **API contract**: implement endpoints to match `docs/api/api.yaml`. Update the spec in the same change when behavior differs.
- **Tests**: integration tests extend `TestcontainersConfig`. Keep coverage at or above 85% or `check` fails.

## Docs

`docs/` is an Obsidian vault: `api/api.yaml` (OpenAPI 3.0.3) and `cdm.canvas` (conceptual data model).
