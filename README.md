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

## Option A: Railway (Recommended)

Railway provides native Maven/Java support with managed MySQL. It is the easiest deployment path for this project.

### Step 1: Push to GitHub
```bash
git add .
git commit -m "Ready for Railway deployment"
git push origin main
```

### Step 2: Deploy on Railway
1. Go to [https://railway.app](https://railway.app) and sign in with GitHub
2. Click **New Project** → **Deploy from GitHub repo**
3. Select your repository (`AfifJZ/DigitalMenuSystemDemo`)
4. Railway detects the `Dockerfile` automatically. Set **Root Directory** to `menumanager`
5. Click **Deploy**

### Step 3: Add MySQL Database
1. In your Railway project dashboard, click **New** → **Database** → **Add MySQL**
2. Railway automatically creates and links the database
3. The following env vars are **injected automatically**: `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_DATABASE`
4. Your app already reads these (see `application.properties`), so no extra config needed

### Step 4: Set Environment Variables
In Railway Dashboard → your service → **Variables**, add:

| Variable | Value | Description |
|---|---|---|
| `STRIPE_SECRET_KEY` | `sk_test_...` | Your Stripe secret key |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` | Stripe webhook signing secret |
| `PUBLIC_BASE_URL` | `https://your-app.up.railway.app` | Your Railway app URL |
| `MAIL_USERNAME` | `your-email@gmail.com` | Gmail for OTP sending |
| `MAIL_PASSWORD` | `your-app-password` | Gmail app password |
| `MAIL_CONSOLE_FALLBACK` | `false` | Disable OTP console print in production |
| `STRIPE_CONNECT_ENABLED` | `true` | Enable Stripe Connect in production |

### Step 5: Preview Your App
- Railway auto-deploys and provides a public URL like `https://your-app.up.railway.app`
- **Customer menu:** `https://your-app.up.railway.app/customer`
- **Staff login:** `https://your-app.up.railway.app/manage-login`

### Step 6: Configure Stripe Webhook (for payments)
In Stripe Dashboard → **Webhooks** → **Add endpoint**:
- **URL:** `https://your-app.up.railway.app/api/payments/stripe/webhook`
- **Events:** `checkout.session.completed`
- Copy the signing secret and set it as `STRIPE_WEBHOOK_SECRET` in Railway variables

---

## Option B: Docker (Any Cloud VM)

```bash
cd menumanager
docker build -t digital-menu-system .
docker run -p 8080:8080 ^
  -e DB_URL=jdbc:mysql://... ^
  -e STRIPE_SECRET_KEY=sk_test_... ^
  -e PUBLIC_BASE_URL=https://your-domain.com ^
  digital-menu-system
```

---

## Project Structure

```
menumanager/
|-- Dockerfile              # Container build (for Railway / any cloud)
|-- railway.toml            # Railway deployment config
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


