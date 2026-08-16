# Perfect Run

Perfect Run is a full-stack cricket drafting and season-simulation game. Build an XI from real historical squads, appoint a captain and wicketkeeper, and chase an undefeated run across IPL, ODI World Cup, T20 World Cup, or World Test Championship cricket.

The player catalogue, available seasons, team rosters, statistics, roles, and ratings are derived from local [Cricsheet](https://cricsheet.org/) match data. Simulation is seeded and reproducible, while captain and wicketkeeper choices meaningfully change team strength and results.

## Features

- Four playable formats: IPL, ODI World Cup, T20 World Cup, and WTC.
- Random historical team-season reveals with limited team and year rerolls.
- Real player statistics and format-aware ratings.
- XI validation, including IPL overseas limits and a mandatory wicketkeeper.
- Live XI composition counts for batters, bowlers, all-rounders, and wicketkeepers throughout the draft.
- Captain and keeper selections that affect ratings, match scores, and season outcomes.
- Two confirmed post-simulation player swaps per run in every format, with one-for-one redrafting and leader revalidation.
- Hard Mode that permanently locks for the run when the first player is drafted.
- Historical Squad and Legacy XI opponent modes.
- Historical opponents default to random eligible editions. Selecting a year switches every format to an exact-edition field: selecting 2022 adds the drafted XI to that 2022 competition and every historical opponent is its 2022 version.
- One row per opponent identity in standings. When a format needs more fixtures than the edition has teams, opponents repeat as fixtures without importing squads from another season.
- Correct format-specific qualification paths and knockout brackets.
- Full scorecards, league tables, and awards for the current session without silently persisting private results.
- Format-aware results presentation with a clear season verdict, finish, record, XI identity, copyable challenge text, and reduced-motion-safe champion confetti.
- Ball-based cricket over notation: six legal balls per over, never decimal-base overs such as `12.09` or `60.98`.
- Seeded toss winners and bat/bowl decisions, alternating innings order, target-aware chases that stop on the winning delivery, and run/wicket margins that match the result.
- Responsive, sharp-edged interface with route-level code splitting.
- Mobile-first result cards and navigation, horizontally safe data tables, and bounded ultrawide layouts up to 1500px.
- Persistent, server-verified Hard Mode leaderboards for unbeaten runs in every format, with shareable XIs.

## Game modes

| Mode | Competition path | Perfect run |
| --- | --- | ---: |
| IPL | 14 league matches and IPL Page playoffs | 16–0 |
| ODI World Cup | 9 league matches, semifinal, final | 11–0 |
| T20 World Cup | 6 group/Super 8 matches, semifinal, final | 8–0 |
| World Test Championship | 9 unique WTC nations and the final | 10–0 |

Qualification is driven by the league table. World Cups use `1st vs 4th` and `2nd vs 3rd` semifinals. WTC sends the top two directly to the final. IPL uses Qualifier 1, Eliminator, Qualifier 2, and the Final, including the top-two second chance.

## How a run works

1. Choose a format.
2. Keep **Random editions** for a varied historical field, or select a competition year. A selected year caps draft reveals and makes every Historical Squad opponent use that exact edition.
3. Reveal a historical squad and load its players.
4. Draft one player, then continue until the XI is complete.
5. Meet the mode rules:
   - exactly 11 players;
   - at least one wicketkeeper;
   - no more than four overseas players in IPL.
6. Select a captain and an eligible wicketkeeper.
7. Choose Historical Squads or Legacy XI opponents and optionally enable Hard Mode.
8. Simulate the competition and inspect standings, scorecards, and awards.
9. If the Hard Mode run finishes unbeaten, upload the verified XI from Results and share it.

The same XI, role selections, mode settings, and run seed always reproduce the same season. Changing the captain or keeper changes the deterministic simulation identity and therefore the results.

Hard Mode must be selected before the first draft pick. Once drafting begins, the setting is locked until **New Game**, preventing difficulty changes midway through a run.

## Leaderboards and sharing

Every format has its own leaderboard at `/leaderboard/{mode}`. The board is a trophy room, not a game-start flow: keep playing through Draft, and upload only from a qualifying Results screen. A run must use Hard Mode and finish with zero losses before the 2–24 character display-name form is unlocked. Publishing also requires an explicit consent checkbox and the **Post run** action.

Ordinary simulation results are session-only. They are not written to browser storage and disappear on refresh or browser restart. A storage migration clears results saved by older app versions. Only a user-approved, server-verified, unbeaten Hard Mode leaderboard entry is stored durably.

Leaderboard submissions are not trusted blindly. The backend:

1. validates all eleven players against their real Cricsheet team-season records;
2. verifies the captain, eligible wicketkeeper, unique-player rule, and IPL overseas limit;
3. reruns the exact seeded simulation with the submitted settings;
4. requires an explicit publication-consent flag;
5. requires Hard Mode and zero verified losses;
6. rejects the entry if the claimed record differs;
7. ranks verified entries by perfect run, championship, wins, draws, losses, and rating.

Each entry includes the full XI, source seasons, captain, wicketkeeper, opponent type, record, and shareable anchor link. Production entries are stored in Supabase PostgreSQL; local development falls back to `data/leaderboard.json`, which is intentionally ignored by Git.

The global **Rules** dialog explains drafting, the keeper and captain requirements, opponent uniqueness, every format's tournament path, Hard Mode locking, and leaderboard eligibility from any page.

## Technology

### Frontend

- React 19
- Vite 8
- Tailwind CSS 4
- Zustand persistence
- React Router
- Lucide icons
- Native Fetch API with connection retry and request timeouts

### Backend

- Java 21
- Spring Boot 3.5
- Maven Wrapper
- Jackson
- In-memory parsed-data caches

A database is optional for local play. Production uses Supabase PostgreSQL only for explicitly approved, verified leaderboard entries; ordinary runs remain private and session-only.

## Architecture and request flow

```text
Browser (React + Zustand)
        │
        │  /api through the Vite development proxy
        ▼
Spring Boot controllers
        │
        ├── catalogue/player requests ──► indexed Cricsheet caches
        ├── simulation requests ────────► seeded competition engine
        └── approved leaderboard posts ─► server replay + JSON storage
```

The frontend is a client-rendered single-page application. Routes are loaded only when opened, so entering Draft does not download Results, Scorecard, or Leaderboard code in advance. During development, Vite forwards `/api` to Spring Boot. In production, serve both applications behind one origin or set `VITE_API_URL` to the backend URL at build time.

The backend is stateless for ordinary runs. It reads local Cricsheet JSON, converts delivery-level records into player-season aggregates, builds searchable in-memory indexes, and caches those aggregates on disk. A simulation request contains everything needed to reproduce a run: XI, captain, keeper, format, opponent pool, difficulty, season cap, and seed.

## Frontend routes and screens

| Route | Screen | Responsibility |
| --- | --- | --- |
| `/` | Modes | Introduces the game and selects one of the four formats |
| `/draft/{mode}` | Draft | Reveals squads, loads players, validates the XI, selects leaders, and starts simulation |
| `/result` | Results | Shows the verdict, awards, balance, matches, standings, swaps, sharing, and eligible upload form |
| `/scorecard/{matchId}` | Scorecard | Shows toss, innings order, batting, bowling, and result for one match |
| `/leaderboard/{mode}` | Leaderboard | Shows explicitly published, server-verified unbeaten Hard Mode runs |

The global navigation provides Modes, Draft, Results, Leaderboard, and Rules. The responsive footer contains creator links and a back-to-top action while following the same sharp visual system as the game.

## Draft state and validation

The Zustand store owns the active run: mode, season cap, seed, opponent type, difficulty, reveal, rerolls, XI, captain, keeper, swaps, and current session result.

- Catalogue metadata is retained between new runs to avoid repeat downloads.
- Loaded squad payloads are disposable runtime data.
- Full scorecards and simulation results are excluded from persistent browser storage.
- Switching formats resets incompatible run state.
- **New Game** creates a fresh seed, clears the XI/cache, and unlocks Hard Mode selection.
- Duplicate players are rejected by name.
- The eleventh slot is reserved for a wicketkeeper when the first ten contain none.
- Player cards use real cricket country codes rather than treating international players as Indian.
- IPL enforces four overseas players; international modes do not apply that franchise rule.

## Data processing and ratings

Cricsheet files are parsed at delivery level. For every player, team, season, and format the backend aggregates matches, wins, runs, balls faced, dismissals, wickets, legal balls bowled, runs conceded, catches, stumpings, and player-of-the-match awards.

Batting average, strike rate, bowling average, and economy come from those counts. Rating inputs are format-specific and sample-adjusted: small samples regress toward a format baseline so one brief exceptional appearance cannot outrank sustained elite performance. Roles come from real batting/bowling workload. Keeper eligibility uses stumpings plus the keeper supplement. Leadership and keeping impact remain separate from the base rating and are applied when captain and wicketkeeper are selected.

The backend writes a fingerprinted aggregate under `data/.perfect-run-cache/`. Warm starts read the compact cache instead of reparsing thousands of matches. Adding, removing, or replacing source files invalidates the fingerprint.

## Competition and match engine

Every run is deterministic for its complete input, not universally fixed. The same XI, leaders, settings, and seed reproduce the same run. A new game changes the seed; changing captain or keeper also changes the simulation identity.

### Opponents

Historical mode defaults to random eligible team editions. When the player selects a year, it constructs the field from that exact edition in IPL, ODI World Cup, T20 World Cup, and WTC. Selecting 2022 therefore means every historical opponent label and roster is from 2022—not merely a season at or before 2022. Franchise renames share one canonical identity, and standings contain one row per identity. If the competition schedule is longer than the number of unique teams, the same edition's teams are drawn again for return fixtures. Legacy XI mode still builds best-of-era rosters, but a selected year limits eligible team identities to that edition.

### Toss and innings

Every match has a seeded toss winner and bat/bowl decision. The user does not always bat first. Limited-over chases stop on the winning delivery. Tests use two innings per side, a shared time budget, declarations or unfinished innings, and possible draws.

### Scorecard invariants

- Overs derive from legal balls; `18.5` means 18 overs and 5 balls.
- Batting balls and bowling allocations reconcile with innings length.
- Only the bowler delivering an unfinished final over can have a partial over.
- Consecutive overs are allocated to different bowlers.
- T20 bowlers are capped at 4 overs, ODI bowlers at 10, and Test bowlers are unlimited.
- Wickets, totals, chases, margins, innings order, and result text agree.
- Knockout teams come from qualification positions, not a separate random list.

## Results, swaps, and awards

Results separate league-table seed from final tournament finish. Losing the final produces **Runner-up** even if the XI topped the league. Matches appear in competition order and link to full scorecards.

The engine aggregates runs, wickets, and player-of-the-match awards for MVP, best batter, best bowler, and best all-rounder. Results also report batting, bowling, all-round depth, keeping, captain impact, keeper impact, XI identity, role mix, and overall rating.

Every format grants two post-simulation swaps. A swap removes one player, consumes one swap, returns to Draft for one replacement, and clears the old result. Removing the captain or keeper requires that role to be chosen again. Champion presentation supports confetti, while reduced-motion users receive the same information without unnecessary animation.

## Repository structure

```text
cricket-perfect-run/
├── backend/backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/cricketperfectrun/backend/
│       │   ├── config/          # web and request configuration
│       │   ├── controller/      # HTTP API endpoints
│       │   ├── dto/             # transport objects
│       │   ├── model/           # domain and scorecard records
│       │   └── service/         # parsing, ratings, simulation, leaderboard
│       └── resources/           # properties and metadata supplements
│   └── src/test/                # context and cricket-invariant tests
├── data/cricsheet/                 # local data; ignored by Git
├── frontend/
│   ├── package.json
│   └── src/
│       ├── components/          # shared navigation, footer, field, rules
│       ├── data/                # four-mode UI configuration
│       ├── pages/               # lazy-loaded route screens
│       ├── services/            # native HTTP client
│       ├── store/               # run state and persistence policy
│       └── utils/               # cricket display helpers
└── README.md
```

## Prerequisites

- Java 21
- Node.js 20 or newer
- npm
- Cricsheet JSON archives

## Data setup

Download the JSON archives from [Cricsheet downloads](https://cricsheet.org/downloads/) and extract them into:

```text
data/cricsheet/
├── ipl_json/
├── odis_json/
├── t20s_json/
└── tests_json/
```

The backend searches common project-relative data locations, so it works when launched from `backend/backend` as shown below. The large data directory is intentionally excluded from Git.

Optional metadata supplements live in `backend/backend/src/main/resources/`:

- `keeper_players.csv` adds known wicketkeeper names.
- `player_metadata.csv` can provide country or role overrides.

## Run locally

Use two terminal windows from the repository root.

### Terminal 1: backend

```bash
cd backend/backend
./mvnw clean spring-boot:run
```

The API starts at `http://localhost:8080`.

### Terminal 2: frontend

```bash
cd frontend
npm install
npm run dev
```

The development ports are intentionally fixed: the backend uses `8080` and Vite uses `5173`.
If either port is occupied, stop that older process before restarting; Vite will report the conflict
instead of silently opening `5174` and leaving you on an outdated tab. Running Maven with `clean`
also removes stale compiled classes, while the configured Spring Boot main class prevents duplicate
entry-point discovery.

Open `http://localhost:5173`. If that port is occupied, Vite now stops with a clear error so an older frontend cannot remain hidden behind a second port.

The Vite development server proxies `/api` requests to port `8080`. Both processes must remain running.

### Production build

```bash
cd frontend
npm ci
npm run build
npm run preview
```

The optimized static application is written to `frontend/dist/`. For a separately hosted API:

```bash
VITE_API_URL=https://api.example.com/api npm run build
```

Build and run the Java 21 backend artifact with:

```bash
cd backend/backend
./mvnw clean package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

### Free production deployment: GitHub Pages + Render + Supabase

The repository is configured for continuous deployment from GitHub's `main` branch:

- `.github/workflows/deploy-frontend.yml` builds and publishes the React application to GitHub Pages after every push to `main`.
- `render.yaml` defines the free Render backend, health check, Docker runtime, and required database URL.
- `backend/backend/Dockerfile` creates a Java 21 multi-stage production image and runs it as an unprivileged user.
- `backend/backend/src/main/resources/cricket-data/` contains compact generated player-season aggregates. The deployed API therefore does not need the 567 MB raw Cricsheet workspace or a cold parse at startup.

Current production endpoints:

- Game: `https://harshil-lotwala.github.io/cricket-perfect-run/`
- API: `https://perfect-run-api.onrender.com/api`

#### 1. Create the free leaderboard database

1. Create a free Supabase project.
2. Open **Connect**, select the **Session pooler** connection string, and copy its PostgreSQL URI.
3. Keep that URI private. The backend creates the `perfect_run_leaderboard` table automatically on first boot.

Supabase is used only for leaderboard entries that meet all eligibility rules and that the player explicitly approves for publishing. Ordinary simulations and losing runs are not persisted.

#### 2. Deploy the backend on Render

1. In Render, create a **Blueprint** and connect this GitHub repository.
2. Select the repository's `main` branch. Render reads `render.yaml` from the repository root.
3. Set `DATABASE_URL` to the private Supabase Session pooler URI.
4. Set `APP_CORS_ALLOWED_ORIGINS=https://harshil-lotwala.github.io` when prompted. Multiple origins are comma-separated.
5. Create the Blueprint. The API health endpoint is `/api/health` and the public API base is `https://<render-service>.onrender.com/api`.

The Blueprint uses Render's free web-service plan and never requests a disk or payment. Free services sleep after inactivity, so the first request after a quiet period can take longer. Durable approved leaderboard entries live in Supabase instead of Render's temporary filesystem.

#### 3. Deploy the frontend on GitHub Pages

1. Open the repository's **Settings → Pages**.
2. Set **Source** to **GitHub Actions**.
3. Push to `main`. The workflow installs dependencies, builds with `VITE_API_URL=https://perfect-run-api.onrender.com/api` and `VITE_BASE_PATH=/cricket-perfect-run/`, then publishes `frontend/dist`.
4. Check the repository's **Actions** tab if a deployment fails.

GitHub Pages and Render both watch `main`, so every successful push automatically updates the live application and API. Supabase persists only player-approved qualifying leaderboard records. Do not commit database credentials, secrets, or a generated leaderboard file.

#### Why this project does not use Vercel

Vercel is technically compatible—the portable `frontend/vercel.json` remains in the repository—but its GitHub import flow did not attach this repository cleanly during setup and repeatedly led into a trial/clone flow. Because this deployment had a strict zero-dollar requirement, GitHub Pages was selected as the simpler no-card static host. It provides the required `main`-branch automatic deployment and works with the separately hosted Render API.

To validate the exact backend container locally:

```bash
docker build -t perfect-run-api backend/backend
docker run --rm -p 8080:8080 \
  -e APP_CORS_ALLOWED_ORIGINS=http://localhost:5173 \
  -e LEADERBOARD_FILE=/tmp/leaderboard.json \
  perfect-run-api
```

## Troubleshooting

### The frontend says it cannot reach the backend

Confirm that Spring Boot is still running and check:

```bash
curl http://localhost:8080/api/health
```

Expected response:

```text
Backend is running
```

### Vite says port 5173 is in use

Stop the older Vite process. `strictPort` deliberately prevents this app from silently moving to 5174.

```bash
lsof -nP -iTCP:5173 -sTCP:LISTEN
```

For a backend port conflict:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

### A result disappears after refreshing

This is intentional. Private simulation results are session-only. Only unbeaten Hard Mode teams that the user explicitly approves and posts are stored on the leaderboard.

### The season catalogue is empty

Verify that the Cricsheet directories exist and contain extracted JSON files, not only the downloaded ZIP archives.

## API

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/health` | Backend health check |
| `GET` | `/api/meta/{mode}/years` | Available data-backed seasons |
| `GET` | `/api/meta/{mode}/teams-by-year` | Teams grouped by season |
| `GET` | `/api/players/{mode}/{year}/{team}` | Enriched team-season players |
| `POST` | `/api/play/{mode}` | Simulate a full competition |
| `POST` | `/api/simulate/{mode}` | Backward-compatible simulation route |
| `GET` | `/api/leaderboard/{mode}` | Ranked verified runs for one format |
| `POST` | `/api/leaderboard` | Validate, replay, and publish a run |
| `GET` | `/api/admin/cricsheet/stats/{mode}` | Aggregated statistics for diagnostics |

Valid mode identifiers are `ipl`, `odi-world-cup`, `t20-world-cup`, and `wtc`.

Example simulation body:

```json
{
  "team": [],
  "captainId": 123,
  "keeperId": 456,
  "opponentType": "historical",
  "hardMode": false,
  "seed": 424242,
  "maxSeason": 2023
}
```

`team` contains the eleven player objects returned by the player endpoint.
`maxSeason` is optional: omit it or send `null` for random eligible opponent editions; send a data-backed year to require that exact edition for every Historical Squad opponent.

Catalogue and player endpoints send public one-hour cache headers because historical data is immutable during a running backend process. Simulation and publication endpoints are never browser-cached.

### Player-facing and developer errors

The UI uses recovery-focused messages such as retrying, revealing another squad, reviewing XI rules, or starting a new run. It never asks players to inspect frontend/backend terminals or exposes raw HTTP responses. Detailed errors remain available to developers in browser and Spring logs. The native client has a 120-second timeout and retries connection failures four times; it does not retry explicit HTTP validation/authorization failures.

## Simulation rules

- Player ratings are computed from actual format-specific batting and bowling output with sample-size adjustment.
- Captaincy and keeping contribute explicit team-strength effects.
- Captain and keeper IDs are folded into the seeded random stream, making those selections consequential and reproducible.
- Historical opponents are strength-ranked within the selected era.
- Only one season-version of a franchise or country can enter a competition.
- Draft cards show each player's real cricket country code (such as `NZL`, `AUS`, `IND`, or `WI`) in every format.
- Franchise renames such as Delhi Daredevils/Delhi Capitals, Kings XI Punjab/Punjab Kings, and Royal Challengers Bangalore/Bengaluru share one identity.
- Knockout opponents come from qualified league positions, never from a separate random pool.
- Test matches use four innings, draws, declarations/unfinished innings, and ball-correct over notation.
- Bowling cards are allocated over by over: figures reconcile exactly to the innings, only the active final bowler can have a partial over, and T20/ODI bowler limits are enforced.

## Validation

### Frontend

```bash
cd frontend
npm run lint
npm run build
```

### Backend

```bash
cd backend/backend
./mvnw test
```

Manual end-to-end checks should cover:

- all four formats;
- Historical and Legacy opponent pools;
- keeper enforcement on the final draft slot;
- captain and keeper result variation;
- unique team/country representation;
- standings-driven qualification;
- cricket-valid overs and internally consistent scorecards;
- session-only results and scorecard navigation;
- Hard Mode locking after the first draft pick;
- Hard Mode unbeaten-only leaderboard submission and shared-XI expansion;
- complete Rules dialog availability across routes.

## Performance

- Routes are lazy-loaded, so Draft, Results, Scorecard, and Leaderboard load only when visited.
- The client uses native Fetch instead of shipping Axios.
- The squad reveal uses a CSS transition instead of shipping Framer Motion.
- Measured production build: Draft fell from **46.20 KB to 7.06 KB gzip** (about **85% smaller**) and the API client from **17.06 KB to 0.48 KB gzip** (about **97% smaller**).
- Removing Axios and Framer Motion eliminated 41 packages and about 14 MB from the installed dependency tree.
- Unused starter SVG assets were removed.
- Browsers cache catalogue and player responses for one hour.
- Spring compresses JSON, JavaScript, CSS, and text responses above 1 KB.
- Parsed aggregates, team indexes, season catalogues, keeper/leadership data, and Legacy XIs are cached in memory.
- Fingerprinted disk caches avoid repeated cold parsing after restarts; cold parsing processes matches in parallel.
- Disposable squads and simulation results are excluded from persistent browser state to protect privacy and avoid Safari storage failures.

## Privacy and storage

Normal games are private and session-only. The application never automatically uploads or permanently saves every result. Browser persistence keeps enough catalogue/draft state to recover an interrupted draft but explicitly removes results, scorecards, loaded squad payloads, and runtime caches.

The only durable player-created record is an unbeaten Hard Mode run that the player explicitly approves. The backend replays it before writing. Production stores the chosen display name and cricket XI in Supabase PostgreSQL; local development uses `data/leaderboard.json` when `DATABASE_URL` is absent. This project has no accounts, passwords, payments, analytics trackers, or advertising cookies.

## Configuration reference

| Setting | Location | Default | Purpose |
| --- | --- | --- | --- |
| `PORT` / `server.port` | Spring Boot | `8080` | Backend HTTP port; Render injects `PORT` |
| `DATABASE_URL` | Spring Boot | empty | Production PostgreSQL URI; when present, approved leaderboard entries are stored durably in PostgreSQL |
| `LEADERBOARD_FILE` / `leaderboard.file` | Spring Boot | `../../data/leaderboard.json` | Local-development fallback used only when `DATABASE_URL` is empty |
| `APP_CORS_ALLOWED_ORIGINS` | Spring Boot | local Vite origins | Comma-separated allowed frontend origin patterns |
| `server.compression.*` | `application.properties` | enabled | Text/API response compression |
| `VITE_API_URL` | frontend build environment | `/api` | API base URL embedded at build time |
| `VITE_BASE_PATH` | frontend build environment | `/` | Static hosting base path; production GitHub Pages uses `/cricket-perfect-run/` |
| Vite `server.port` | `vite.config.js` | `5173` | Local frontend port |
| Vite `strictPort` | `vite.config.js` | `true` | Prevents accidental fallback to another port |

## Known boundaries

- This is a seeded game model informed by real historical performance, not a prediction service.
- Raw Cricsheet archives are required only to rebuild statistics locally. Production uses the committed compact aggregate bundle.
- Leaderboard display names are public labels, not authenticated identities.
- JSON leaderboard storage suits one backend instance; multi-instance hosting should use transactional shared storage.
- Historical source coverage determines available seasons, squads, and players.

## License and data

This repository does not redistribute raw Cricsheet match archives. It includes generated player-season aggregates required by the production game. Review the terms provided by [Cricsheet](https://cricsheet.org/) before reusing or redistributing derived match data.

Built by [Harshil Lotwala](https://github.com/Harshil-Lotwala).
