# Goldencat Bank — Spring Boot Banking Application

A full-stack banking web application built with **Spring Boot**, **Spring Security**, **Thymeleaf**, and **MySQL**, featuring account registration, login, deposits, withdrawals, and peer-to-peer transfers. The project also ships with a complete **CI/CD pipeline** (GitHub Actions), **Docker** packaging, and **Kubernetes/Helm** deployment manifests.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started (Local Development)](#getting-started-local-development)
- [Configuration](#configuration)
- [Running Tests & Coverage](#running-tests--coverage)
- [Running with Docker](#running-with-docker)
- [Deploying to Kubernetes](#deploying-to-kubernetes)
  - [Raw Manifests](#option-a-raw-manifests)
  - [Helm Chart](#option-b-helm-chart)
- [CI/CD Pipeline](#cicd-pipeline)
- [Application Routes](#application-routes)
- [Security Notes](#security-notes)


---

## Features

- **User registration & login** secured with Spring Security and BCrypt password hashing
- **Dashboard** showing current balance and account details
- **Deposit** funds into an account
- **Withdraw** funds with insufficient-funds protection
- **Transfer** funds between accounts by username
- **Transaction history** with a styled, filterable ledger view
- Server-rendered UI using **Thymeleaf** + Bootstrap
- Unit and MVC slice test coverage (JUnit 5, Mockito, `spring-security-test`)
- **JaCoCo** code coverage reporting integrated with **SonarQube/SonarCloud**
- **Trivy** vulnerability scanning (filesystem + built image) wired into CI
- Containerized with **Docker**, deployable via plain **Kubernetes manifests** or a **Helm chart**

## Tech Stack

| Layer            | Technology                                      |
|-------------------|--------------------------------------------------|
| Language          | Java 17                                          |
| Framework         | Spring Boot 3.3.3 (Web, Data JPA, Security, Thymeleaf) |
| Database          | MySQL 8                                          |
| Templating        | Thymeleaf + Thymeleaf Extras (Spring Security 6) |
| Build Tool        | Maven (via Maven Wrapper `mvnw` / `mvnw.cmd`)    |
| Testing           | JUnit 5, Mockito, Spring Security Test           |
| Coverage          | JaCoCo                                           |
| Static Analysis   | SonarQube / SonarCloud                           |
| Vulnerability Scan| Trivy (fs + image)                               |
| Containerization  | Docker (Eclipse Temurin 17 JDK Alpine base)      |
| Orchestration     | Kubernetes manifests + Helm chart                |
| CI/CD             | GitHub Actions                                   |

## Project Structure

```
.
├── src/main/java/com/example/bankapp/
│   ├── BankappApplication.java        # Spring Boot entry point
│   ├── config/SecurityConfig.java     # Spring Security filter chain & auth setup
│   ├── controller/BankController.java # MVC routes (dashboard, deposit, withdraw, transfer...)
│   ├── model/                         # Account, Transaction JPA entities
│   ├── repository/                    # AccountRepository, TransactionRepository
│   └── service/AccountService.java    # Core business logic & UserDetailsService
├── src/main/resources/
│   ├── application.properties         # DB & JPA configuration (env-var driven)
│   └── templates/                     # Thymeleaf views (login, register, dashboard, transactions)
├── src/test/java/com/example/bankapp/ # Unit + MVC slice tests
├── k8s/                               # Raw Kubernetes manifests (bankapp + mysql)
├── bankapp/                           # Helm chart (mirrors k8s/ manifests, templated)
├── .github/workflows/
│   ├── ci.yml                         # Build, scan, test, sonar, dockerize
│   └── cd.yaml                        # Deploy to Kubernetes cluster
├── Dockerfile                         # Runtime image for the packaged jar
├── sonar-project.properties           # SonarQube/SonarCloud project config
├── Setup-RBAC.md                      # Notes for setting up a Jenkins service account/RBAC
└── pom.xml                            # Maven build configuration
```

## Prerequisites

- **Java 17** (JDK)
- **Maven** (or use the bundled `./mvnw` / `mvnw.cmd` wrapper — no local install required)
- **MySQL 8** running locally, in a container, or accessible remotely
- **Docker** (optional, for containerized builds/deploys)
- **kubectl** + a Kubernetes cluster (optional, for k8s deployment)
- **Helm 3** (optional, if using the Helm chart)

## Getting Started (Local Development)

1. **Clone the repository**
   ```bash
   git clone <your-repo-url>
   cd <repo-directory>
   ```

2. **Create the database**
   ```sql
   CREATE DATABASE bankappdb;
   ```
   (see `src/main/resources/static/mysql/SQLScript.txt`)

3. **Set required environment variables** (see [Configuration](#configuration) below):
   ```bash
   export DB_HOST=localhost
   export DB_PORT=3306
   export DB_NAME=bankappdb
   export DB_USERNAME=root
   export DB_PASSWORD=your_password
   ```

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```
   On Windows: `mvnw.cmd spring-boot:run`

5. Visit **http://localhost:8080/login** and register a new account to get started.

## Configuration

Database connectivity is driven entirely by environment variables in `application.properties`:

| Variable       | Description                          | Default       |
|-----------------|---------------------------------------|----------------|
| `DB_HOST`       | MySQL host                           | `localhost`    |
| `DB_PORT`       | MySQL port                           | `3306`         |
| `DB_NAME`       | Database name                        | `bankappdb`    |
| `DB_USERNAME`   | Database username                    | *(required)*   |
| `DB_PASSWORD`   | Database password                    | *(required)*   |

> ⚠️ `DB_USERNAME` and `DB_PASSWORD` have no defaults and **must** be supplied, or the application will fail to start.

`spring.jpa.hibernate.ddl-auto=update` is enabled, so JPA entities will auto-create/update tables on startup — convenient for development, but you may want a migration tool (Flyway/Liquibase) and a stricter DDL strategy for production.

## Running Tests & Coverage

```bash
./mvnw test
```

This runs the full suite (`AccountServiceTest`, `BankControllerTest`, `BankappApplicationTests`) and generates a JaCoCo report at:

```
target/site/jacoco/jacoco.xml
target/site/jacoco/index.html
```

## Running with Docker

The CI pipeline builds the jar separately and copies it into the image, so to build locally:

```bash
./mvnw package -DskipTests
mkdir -p app && cp target/*.jar app/
docker build -t bankapp:local .
docker run -p 8080:8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3306 \
  -e DB_NAME=bankappdb \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=your_password \
  bankapp:local
```

## Deploying to Kubernetes

### Option A: Raw Manifests

Manifests live under `k8s/` and target the `webapps` namespace.

```bash
kubectl apply -f k8s/rbac/namespace.yaml
kubectl apply -f k8s/mysql/mysql-secret.yaml
kubectl apply -f k8s/mysql/mysql-configmap.yaml
kubectl apply -f k8s/mysql/mysql-pvc.yaml
kubectl apply -f k8s/mysql/mysql-deployment.yaml
kubectl apply -f k8s/mysql/mysql-service.yaml
kubectl apply -f k8s/bankapp/bankapp-secret.yaml
kubectl apply -f k8s/bankapp/bankapp-deployment.yaml
kubectl apply -f k8s/bankapp/bankapp-service.yaml
```

### Option B: Helm Chart

A Helm chart mirroring the raw manifests lives under `bankapp/`, parameterized via `bankapp/values.yaml`.

```bash
helm install bankapp ./bankapp --create-namespace --namespace webapps
```

Key values you'll likely want to override for a real deployment (`--set` or a custom values file):

- `bankapp.image.repository` / `bankapp.image.tag`
- `mysql.rootPassword`, `bankapp.dbPassword` — **do not** leave the committed defaults (`Test@123`) in any real environment; supply these via `--set` or a separate untracked values file / Kubernetes Secret.
- `bankapp.service.type` — defaults to `LoadBalancer`

> ⚠️ **Security note:** `bankapp/values.yaml`, `k8s/mysql/mysql-secret.yaml`, and `k8s/bankapp/bankapp-secret.yaml` currently contain hardcoded example credentials (`Test@123`). Replace these before deploying anywhere beyond a local sandbox, and avoid committing real secrets to source control.

## CI/CD Pipeline

### CI (`.github/workflows/ci.yml`)
Triggered on pushes to `master`, `develop`, `feature/*`, and PRs into `master`:

1. **compile** — `mvn compile` on JDK 17
2. **security-check** — Trivy filesystem scan (fails on CRITICAL/HIGH with available fixes), SARIF results uploaded to the GitHub Security tab
3. **build_project_and_sonar_scan** — runs tests with coverage, verifies the JaCoCo report, packages the jar, uploads it as a build artifact, runs SonarQube/SonarCloud analysis, and enforces the quality gate
4. **build_docker_image_and_push** *(master only)* — downloads the jar artifact, builds a multi-arch-ready Docker image (QEMU + Buildx), pushes to Docker Hub, and runs a Trivy **image** scan before publishing results

### CD (`.github/workflows/cd.yaml`)
Triggered automatically when the CI pipeline succeeds on `master` (or manually via `workflow_dispatch`):

1. Applies the namespace/RBAC
2. Applies the MySQL layer and waits for rollout
3. Applies the bankapp Secret/Deployment/Service
4. Rolls the deployment to the newly built image tag (matching the triggering commit SHA)

## Application Routes

| Method | Path            | Auth Required | Description                          |
|--------|-----------------|----------------|----------------------------------------|
| GET    | `/login`        | No             | Login page                            |
| GET    | `/register`     | No             | Registration page                     |
| POST   | `/register`     | No             | Create a new account                  |
| GET    | `/dashboard`    | Yes            | View balance & account details        |
| POST   | `/deposit`      | Yes            | Deposit funds                         |
| POST   | `/withdraw`     | Yes            | Withdraw funds                        |
| POST   | `/transfer`     | Yes            | Transfer funds to another user        |
| GET    | `/transactions` | Yes            | View transaction history              |
| POST   | `/logout`       | Yes            | Log out                               |

## Security Notes

- Passwords are hashed with `BCryptPasswordEncoder`.
- CSRF protection is currently **disabled** (`.csrf(csrf -> csrf.disable())`) in `SecurityConfig` — re-enable and wire up CSRF tokens in the Thymeleaf forms before treating this as production-ready.
- All routes except `/login`, `/register` (GET/POST), and static login/logout handling require authentication.
- Rotate/replace all example secrets (`Test@123`) shown in `k8s/` and `bankapp/values.yaml` before any non-local deployment.


