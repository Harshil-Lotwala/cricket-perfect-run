# 🏏 Cricket Perfect Run

A full-stack cricket roguelite inspired by **16-0**. Draft an all-time XI from random
historical squad reveals, then run a season path against historical squads or boss-mode
**Legacy XIs** and try to go undefeated.

- **Frontend:** React 19 + Vite + Tailwind v4 + Zustand (persisted) + Framer Motion + lucide-react
- **Backend:** Spring Boot 3.5 (Java 21), reads raw [Cricsheet](https://cricsheet.org) JSON
- **Data philosophy:** *nothing* about players (nationality, keeper status, roles, ratings,
  team lists) is hardcoded. Everything is derived from the Cricsheet datasets.

---

## Table of contents
- [Game design](#game-design)
- [Architecture](#architecture)
- [Data setup (required)](#data-setup-required)
- [Running locally](#running-locally)
- [API reference](#api-reference)
- [How metadata is derived](#how-metadata-is-derived)
- [Data assumptions](#data-assumptions)
- [Testing checklist](#testing-checklist)
- [Roadmap](#roadmap)

---

## Game design

### IPL Perfect Run (fully playable)
1. **Reveal** — a slot-machine spin cycles through real seasons/teams and stops on a random
   squad. You may **Reroll Team** or **Reroll Year** (2 each) before drafting.
2. **Load Squad** — fetches that exact team-season's real players.
3. **Draft** — each card has a single **Draft Player** button. Drafting consumes the squad and
   immediately rolls the next reveal. A player can be drafted only once per run (by identity),
   even if they appear in multiple seasons/teams.
4. **Constraints** — max **4 overseas** players (overseas cards lock with *"4 overseas already
   selected"* once the cap is hit; Indians stay draftable), at least **1 keeper**.
5. **Finalize** — pick a **captain** (any) and a **wicketkeeper** (keeper-eligible only).
6. **Simulate** — choose an opponent pool and (optionally) Hard Mode, then run the season.

### Opponent pools
- **Historical Squads** — real franchises from specific seasons (e.g. CSK 2011, MI 2020).
  You do **not** face every franchise; the schedule is a seeded, varied subset, so two runs of
  the same XI produce different opponents.
- **Legacy XI** — each franchise represented by the **prime (peak-rated) season of every player
  who ever played for it**, assembled automatically. This is the hardest, boss-mode pool.

### Hard Mode
The only difficulty toggle. It **hides OVR** so you draft purely on real stats, and slightly
lowers win probability.

### Perfect-run targets
| Mode | Structure | Perfect target |
|---|---|---|
| IPL | 14 league + 2 knockouts | **16-0** |
| ODI World Cup | 9 league + 2 knockouts | **11-0** |
| T20 World Cup | groups/Super 8 + 2 knockouts | **8-0** |
| WTC | 13 Tests + final | **14-0** |

> The ODI/T20/WTC modes share the same draft, cache, scorecard and simulation engine and are
> wired end-to-end, but are still being tuned (marked *preview* in the UI). IPL is the polished mode.

### Player card stats
- **BAT / WK:** Runs, Batting Average, Strike Rate
- **BOWL:** Wickets, Bowling Average, Economy
- **AR:** Wickets, Batting Average, Economy
- Missing values render as `N/A` (never a fake placeholder).

---

## Architecture

```
cricket-perfect-run/
├── backend/backend/            # Spring Boot app
│   └── src/main/java/com/cricketperfectrun/backend/
│       ├── controller/         # Player, Meta, Simulation, Admin, Health
│       ├── service/            # data-derived metadata + simulation engine
│       ├── model/              # records (Player, PlayerSeasonStats, SimulationModels…)
│       └── dto/                # request payloads
│   └── src/main/resources/     # keeper_players.csv, player_metadata.csv (optional overrides)
├── data/cricsheet/             # raw Cricsheet JSON (NOT committed — see Data setup)
└── frontend/                   # React + Vite app
    └── src/
        ├── pages/              # Home, Draft, Result, Scorecard
        ├── store/              # useGameStore (Zustand, persisted)
        ├── data/modes.js       # static mode rules (no team/season hardcoding)
        └── services/api.js     # axios instance
```

### Key backend services
| Service | Responsibility |
|---|---|
| `CricsheetParserService` | Parses Cricsheet JSON into per-season player stats (cached) |
| `NationalityService` | Maps players → country from international rosters |
| `PlayerMetadataService` | Overseas / keeper eligibility / role resolution (+ CSV overrides) |
| `RatingService` | Averages, strike/economy rates, role detection, OVR |
| `LeadershipRatingService` | Captain & keeper impact from real career data |
| `PlayerFactory` | Builds enriched `Player` objects consistently |
| `LegacyXiService` | Builds each franchise's prime XI automatically |
| `OpponentService` | Builds opponent pools + seeded, varied schedule |
| `StrengthCalculator` | Team batting/bowling/AR/keeping strength + rating breakdown |
| `SimulationService` | Seeded season sim: matches, scorecards, standings, playoffs, awards |

---

## Data setup (required)

The Cricsheet dataset (~1.7 GB) is **not** committed. Download the JSON match archives from
[cricsheet.org/downloads](https://cricsheet.org/downloads/) and extract them so the structure is:

```
data/cricsheet/
├── ipl_json/      # IPL matches      (powers IPL mode)
├── odis_json/     # ODI matches      (powers ODI mode + nationality)
├── t20s_json/     # T20I matches     (powers T20 mode + nationality)
└── tests_json/    # Test matches     (powers WTC mode + nationality)
```

The backend resolves this folder relative to its working directory (it searches `../../data`,
`../data`, `data`, and `cricsheet`).

Optional override files live in `backend/backend/src/main/resources/`:
- `keeper_players.csv` — one keeper name per line (supplements stumping-derived keepers).
- `player_metadata.csv` — `name,country,roleOverride,captaincyImpact,keepingImpact` (all optional).

---

## Running locally

**Prerequisites:** Java 21, Node 18+, and the Cricsheet data in place.

### Backend (port 8080)
```bash
cd backend/backend
./mvnw spring-boot:run
```

### Frontend (port 5173)
```bash
cd frontend
npm install
npm run dev
```

Then open http://localhost:5173. The frontend talks to the backend at `http://localhost:8080/api`.

> First load of the player/simulation endpoints scans the dataset (and the international data for
> nationality), which takes a few seconds; results are cached afterward.

---

## API reference

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/health` | Health check |
| GET | `/api/meta/{mode}/years` | Available seasons for a mode |
| GET | `/api/meta/{mode}/teams-by-year` | Team catalogue per season |
| GET | `/api/players/{mode}/{year}/{team}` | Enriched players for a team-season |
| POST | `/api/simulate/{mode}` | Run a season; returns the full result + scorecards |
| GET | `/api/admin/cricsheet/stats/{mode}` | Raw aggregated stats (debug) |

`mode` ∈ `ipl`, `odi-world-cup`, `t20-world-cup`, `wtc`.

**Simulate request body:**
```json
{
  "team": [ /* full Player objects from /api/players */ ],
  "captainId": 123,
  "keeperId": 456,
  "opponentType": "historical",   // or "legacy"
  "hardMode": false,
  "seed": 42                        // run seed → reproducible results
}
```

---

## How metadata is derived

- **Nationality / overseas** — international Cricsheet files key rosters by national team. We scan
  every ODI/T20I/Test match and map each player to the country they most represented. Overseas =
  country ≠ India. Players never seen internationally default to Indian.
- **Keeper eligibility** — any player who recorded a **stumping** in the dataset, plus the
  optional `keeper_players.csv`. Keeper-eligible batters are tagged `WK` and can be selected behind
  the stumps even if their main role differs.
- **Role** — derived from balls faced vs balls bowled and output share (BAT / BOWL / AR / WK).
- **OVR / ratings** — computed from real batting average, strike rate, wickets, economy and bowling
  average, weighted by role (40–99). Never random.
- **Captain impact** — derived from real career win-rate, longevity and player-of-match awards;
  inexperienced captains are penalised, successful veterans rewarded.
- **Simulation** — match outcomes use team batting/bowling/all-round depth/keeping strength plus
  captain & keeper impact vs opponent strength, with a **seeded** RNG so navigating to results /
  scorecards or **refreshing never re-runs or changes** the season. Results only reset on
  **New Game** or **mode change**.

---

## Data assumptions
- Players with no international appearance are assumed **Indian** (uncapped IPL players are
  overwhelmingly domestic Indians). Override via `player_metadata.csv` if needed.
- Cricsheet does not record the on-field captain per match, so captaincy quality is **approximated**
  from career success/experience signals rather than actual captaincy records.
- Scorecards are generated by a strength-weighted statistical model (not ball-by-ball), so totals
  and figures are plausible and result-consistent rather than literal simulations.

---

## Testing checklist
- **Draft flow:** reveal spins and stops on a real squad → Load Squad → one Draft button per card →
  drafting rolls the next reveal → rerolls only before drafting from the current reveal.
- **Overseas count:** plane icon on overseas cards; sidebar count accurate; 4th overseas locks
  further overseas cards while Indians remain draftable.
- **Keeper:** keeper-eligible players show a keeper badge and can be chosen as wicketkeeper.
- **Hard Mode:** OVR hidden everywhere.
- **Simulation:** records vary by run; Legacy XI is noticeably harder; titles uncommon, undefeated
  runs rare.
- **Persistence:** refresh on Result / Scorecard pages keeps the same result (no re-sim); New Game
  resets everything.
- **Scorecards:** every match links to a full batting + bowling card with POM and margin.

---

## Roadmap
- Tune and fully ship ODI World Cup (11-0), T20 World Cup (8-0) and WTC (14-0) modes.
- National Legends / Franchise Legends opponent variants for international modes.
- Difficulty tiers (Easy/Normal/Legend) beyond the current Hard Mode toggle.
