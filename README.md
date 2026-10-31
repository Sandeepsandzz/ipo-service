# IPO Tracker Microservice & Telegram Bot (Spring Boot)

A production-ready Java 21 Spring Boot REST microservice and interactive **Telegram Bot** that tracks and provides Initial Public Offerings (IPOs) currently available to apply as of today, along with upcoming and closed IPOs.

---

## Features

- **Interactive Telegram Bot**:
  - Live long polling (no public webhook/SSL domain needed for local execution).
  - Rich HTML formatted message cards with live GMP, price bands, and subscription data.
  - Interactive reply keyboard buttons (`[🟢 Active IPOs]`, `[📅 Upcoming IPOs]`, `[📊 Closed IPOs]`, `[❓ Help]`).
  - Search IPO details by typing symbol or company name (e.g. `/ipo swiggy`).
- **Active IPOs as of Today**:
  - Instantly retrieve all Mainboard and SME IPOs open for subscription (`GET /api/v1/ipos/active`).
- **Dynamic Date Evaluation**:
  - Computes real-time status (`OPEN`, `UPCOMING`, `CLOSED`) dynamically against today's system date.
- **REST APIs**: Full CRUD and filtering by status, issue type (`MAINBOARD`, `SME`), or keyword.
- **Deployment Ready**:
  - Multi-stage `Dockerfile` ready for free cloud deployments (Render, Railway, Fly.io, Koyeb).
  - Spring Boot Actuator health endpoint (`/actuator/health`).

---

## Telegram Bot Setup

### 1. Get Your Bot Token
1. Open Telegram and search for `@BotFather`.
2. Send `/newbot` and follow the steps to choose a name and username (e.g., `MyIpoTrackerBot`).
3. BotFather will provide an HTTP API token (e.g., `7890123456:AAFlkdfj_...`).

### 2. Configure the Token
You can provide the token in either of two ways:

#### Option A: Via Environment Variable (Recommended)
```powershell
$env:TELEGRAM_BOT_TOKEN="your_botfather_token_here"
.\mvnw.cmd spring-boot:run
```

#### Option B: In `src/main/resources/application.properties`
```properties
telegram.bot.token=your_botfather_token_here
telegram.bot.username=MyIpoTrackerBot
```

---

## Telegram Bot Commands & Interactions

| Command / Button | Description |
| :--- | :--- |
| **`/start`** | Welcome message with interactive keyboard menu |
| **`🟢 Active IPOs`** or **`/active`** | List all IPOs open today with quick 1-click company buttons |
| **`📅 Upcoming IPOs`** or **`/upcoming`** | List upcoming IPOs and expected opening dates |
| **`🎲 Allotment Odds`** or **`/odds [name] [pans]`** | **Multi-PAN Allotment Odds Calculator** across family PAN cards (e.g. `/odds swiggy 3`) |
| **`🧮 Profit Calculator`** or **`/calc [name] [lots]`** | **Custom Listing Profit Estimator** for 1, 2, 5 & 14 lots (e.g. `/calc swiggy 5`) |
| **`📊 Closed IPOs`** or **`/closed`** | List recently closed IPOs |
| **`/ipo <symbol>`** | Detailed card with interactive inline buttons |
| **`🎯 Apply Score` (Inline)** | Automated 0-100 rating based on GMP, QIB subscription & fundamentals |
| **`📊 Subscription` (Inline)** | Live category-wise demand breakdown (QIB, Retail, NII) |
| **`🏦 Registrar Link` (Inline)** | Direct 1-click verified link to MUFG Intime, KFintech & BSE India |
| **`❓ Help`** or **`/help`** | Full command reference |

---

## REST API Endpoints

### 1. Get IPOs Currently Open as of Today
- **URL**: `GET /api/v1/ipos/active`
- **Response**:
```json
{
  "success": true,
  "message": "Successfully fetched 5 IPO(s) open to apply as of 2026-10-22",
  "count": 5,
  "timestamp": "2026-10-22T21:47:03",
  "data": [
    {
      "id": "swiggy-ltd",
      "companyName": "Swiggy Limited",
      "symbol": "SWIGGY",
      "exchange": "NSE, BSE",
      "issueType": "MAINBOARD",
      "openDate": "2026-10-21",
      "closeDate": "2026-10-24",
      "listingDate": "2026-10-28",
      "minPrice": 371.0,
      "maxPrice": 390.0,
      "lotSize": 38,
      "minInvestment": 14820.0,
      "issueSize": "₹11,327 Cr",
      "status": "OPEN",
      "gmp": "+₹25 (6.41%)",
      "subscriptionStatus": "3.59x (Retail: 1.14x, QIB: 6.02x, NII: 1.25x)",
      "registrar": "Link Intime India Pvt Ltd"
    }
  ]
}
```

### 2. Search / Filter All IPOs
- **URL**: `GET /api/v1/ipos?status=OPEN&type=MAINBOARD&query=swiggy`

### 3. Upcoming IPOs
- **URL**: `GET /api/v1/ipos/upcoming`

### 4. Single IPO Details
- **URL**: `GET /api/v1/ipos/{id}`

---

## Running Locally

```powershell
cd "C:\Users\sandy\.gemini\antigravity\scratch\ipo-service"

# Run tests
.\mvnw.cmd test

# Run application
.\mvnw.cmd spring-boot:run
```

---

## Free Cloud Deployment

### Render / Railway / Fly.io (Docker)
The included multi-stage `Dockerfile` builds and runs the service automatically.
In your deployment environment variables on Render / Railway, simply add:
- `TELEGRAM_BOT_TOKEN` = `your_bot_token`
- `TELEGRAM_BOT_USERNAME` = `your_bot_username`
The bot will connect and poll automatically without needing a webhook URL.

### Administrative alert API

All `/api/v1/alerts/**` routes require the `X-Admin-Token` header and the server must be configured with a strong `IPO_ADMIN_TOKEN`. If it is unset, these routes return `503`. Do not place this token in a mobile or browser build. Subscriber listing is not exposed by the API.

Subscriber records, including saved Telegram PANs, are encrypted with AES-GCM at rest. Set `IPO_PAN_ENCRYPTION_KEY` to a strong secret of at least 16 characters and keep it stable across restarts; encrypted data cannot be read without it. Existing plaintext subscriber JSON is migrated when this key is configured.
