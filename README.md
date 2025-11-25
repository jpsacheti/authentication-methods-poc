# Authentication POC

This project is a Proof of Concept (POC) demonstrating two authentication methods using Java and Spring Boot:
1.  Password Authentication: traditional username/password login with BCrypt hashing.
2.  WebAuthn (FIDO2): passwordless authentication using FIDO2 standards (e.g., TouchID, Android/iOS platform authenticators, YubiKey).

Both methods produce a JWT (JSON Web Token) as the final artifact of a successful authentication. In this POC the JWT is output-only for demonstration; there is no bearer-token authentication filter in the request pipeline.

## What this POC focuses on
- Compare Password vs WebAuthn login flows side by side.
- Keep infrastructure minimal and dev-friendly.
- Favor clarity over production-hardening. No rate-limits, no user-enumeration protections, etc.

## Technologies
- Java 21
- Spring Boot 3.2.0
- Spring Security
- PostgreSQL (Dockerized; compose uses postgres:18)
- Yubico WebAuthn Server Core (FIDO2)
- JJWT (JWT generation)

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

## API Endpoints

### Password Authentication
- `POST /auth/register`: register a new user.
  - Body: `{ "username": "user", "password": "password", "displayName": "User Name" }`
  - Returns: `{ "token": "<jwt>" }`
- `POST /auth/login`: login with password.
  - Body: `{ "username": "user", "password": "password" }`
  - Returns: `{ "token": "<jwt>" }`

### WebAuthn Authentication
Note: These endpoints require a browser environment to interact with the authenticator.

- `POST /webauthn/register/start?username=user`: start registration ceremony. Returns options JSON.
- `POST /webauthn/register/finish?username=user`: finish registration. Body is the credential JSON from the browser.
- `POST /webauthn/login/start?username=user`: start login ceremony. Returns options JSON.
- `POST /webauthn/login/finish?username=user`: finish login. Body is the assertion JSON from the browser.
  - Returns: `{ "token": "<jwt>" }`

Implementation notes:
- The WebAuthn user handle is the user UUID bytes (opaque identifier), not the username.
- Registration/login “challenge/options” are persisted in Postgres and removed after finish.
- This POC targets platform authenticators (browser/Android/iOS) by design.

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

### WebAuthn Authentication
WebAuthn requires a browser environment to interact with the authenticator (TouchID, Android/iOS, etc.). You cannot complete the entire flow with a plain HTTP client, but you can initiate it:

Start registration:
```bash
curl -X POST "http://localhost:8080/webauthn/register/start?username=user"
```

Finish steps must be performed by the browser using the WebAuthn API (`navigator.credentials.create/get`).

## Notes and scope
- JWT is generated after successful auth but is not used by a security filter in this POC.
- H2 is used only for tests; runtime storage is PostgreSQL.
- This is not production-hardened; it’s intended for learning/comparison.

