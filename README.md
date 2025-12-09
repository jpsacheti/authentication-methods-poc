# Authentication POC

This project is a Proof of Concept (POC) demonstrating two authentication methods using Java and Spring Boot:
1. Password Authentication: traditional username/password login with BCrypt hashing.
2. WebAuthn (FIDO2): passwordless authentication using FIDO2 standards (e.g., Passkeys on Android/iOS/macOS/Windows and security keys like YubiKey).

Both methods produce a JWT (JSON Web Token) as the final artifact of a successful authentication. In this POC the JWT is output-only for demonstration; there is no bearer-token authentication filter in the request pipeline.

## What this POC focuses on
- Compare Password vs WebAuthn login flows side by side.
- Keep infrastructure minimal and dev-friendly.
- Favor clarity over production-hardening. No rate-limits, no user-enumeration protections, etc.

## Technologies
- Java 21
- Spring Boot 3.2.x
- Spring Security
- PostgreSQL (Dockerized)
- Yubico WebAuthn Server Core (FIDO2)
- JJWT (JWT generation)

## Features overview
- Username/password flow with BCrypt password hashing and JWT issuance
- WebAuthn registration and authentication with server-side verification
- Passkeys (platform authenticators) and external security keys (cross‑platform, e.g., YubiKey)
- Choice of authenticator attachment during registration: platform vs cross‑platform
- Minimal static frontend to exercise the flows (`src/main/resources/static`)

## Configuration
Application properties relevant to the POC:

- WebAuthn Relying Party
  - `webauthn.rp.id=localhost`
  - `webauthn.rp.name=Auth POC`
  - `webauthn.origin=http://localhost:8080`

- JWT (output-only)
  - `jwt.secret` (can be set via environment variable `JWT_SECRET`)
  - `jwt.ttl-hours` (default 24)

- Database
  - In dev, the app connects to PostgreSQL at `jdbc:postgresql://localhost:5432/authdb` by default.
  - With Docker Compose, the app service is wired to the `db` service automatically.

## REST API and WebAuthn flows

### Password Authentication
- `POST /auth/register`: register a new user.
  - Body: `{ "username": "user", "password": "password", "displayName": "User Name" }`
  - Returns: `{ "token": "<jwt>" }`
- `POST /auth/login`: login with password.
  - Body: `{ "username": "user", "password": "password" }`
  - Returns: `{ "token": "<jwt>" }`

### WebAuthn Authentication
Note: These endpoints require a browser environment to interact with authenticators. The included static frontend wires everything for you.

- `POST /webauthn/register/start?username=user[&attachment=platform|cross-platform|any]`: start registration ceremony. Returns options JSON.
  - `attachment` (optional):
    - `platform` (default): prefer platform authenticator (passkey on the device)
    - `cross-platform`: prefer external authenticator (e.g., YubiKey)
    - `any`: let the browser decide
- `POST /webauthn/register/finish?username=user`: finish registration. Body is the credential JSON from the browser.
- `POST /webauthn/login/start?username=user`: start login ceremony. Returns options JSON.
- `POST /webauthn/login/finish?username=user`: finish login. Body is the assertion JSON from the browser.
  - Returns: `{ "token": "<jwt>" }`

Implementation notes:
- The WebAuthn user handle is the user UUID bytes (opaque identifier), not the username.
- Registration/login challenges (options) are persisted in Postgres and removed after finish.
- Registration allows selecting authenticator attachment; login works with any of the user’s registered credentials.

## Static frontend
Open `http://localhost:8080/` and use the demo UI:
- Enter a username.
- Click “Register Passkey (Platform)” or “Register Security Key (YubiKey)” to create a credential.
- Click “Login with Device” to sign in using a registered credential.

The frontend is located at `src/main/resources/static` and uses vanilla JS WebAuthn APIs.

## OpenAPI
An OpenAPI document is provided at `openapi.yaml`. You can render it with any viewer (e.g., Swagger UI, Insomnia, Yaak, Stoplight).

## Design approach and testing strategy (WebAuthn)
- We introduced a small wrapper `WebAuthnClient` (`src/main/java/dev/jpsacheti/authpoc/service/WebAuthnClient.java`) with a Spring implementation `WebAuthnClientImpl` that delegates to Yubico’s SDK. The goal is to isolate final classes and static parse/factory methods from the service layer.
- `WebAuthnService` depends on that wrapper, so in tests we can simply mock `WebAuthnClient` without any static mocking. This keeps tests stable across JDKs.
- Registration and login “challenge” payloads (options JSON) are persisted and removed after finishing, to prevent replay and simplify troubleshooting.
- To ensure tests are robust when running with newer JDKs than the target (e.g., Java 23 runtime vs Java 21 target), we added Surefire JVM flags: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED`.

Key classes:
- Service: `src/main/java/dev/jpsacheti/authpoc/service/WebAuthnService.java`
- Wrapper interface: `src/main/java/dev/jpsacheti/authpoc/service/WebAuthnClient.java`
- Wrapper implementation: `src/main/java/dev/jpsacheti/authpoc/service/WebAuthnClientImpl.java`

## Javadocs
This project includes conventional Javadoc-style comments at key points. To generate site docs:

```bash
mvn -q -DskipTests javadoc:javadoc
open target/site/apidocs/index.html  # macOS
```

Notes:
- The wrapper and service classes contain class-level and method-level Javadocs describing the design choices above.

## Running the application

### Prerequisites
- Java 21
- Docker and Docker Compose v2 (`docker compose`)

### One-command startup (DB + App)
```bash
docker compose up --build -d
```

Environment variables you can override for the app service (see `docker-compose.yml`):
- `POSTGRES_USER` (default: postgres)
- `POSTGRES_PASSWORD` (default: postgres)
- `JWT_SECRET` (default: dev-only-change-me)

The app will be available at `http://localhost:8080`.

### Run DB only, then app from IDE
```bash
docker compose up -d db
```
Then run the Spring Boot application normally from your IDE or with `./mvnw spring-boot:run`.

## Validation and error responses
- Request DTOs use bean validation (`@NotBlank`, `@Size`).
- A minimal `@RestControllerAdvice` returns structured 400 responses for validation errors.

## Testing with Yaak / cURL

### Password Authentication

Register:
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{ "username": "user", "password": "password", "displayName": "User Name" }'
```

Login:
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "username": "user", "password": "password" }'
```

### WebAuthn (init-only over HTTP)
Start registration (platform):
```bash
curl -X POST "http://localhost:8080/webauthn/register/start?username=user&attachment=platform"
```
Start registration (security key):
```bash
curl -X POST "http://localhost:8080/webauthn/register/start?username=user&attachment=cross-platform"
```
Start login:
```bash
curl -X POST "http://localhost:8080/webauthn/login/start?username=user"
```

Finish steps must be performed by the browser using the WebAuthn API (`navigator.credentials.create/get`).

## Notes and scope
- JWT is generated after successful auth but is not used by a security filter in this POC.
- H2 is used only for tests; runtime storage is PostgreSQL.
- This is not production-hardened; it’s intended for learning/comparison.

