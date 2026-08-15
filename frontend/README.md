# Perfect Run frontend

This directory contains the React/Vite client for Perfect Run. Complete setup, architecture, gameplay, API, privacy, performance, testing, and deployment documentation is in the repository-level [`README.md`](../README.md).

## Commands

```bash
npm install       # install dependencies for local development
npm run dev       # fixed local server at http://localhost:5173
npm run lint      # run ESLint
npm run build     # write optimized production assets to dist/
npm run preview   # preview the production build locally
```

The development server proxies `/api` to Spring Boot on `http://localhost:8080`. Both processes must run for catalogue loading, drafting, simulation, scorecards, and leaderboard actions.

Set `VITE_API_URL` while building when the production API is hosted separately:

```bash
VITE_API_URL=https://api.example.com/api npm run build
```
