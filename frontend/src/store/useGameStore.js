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

  selectedTeam: [],
  selectedCaptainId: null,
  selectedKeeperId: null,
  simulationResult: null,

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
          const years = state.yearsByMode[state.gameMode] || [];
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
          const years = state.yearsByMode[state.gameMode] || [];
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
          if (state.selectedTeam.length >= SQUAD_SIZE) return state;
          if (state.selectedTeam.some((p) => p.name === player.name)) return state;

          const nextTeam = [...state.selectedTeam, player];

          if (nextTeam.length >= SQUAD_SIZE) {
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
          const years = state.yearsByMode[state.gameMode] || [];
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
      toggleHardMode: () => set((state) => ({ hardMode: !state.hardMode })),

      setSimulationResult: (result) => set({ simulationResult: result }),

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
    }
  )
);

export default useGameStore;
