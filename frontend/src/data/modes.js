// Mode metadata. Season/team catalogue is NOT hardcoded here; it is fetched from
// the backend /api/meta endpoints. This only holds static rules per mode.
export const GAME_MODES = {
  ipl: {
    id: "ipl",
    title: "IPL Perfect Run",
    tag: "League",
    description:
      "Draft an all-time IPL XI from random historical squad reveals and chase the 16-0 perfect season.",
    squadSize: 11,
    overseasLimit: 4,
    minKeepers: 1,
    rerollTeam: 2,
    rerollYear: 2,
    perfectTarget: 16,
    supportsOpponentTypes: true,
    available: true,
  },

  "odi-world-cup": {
    id: "odi-world-cup",
    title: "ODI World Cup Perfect Run",
    tag: "ODI",
    description: "10-team World Cup path. Perfect run target: 11-0.",
    squadSize: 11,
    overseasLimit: null,
    minKeepers: 1,
    rerollTeam: 2,
    rerollYear: 2,
    perfectTarget: 11,
    supportsOpponentTypes: true,
    available: true,
  },

  "t20-world-cup": {
    id: "t20-world-cup",
    title: "T20 World Cup Perfect Run",
    tag: "T20",
    description: "Groups, Super 8, semis, final. Perfect run target: 8-0.",
    squadSize: 11,
    overseasLimit: null,
    minKeepers: 1,
    rerollTeam: 2,
    rerollYear: 2,
    perfectTarget: 8,
    supportsOpponentTypes: true,
    available: true,
  },

  wtc: {
    id: "wtc",
    title: "World Test Championship",
    tag: "Test",
    description: "13 Tests then the final. Perfect run target: 14-0.",
    squadSize: 11,
    overseasLimit: null,
    minKeepers: 1,
    rerollTeam: 2,
    rerollYear: 2,
    perfectTarget: 14,
    supportsOpponentTypes: true,
    available: true,
  },
};

export const OPPONENT_TYPES = {
  historical: {
    id: "historical",
    label: "Historical Squads",
    description: "Real teams from specific seasons (e.g. CSK 2011, MI 2020).",
  },
  legacy: {
    id: "legacy",
    label: "Legacy XI",
    description:
      "Each franchise's all-time prime XI. The hardest, boss-mode opponents.",
  },
};
