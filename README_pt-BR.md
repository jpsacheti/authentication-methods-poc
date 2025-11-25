# POC de Autenticação

Este projeto é uma Prova de Conceito (POC) demonstrando dois métodos de autenticação usando Java e Spring Boot:
1.  **Autenticação por Senha**: Login tradicional com usuário/senha e hash BCrypt.
2.  **WebAuthn (FIDO2)**: Autenticação sem senha usando padrões FIDO2 (ex: TouchID, YubiKey).

Ambos os métodos resultam em um **JWT (JSON Web Token)** que serve como token de portador (bearer token) para requisições autenticadas.

## Tecnologias
- Java 21
- Spring Boot 3.2.0
- Spring Security
- Banco de Dados Postgres
- Yubico WebAuthn Server Core (para FIDO2)
- JJWT (para geração de JWT)

## Endpoints da API

### Autenticação por Senha
- `POST /auth/register`: Registrar um novo usuário.
    - Corpo: `{ "username": "usuario", "password": "senha", "displayName": "Nome do Usuário" }`
- `POST /auth/login`: Login com senha.
    - Corpo: `{ "username": "usuario", "password": "senha" }`
    - Retorna: `{ "token": "jwt-token..." }`

### Autenticação WebAuthn
**Nota**: Estes endpoints requerem um ambiente de navegador para interagir com o autenticador.

- `POST /webauthn/register/start?username=usuario`: Iniciar cerimônia de registro. Retorna JSON de opções.
- `POST /webauthn/register/finish?username=usuario`: Finalizar registro. Corpo é o JSON da credencial vindo do navegador.
- `POST /webauthn/login/start?username=usuario`: Iniciar cerimônia de login. Retorna JSON de opções.
- `POST /webauthn/login/finish?username=usuario`: Finalizar login. Corpo é o JSON de asserção vindo do navegador.
    - Retorna: `{ "token": "jwt-token..." }`

## Executando a Aplicação

### Pré-requisitos
- Java 21
- Docker & Docker Compose

### Passos
1.  Inicie o banco de dados:
    ```bash
    docker-compose up -d
    ```
2.  Execute a aplicação:
    ```bash
    ./mvnw spring-boot:run
    ```
3.  A aplicação iniciará em `http://localhost:8080`.

## Testando com Yaak / cURL

### Autenticação por Senha

**Registrar:**
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{ "username": "usuario", "password": "senha", "displayName": "Nome do Usuário" }'
```

**Login:**
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "username": "usuario", "password": "senha" }'
```

### Autenticação WebAuthn
WebAuthn requer um navegador para interagir com o autenticador (TouchID, YubiKey, etc.). Você não pode testar o fluxo completo apenas com um cliente HTTP como o Yaak.
No entanto, você pode iniciar os fluxos:

**Iniciar Registro:**
```bash
curl -X POST "http://localhost:8080/webauthn/register/start?username=usuario"
```

