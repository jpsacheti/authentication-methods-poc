# POC de Autenticação

Este projeto é uma Prova de Conceito (POC) que demonstra dois métodos de autenticação usando Java e Spring Boot:
1.  Autenticação por Senha: login tradicional com usuário/senha e hash BCrypt.
2.  WebAuthn (FIDO2): autenticação sem senha usando padrões FIDO2 (ex.: TouchID, autenticadores de plataforma Android/iOS, YubiKey).

Ambos os métodos geram um JWT (JSON Web Token) como artefato final após a autenticação bem-sucedida. Neste POC, o JWT é apenas de saída (output-only) para demonstração; não há filtro de autenticação por bearer token no pipeline de requisições.

## Foco do POC
- Comparar os fluxos de login por Senha vs WebAuthn lado a lado.
- Manter a infraestrutura mínima e amigável para desenvolvimento.
- Priorizar clareza em vez de hardening de produção. Sem rate-limiting, sem mitigação de enumeração de usuários, etc.

## Tecnologias
- Java 21
- Spring Boot 3.2.0
- Spring Security
- PostgreSQL (dockerizado; o compose usa postgres:18)
- Yubico WebAuthn Server Core (FIDO2)
- JJWT (geração de JWT)

## Configuração
Propriedades relevantes para o POC:

- Relying Party (WebAuthn)
  - `webauthn.rp.id=localhost`
  - `webauthn.rp.name=Auth POC`
  - `webauthn.origin=http://localhost:8080`

- JWT (apenas saída)
  - `jwt.secret` (pode ser definido via variável de ambiente `JWT_SECRET`)
  - `jwt.ttl-hours` (padrão 24)

- Banco de dados
  - Em desenvolvimento, a aplicação conecta em `jdbc:postgresql://localhost:5432/authdb` por padrão.
  - Com Docker Compose, o serviço da aplicação já está ligado ao serviço `db` automaticamente.

## Endpoints da API

### Autenticação por Senha
- `POST /auth/register`: registra um novo usuário.
  - Corpo: `{ "username": "usuario", "password": "senha", "displayName": "Nome do Usuário" }`
  - Retorna: `{ "token": "<jwt>" }`
- `POST /auth/login`: login com senha.
  - Corpo: `{ "username": "usuario", "password": "senha" }`
  - Retorna: `{ "token": "<jwt>" }`

### Autenticação WebAuthn
Observação: estes endpoints requerem um navegador para interagir com o autenticador.

- `POST /webauthn/register/start?username=usuario`: inicia a cerimônia de registro. Retorna o JSON de opções.
- `POST /webauthn/register/finish?username=usuario`: finaliza o registro. O corpo é o JSON da credencial vindo do navegador.
- `POST /webauthn/login/start?username=usuario`: inicia a cerimônia de login. Retorna o JSON de opções.
- `POST /webauthn/login/finish?username=usuario`: finaliza o login. O corpo é o JSON de asserção vindo do navegador.
  - Retorna: `{ "token": "<jwt>" }`

Notas de implementação:
- O user handle do WebAuthn é o UUID do usuário em bytes (identificador opaco), e não o nome de usuário.
- As “challenges/opções” de registro/login são persistidas no Postgres e removidas após a conclusão.
- Este POC mira autenticadores de plataforma (navegador/Android/iOS) por design.

## Executando a aplicação

### Pré-requisitos
- Java 21
- Docker e Docker Compose v2 (`docker compose`)

### Startup em um comando (DB + App)
```bash
docker compose up --build -d
```

Variáveis de ambiente que você pode sobrescrever no serviço da app (ver `docker-compose.yml`):
- `POSTGRES_USER` (padrão: postgres)
- `POSTGRES_PASSWORD` (padrão: postgres)
- `JWT_SECRET` (padrão: dev-only-change-me)

O app ficará disponível em `http://localhost:8080`.

### Rodar apenas o DB e subir a app pelo IDE
```bash
docker compose up -d db
```
Depois rode a aplicação Spring Boot normalmente pelo IDE ou com `./mvnw spring-boot:run`.

## Validação e respostas de erro
- DTOs de requisição usam bean validation (`@NotBlank`, `@Size`).
- Um `@RestControllerAdvice` mínimo retorna respostas 400 estruturadas para erros de validação.

## Testando com Yaak / cURL

### Autenticação por Senha

Registrar:
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{ "username": "usuario", "password": "senha", "displayName": "Nome do Usuário" }'
```

Login:
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "username": "usuario", "password": "senha" }'
```

### Autenticação WebAuthn
WebAuthn requer um navegador para interagir com o autenticador (TouchID, Android/iOS, etc.). Você não consegue completar o fluxo somente com um cliente HTTP, mas pode iniciá-lo:

Iniciar registro:
```bash
curl -X POST "http://localhost:8080/webauthn/register/start?username=usuario"
```

Os passos de finalização precisam ser feitos pelo navegador usando a API WebAuthn (`navigator.credentials.create/get`).

## Observações e escopo
- O JWT é gerado após autenticação bem-sucedida, mas não é usado por um filtro de segurança neste POC.
- H2 é usado apenas para testes; em execução, o armazenamento é PostgreSQL.
- Não é um projeto endurecido para produção; é voltado a aprendizado/comparação.

