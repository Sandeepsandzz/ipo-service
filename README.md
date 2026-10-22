# IPO Tracker Microservice (Spring Boot)

A production-ready Java 21 Spring Boot REST microservice that tracks and provides the list of Initial Public Offerings (IPOs) currently available to apply as of today, along with upcoming and closed IPOs.

---

## Features

- **Active IPOs as of Today**: Instantly retrieve all Mainboard and SME IPOs currently open for subscription (`GET /api/v1/ipos/active`).
- **Dynamic Date Evaluation**: Computes real-time status (`OPEN`, `UPCOMING`, `CLOSED`) dynamically against today's system date or an optional query date.
- **Search & Filtering**: Filter IPOs by status, issue type (`MAINBOARD`, `SME`), or keyword query (symbol/company).
- **Comprehensive IPO Metadata**:
  - Issue price band (min/max price)
  - Lot size & minimum investment calculation
  - Issue size (in ₹ Cr)
  - Grey Market Premium (GMP)
  - Live subscription status breakdown (Retail, QIB, NII)
  - Registrar and Exchange listings (NSE/BSE)
- **Production Ready**: Spring Actuator health/metrics endpoints, CORS pre-configured, multi-stage Docker build ready for free cloud deployments (Render, Railway, Fly.io, Koyeb, AWS App Runner).

---

## API Endpoints

### 1. Get IPOs Currently Open to Apply as of Today
- **URL**: `GET /api/v1/ipos/active`
- **Optional Query Param**: `asOfDate` (YYYY-MM-DD, defaults to current date)
- **Response**:
```json
{
  "success": true,
  "message": "Successfully fetched 5 IPO(s) open to apply as of 2026-10-22",
  "count": 5,
  "timestamp": "2026-10-22T21:40:00",
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

### 2. Get All IPOs (With Filters)
- **URL**: `GET /api/v1/ipos`
- **Query Params**:
  - `status`: `OPEN` | `UPCOMING` | `CLOSED`
  - `type`: `MAINBOARD` | `SME`
  - `query`: Search string for company name or symbol (e.g. `swiggy`, `solar`)

### 3. Get Upcoming IPOs
- **URL**: `GET /api/v1/ipos/upcoming`

### 4. Get Specific IPO by ID or Symbol
- **URL**: `GET /api/v1/ipos/{id}` (e.g. `/api/v1/ipos/swiggy-ltd` or `/api/v1/ipos/SWIGGY`)

### 5. Health Check (Actuator)
- **URL**: `GET /actuator/health`

---

## Running Locally

### Prerequisites
- JDK 21+
- Maven 3.9+

### Build & Run
```bash
# Build the application
mvn clean package

# Run the Spring Boot application
mvn spring-boot:run
```
The application will start on `http://localhost:8080`.

---

## Free Cloud Deployment

### Option 1: Render.com (Docker)
1. Push this project to a GitHub repository.
2. In Render, create a new **Web Service** and connect the repository.
3. Choose **Docker** environment. Render will automatically build the `Dockerfile` and expose port `8080`.

### Option 2: Railway.app / Fly.io / Koyeb
Simply connect your repo; the included multi-stage `Dockerfile` handles everything automatically.
