# POC de Autenticação

Este projeto é uma Prova de Conceito (POC) que demonstra dois métodos de autenticação usando Java e Spring Boot:
1. Autenticação por Senha: login tradicional com usuário/senha e hash BCrypt.
2. WebAuthn (FIDO2): autenticação sem senha usando padrões FIDO2 (ex.: Passkeys em Android/iOS/macOS/Windows e chaves de segurança como YubiKey).

Ambos os métodos geram um JWT (JSON Web Token) como artefato final após a autenticação bem-sucedida. Neste POC, o JWT é apenas de saída (output‑only) para demonstração; não há filtro de autenticação por bearer token no pipeline de requisições.

## Foco do POC
- Comparar os fluxos de login por Senha vs WebAuthn lado a lado.
- Manter a infraestrutura mínima e amigável para desenvolvimento.
- Priorizar clareza em vez de hardening de produção. Sem rate‑limiting, sem mitigação de enumeração de usuários, etc.

## Tecnologias
- Java 21
- Spring Boot 3.2.x
- Spring Security
- PostgreSQL (dockerizado)
- Yubico WebAuthn Server Core (FIDO2)
- JJWT (geração de JWT)

## Visão geral de funcionalidades
- Fluxo usuário/senha com hash BCrypt e emissão de JWT
- Registro e autenticação WebAuthn com verificação no servidor
- Passkeys (autenticadores de plataforma) e chaves de segurança externas (cross‑platform, ex.: YubiKey)
- Escolha do tipo de autenticador durante o registro: plataforma vs cross‑platform
- Frontend estático mínimo para exercitar os fluxos (`src/main/resources/static`)

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

## API REST e fluxos WebAuthn

### Autenticação por Senha
- `POST /auth/register`: registra um novo usuário.
  - Corpo: `{ "username": "usuario", "password": "senha", "displayName": "Nome do Usuário" }`
  - Retorna: `{ "token": "<jwt>" }`
- `POST /auth/login`: login com senha.
  - Corpo: `{ "username": "usuario", "password": "senha" }`
  - Retorna: `{ "token": "<jwt>" }`

### Autenticação WebAuthn
Observação: estes endpoints requerem um navegador para interagir com autenticadores. O frontend estático incluso já faz o encadeamento para você.

- `POST /webauthn/register/start?username=usuario[&attachment=platform|cross-platform|any]`: inicia a cerimônia de registro. Retorna o JSON de opções.
  - `attachment` (opcional):
    - `platform` (padrão): preferir autenticador de plataforma (passkey no dispositivo)
    - `cross-platform`: preferir autenticador externo (ex.: YubiKey)
    - `any`: deixar o navegador decidir
- `POST /webauthn/register/finish?username=usuario`: finaliza o registro. O corpo é o JSON da credencial vindo do navegador.
- `POST /webauthn/login/start?username=usuario`: inicia a cerimônia de login. Retorna o JSON de opções.
- `POST /webauthn/login/finish?username=usuario`: finaliza o login. O corpo é o JSON de asserção vindo do navegador.
  - Retorna: `{ "token": "<jwt>" }`

Notas de implementação:
- O user handle do WebAuthn é o UUID do usuário em bytes (identificador opaco), e não o nome de usuário.
- As challenges/opções de registro/login são persistidas no Postgres e removidas após a conclusão.
- O registro permite selecionar o tipo de autenticador; o login funciona com qualquer credencial cadastrada do usuário.

## Frontend estático
Abra `http://localhost:8080/` e use a interface de demonstração:
- Informe um nome de usuário.
- Clique em “Register Passkey (Platform)” ou “Register Security Key (YubiKey)” para criar uma credencial.
- Clique em “Login with Device” para autenticar usando uma credencial já registrada.

O frontend fica em `src/main/resources/static` e usa a API WebAuthn em JavaScript puro.

## OpenAPI
Um documento OpenAPI está disponível em `openapi.yaml`. Você pode visualizá‑lo em qualquer viewer (ex.: Swagger UI, Insomnia, Yaak, Stoplight).

## Abordagem de design e estratégia de testes (WebAuthn)
- Introduzimos um pequeno wrapper `WebAuthnClient` (`src/main/java/dev/jpsacheti/authpoc/service/WebAuthnClient.java`) com uma implementação Spring `WebAuthnClientImpl` que delega para o SDK da Yubico. O objetivo é isolar classes `final` e métodos estáticos de parse/factory da camada de serviço.
- `WebAuthnService` depende desse wrapper; assim, nos testes podemos simplesmente mockar `WebAuthnClient` sem mocking estático. Isso mantém os testes estáveis em diferentes versões de JDK.
- Os payloads (JSON) de “challenge” de registro e login são persistidos e removidos após a conclusão, para evitar replay e facilitar a inspeção.
- Para robustez quando os testes rodam em JDK mais novo que o alvo (ex.: runtime Java 23 vs alvo Java 21), adicionamos flags do Surefire: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED`.

Classes principais:
- Serviço: `src/main/java/dev/jpsacheti/authpoc/service/WebAuthnService.java`
- Interface do wrapper: `src/main/java/dev/jpsacheti/authpoc/service/WebAuthnClient.java`
- Implementação do wrapper: `src/main/java/dev/jpsacheti/authpoc/service/WebAuthnClientImpl.java`

## Javadocs
Este projeto inclui comentários no estilo Javadoc em pontos-chave. Para gerar a documentação do site:

```bash
mvn -q -DskipTests javadoc:javadoc
open target/site/apidocs/index.html  # macOS
```

Observações:
- As classes do wrapper e do serviço possuem Javadocs de nível de classe e métodos descrevendo as escolhas de design acima.

## Executando a aplicação

### Pré‑requisitos
- Java 21
- Docker e Docker Compose v2 (`docker compose`)

### Subida em um comando (DB + App)
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

### WebAuthn (apenas início via HTTP)
Iniciar registro (plataforma):
```bash
curl -X POST "http://localhost:8080/webauthn/register/start?username=usuario&attachment=platform"
```
Iniciar registro (chave de segurança):
```bash
curl -X POST "http://localhost:8080/webauthn/register/start?username=usuario&attachment=cross-platform"
```
Iniciar login:
```bash
curl -X POST "http://localhost:8080/webauthn/login/start?username=usuario"
```

A finalização precisa ser feita no navegador usando a API WebAuthn (`navigator.credentials.create/get`).

## Observações e escopo
- O JWT é gerado após autenticação bem-sucedida, mas não é usado por um filtro de segurança neste POC.
- H2 é usado apenas para testes; em execução, o armazenamento é PostgreSQL.
- Não é um projeto endurecido para produção; é voltado a aprendizado/comparação.

