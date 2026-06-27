# Digital Menu System

A Spring Boot 4.0.5 (Java 21) digital menu management system for restaurants and food businesses. Features menu management, QR code ordering, Stripe payments (cards + FPX), order tracking, and multi-branch support.

---

## Quick Start (Local Development)

### Prerequisites
- Java 21+
- Maven 3.9+
- MySQL 8+ (via XAMPP or standalone)

### 1. Set up the Database
```sql
CREATE DATABASE digital_menu_db;
```

### 2. Configure Environment
Copy the example env file and fill in your values:
```bash
cp .env.example .env
# Edit .env with your MySQL, Stripe, and email credentials
```

### 3. Run the Application
```bash
cd menumanager
mvn spring-boot:run
```
The app starts at http://localhost:8080

### 4. Access the App
- **Customer menu:** http://localhost:8080/customer
- **Staff login:** http://localhost:8080/manage-login
- **QR code generator:** http://localhost:8080/manage-qr

---

# Deployment Guide

Deploy this Spring Boot application using Docker:

```bash
cd menumanager
docker build -t digital-menu-system .
docker run -p 8080:8080 ^
  -e DB_URL=jdbc:mysql://... ^
  -e STRIPE_SECRET_KEY=sk_test_... ^
  -e PUBLIC_BASE_URL=https://your-domain.com ^
  digital-menu-system
```

Set the required environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `STRIPE_SECRET_KEY`, `PUBLIC_BASE_URL`, etc.) in your hosting environment.

---

## Project Structure

```
menumanager/
|-- Dockerfile              # Container build (for any cloud platform)
|-- pom.xml                 # Maven project config
|-- src/
|   |-- main/
|   |   |-- java/com/example/menumanager/
|   |   |   |-- controller/   # Web controllers (Menu, Payment, Auth, Branch)
|   |   |   |-- model/        # JPA entities (6 tables)
|   |   |   |-- repository/   # Spring Data repos
|   |   |   |-- service/      # Business logic (Orders, Email, QR, Stripe)
|   |   |   |-- config/       # Web config, interceptors
|   |   |   |-- dto/          # Data transfer objects
|   |   |   |-- session/      # Session context objects
|   |   |   |-- util/         # Password utility
|   |   |-- resources/
|   |       |-- application.properties  # Config (uses env vars)
|   |       |-- static/                 # CSS, JS
|   |       |-- templates/             # Thymeleaf HTML templates
|   |-- test/
.env.example                   # Template for local dev env vars
.gitignore
README.md
```
