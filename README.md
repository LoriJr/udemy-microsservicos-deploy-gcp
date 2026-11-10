# Microsserviços com Spring Boot — Deploy no GCP (Docker + GitHub Actions + Docker Hub)

Projeto de estudo (Seção 19 — *Deploy de Microsserviços no GCP*) que prepara a stack de microsserviços
para publicação em nuvem: os serviços foram **desacoplados do Eureka e do Zipkin** (agora rodam de forma
standalone, com URLs configuráveis por variáveis de ambiente e health probes do Actuator habilitados),
são empacotados em imagens Docker e publicados no **Docker Hub** por um pipeline de
**CI/CD com GitHub Actions** — passo anterior ao deploy no GCP.

---

## 🏛️ Arquitetura (estado atual)

```
        ┌──────────────────────────────┐
        │   MySQL no host (:3306)       │
        │   book_service / exchange_service
        └───────▲──────────────▲───────┘
                │ JDBC          │ JDBC
                │ (host.docker.internal)
   ┌────────────┴─────────┐  ┌──┴───────────────────┐
   │  book-service (:8100)│  │ exchange-service (:8000)│
   │  JPA + Flyway        │  │  JPA + Flyway           │
   │  OpenFeign ──────────┼─►│  (URL direta via env    │
   │  Swagger / Actuator  │  │   EXCHANGE_SERVICE_     │
   └──────────────────────┘  │   SERVICE_HOST)         │
                             └─────────────────────────┘

    Imagens construídas localmente por Dockerfile (eclipse-temurin:21-jre)
    e publicadas no Docker Hub pelo GitHub Actions (tags latest + RUN_ID)
```

> Em relação às seções anteriores do curso, **Eureka (naming-server), API Gateway e Zipkin foram
> desativados** neste estágio: as dependências e configurações aparecem comentadas nos `pom.xml`
> e `application.yaml`, e os serviços se comunicam por URL direta configurável — formato exigido
> por plataformas de deploy gerenciadas (Cloud Run / GKE).

---

## 📦 Módulos

| Módulo | Porta | Descrição |
|---|---|---|
| **book-service** | `8100` | CRUD de livros (JPA + MySQL). Chama o exchange-service via **OpenFeign** (URL direta) para converter preço/câmbio |
| **exchange-service** | `8000` | Conversão de moedas (JPA + MySQL) |
| **MySQL (host)** | `3306` | Banco `book_service` e `exchange_service` rodando no host, acessíveis dos containers via `host.docker.internal` |

---

## 🧰 Tecnologias utilizadas

### Core
- **Java 21**
- **Spring Boot 4.1.1** (Maven, `spring-boot-maven-plugin`)
- **Spring Cloud 2025.1.3** (BOM de dependências)

### Microsserviços
- **OpenFeign** (`spring-cloud-starter-openfeign`) + `feign-micrometer` — comunicação declarativa book → exchange, com endpoint resolvido pela variável `EXCHANGE_SERVICE_SERVICE_HOST` (default `http://host.docker.internal`)
- **Spring Boot Actuator** — **liveness/readiness probes habilitadas** (`management.endpoint.health.probes.enabled`), requisito para plataformas de deploy gerenciadas
- ~~Netflix Eureka~~ / ~~Spring Cloud Gateway~~ — **desativados neste estágio** (comentados nos poms/YAMLs)

### Persistência
- **Spring Data JPA** (dialeto MySQL, `ddl-auto: none`)
- **MySQL 8** (`mysql-connector-j`) — servidor no host (`host.docker.internal:3306`)
- **Flyway** (`spring-boot-starter-flyway` + `flyway-mysql`) — migrações em `src/main/resources/db/migration`

### Documentação de APIs — Swagger / OpenAPI
- **SpringDoc OpenAPI 3.2.0** (`springdoc-openapi-starter-webmvc-ui`) nos dois serviços

### Cloud Native Readiness
- **Health probes** (liveness/readiness) do Actuator habilitadas nos dois serviços
- Configuração 100% **por variável de ambiente** (datasource via `SPRING_DATASOURCE_URL` etc. no deploy; `EXCHANGE_SERVICE_SERVICE_HOST` para o Feign)
- ~~Zipkin / Micrometer Tracing~~ — **desativado neste estágio** (propriedades comentadas; a env `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` ainda consta no compose, mas é ignorada sem o starter do Zipkin)

### Infra & CI/CD
- **Docker** + **Docker Compose** — rede `microservices-app`
- **Docker Hub** — registry das imagens publicadas
- **GitHub Actions** — pipeline `continuous-deployment.yml` (build + push das imagens)
- **Lombok** e **Spring Boot DevTools**

---

## ▶️ Como executar

### Pré-requisitos
Java 21+ · Maven 3.9+ · Docker + Docker Compose · MySQL 8 no host (ou nuvem)

### 1. Banco de dados
Crie os bancos no MySQL do host (o Flyway cria e popula as tabelas na primeira subida):

```sql
CREATE DATABASE book_service;
CREATE DATABASE exchange_service;
```

Credenciais padrão dos serviços: usuário `mysql` / senha `123456` (ajustáveis via env
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` no deploy).

### 2. Build dos JARs
Em cada módulo (`book-service`, `exchange-service`):

```bash
mvn clean package
```

> O `Dockerfile` de cada serviço copia o `target/*.jar` para a imagem `eclipse-temurin:21-jre`.
> Rode o `mvn package` **antes** do compose e sempre que alterar um `.java` **ou** o
> `application.yaml` — o YAML é empacotado dentro do JAR, então mudar só o YAML exige `--build`.

### 3. Subir a stack (a partir da raiz do projeto)

```bash
docker compose up -d --build
```

Após mudar env/config de um serviço:

```bash
docker compose up -d --force-recreate book-service
```

> O Feign do book-service resolve o exchange pela env `EXCHANGE_SERVICE_SERVICE_HOST`
> (default `http://host.docker.internal`). No compose local, defina-a como
> `http://exchange-service` para usar o DNS da rede `microservices-app`, ou deixe o default
> e exponha o exchange na porta `8000` do host (como o compose já faz).

---

## 🌐 URLs úteis

| Serviço | URL |
|---|---|
| book-service | http://localhost:8100 |
| exchange-service | http://localhost:8000 |
| Swagger UI — book-service | http://localhost:8100/swagger-ui/index.html |
| Swagger UI — exchange-service | http://localhost:8000/swagger-ui/index.html |
| OpenAPI docs — book-service | http://localhost:8100/book-service/v3/api-docs |
| OpenAPI docs — exchange-service | http://localhost:8000/exchange-service/v3/api-docs |
| Liveness probe | `GET /actuator/health/liveness` (em ambos os serviços) |
| Readiness probe | `GET /actuator/health/readiness` (em ambos os serviços) |
| MySQL (host) | localhost:3306 — user `mysql` / `123456`, dbs `book_service` e `exchange_service` |

> ⚠️ No Windows + Docker Desktop (WSL2), se `http://localhost` travar no PowerShell/`curl`,
> use `http://127.0.0.1` — o proxy do Docker pode não escutar em IPv6 (`::1`).
> No navegador, `localhost` funciona normalmente.

---

## 🔗 Endpoints

### book-service
`GET /book-service/{id}/{currency}` — retorna o livro com o preço convertido para a moeda pedida
(conversão feita pelo exchange-service via Feign).

```bash
curl http://localhost:8100/book-service/1/BRL
```

```json
{
  "id": 1,
  "author": "Michael C. Feathers",
  "title": "Working Effectively with Legacy Code",
  "price": 47.7349,
  "currency": "BRL",
  "environment": "BOOK PORT: 8100 | EXCHANGE PORT: 8000"
}
```

### exchange-service
`GET /exchange-service/{amount}/{from}/{to}`

```bash
curl http://localhost:8000/exchange-service/100/BRL/USD
```

---

## 🚀 CI/CD — GitHub Actions → Docker Hub

O workflow [`.github/workflows/continuous-deployment.yml`](.github/workflows/continuous-deployment.yml)
roda a cada **push na branch `main`** e executa:

1. **Login no Docker Hub** — usa os secrets `DOCKER_USERNAME` e `DOCKER_ACCESS_TOKEN`;
2. **Setup Java 21** (Temurin);
3. **Build** — `mvn spring-boot:build-image` para `naming-server` e `api-gateway`,
   `mvn clean package` para `book-service` e `exchange-service`;
4. **`docker compose build`** — gera as imagens dos serviços com Dockerfile próprio;
5. **Tag** — cada imagem recebe `latest` e a tag do run (`{DOCKER_USERNAME}/{imagem}:{RUN_ID}`);
6. **Push** — publica `naming-server`, `api-gateway`, `book-service` e `exchange-service` no Docker Hub.

> ⚠️ **Atenção**: o workflow ainda referencia as pastas `naming-server/` e `api-gateway/`
> (etapas 3 e 6), que **não existem nesta seção do curso**. Para o pipeline passar verde aqui,
> remova (ou comente) essas duas etapas de build e os respectivos tags/pushs, mantendo apenas
> `book-service` e `exchange-service`.

### Secrets necessários no repositório (GitHub → Settings → Secrets and variables → Actions)

| Secret | Descrição |
|---|---|
| `DOCKER_USERNAME` | Usuário do Docker Hub |
| `DOCKER_ACCESS_TOKEN` | Access Token do Docker Hub (não use a senha da conta) |

---

## 🗄️ Banco de dados (Flyway)

Cada serviço possui seu próprio schema (*database per service*), inicializado por migrações versionadas:

- `book-service/src/main/resources/db/migration`
  - `V1__Create_Table_Books.sql`
  - `V2__Insert_Data_In_Books.sql`
- `exchange-service/src/main/resources/db/migration`
  - `V1__Create_Table_Exchange.sql`
  - `V2__Populate_Table_Exchange.sql`

---

## 📁 Estrutura

```
secao-19-deploy de microsserviços no gcp/
├── .github/workflows/
│   └── continuous-deployment.yml   # CI/CD: build + push das imagens p/ Docker Hub
├── book-service/                   # Microsserviço de livros (Feign → exchange-service)
│   ├── Dockerfile                  # eclipse-temurin:21-jre + target/*.jar
│   └── src/main/resources/
│       ├── application.yaml        # Eureka/Zipkin comentados; probes habilitadas
│       └── db/migration/           # Migrações Flyway
├── exchange-service/               # Microsserviço de câmbio
│   ├── Dockerfile
│   └── src/main/resources/
│       ├── application.yaml
│       └── db/migration/
└── docker-compose.yml              # Rede microservices-app; portas 8100 e 8000
```

O `docker-compose.yml` na raiz define os dois serviços com `build:` apontando para os
Dockerfiles locais, a rede bridge `microservices-app` e as portas `8100`/`8000` publicadas no host.
O MySQL **não** faz parte do compose — os containers o acessam pelo host via
`host.docker.internal:3306` (padrão usado até migrar o banco para o GCP, ex.: Cloud SQL).
