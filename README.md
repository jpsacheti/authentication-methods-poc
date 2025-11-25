# Authentication POC

This project is a Proof of Concept (POC) demonstrating two authentication methods using Java and Spring Boot:
1.  **Password Authentication**: Traditional username/password login with BCrypt hashing.
2.  **WebAuthn (FIDO2)**: Passwordless authentication using FIDO2 standards (e.g., TouchID, YubiKey).

Both methods result in a **JWT (JSON Web Token)** which serves as a bearer token for authenticated requests.

## Technologies
- Java 21
- Spring Boot 3.2.0
- Spring Security
- Postgres Database
- Yubico WebAuthn Server Core (for FIDO2)
- JJWT (for JWT generation)

## API Endpoints

### Password Authentication
- `POST /auth/register`: Register a new user.
    - Body: `{ "username": "user", "password": "password", "displayName": "User Name" }`
- `POST /auth/login`: Login with password.
    - Body: `{ "username": "user", "password": "password" }`
    - Returns: `{ "token": "jwt-token..." }`

### WebAuthn Authentication
**Note**: These endpoints require a browser environment to interact with the authenticator.

- `POST /webauthn/register/start?username=user`: Start registration ceremony. Returns options JSON.
- `POST /webauthn/register/finish?username=user`: Finish registration. Body is the credential JSON from the browser.
- `POST /webauthn/login/start?username=user`: Start login ceremony. Returns options JSON.
- `POST /webauthn/login/finish?username=user`: Finish login. Body is the assertion JSON from the browser.
    - Returns: `{ "token": "jwt-token..." }`

## Running the Application

### Prerequisites
- Java 21
- Docker & Docker Compose

### Steps
1.  Start the database:
    ```bash
    docker-compose up -d
    ```
2.  Run the application:
    ```bash
    ./mvnw spring-boot:run
    ```
3.  The application will start on `http://localhost:8080`.

## Testing with Yaak / cURL

### Password Authentication

**Register:**
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{ "username": "user", "password": "password", "displayName": "User Name" }'
```

**Login:**
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "username": "user", "password": "password" }'
```

### WebAuthn Authentication
WebAuthn requires a browser environment to interact with the authenticator (TouchID, YubiKey, etc.). You cannot fully test it with just an HTTP client like Yaak.
However, you can initiate the flows:

**Start Registration:**
```bash
curl -X POST "http://localhost:8080/webauthn/register/start?username=user"
```

