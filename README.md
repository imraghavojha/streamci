# 🚀 StreamCI Backend

> **Real-time CI/CD pipeline analytics**


[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-blue.svg)](https://supabase.com/)
[![Google Cloud](https://img.shields.io/badge/Deployed%20on-Google%20Cloud-4285F4.svg)](https://cloud.google.com/)
[![Status](https://img.shields.io/badge/Status-Under%20Development-yellow.svg)]()

---


---

## 🎯 Overview

StreamCI is a **real-time CI/CD pipeline monitoring and analytics platform** that processes GitHub webhook events, tracks build metrics

---

## ✨ Features

### 🔴 **Real-Time Monitoring**
- Live WebSocket updates for build status changes
- Dashboard with instant metrics refresh
- Current queue depth and concurrent build tracking

**API:** `GET /api/dashboard/live`

### 📊 **Unified Dashboard**
- Aggregated metrics across all pipelines
- Success rates, build counts, and trends
- Recent activity tracking (last 24 hours)
- Active alerts and queue status

**API:** `GET /api/dashboard/summary`

### 🔗 **GitHub Webhook Integration**
- Automated processing of `workflow_run` events
- Build lifecycle tracking (queued → in_progress → completed)
- Repository and pipeline management
- Webhook signature verification

**API:** `POST /api/webhooks/github`



## 🏗️ Architecture

```
┌─────────────────┐
│  GitHub Actions │
│   (Webhooks)    │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────┐
│   Spring Boot Backend (Java 17) │
│  ┌──────────────────────────┐   │
│  │   REST API Controllers   │   │
│  └──────────┬───────────────┘   │
│             │                    │
│  ┌──────────▼───────────────┐   │
│  │   Service Layer          │   │
│  │  - WebhookService        │   │
│  │  - PipelineService       │   │
│  │  - MetricsService        │   │
│  │  - QueueService          │   │
│  │  - AlertService          │   │
│  └──────────┬───────────────┘   │
│             │                    │
│  ┌──────────▼───────────────┐   │
│  │   JPA Repositories       │   │
│  └──────────┬───────────────┘   │
└─────────────┼───────────────────┘
              │
              ▼
     ┌─────────────────┐
     │   PostgreSQL    │
     │   (Supabase)    │
     └─────────────────┘
              │
              ▼
     ┌─────────────────┐
     │  WebSocket ←─────┼─→ Frontend (Next.js)
     └─────────────────┘
```

**Key Components:**
- **Controllers:** Handle HTTP requests and WebSocket connections
- **Services:** Business logic and data processing
- **Repositories:** Database access layer (Spring Data JPA)
- **Models:** JPA entities (Pipeline, Build, QueueTracker, Alert, PipelineMetrics)

---

## 🛠️ Tech Stack

### **Backend Framework**
- **Spring Boot** 3.5.4
- **Java** 17 (OpenJDK)
- **Maven** for dependency management

### **Database**
- **PostgreSQL** (production - Supabase)
- **H2** (testing - in-memory)
- **Spring Data JPA** with Hibernate

### **Key Dependencies**
- **Spring Web** - REST API
- **Spring WebSocket** - Real-time updates
- **Spring Data JPA** - Data persistence
- **Spring Actuator** - Health monitoring
- **PostgreSQL Driver** - Database connectivity
- **Lombok** - Code generation

### **Deployment**
- **Docker** - Containerization
- **Google Cloud Run** - Serverless deployment
- **Supabase** - Managed PostgreSQL

---

## 🔌 API Endpoints

### **Dashboard & Monitoring**

| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| `GET` | `/api/dashboard/summary` | Aggregated pipeline metrics | JSON with overview, pipelines, alerts |
| `GET` | `/api/dashboard/live` | Real-time build status | Current running/queued builds |
| `GET` | `/api/trends?days={n}` | Historical trend analysis | Success rates, frequencies, durations |
| `GET` | `/actuator/health` | Service health check | UP/DOWN status |

### **Pipeline Management**

| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| `GET` | `/api/pipelines` | List all pipelines | Array of pipeline objects |
| `GET` | `/api/pipelines/{id}` | Get pipeline details | Pipeline with metrics |
| `GET` | `/api/builds` | List all builds | Build history |
| `GET` | `/api/alerts` | Active alerts | Current system alerts |

### **Webhooks**

| Method | Endpoint | Description | Headers Required |
|--------|----------|-------------|------------------|
| `POST` | `/api/webhooks/github` | Process GitHub events | `X-GitHub-Event`, `X-Hub-Signature-256` |

### **Example Response: Dashboard Summary**

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": "success",
  "total_pipelines": 5,
  "overview": {
    "total_success_rate": 87.5,
    "total_builds": 1250,
    "total_successful_builds": 1094,
    "total_failed_builds": 156
  },
  "recent_activity": {
    "builds_last_24h": 45,
    "successful_builds_last_24h": 42,
    "failed_builds_last_24h": 3
  },
  "active_alerts": [
    {
      "id": 1,
      "type": "queue_congestion",
      "severity": "warning",
      "message": "Queue depth increasing - 12 builds waiting"
    }
  ],
  "queue_status": {
    "current_depth": 12,
    "total_running": 5,
    "concurrent_limit": 5
  }
}
```

---

## 🚀 Quick Start

### **Prerequisites**
- Java 17+
- Maven 3.8+
- PostgreSQL (or use Supabase)
- GitHub account for webhooks

### **Local Development Setup**

1. **Clone the repository**
```bash
git clone <your-repo-url>
cd streamci-backend
```

2. **Configure application properties**
```bash
# Copy template
cp src/main/resources/application.properties.template src/main/resources/application.properties

# Edit with your values
nano src/main/resources/application.properties
```

3. **Set required environment variables**
```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/streamci"
export DATABASE_USERNAME="postgres"
export DATABASE_PASSWORD="your_password"
export GITHUB_TOKEN="your_github_token"
export GITHUB_WEBHOOK_SECRET="your_webhook_secret"
export ENCRYPTION_KEY="your_32_byte_encryption_key"
```

4. **Build and run**
```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run
```

5. **Verify it's running**
```bash
curl http://localhost:8080/actuator/health
# Response: {"status":"UP"}
```

### **Run Tests**
```bash
./mvnw test
```

---

## ☁️ Deployment

### **Google Cloud Run Deployment**

1. **Build Docker image**
```bash
docker build -t streamci-backend .
```

2. **Tag for Google Container Registry**
```bash
docker tag streamci-backend gcr.io/YOUR_PROJECT_ID/streamci-backend
```

3. **Push to GCR**
```bash
docker push gcr.io/YOUR_PROJECT_ID/streamci-backend
```

4. **Deploy to Cloud Run**
```bash
gcloud run deploy streamci-backend \
  --image gcr.io/YOUR_PROJECT_ID/streamci-backend \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars DATABASE_URL=$DATABASE_URL,DATABASE_USERNAME=$DATABASE_USERNAME,DATABASE_PASSWORD=$DATABASE_PASSWORD
```

5. **Configure environment variables in Cloud Run console**
   - Navigate to Cloud Run > Your Service > Variables & Secrets
   - Add all required environment variables

### **Health Checks**

The application exposes health check endpoints via Spring Actuator:

```bash
# Liveness probe
GET /actuator/health/liveness

# Readiness probe
GET /actuator/health/readiness
```

Configure these in your `app.yaml` or Cloud Run configuration.

---

## 📁 Project Structure

```
streamci-backend/
├── src/
│   ├── main/
│   │   ├── java/com/yourname/streamci/streamci/
│   │   │   ├── controller/          # REST API endpoints
│   │   │   │   ├── DashboardController.java
│   │   │   │   ├── WebhookController.java
│   │   │   │   └── PipelineController.java
│   │   │   ├── service/             # Business logic
│   │   │   │   ├── WebhookService.java
│   │   │   │   ├── PipelineService.java
│   │   │   │   ├── BuildService.java
│   │   │   │   ├── MetricsService.java
│   │   │   │   ├── QueueService.java
│   │   │   │   └── AlertService.java
│   │   │   ├── model/               # JPA entities
│   │   │   │   ├── Pipeline.java
│   │   │   │   ├── Build.java
│   │   │   │   ├── QueueTracker.java
│   │   │   │   ├── Alert.java
│   │   │   │   └── PipelineMetrics.java
│   │   │   ├── repository/          # Data access
│   │   │   │   ├── PipelineRepository.java
│   │   │   │   ├── BuildRepository.java
│   │   │   │   └── QueueTrackerRepository.java
│   │   │   └── config/              # Configuration
│   │   │       ├── WebSocketConfig.java
│   │   │       └── CorsConfig.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── application-prod.properties
│   └── test/                        # Integration tests
├── Dockerfile                       # Container definition
├── pom.xml                          # Maven dependencies
└── README.md
```

---

## 🔐 Environment Variables

### **Required for Production**

| Variable | Description | Example |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL connection string | `jdbc:postgresql://host:6543/postgres?sslmode=require` |
| `DATABASE_USERNAME` | Database username | `postgres.xxxx` |
| `DATABASE_PASSWORD` | Database password | `your_secure_password` |
| `GITHUB_TOKEN` | GitHub personal access token | `ghp_xxxxxxxxxxxx` |
| `GITHUB_WEBHOOK_SECRET` | Webhook signature secret | `your_webhook_secret` |
| `ENCRYPTION_KEY` | 32-byte encryption key | `your_32_byte_key` |
| `FRONTEND_URL` | CORS allowed origin | `https://your-frontend.vercel.app` |
| `PORT` | Server port (Cloud Run sets this) | `8080` |

### **Optional Configuration**

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Hibernate DDL mode | `update` |

---

## 🗺️ Development Roadmap

### ✅ **Completed**
- [x] GitHub webhook integration (workflow_run events)
- [x] Real-time dashboard with WebSocket support
- [x] Pipeline and build tracking
- [x] Queue management and depth monitoring
- [x] Basic alerting system
- [x] Historical metrics and trends
- [x] PostgreSQL integration with Supabase
- [x] Docker containerization
- [x] Google Cloud Run deployment



**Built with ❤️ using Spring Boot and Java**
