import { create } from "zustand";
import { persist } from "zustand/middleware";
import { GAME_MODES } from "../data/modes";

const SQUAD_SIZE = 11;

const randomItem = (items) => items[Math.floor(Math.random() * items.length)];

const randomItemExcept = (items, current) => {
  const filtered = items.filter((item) => item !== current);
  if (filtered.length === 0) return current;
  return randomItem(filtered);
};

const makeSeed = () => Math.floor(Math.random() * 1_000_000_000) + 1;

const cacheKeyFor = (mode, year, team) => `${mode}-${year}-${team}`;

// Fresh per-run state. teamsByYear/years are kept (they are catalogue metadata,
// not run state) so we don't refetch unnecessarily.
const freshRun = (mode) => ({
  gameMode: mode,
  runSeed: makeSeed(),

  opponentType: "historical",
  hardMode: false,
  seasonCap: null, // null = random opponent editions; a year = exact historical edition

  selectedTeam: [],
  selectedCaptainId: null,
  selectedKeeperId: null,
  simulationResult: null,
  swapsLeft: 2,

  teamRerollsLeft: GAME_MODES[mode]?.rerollTeam ?? 2,
  yearRerollsLeft: GAME_MODES[mode]?.rerollYear ?? 2,

  currentYear: null,
  currentTeam: null,
  currentPlayers: [],
  isSquadLoaded: false,
  draftError: "",
});

const useGameStore = create(
  persist(
    (set, get) => ({
      ...freshRun("ipl"),

      // Catalogue metadata fetched from backend (per mode).
      teamsByYearByMode: {},
      yearsByMode: {},

      setMeta: (mode, teamsByYear, years) =>
        set((state) => ({
          teamsByYearByMode: { ...state.teamsByYearByMode, [mode]: teamsByYear },
          yearsByMode: { ...state.yearsByMode, [mode]: years },
          seasonCap:
            state.gameMode === mode && state.seasonCap !== null && !years.includes(state.seasonCap)
              ? null
              : state.seasonCap,
        })),

      hasMeta: (mode) => Boolean(get().yearsByMode[mode]?.length),

      ensureGameMode: (mode) =>
        set((state) => {
          if (state.gameMode === mode) return state;
          // Switching modes resets the run (clears caches/results).
          return freshRun(mode);
        }),

      // Pick a random (year, team) from the fetched catalogue for the current mode.
      rollReveal: () =>
        set((state) => {
          const all = state.yearsByMode[state.gameMode] || [];
          const years = state.seasonCap ? all.filter((y) => y <= state.seasonCap) : all;
          if (years.length === 0) return state;
          const year = randomItem(years);
          const teams = state.teamsByYearByMode[state.gameMode]?.[year] || [];
          if (teams.length === 0) return state;
          return {
            currentYear: year,
            currentTeam: randomItem(teams),
            currentPlayers: [],
            isSquadLoaded: false,
            draftError: "",
          };
        }),

      rerollTeam: () =>
        set((state) => {
          if (state.teamRerollsLeft <= 0) return state;
          const teams = state.teamsByYearByMode[state.gameMode]?.[state.currentYear] || [];
          if (teams.length < 2) return state;
          return {
            currentTeam: randomItemExcept(teams, state.currentTeam),
            teamRerollsLeft: state.teamRerollsLeft - 1,
            currentPlayers: [],
            isSquadLoaded: false,
            draftError: "",
          };
        }),

      rerollYear: () =>
        set((state) => {
          if (state.yearRerollsLeft <= 0) return state;
          const all = state.yearsByMode[state.gameMode] || [];
          const years = state.seasonCap ? all.filter((y) => y <= state.seasonCap) : all;
          const yearsForTeam = years.filter((y) =>
            (state.teamsByYearByMode[state.gameMode]?.[y] || []).includes(state.currentTeam)
          );
          const pool = yearsForTeam.length > 1 ? yearsForTeam : years;
          if (pool.length < 2) return state;
          const nextYear = randomItemExcept(pool, state.currentYear);
          const teams = state.teamsByYearByMode[state.gameMode]?.[nextYear] || [];
          const nextTeam = teams.includes(state.currentTeam)
            ? state.currentTeam
            : randomItem(teams);
          return {
            currentYear: nextYear,
            currentTeam: nextTeam,
            yearRerollsLeft: state.yearRerollsLeft - 1,
            currentPlayers: [],
            isSquadLoaded: false,
            draftError: "",
          };
        }),

      loadSquadPlayers: (players) =>
        set((state) => ({
          currentPlayers: players,
          isSquadLoaded: true,
          draftError: "",
          squadCache: {
            ...state.squadCache,
            [cacheKeyFor(state.gameMode, state.currentYear, state.currentTeam)]: players,
          },
        })),

      setDraftError: (message) => set({ draftError: message }),

      // Draft a player: unique by name across the run, then consume the squad and roll the next reveal.
      draftPlayer: (player) =>
        set((state) => {
          if (!player) return state;
          const config = GAME_MODES[state.gameMode];
          const squadSize = config?.squadSize ?? SQUAD_SIZE;
          const minKeepers = config?.minKeepers ?? 1;
          if (state.selectedTeam.length >= squadSize) return state;
          if (state.selectedTeam.some((p) => p.name === player.name)) return state;

          const keeperCount = state.selectedTeam.filter((p) => p.keeperEligible).length;
          const finalSlotNeedsKeeper =
            state.selectedTeam.length === squadSize - 1 && keeperCount < minKeepers;
          if (finalSlotNeedsKeeper && !player.keeperEligible) {
            return { draftError: "Final pick must be a wicketkeeper." };
          }

          const nextTeam = [...state.selectedTeam, player];

          if (nextTeam.length >= squadSize) {
            return {
              selectedTeam: nextTeam,
              currentPlayers: [],
              isSquadLoaded: false,
              currentYear: null,
              currentTeam: null,
              draftError: "",
            };
          }

          // Roll the next reveal immediately.
          const all = state.yearsByMode[state.gameMode] || [];
          const years = state.seasonCap ? all.filter((y) => y <= state.seasonCap) : all;
          const year = years.length ? randomItem(years) : null;
          const teams = year ? state.teamsByYearByMode[state.gameMode]?.[year] || [] : [];
          return {
            selectedTeam: nextTeam,
            currentYear: year,
            currentTeam: teams.length ? randomItem(teams) : null,
            currentPlayers: [],
            isSquadLoaded: false,
            draftError: "",
          };
        }),

      // Recovery for persisted/older runs that reached XI without a keeper. Remove only the most
      // recent pick, preserve the other ten, and reveal another squad within the selected era.
      undoLastPick: () =>
        set((state) => {
          if (state.selectedTeam.length === 0) return state;
          const removed = state.selectedTeam.at(-1);
          const selectedTeam = state.selectedTeam.slice(0, -1);
          const all = state.yearsByMode[state.gameMode] || [];
          const years = state.seasonCap ? all.filter((y) => y <= state.seasonCap) : all;
          const year = years.length ? randomItem(years) : null;
          const teams = year ? state.teamsByYearByMode[state.gameMode]?.[year] || [] : [];
          return {
            selectedTeam,
            selectedCaptainId: state.selectedCaptainId === removed.id ? null : state.selectedCaptainId,
            selectedKeeperId: state.selectedKeeperId === removed.id ? null : state.selectedKeeperId,
            currentYear: year,
            currentTeam: teams.length ? randomItem(teams) : null,
            currentPlayers: [],
            isSquadLoaded: false,
            draftError: "Final slot reserved for a wicketkeeper. Load squads until you find a player marked Keeper.",
          };
        }),

      setCaptain: (playerId) =>
        set((state) => {
          if (!state.selectedTeam.some((p) => p.id === playerId)) return state;
          return { selectedCaptainId: playerId };
        }),

      setKeeper: (playerId) =>
        set((state) => {
          const player = state.selectedTeam.find((p) => p.id === playerId);
          if (!player || !player.keeperEligible) return state;
          return { selectedKeeperId: playerId };
        }),

      setOpponentType: (opponentType) => set({ opponentType }),
      toggleHardMode: () =>
        set((state) => {
          if (state.selectedTeam.length > 0) {
            return { draftError: "Hard Mode is locked after the first draft pick." };
          }
          return { hardMode: !state.hardMode, draftError: "" };
        }),

      // Null keeps opponent editions random. A selected year caps draft reveals and fixes every
      // Historical Squad opponent to that exact competition edition.
      setSeasonCap: (year) =>
        set((state) => {
          const cap = Number(year) || null;
          const next = { seasonCap: cap };
          if (!state.isSquadLoaded && cap && state.currentYear && state.currentYear > cap) {
            const all = state.yearsByMode[state.gameMode] || [];
            const years = all.filter((y) => y <= cap);
            if (years.length) {
              const y = randomItem(years);
              const teams = state.teamsByYearByMode[state.gameMode]?.[y] || [];
              next.currentYear = y;
              next.currentTeam = teams.length ? randomItem(teams) : null;
              next.currentPlayers = [];
              next.isSquadLoaded = false;
            }
          }
          return next;
        }),

      setSimulationResult: (result) => set({ simulationResult: result }),

      startSwap: (playerId) =>
        set((state) => {
          const remainingSwaps = Number.isInteger(state.swapsLeft) ? state.swapsLeft : 2;
          if (!state.simulationResult || remainingSwaps <= 0) return state;
          const removed = state.selectedTeam.find((player) => player.id === playerId);
          if (!removed) return state;
          const selectedTeam = state.selectedTeam.filter((player) => player.id !== playerId);
          const all = state.yearsByMode[state.gameMode] || [];
          const years = state.seasonCap ? all.filter((year) => year <= state.seasonCap) : all;
          const year = years.length ? randomItem(years) : null;
          const teams = year ? state.teamsByYearByMode[state.gameMode]?.[year] || [] : [];
          return {
            selectedTeam,
            selectedCaptainId: state.selectedCaptainId === playerId ? null : state.selectedCaptainId,
            selectedKeeperId: state.selectedKeeperId === playerId ? null : state.selectedKeeperId,
            simulationResult: null,
            swapsLeft: remainingSwaps - 1,
            currentYear: year,
            currentTeam: teams.length ? randomItem(teams) : null,
            currentPlayers: [],
            isSquadLoaded: false,
            draftError: `${removed.name} removed. Draft one replacement to complete your XI.`,
          };
        }),

      getMatchById: (matchId) =>
        get().simulationResult?.matches?.find((m) => m.id === matchId) || null,

      newGame: () =>
        set((state) => ({
          ...freshRun(state.gameMode),
          // keep catalogue caches
          teamsByYearByMode: state.teamsByYearByMode,
          yearsByMode: state.yearsByMode,
          squadCache: {},
        })),

      squadCache: {},
    }),
    {
      name: "cricket-perfect-run",
      version: 2,
      migrate: (persistedState) => ({
        ...persistedState,
        // Results are deliberately session-only. Clear results written by older app versions.
        simulationResult: null,
        swapsLeft: Number.isInteger(persistedState?.swapsLeft) ? persistedState.swapsLeft : 2,
      }),
      // Loaded squad payloads are disposable API cache. Persisting every reveal alongside full
      // scorecards can exceed Safari's localStorage quota. Results are also private/session-only
      // unless the player explicitly publishes an eligible unbeaten Hard Mode XI.
      partialize: (state) => ({
        ...state,
        simulationResult: null,
        squadCache: {},
        currentPlayers: [],
        isSquadLoaded: false,
      }),
    }
  )
);

export default useGameStore;
