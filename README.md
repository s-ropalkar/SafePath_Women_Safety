<div align="center">

# 🛡️ SafePath AI

### Safety-Aware Navigation with Explainable AI

**Plan safer routes. Track live journeys. Alert guardians in real time.**

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-red?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8+-blue?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Leaflet](https://img.shields.io/badge/Leaflet-Maps-green?logo=leaflet&logoColor=white)](https://leafletjs.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

[Features](#-features) · [Demo](#-demo) · [Installation](#-installation) · [Architecture](#-architecture) · [Algorithms](#-algorithms-used)

</div>

---

## 📖 Project Description

**SafePath AI** is a hackathon-grade **women's safety navigation platform** that goes beyond shortest-path routing. It combines **graph algorithms**, **OpenStreetMap POI intelligence**, and **community-sourced risk data** to recommend routes that balance distance and safety — with full **Explainable AI (XAI)** transparency for judges and users.

There is **no dependency on official NCRB/police crime datasets** at launch. Safety scores are built from a **POI + community proxy model**, with a clear **roadmap for NCRB and police open-data integration**.

> *“Don't just show Safety = 91. Show Safety = 91, Confidence = 96%, and why.”*

---

## ✨ Features

### 🗺️ Smart Routing
| Feature | Description |
|---------|-------------|
| **3 route modes** | Shortest (Dijkstra), Safest (safety-maximizing Dijkstra), Balanced (A\*) |
| **OSRM integration** | Real road geometry from OpenStreetMap via OSRM alternatives |
| **Night-time AI** | After 9 PM, automatically recommends the Safest route when competitive |
| **Route comparison panel** | Distance, safety score, algorithm, XAI confidence, and reason bullets |

### 🧠 Explainable Safety Score (XAI)
| Feature | Description |
|---------|-------------|
| **Live safety score** | 0–100 score with SAFE / MODERATE / RISKY labels |
| **Confidence %** | AI-estimated confidence from POI coverage, report density, and freshness |
| **30-min forecast** | Predicts whether a route may become riskier (night, reports, patterns) |
| **Factor breakdown** | Human-readable XAI factors in the sidebar |

### 👥 Community Intelligence
| Feature | Description |
|---------|-------------|
| **Unsafe heatmap** | Community reports visualized with risk levels |
| **Confirmed zones** | 3+ reports within ~100 m → **Confirmed** badge on heatmap |
| **Recency decay** | Reports older than 30 days count **50% less** in routing |
| **AI anomaly filter** | Rate limit (5/day), velocity spike & geo-jump detection before routing impact |
| **Zone prediction** | AI-estimated unsafe-zone probability without official crime data |

### 🚨 Safety & Guardians
| Feature | Description |
|---------|-------------|
| **Live GPS tracking** | Real-time position updates with safety recalculation |
| **Guardian dashboard** | Secure live view link (`sessionId` + `viewKey`) |
| **SOS emergency** | 3-second countdown with audible siren + guardian email alerts |
| **Journey AI monitor** | Deviation alerts, 15-min stop confirmation, risk-forecast toasts |
| **Emergency reroute** | Auto-suggest safer path when safety drops critically |
| **Safe arrival** | Trip rating feeds back into the safety model |

### 🎯 Demo & UX
| Feature | Description |
|---------|-------------|
| **One-click demo** | Full Delhi demo trip without laptop GPS — built for judges |
| **POI checkpoints** | Police, hospitals, hotels, metro, bus stops on map |
| **Trip history & reports** | Persisted in MySQL, visible in user profile |
| **Premium purple UI** | Sidebar layout, scrollable map + route panel, resizable divider |

---

## 🛠️ Tech Stack

| Layer | Technologies |
|-------|--------------|
| **Backend** | Java 17, Maven, `com.sun.net.httpserver`, JDBC |
| **Database** | TiDB Cloud / MySQL 8 (mysql-connector-j 9.4.0 via Maven) |
| **Frontend** | HTML5, CSS3, Vanilla JavaScript |
| **Maps** | [Leaflet.js](https://leafletjs.com/), Leaflet.heat |
| **Routing** | [OSRM](http://project-osrm.org/) (road geometry & alternatives) |
| **POI data** | [OpenStreetMap Overpass API](https://overpass-api.de/) |
| **Auth** | Email/password, Google OAuth 2.0, password reset tokens |
| **Alerts** | SMTP email (Gmail-compatible), async email queue |
| **Audio** | Web Audio API (SOS siren) |

---

## 🏗️ Architecture

```mermaid
flowchart TB
    subgraph Client["Browser (Frontend)"]
        UI[index.html / app.js]
        MAP[Leaflet Map]
        RC[Route Compare + XAI Panel]
        HM[Community Heatmap]
    end

    subgraph Server["Java HTTP Server :8080"]
        API[REST API Handlers]
        RA[RouteAnalyzer]
        SE[SafetyEngine + XAI]
        RV[ReportValidator]
        PE[PathEngine / A* / Yen]
        GB[GraphBuilder]
    end

    subgraph External["External Services"]
        OSRM[OSRM Routing]
        OVR[Overpass POI API]
        SMTP[SMTP Email]
    end

    subgraph Data["Persistence"]
        MY[(MySQL)]
        MEM[(In-Memory Sessions)]
    end

    UI --> API
    MAP --> OSRM
    API --> RA
    RA --> GB --> PE
    RA --> SE
    SE --> OVR
    SE --> RV
    API --> MY
    API --> MEM
    API --> SMTP
    HM --> API
```

### Request flow (Plan Route)

```
User enters source & destination
        ↓
Frontend geocodes + fetches OSRM road alternatives
        ↓
POST /api/analyze-route  →  Build road graph from segments
        ↓
Dijkstra (shortest) · Dijkstra (safest) · A* (balanced)
        ↓
SafetyEngine scores each path (POI Gaussian decay + community weight)
        ↓
XAI: confidence %, 30-min forecast, reason bullets
        ↓
Route cards rendered · user selects · live tracking begins
```

---

## 🧮 Algorithms Used

| Problem | Algorithm / Technique | Implementation |
|---------|----------------------|----------------|
| Shortest path | **Dijkstra** (min distance) | `PathEngine.shortestPath()` |
| Safest path | **Dijkstra** (maximize min edge safety) | `PathEngine.safestPath()` |
| Balanced path | **A\*** (combined distance + safety heuristic) | `AStarEngine.balancedPath()` |
| Alternative routes | **Yen's k-shortest paths** (k=3) | `YenPathFinder` |
| Graph structure | **Adjacency list** (`HashMap`) | `graph/Graph.java` |
| Safety scoring | **Gaussian POI decay** + time-of-day + community penalty | `SafetyEngine.java` |
| POI proximity | Cached Overpass POI linear scan + haversine distance | `PoiFetcher.java` |
| Community zones | Haversine radius (500 m) + recency decay + confirmation weight | `UnsafeStore` + `SafetyEngine` |
| Report abuse | Rate limit, velocity spike, geo-jump anomaly detection | `ReportValidator.java` |
| XAI bundle | Confidence, forward prediction, trend classification | `SafetyInsight.java` |
| Deviation check | Point-to-polyline distance (client) | `features/deviation.js` |

### Safety score formula (simplified)

```
nodeSafety = baseScore
           + Σ (POI_weight × Gaussian(distance, σ))   ← police, hospital, metro, …
           − communityPenalty × effectiveReportWeight  ← recency × confirmation
           ± nightTimeModifier
```

---

## 📁 Folder Structure

```
safepath_realtime_fixed/
├── README.md                          ← You are here
├── render.yaml                        ← Render.com deployment blueprint
├── START-SAFEPATH.bat                 ← Quick launcher (Windows)
├── .vscode/                           ← VS Code tasks & launch configs
│
└── safepath/
    ├── pom.xml                        ← Maven build (Java 17, fat JAR)
    ├── config/
    │   ├── app.properties.example     ← Config template (copy to app.properties)
    │   └── app.properties             ← Local secrets (gitignored)
    │
    ├── src/main/java/server/          ← Java source (Maven standard layout)
    │   ├── Server.java                ← HTTP server & all API routes
    │   ├── db/Database.java           ← TiDB/MySQL schema & queries
    │   ├── core/                      ← Routing engines, SafetyEngine, XAI
    │   ├── graph/
    │   ├── models/
    │   ├── store/
    │   ├── services/
    │   └── util/                      ← AppConfig, AppPaths, JsonUtil
    │
    ├── frontend/                      ← Static HTML/CSS/JS (served by Server)
    │   ├── index.html, app.js, styles.css
    │   └── features/                  ← heatmap, routeCompare, demo, …
    │
    ├── target/                        ← Maven build output (gitignored)
    │   └── safepath-1.0.0.jar         ← Executable fat JAR (all deps bundled)
    │
    └── run.bat / run.ps1 / run.sh     ← Maven build + run scripts
```

---

## ⚙️ Installation

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Java JDK | **17** or higher |
| Apache Maven | 3.9+ |
| Database | **TiDB Cloud** or MySQL 8.0+ |
| Browser | Chrome / Edge / Firefox (modern) |
| Internet | Required for OSRM, OSM tiles, Overpass POIs |

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/RiddhiRopalkar/SafePath_Women_Safety.git
cd SafePath_Women_Safety/safepath

# 2. Copy configuration template
cp config/app.properties.example config/app.properties   # Linux/macOS
# copy config\app.properties.example config\app.properties   # Windows

# 3. Edit config/app.properties — set TiDB/MySQL host, user, password

# 4. Build with Maven (wrapper included — no global Maven install required)
./mvnw clean package          # Linux/macOS
# mvnw.cmd clean package      # Windows

# 5. Run
java -jar target/safepath-1.0.0.jar
```

On first startup the server creates all required tables. For **TiDB Cloud**, set `mysql.autoCreateDatabase=false` (database is pre-provisioned).

---

## 🔧 Configuration

Copy `safepath/config/app.properties.example` → `safepath/config/app.properties` (never commit real passwords).

### TiDB Cloud (production)

```properties
mysql.host=gateway01.ap-southeast-1.prod.aws.tidbcloud.com
mysql.port=4000
mysql.database=safepath
mysql.user=your-tidb-user
mysql.password=your-tidb-password
mysql.sslMode=REQUIRED
mysql.autoCreateDatabase=false
```

JDBC URL format used internally:

```
jdbc:mysql://HOST:PORT/DATABASE?sslMode=REQUIRED&serverTimezone=UTC
```

### Local MySQL (development)

```properties
mysql.host=gateway01.ap-southeast-1.prod.aws.tidbcloud.com
mysql.port=4000
mysql.database=safepath
mysql.user=2aLhSpk2aZmebUg.root
mysql.password=YOUR_TIDB_PASSWORD


mysql.sslMode=REQUIRED
mysql.autoCreateDatabase=true
```

**Environment overrides** (recommended on Render): `SAFEPATH_MYSQL_HOST`, `SAFEPATH_MYSQL_PORT`, `SAFEPATH_MYSQL_DATABASE`, `SAFEPATH_MYSQL_USER`, `SAFEPATH_MYSQL_PASSWORD`, `SAFEPATH_MYSQL_SSL_MODE`

**Project root override** (when cwd differs): `SAFEPATH_ROOT=/path/to/safepath`

### SMTP email (optional — guardian alerts)

```properties
smtp.host=smtp.gmail.com
smtp.port=587
smtp.user=your@gmail.com
smtp.password=your-16-char-app-password
smtp.from=your@gmail.com
smtp.ssl=false
```

Use a [Gmail App Password](https://myaccount.google.com/apppasswords) with 2FA enabled.  
Overrides: `SAFEPATH_SMTP_HOST`, `SAFEPATH_SMTP_USER`, `SAFEPATH_SMTP_PASS`

### Google Sign-In (optional)

1. [Google Cloud Console](https://console.cloud.google.com/) → Credentials → OAuth 2.0 Client ID (Web)
2. Authorized origin: `http://localhost:8080`
3. Set `google.client.id` in `app.properties`

### Server port

```properties
server.port=8080
```

---

## ▶️ Run

> **Important:** Do **not** use VS Code Live Server (port 5503). The app must be served by the Java backend.

### Build

```bash
cd safepath
mvn clean package
```

### Run locally

```bash
cd safepath
java -jar target/safepath-1.0.0.jar
```

Open: **http://localhost:8080/**

| Method | Command |
|--------|---------|
| **Maven + JAR** | `./mvnw clean package && java -jar target/safepath-1.0.0.jar` |
| **Windows — double-click** | `safepath/run.bat` or `START-SAFEPATH.bat` |
| **PowerShell** | `.\safepath\run.ps1` |
| **Linux / macOS** | `./safepath/run.sh` |
| **VS Code** | Task **safepath: run server** or F5 |

Health check: `http://localhost:8080/health`

### Deploy on Render

1. Push repo to GitHub.
2. Create a **Web Service** on [Render](https://render.com) and connect the repo.
3. Use `render.yaml` or set manually:
   - **Build command:** `cd safepath && ./mvnw clean package -DskipTests`
   - **Start command:** `cd safepath && java -jar target/safepath-1.0.0.jar`
4. Add environment variables:
   - `SAFEPATH_MYSQL_HOST`, `SAFEPATH_MYSQL_PORT` (`4000`), `SAFEPATH_MYSQL_USER`, `SAFEPATH_MYSQL_PASSWORD`
   - `SAFEPATH_MYSQL_SSL_MODE=REQUIRED`
   - `SAFEPATH_ROOT` → absolute path to `safepath` folder on Render (e.g. `/opt/render/project/src/safepath`)
   - Optional: `SAFEPATH_SMTP_*`, `SAFEPATH_APP_BASE_URL`, `SAFEPATH_GOOGLE_CLIENT_ID`
5. Render sets `PORT` automatically — the server reads it via `AppConfig.serverPort()`.

---

## 🎬 Demo

### Quick judge demo (no GPS needed)

1. Open **http://localhost:8080/** and log in (or register).
2. Click **Launch One-Click Demo** in the sidebar.
3. Click **Find Route** — compare **Shortest**, **Safest**, and **Balanced** with XAI confidence.
4. Toggle **Show Unsafe Heatmap** — see Confirmed vs Unverified community zones.
5. Open **Guardians** → add a guardian → copy the live tracking link.
6. Open `guardian.html` in another tab to show the live guardian view.

### Live GPS demo

1. Click **Turn On Location** → allow browser GPS.
2. **Start Tracking** — watch the XAI sidebar update (confidence, 30-min forecast).
3. **Report Unsafe Location** — see AI anomaly filter feedback.
4. **Emergency SOS** — 3-second countdown with siren (guardian email if SMTP configured).

### Sample API — analyze route

```http
POST /api/analyze-route
Content-Type: application/json

{
  "coordinates": "[[28.6315,77.2167],[28.6129,77.2295]]",
  "mode": "WALK"
}
```

Response includes `routes.shortest`, `routes.safest`, `routes.balanced` each with `safetyScore`, `confidence`, `predictedScore30Min`, and `reasons`.

---

## 🔮 Future Scope

- [ ] **NCRB / police open-data integration** for official crime statistics layer
- [ ] **ML-based risk prediction** trained on historical trip + report data
- [ ] **Native mobile apps** (Android / iOS) with background GPS
- [ ] **SMS / push notifications** alongside email alerts
- [ ] **Multi-city POI pre-caching** for faster cold starts
- [ ] **Crowdsourced verification** with trusted-user weighting
- [ ] **Public transport safety** (bus/metro timing + crowd density)
- [ ] **Accessibility routing** (well-lit, wheelchair-friendly paths)
- [ ] **Unit & integration tests** for routing and safety engines
- [ ] **Docker Compose** one-command deployment

---

## 👥 Contributors

| Name | Role |
|------|------|
| **[Riddhi Ropalkar](https://github.com/RiddhiRopalkar)** | Project lead & repository maintainer |
| **[Siddhi Ropalkar](https://github.com/s-ropalkar)** | Project lead & repository maintainer |

> Add your name here via pull request if you contributed to SafePath AI.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

```
MIT License — free to use, modify, and distribute with attribution.
```

---

<div align="center">

**Built with ❤️ for safer journeys**

*SafePath AI — Your Safety, Our Priority*

[⬆ Back to top](#️-safepath-ai)

</div>
