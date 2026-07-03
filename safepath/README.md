# SafePath AI - Setup & Run

## Requirements
- Java 11+
- **MySQL 8+** (running locally or remote)

## Configuration

1. Copy `config/app.properties.example` to `config/app.properties` (gitignored — never commit real passwords).
2. Set **MySQL** credentials (required), or use env vars `SAFEPATH_MYSQL_PASSWORD`, etc.

### Real email (SMTP)

Gmail example:
```properties
smtp.host=smtp.gmail.com
smtp.port=587
smtp.user=your@gmail.com
smtp.password=your-16-char-app-password
smtp.from=your@gmail.com
smtp.ssl=false
```

Create a **Gmail App Password**: Google Account → Security → 2-Step Verification → App passwords.

Environment variables override the file: `SAFEPATH_SMTP_HOST`, `SAFEPATH_SMTP_USER`, `SAFEPATH_SMTP_PASS`, etc.

### Google Sign-In (real OAuth)

1. Open [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → **Credentials**
2. Create **OAuth 2.0 Client ID** → Application type: **Web application**
3. **Authorized JavaScript origins:** `http://localhost:8080`
4. Copy the Client ID into `config/app.properties`:
   ```properties
   google.client.id=123456789-xxxx.apps.googleusercontent.com
   ```
5. Restart the server and use the **Continue with Google** button on the login page.

## Database (MySQL)

All persistent data is stored in **MySQL** only:
- `users` — accounts (register / Google login)
- `guardians` — guardian name, phone, **email** per user
- `auth_tokens` — login sessions
- `email_outbox` — alert copies when SMTP is unavailable
- `unsafe_locations` — community unsafe zone reports (persistent, affects routing)
- `password_reset_tokens` — password reset links (1 hour expiry)

The server auto-creates the `safepath` database and tables on first run.

## New in this release

- **Password reset** — Login → Forgot password; reset link emailed via async queue
- **Secure guardian view** — `/api/session` requires `sessionId` + `viewKey`; no guardian PII exposed
- **Async email queue** — SMTP no longer blocks HTTP threads
- **Demo mode** — One-click Delhi demo trip with simulated GPS (no laptop GPS needed)
- **Unsafe heatmap** — Community reports from MySQL with risk legend
- **Route comparison panel** — Distance, safety score, algorithm, and explanation for 3 routes
- **Unified dark UI** — Login, main app, and guardian dashboard share one design system

## Demo for judges

1. Log in → click **Launch One-Click Demo**
2. Toggle **Show Unsafe Heatmap** to see community risk zones
3. Copy guardian link (includes secure key) and open `guardian.html` in another tab

## Run (VS Code / Windows)

**Do not use Live Server** (port 5503). The app needs the Java server on port **8080**.

### Option A — VS Code Run (recommended)
1. Open the `safepath_realtime_fixed` folder in VS Code
2. **Run and Debug** → **SafePath: Run Server (port 8080)** → F5
3. Browser opens **http://localhost:8080/**

### Option B — Double-click
Run `safepath/run.bat`

### Option C — Terminal
```bash
cd safepath
javac -cp "lib/*" -d out -sourcepath src src/server/Server.java
java -cp "out;lib/*" server.Server
```
Then open: http://localhost:8080/

If you see `ERR_CONNECTION_REFUSED`, the Java server is not running. Start it first, then open port **8080**, not Live Server.

## Project Structure
```
safepath/
├── config/app.properties    ← MySQL, SMTP, Google settings
├── lib/mysql-connector-j.jar
├── src/server/
│   ├── Server.java          ← HTTP server, all endpoints
│   ├── db/Database.java     ← MySQL JDBC
│   ├── core/
│   │   ├── PathEngine.java  ← Dijkstra (shortest/safest)
│   │   ├── AStarEngine.java ← A* balanced route
│   │   ├── RouteAnalyzer.java
│   │   ├── YenPathFinder.java
│   │   ├── PathResult.java
│   │   └── SafetyEngine.java← Overpass API safety scoring
│   ├── graph/
│   ├── models/
│   ├── store/               ← In-memory sessions & unsafe reports
│   ├── services/            ← Auth, guardians, email, alerts
│   └── util/
└── frontend/
    ├── index.html, login.html, guardian.html
    ├── app.js, login.js, guardian.js, origin.js
    └── features/            ← heatmap, demo, routeCompare, safetyStatus, …
```

## DSA Used
| Feature | Data Structure / Algorithm |
|---|---|
| Route finding | Dijkstra, A*, Yen's k-shortest paths |
| Graph | HashMap adjacency list |
| Safety scoring | Weighted formula with live OSM POI data |
| POI lookup | Linear scan over cached Overpass POI list |
| Unsafe zones | MySQL `unsafe_locations` + haversine proximity |
| Sessions | In-memory HashMap (per trip) |
