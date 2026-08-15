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
- One historical version per opponent identity: a tournament can contain CSK 2018 or India 2019, but never another CSK or India season in the same run. The eligible season for each identity is selected by the run seed, so a new run can face a different version of that team; World Cup modes still use the exact selected edition.
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
2. Set the last eligible season or tournament edition.
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

Each entry includes the full XI, source seasons, captain, wicketkeeper, opponent type, record, and shareable anchor link. Leaderboard data is stored in `data/leaderboard.json`, which is intentionally ignored by Git.

The global **Rules** dialog explains drafting, the keeper and captain requirements, opponent uniqueness, every format's tournament path, Hard Mode locking, and leaderboard eligibility from any page.

## Technology

### Frontend

- React 19
- Vite 8
- Tailwind CSS 4
- Zustand persistence
- Framer Motion
- React Router
- Lucide icons

### Backend

- Java 21
- Spring Boot 3.5
- Maven Wrapper
- Jackson
- In-memory parsed-data caches

No database is required.

## Repository structure

```text
cricket-perfect-run/
├── backend/backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/cricketperfectrun/backend/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── model/
│       │   └── service/
│       └── resources/
├── data/cricsheet/                 # local data; ignored by Git
├── frontend/
│   ├── package.json
│   └── src/
│       ├── components/
│       ├── data/
│       ├── pages/
│       ├── services/
│       └── store/
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

Use the new `Local` URL printed in the terminal. Do not keep opening the old port.

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

## Simulation rules

- Player ratings are computed from actual format-specific batting and bowling output with sample-size adjustment.
- Captaincy and keeping contribute explicit team-strength effects.
- Captain and keeper IDs are folded into the seeded random stream, making those selections consequential and reproducible.
- Historical opponents are strength-ranked within the selected era.
- Only one season-version of a franchise or country can enter a competition.
- Franchise renames such as Delhi Daredevils/Delhi Capitals, Kings XI Punjab/Punjab Kings, and Royal Challengers Bangalore/Bengaluru share one identity.
- Knockout opponents come from qualified league positions, never from a separate random pool.
- Test matches use four innings, draws, declarations/unfinished innings, and ball-correct over notation.

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

- Parsed Cricsheet results and derived metadata are cached in memory.
- Backend responses use HTTP compression.
- The frontend lazy-loads route bundles.
- Disposable squad payloads and all simulation results are excluded from persisted browser state to protect privacy and avoid Safari storage failures.

## License and data

This repository does not redistribute the Cricsheet archives. Review the terms provided by [Cricsheet](https://cricsheet.org/) before using or distributing match data.

Built by [Harshil Lotwala](https://github.com/Harshil-Lotwala).
