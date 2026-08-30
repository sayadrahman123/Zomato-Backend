# Zomato Backend Clone

> A production-grade backend system for a food delivery platform, built with **Spring Boot 3**, **MySQL 8**, **Redis 7**, and **Docker**.

## 🚀 Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.3.4 |
| Language | Java 17 |
| Database | MySQL 8 |
| Cache | Redis 7 |
| Containerization | Docker + Docker Compose |
| Auth | JWT (Spring Security) |
| Docs | Swagger UI (SpringDoc OpenAPI) |

## 📦 Modules

- **User** — Registration, JWT Auth, Profile
- **Restaurant** — CRUD, Search, Redis Caching
- **Menu** — Categories and Items per Restaurant
- **Cart** — Redis-backed Cart (no DB table)
- **Order** — State Machine (PLACED → DELIVERED)
- **Delivery** — Partner Management, Location in Redis
- **Reviews** — Post-delivery Ratings
- **Addresses** — Multi-address per User
- **Admin** — User & Restaurant Management

## 🛠️ Local Development Setup

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker Desktop

### 1. Clone the repository
```bash
git clone https://github.com/sayadrahman123/Zomato-Backend.git
cd Zomato-Backend
```

### 2. Start MySQL and Redis (Docker)
```bash
docker compose -f docker-compose-dev.yml up -d
```

### 3. Configure environment
```bash
cp .env.example .env
# Edit .env if needed
```

### 4. Run the application
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 5. Access Swagger UI
```
http://localhost:8080/swagger-ui.html
```

## 🐳 Full Docker Deployment

```bash
docker compose up --build -d
```

## 🔑 Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `mysql` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `zomato_db` | Database name |
| `DB_USERNAME` | `root` | DB username |
| `DB_PASSWORD` | `root` | DB password |
| `REDIS_HOST` | `redis` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | `...` | JWT signing key |
| `JWT_EXPIRATION_MS` | `86400000` | Token TTL (ms) |

## 📡 API Endpoints (Overview)

| Module | Base Path |
|---|---|
| Auth | `/api/auth` |
| Users | `/api/users` |
| Restaurants | `/api/restaurants` |
| Menu | `/api/menu-items` |
| Cart | `/api/cart` |
| Orders | `/api/orders` |
| Delivery | `/api/delivery` |
| Reviews | `/api/reviews` |
| Admin | `/api/admin` |

## 📄 License

MIT
