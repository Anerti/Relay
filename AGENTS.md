# AGENTS.md

Relay is a Spring Boot REST API (users, JWT auth, direct messages, AI-agent conversations, per-account host/event logs). Most of it is still specified, not built: `docs/api/api.yaml` is the contract, and only `GET /syn` is implemented so far.

## Stack

- Java 27 (Gradle toolchain), Spring Boot 4.1.2, Gradle wrapper
- Spring Web MVC, Security, Data JPA, Data Redis, Flyway
- PostgreSQL (schema `relay_app`), Redis
- jjwt 0.12 (HMAC), Argon2 password hashing (Bouncy Castle)
- Jackson 3 (`tools.jackson.*`), Lombok

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
- `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`
- `spring.mail.username` (Gmail address), `spring.mail.password` (Gmail App Password, needs 2-Step Verification; the account password is rejected). Host, port and STARTTLS are in `application.properties`.

Tests get Postgres, Redis, a Mailpit SMTP server and a random JWT secret from `TestcontainersConfig`; `src/test/resources/application.properties` holds the rest.

## Layout (`src/main/java/com/techindna/relay`)

| Package | Holds |
|---|---|
| `controller` | REST controllers |
| `entity` | Domain records returned by the API (`User`) and enums |
| `repository/model` | JPA entities, prefixed `J` (`JUser`) |
| `exception`, `exception/http` | `ErrorBody`, `GlobalExceptionHandler`, one exception per HTTP status |
| `validator` | `DataValidator`: input normalization and validation |
| `security`, `security/jwt` | Filter chain, JWT issue/verify, ABAC rules, client IP resolution |
| `config` | CORS |

## Conventions

- **Schema changes go through Flyway** (`src/main/resources/db/migration/V<n>__<name>.sql`), never `ddl-auto`. Hibernate only validates. Never edit an applied migration; add a new one.
- **Postgres enums** are named types in `relay_app`, mapped with `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`. Adding a Java enum value needs a migration too.
- **Entities vs domain**: JPA entities (`J*`) stay in `repository/model`; controllers return `entity` records, never JPA entities (`JUser` carries the password hash).
- **Errors**: throw an `exception/http` exception; `GlobalExceptionHandler` turns it into `ErrorBody` JSON. Filters and security handlers write errors with `ErrorBody.send(...)`. Unknown exceptions become a generic 500, logged.
- **Validation**: use `DataValidator`. Semantic problems are 422 (`UnprocessableContentException`), malformed requests and bad pagination are 400. Username and email are trimmed and lower-cased before checks and storage; uniqueness is case-insensitive in the DB.
- **Auth**: stateless JWT in `Authorization: Bearer`. Claims: `sub` = user id, `role` (becomes `ROLE_<role>`), `ip_address`. `ABACRules.authorizeIpAddress` rejects a token used from a different IP with 403. Only `GET /syn` and error dispatches are public; open new public endpoints explicitly in `SecurityConfig`.
- **API contract**: implement endpoints to match `docs/api/api.yaml`. Update the spec in the same change when behavior differs.
- **Tests**: integration tests extend `TestcontainersConfig`. Keep coverage at or above 85% or `check` fails.

## Docs

`docs/` is an Obsidian vault: `api/api.yaml` (OpenAPI 3.0.3) and `cdm.canvas` (conceptual data model).
