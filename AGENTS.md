# AGENTS.md

Relay is a Spring Boot REST API (users, JWT auth, direct messages, AI-agent conversations, per-account host/event logs). Most of it is still specified, not built: `docs/api/api.yaml` is the contract, and only `GET /syn`, `POST /auth/register`, `POST /auth/login`, `GET /auth/verify/{token}`, `POST /auth/mfa/confirm/register/{token}`, `POST /auth/mfa/confirm/login/{token}` and `GET /users/{userId}` are implemented so far.

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

`server.forward-headers-strategy=none` is set in `application.properties`: Relay is not deployed behind a proxy, so `X-Forwarded-*` headers are ignored and the client IP is the address that opened the connection.

Tests get Postgres, Redis, a Mailpit SMTP server, a stub GeoIP server and a random JWT secret from `TestcontainersConfig`; `src/test/resources/application.properties` holds the rest. Read delivered mail through Mailpit's REST API at `mailpitApiUrl()`.

## Layout (`src/main/java/com/techindna/relay`)

| Package | Holds |
|---|---|
| `controller` | REST controllers |
| `dto` | Request and response bodies named after their spec schemas (`RegisterInput`, `MessageBody`) |
| `entity` | Domain records returned by the API (`User`) and enums; `entity/email` holds `EmailDetails` and `EmailTemplate` |
| `service` | Business logic and transactions: `UserService`, `auth`, `mail`, `redis`, `mapper`, `event` (`HostEventService`: hosts and event logs), `event/auth` (emails sent by auth flows), `enums` |
| `repository` | Repositories |
| `repository/model` | JPA entities, prefixed `J` (`JUser`) |
| `exception`, `exception/http` | `ErrorBody`, `GlobalExceptionHandler`, one exception per HTTP status |
| `validator` | `DataValidator`: input normalization and validation; `AuthValidator` composes it per request body |
| `security`, `security/jwt` | Filter chain, JWT issue/verify, ABAC and RBAC rules, client IP resolution |
| `config` | CORS, GeoIP `RestTemplate` |

## Conventions

- **Schema changes go through Flyway** (`src/main/resources/db/migration/V<n>__<name>.sql`), never `ddl-auto`. Hibernate only validates. Never edit an applied migration; add a new one.
- **GeoIP** comes from the separate MaxMind-GeoIp service over HTTP: `GeoIpService` calls `GET <app.geoip.url>/geoip?ip=<ip>` with the `RestTemplate` from `GeoIpClientConfig` and returns `null` on any `RestClientException` (down, timeout, 4xx/5xx), logged. Relay has no GeoIP endpoint or data of its own. The test stub answers `203.0.113.42` with Lisbon data (without `ipAddress`, so the email must show the request's IP) and any other IP with 400.
- **Postgres enums** are named types in `relay_app`, mapped with `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`. Adding a Java enum value needs a migration too. Java and DB values are upper case; JSON writes them lower case (`spring.jackson.datatype.enum.write-enums-to-lowercase=true`, in both `application.properties` files), matching the spec.
- **Entities vs domain**: JPA entities (`J*`) stay in `repository/model`; controllers return `entity` records, never JPA entities (`JUser` carries the password hash).
- **Errors**: throw an `exception/http` exception; `GlobalExceptionHandler` turns it into `ErrorBody` JSON, always with `Content-Type: application/json` so it is written whatever the `Accept` header says. Filters and security handlers write errors with `ErrorBody.send(...)`. Wrong method is 405 with `Allow`, an `Accept` that excludes the response type is 406. JSON endpoints with side effects declare `produces = MediaType.APPLICATION_JSON_VALUE` so the 406 comes before the handler runs. Unknown exceptions become a generic 500, logged.
- **Validation**: use `DataValidator`. Semantic problems are 422 (`UnprocessableContentException`), malformed requests and bad pagination are 400. Lengths count Unicode code points, matching the spec's `maxLength` and Postgres `VARCHAR(n)`. Username and email are trimmed and lower-cased before checks and storage; uniqueness is case-insensitive in the DB.
- **Auth**: stateless JWT in `Authorization: Bearer`. Claims: `sub` = user id, `role` (becomes `ROLE_<role>`), `ip_address`. `ABACRules.authorizeIpAddress` rejects a token used from a different IP with 403. Only `GET /syn`, `/auth/register` and `/auth/login` (every method, so wrong ones get 405 instead of 401), `GET /auth/verify/*`, `POST /auth/mfa/confirm/register/*`, `POST /auth/mfa/confirm/login/*` and error dispatches are public; open new public endpoints explicitly in `SecurityConfig`.
- **Registration and verification mail**: `POST /auth/register` validates the whole body before hashing, saves the user with `verified=false`, stores a 15-minute token in Redis (`auth:verification:<uuid>` = `<VerificationFlow>:<userId>`), and sends the email synchronously inside the transaction. On any mail failure, delete the token and rethrow so the user insert rolls back and the client gets a 500. Duplicates are told apart by constraint name (`user_username_lower_key`, `user_email_lower_key`), never by the DB message, which embeds user input. Links are `app.base-url` + `/auth/verify/<token>`. The email shows the client IP's GeoIP details (continent, country, city, timezone, coordinates, accuracy radius, AS organization) from `GeoIpService`; a failed lookup is logged and those rows show `N/A`, it never fails the registration.
- **Login**: `POST /auth/login` validates either username or email plus the password (both together is 422), then rejects `LOCKED` accounts, unverified accounts and banned hosts; `INACTIVE` accounts can still request a login link. Wrong credentials use one 401 message whether the account exists or not, and an unknown username/email still hashes the given password with Argon2 so timing does not reveal the account. An unknown username/email is not logged; every failed attempt on a known account gets a persisted `[LOGIN]` event on the request host — `MEDIUM` `[LOGIN FAILED]: invalid credentials` for a wrong password, or `MEDIUM` `[LOGIN FAILED]: account locked`, `[LOGIN FAILED]: account not verified`, `[LOGIN FAILED]: host banned` for blocked attempts. The method uses `@Transactional(noRollbackFor = {ForbiddenException.class, UnauthorizedException.class})` so those audit writes commit while the 401/403 is returned. On success it records the caller's host and an `INFO` `[LOGIN]: Verification link requested` event, stores a 15-minute `LOGIN` token in Redis and sends the login verification email synchronously inside the transaction; a mail failure deletes the token and rolls everything back.
- **Verification page**: `GET /auth/verify/{token}` renders `templates/auth/auth-redirection.html`, a form that POSTs to `/auth/mfa/confirm/register/{token}` or `/auth/mfa/confirm/login/{token}` depending on the token's flow. `VerificationService.confirmationFlow` reads the flow from Redis through `VerificationTokenService.findFlow` without consuming the token; unknown or malformed tokens, and Redis failures (logged), default to the registration target. It does not touch the account, and the confirm endpoint answers 401. It sends `Cache-Control: no-store` and `Referrer-Policy: no-referrer` because the token is in the URL.
- **Registration confirmation**: `POST /auth/mfa/confirm/register/{token}` (`VerificationService`) consumes the token atomically with `VerificationTokenService.consume` (Redis `GETDEL`), so it is gone even when the request then fails. A missing, malformed or non-`REGISTER` token, or a deleted user, is 401 `Invalid or expired token`. Otherwise it sets `verified=true`, records the host and an `INFO` "Account verified" event, and returns a JWT bound to the caller's IP plus the `User` record.
- **Login confirmation**: `POST /auth/mfa/confirm/login/{token}` also consumes the token first, so it cannot be retried. A missing, malformed or non-`LOGIN` token, or a deleted user, is 401 `Invalid or expired token` and is not logged; a non-active (locked or inactive) or unverified account, or a banned host, is 403 and records the host plus a `MEDIUM` `[LOGIN FAILED]: account locked or inactive`, `[LOGIN FAILED]: account not verified` or `[LOGIN FAILED]: host banned` event that commits despite the exception. Otherwise it records the host and an `INFO` `[LOGIN]: Account signed in` event and returns a JWT bound to the caller's IP plus the `User` record.
- **Hosts and event logs**: `HostEventService.recordHost` upserts the caller's host for (user, client IP) with `INSERT ... ON CONFLICT DO UPDATE` on `last_seen_at`, so concurrent first requests from one IP do not hit the unique key; `ClientDataConfig.ipAddress` strips an IPv6 zone id (`fe80::1%1`), which Postgres `inet` rejects. `logEvent` writes an event log with the user agent (`Unknown` if missing, cut to 512 chars). Call both inside the flow's transaction so they roll back with it, unless an exception is explicitly marked `noRollbackFor`. `POST /auth/register` records the host and an `INFO` `[REGISTRATION]: Account registered` event before sending the email; `POST /auth/login` records the host and `[LOGIN]` events, and its confirmation records an `INFO` `[LOGIN]: Account signed in` event.
- **Account access**: `RBACRules.authorize` lets a superuser reach a guest account or their own and a guest only their own; anything else is 403 `Cannot access this resource.` (a token without a role is 403 too). `GET /users/{userId}` (`UserService.getUser`) looks the user up first, so an unknown id is 404 `User not found` before the access check; a malformed id is 400.
- **API contract**: implement endpoints to match `docs/api/api.yaml`. Update the spec in the same change when behavior differs.
- **Tests**: integration tests extend `TestcontainersConfig`. Keep coverage at or above 85% or `check` fails.

## Docs

`docs/` is an Obsidian vault: `api/api.yaml` (OpenAPI 3.0.3) and `cdm.canvas` (conceptual data model).
