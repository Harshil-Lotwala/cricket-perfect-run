import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Plane, Shield, Loader2, Trophy, Info, RotateCcw, X, CalendarRange, Sparkles, ChevronRight, Lock } from "lucide-react";
import api from "../services/api";
import useGameStore from "../store/useGameStore";
import FieldFormation from "../components/FieldFormation";
import { GAME_MODES, OPPONENT_TYPES } from "../data/modes";
import { cricketCountryCode } from "../utils/countryCodes";

const randomItem = (items) => items[Math.floor(Math.random() * items.length)];

function Draft() {
  const { mode } = useParams();
  const navigate = useNavigate();
  const config = GAME_MODES[mode];

  const store = useGameStore();
  const {
    gameMode, selectedTeam, selectedCaptainId, selectedKeeperId, opponentType, hardMode,
    runSeed, seasonCap, currentYear, currentTeam, currentPlayers, isSquadLoaded, draftError,
    teamRerollsLeft, yearRerollsLeft, teamsByYearByMode, yearsByMode, squadCache,
  } = store;

  const [isLoadingPlayers, setIsLoadingPlayers] = useState(false);
  const [isLoadingMeta, setIsLoadingMeta] = useState(false);
  const [isSimulating, setIsSimulating] = useState(false);
  const [showHow, setShowHow] = useState(false);

  const [spinning, setSpinning] = useState(false);
  const [display, setDisplay] = useState({ year: currentYear, team: currentTeam });
  const spunKeyRef = useRef(null);

  useEffect(() => {
    if (mode) store.ensureGameMode(mode);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  useEffect(() => {
    if (!config) return;
    let cancelled = false;
    (async () => {
      try {
        setIsLoadingMeta(true);
        store.setDraftError("");
        const teamsRes = await api.get(`/meta/${mode}/teams-by-year`);
        if (cancelled) return;
        const years = Object.keys(teamsRes.data).map(Number).sort((a, b) => a - b);
        store.setMeta(mode, teamsRes.data, years);
      } catch (e) {
        console.error("Failed to load catalogue", e);
        store.setDraftError("We couldn't load the season list right now. Please try again in a moment.");
      } finally {
        if (!cancelled) setIsLoadingMeta(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  useEffect(() => {
    if (!config) return;
    const years = yearsByMode[mode];
    if (years && years.length && !currentTeam && selectedTeam.length < config.squadSize) {
      store.rollReveal();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [yearsByMode[mode], currentTeam]);

  useEffect(() => {
    if (!currentTeam || isSquadLoaded) return;
    const key = `${currentYear}-${currentTeam}`;
    if (spunKeyRef.current === key) return;
    spunKeyRef.current = key;
    const allYears = yearsByMode[mode] || [];
    const years = seasonCap ? allYears.filter((year) => year <= seasonCap) : allYears;
    if (!years.length) return;
    let ticks = 0;
    let interval;
    const start = setTimeout(() => {
      setSpinning(true);
      interval = setInterval(() => {
        const y = randomItem(years);
        const ts = teamsByYearByMode[mode]?.[y] || [];
        setDisplay({ year: y, team: ts.length ? randomItem(ts) : currentTeam });
        ticks += 1;
        if (ticks > 12) {
          clearInterval(interval);
          setDisplay({ year: currentYear, team: currentTeam });
          setSpinning(false);
        }
      }, 80);
    }, 0);
    return () => {
      clearTimeout(start);
      clearInterval(interval);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentYear, currentTeam, isSquadLoaded]);

  if (!config) {
    return <div className="min-h-screen bg-slate-950 text-white p-10">Invalid draft mode.</div>;
  }

  const isDraftComplete = selectedTeam.length === config.squadSize;
  const overseasCount = selectedTeam.filter((p) => p.overseas).length;
  const keeperCount = selectedTeam.filter((p) => p.keeperEligible).length;
  const roleCounts = selectedTeam.reduce((counts, player) => {
    const role = String(player.role || "").toUpperCase();
    if (role === "BOWL") counts.bowlers += 1;
    else if (role === "AR") counts.allRounders += 1;
    else if (role === "WK") counts.keepers += 1;
    else counts.batters += 1;
    return counts;
  }, { batters: 0, bowlers: 0, allRounders: 0, keepers: 0 });
  const needsKeeper = keeperCount < config.minKeepers;
  const finalSlotNeedsKeeper = selectedTeam.length === config.squadSize - 1 && needsKeeper;
  const loadedSquadHasAvailableKeeper = currentPlayers.some(
    (player) => player.keeperEligible && !selectedTeam.some((picked) => picked.name === player.name)
  );
  const overseasFull = config.overseasLimit !== null && overseasCount >= config.overseasLimit;
  const hardModeLocked = selectedTeam.length > 0;
  const avgRating = selectedTeam.length
    ? Math.round(selectedTeam.reduce((s, p) => s + (p.rating || 0), 0) / selectedTeam.length)
    : null;

  const canSimulate =
    isDraftComplete && selectedCaptainId !== null && selectedKeeperId !== null &&
    keeperCount >= config.minKeepers &&
    (config.overseasLimit === null || overseasCount <= config.overseasLimit);

  const loadSquad = async () => {
    if (isLoadingPlayers || spinning || !currentTeam) return;
    const key = `${gameMode}-${currentYear}-${currentTeam}`;
    const cached = squadCache?.[key];
    if (cached) { store.loadSquadPlayers(cached); return; }
    try {
      setIsLoadingPlayers(true);
      store.setDraftError("");
      const res = await api.get(`/players/${gameMode}/${currentYear}/${encodeURIComponent(currentTeam)}`);
      if (!res.data || res.data.length === 0) {
        store.setDraftError("This squad has no usable players. Try rerolling.");
        return;
      }
      store.loadSquadPlayers(res.data);
    } catch (e) {
      console.error("Failed to load squad", e);
      store.setDraftError("This squad couldn't be loaded. Try again or reveal another squad.");
    } finally {
      setIsLoadingPlayers(false);
    }
  };

  const handleSimulate = async () => {
    if (!canSimulate || isSimulating) return;
    try {
      setIsSimulating(true);
      const response = await fetch(`/api/play/${gameMode}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          team: selectedTeam, captainId: selectedCaptainId, keeperId: selectedKeeperId,
          opponentType, hardMode, seed: runSeed, maxSeason: seasonCap,
        }),
      });
      if (!response.ok) {
        const detail = await response.text();
        const requestError = new Error(detail || `HTTP ${response.status}`);
        requestError.status = response.status;
        throw requestError;
      }
      const result = await response.json();
      // Results intentionally stay in memory for this session and are never persisted automatically.
      store.setSimulationResult(result);
      navigate("/result");
    } catch (e) {
      console.error("Simulation failed", e);
      const reason = e.status === 400
        ? "Review your XI, captain, wicketkeeper, and squad limits, then try again."
        : e.status === 403
          ? "This run could not be verified. Start a new game and draft the XI again."
          : "The match engine is temporarily unavailable. Your XI is still here—please try again.";
      store.setDraftError(`Simulation unavailable. ${reason}`);
    } finally {
      setIsSimulating(false);
    }
  };

  return (
    <main className="min-h-screen app-shell text-white">
      <div className="max-w-[1400px] mx-auto px-6 py-6">
        {/* Header */}
        <header className="flex items-center justify-between gap-4 flex-wrap mb-4">
          <div>
            <p className="eyebrow mb-1">BUILD THE IMPOSSIBLE XI</p>
            <h1 className="text-3xl md:text-4xl font-black tracking-tight">
              The {config.perfectTarget}–0 Run <span className="text-slate-500 text-base font-semibold ml-2">{config.title}</span>
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => setShowHow(true)} className="flex items-center gap-1 bg-slate-800 hover:bg-slate-700 px-4 py-2 rounded-none text-sm font-semibold">
              <Info size={15} /> How
            </button>
            <span className="flex items-center gap-1 bg-slate-800 px-4 py-2 rounded-none text-sm font-semibold">
              <Trophy size={15} className="text-yellow-400" /> {selectedTeam.length === config.squadSize ? "Ready" : `${selectedTeam.length}/${config.squadSize}`} · Target {config.perfectTarget}-0
            </span>
            <button onClick={() => store.newGame()} className="flex items-center gap-1 bg-slate-800 hover:bg-slate-700 px-4 py-2 rounded-none text-sm font-semibold">
              <RotateCcw size={15} /> New Game
            </button>
          </div>
        </header>

        {/* Stat strip */}
        <div className="glass-panel flex items-center gap-x-6 gap-y-2 flex-wrap rounded-none px-5 py-3 mb-6 text-sm">
          <Stat label="XI" value={`${selectedTeam.length}/${config.squadSize}`} />
          {config.overseasLimit !== null && (
            <Stat label="Overseas" value={`${overseasCount}/${config.overseasLimit}`} danger={overseasFull} />
          )}
          <Stat label="Rating" value={hardMode ? "—" : avgRating ?? "—"} />
          <Stat label="Re-rolls" value={`${teamRerollsLeft + yearRerollsLeft}`} />
          <Stat label="Keepers" value={`${keeperCount}/${config.minKeepers}`} />
          <div className="ml-auto flex items-center gap-2">
            <button
              onClick={() => store.toggleHardMode()}
              disabled={hardModeLocked}
              title={hardModeLocked ? "Difficulty locked after the first draft pick" : "Toggle Hard Mode before drafting"}
              className={`flex items-center gap-2 px-4 py-2 rounded-none font-bold text-sm disabled:cursor-not-allowed ${
                hardMode ? "bg-yellow-400 text-black" : "bg-slate-800 hover:bg-slate-700"
              } ${hardModeLocked ? "opacity-70" : ""}`}
            >
              {hardModeLocked ? <Lock size={14} /> : "●"} Hard Mode {hardModeLocked ? "· Locked" : ""}
            </button>
          </div>
        </div>

        <div className="grid lg:grid-cols-[1fr_1fr] gap-6">
          {/* LEFT: Spin & Draft */}
          <section className="glass-panel rounded-none p-6">
            <p className="text-slate-400 text-xs font-bold uppercase tracking-widest mb-3">Spin &amp; Draft</p>

            {/* Opponent selector */}
            <div className="grid grid-cols-2 gap-2 mb-4" aria-label="Opponent pool">
              {Object.values(OPPONENT_TYPES).map((opt) => (
                <button
                  key={opt.id}
                  onClick={() => store.setOpponentType(opt.id)}
                  className={`text-left px-4 py-3 rounded-none border text-sm transition ${
                    opponentType === opt.id ? "border-lime-400 bg-lime-400/10 text-lime-200" : "border-slate-700 hover:border-slate-500"
                  }`}
                >
                  <span className="font-bold flex items-center justify-between">{opt.label}<ChevronRight size={15} /></span>
                </button>
              ))}
            </div>

            <label className="season-control mb-5">
              <span className="flex items-center gap-2 text-xs font-bold uppercase tracking-widest text-slate-400">
                <CalendarRange size={14} /> Play through season
              </span>
              <select
                value={seasonCap ?? ""}
                onChange={(event) => store.setSeasonCap(event.target.value)}
                className="season-select"
              >
                {(yearsByMode[mode] || []).map((year) => (
                  <option key={year} value={year}>{year}</option>
                ))}
              </select>
              <span className="text-xs text-slate-500 col-span-2">
                {mode.endsWith("world-cup")
                  ? "Drafts use seasons up to this edition; opponents come from this exact World Cup."
                  : "Drafts and historical opponents use seasons up to this year."}
              </span>
            </label>

            {!isDraftComplete ? (
              <>
                {finalSlotNeedsKeeper && (
                  <div className="border border-yellow-400/60 bg-yellow-400/10 text-yellow-200 p-3 mb-4 text-sm font-bold">
                    Final slot reserved: you must draft a wicketkeeper. Look for a player marked with the shield icon.
                  </div>
                )}
                {/* Reveal card */}
                <div
                  key={`${display.year}-${display.team}-${spinning}`}
                  className={`reveal-card rounded-none px-4 py-5 sm:px-5 sm:py-6 mb-3 transition-[opacity,transform] duration-200 ${spinning ? "opacity-60 -translate-y-1" : "opacity-100 translate-y-0"}`}
                >
                  <div className="flex items-center justify-between gap-4">
                    <div className="min-w-0 text-left">
                      <p className="text-[10px] font-black uppercase tracking-[0.2em] text-lime-300 mb-1">
                        {spinning ? "Drawing squad" : "Squad drawn"}
                      </p>
                      <h2 className={`text-xl sm:text-2xl font-black truncate ${spinning ? "text-slate-400" : ""}`}>
                        {isLoadingMeta ? "Loading…" : display.team ?? "Rolling…"}
                      </h2>
                      <p className="text-slate-400 text-sm font-semibold">{display.year ?? "—"} season</p>
                    </div>
                    <Sparkles className="shrink-0 text-lime-300" size={22} aria-hidden="true" />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 mb-4">
                  <button onClick={loadSquad} disabled={isLoadingPlayers || isSquadLoaded || spinning || !currentTeam}
                    className="bg-lime-300 hover:bg-lime-200 text-slate-950 disabled:bg-slate-700 disabled:text-slate-400 disabled:cursor-not-allowed px-3 py-2.5 rounded-none font-black text-sm">
                    {isLoadingPlayers ? "Indexing…" : isSquadLoaded ? "Loaded" : "Load Squad"}
                  </button>
                  <button onClick={() => store.rerollTeam()} disabled={teamRerollsLeft === 0 || spinning}
                    className="border border-slate-600 bg-slate-900/40 hover:border-slate-400 disabled:opacity-40 disabled:cursor-not-allowed px-3 py-2.5 rounded-none font-semibold text-sm">
                    Reroll Team ({teamRerollsLeft})
                  </button>
                  <button onClick={() => store.rerollYear()} disabled={yearRerollsLeft === 0 || spinning}
                    className="border border-slate-600 bg-slate-900/40 hover:border-slate-400 disabled:opacity-40 disabled:cursor-not-allowed px-3 py-2.5 rounded-none font-semibold text-sm">
                    Reroll Year ({yearRerollsLeft})
                  </button>
                </div>

                {finalSlotNeedsKeeper && isSquadLoaded && !loadedSquadHasAvailableKeeper && (
                  <button
                    onClick={() => store.rollReveal()}
                    className="w-full border border-yellow-400 bg-yellow-400/10 hover:bg-yellow-400/20 text-yellow-200 px-4 py-3 mb-4 font-bold"
                  >
                    No available wicketkeeper here — find another squad
                  </button>
                )}

                {draftError && (
                  <div className="bg-red-500/10 border border-red-500/40 text-red-300 rounded-none p-3 mb-3 text-sm">{draftError}</div>
                )}

                {!isSquadLoaded ? (
                  <div className="bg-slate-800/60 rounded-none p-6 text-slate-300 text-sm text-center">
                    {isLoadingPlayers
                      ? "Preparing historical squad data. The first load can take a moment."
                      : spinning ? "Revealing squad…" : "Load the squad to reveal players, then draft one."}
                  </div>
                ) : (
                  <div className="space-y-2 max-h-[540px] overflow-y-auto pr-1">
                    {currentPlayers.map((player) => {
                      const alreadyPicked = selectedTeam.some((p) => p.name === player.name);
                      const blockedByOverseas = player.overseas && overseasFull;
                      const blockedByKeeperRequirement = finalSlotNeedsKeeper && !player.keeperEligible;
                      const cannotDraft = alreadyPicked || blockedByOverseas || blockedByKeeperRequirement;
                      return (
                        <div key={player.id}
                          className={`grid grid-cols-[auto_minmax(0,1fr)_auto] sm:grid-cols-[auto_minmax(0,1fr)_auto_auto] items-center gap-x-3 gap-y-2 rounded-none border p-3 ${cannotDraft ? "border-slate-800 opacity-50" : "border-slate-700 hover:border-slate-500"}`}>
                          <span className="text-[10px] font-black tracking-wide px-1.5 py-1 rounded-none bg-slate-800 text-slate-300" title={player.country || "Country unavailable"}>
                            {cricketCountryCode(player.country, player.overseas)}
                          </span>
                          <div className="flex-1 min-w-0">
                            <p className="font-bold flex items-center gap-1 truncate">
                              {player.name}
                              {player.overseas && <Plane size={12} className="text-sky-400" />}
                              {player.keeperEligible && <Shield size={12} className="text-blue-400" />}
                            </p>
                            <p className="text-slate-400 text-xs truncate">{player.role} · {player.statsSummary}</p>
                            {blockedByOverseas && <p className="text-red-400 text-xs">4 overseas already selected</p>}
                            {blockedByKeeperRequirement && <p className="text-yellow-300 text-xs">Final pick must be a wicketkeeper</p>}
                            {alreadyPicked && <p className="text-red-400 text-xs">Already drafted</p>}
                          </div>
                          {!hardMode && <span className="text-lg sm:text-xl font-black tabular-nums text-slate-200 sm:w-10 text-right">{player.rating}</span>}
                          <button onClick={() => store.draftPlayer(player)} disabled={cannotDraft}
                            className="col-start-2 sm:col-start-auto justify-self-start sm:justify-self-auto border border-lime-400/70 text-lime-200 hover:bg-lime-300 hover:text-slate-950 disabled:border-slate-700 disabled:bg-slate-800 disabled:text-slate-500 px-3 py-1.5 rounded-none font-bold text-sm whitespace-nowrap">
                            Draft
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}
              </>
            ) : (
              /* Finalize: captain + keeper */
              <div>
                <p className="text-slate-400 text-sm mb-4">Your XI is complete. Pick your captain and wicketkeeper.</p>
                {needsKeeper && (
                  <div className="border border-red-500/50 bg-red-500/10 p-4 mb-4">
                    <p className="text-red-300 font-bold mb-1">This XI has no wicketkeeper.</p>
                    <p className="text-slate-400 text-sm mb-3">Keep your first ten players and replace only your last pick.</p>
                    <button onClick={() => store.undoLastPick()} className="bg-yellow-400 text-black px-4 py-2 font-black">
                      Replace last pick
                    </button>
                  </div>
                )}
                <div className="space-y-2 max-h-[520px] overflow-y-auto pr-1">
                  {selectedTeam.map((player) => (
                    <div key={player.id} className="flex items-center justify-between gap-2 bg-slate-800 rounded-none p-3">
                      <div className="min-w-0">
                        <p className="font-bold flex items-center gap-1 truncate">
                          {player.name}
                          {player.overseas && <Plane size={12} className="text-sky-400" />}
                        </p>
                        <p className="text-slate-400 text-xs">{player.role}{player.keeperEligible ? " · Keeper" : ""}{!hardMode ? ` · ${player.rating} OVR` : ""}</p>
                      </div>
                      <div className="flex gap-2">
                        <button onClick={() => store.setCaptain(player.id)}
                          className={`w-9 h-9 rounded-full font-black ${selectedCaptainId === player.id ? "bg-yellow-400 text-black" : "bg-slate-700 hover:bg-yellow-500 hover:text-black"}`}>C</button>
                        {player.keeperEligible && (
                          <button onClick={() => store.setKeeper(player.id)}
                            className={`w-9 h-9 rounded-full font-black ${selectedKeeperId === player.id ? "bg-blue-400 text-black" : "bg-slate-700 hover:bg-blue-500 hover:text-black"}`}>K</button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </section>

          {/* RIGHT: Field formation */}
          <section className="glass-panel rounded-none p-6">
            <div className="flex items-center justify-between mb-3">
              <p className="text-slate-400 text-xs font-bold uppercase tracking-widest">
                Your XI · Avg {hardMode ? "—" : avgRating ?? "—"}
              </p>
              <p className="text-slate-500 text-xs">{selectedTeam.length}/{config.squadSize} drafted</p>
            </div>

            <FieldFormation team={selectedTeam} captainId={selectedCaptainId} keeperId={selectedKeeperId} />

            <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-1 border-l-2 border-lime-300 pl-3 text-xs text-slate-400" aria-label="XI role composition">
              <span className="font-bold uppercase tracking-[0.14em] text-slate-500">Squad mix</span>
              <span><strong className="text-white">{roleCounts.batters}</strong> bat</span>
              <span aria-hidden="true" className="text-slate-700">/</span>
              <span><strong className="text-white">{roleCounts.bowlers}</strong> bowl</span>
              <span aria-hidden="true" className="text-slate-700">/</span>
              <span><strong className="text-white">{roleCounts.allRounders}</strong> all-round</span>
              <span aria-hidden="true" className="text-slate-700">/</span>
              <span><strong className="text-white">{roleCounts.keepers}</strong> keeper</span>
            </div>

            {isDraftComplete ? (
              <div className="mt-4">
                {selectedCaptainId === null && <p className="text-red-400 text-sm mb-2">Pick a captain (C).</p>}
                {selectedKeeperId === null && <p className="text-red-400 text-sm mb-2">Pick a wicketkeeper (K).</p>}
                {draftError && <p className="text-red-400 text-sm mb-2">{draftError}</p>}
                <button onClick={handleSimulate} disabled={!canSimulate || isSimulating}
                  className="w-full bg-yellow-500 hover:bg-yellow-600 disabled:bg-slate-700 disabled:cursor-not-allowed text-black py-4 rounded-none font-black text-lg flex items-center justify-center gap-2">
                  {isSimulating && <Loader2 size={20} className="animate-spin" />}
                  {isSimulating ? "Simulating…" : `Simulate ${config.perfectTarget}-0 Run`}
                </button>
              </div>
            ) : (
              <p className="mt-4 text-slate-500 text-sm text-center">
                Draft players on the left — they auto-slot into the formation by role.
              </p>
            )}
          </section>
        </div>
      </div>

      {showHow && <HowModal config={config} onClose={() => setShowHow(false)} />}
    </main>
  );
}

function Stat({ label, value, danger }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <span className="text-slate-500 uppercase text-[11px] tracking-wider">{label}</span>
      <span className={`font-black ${danger ? "text-red-400" : "text-white"}`}>{value}</span>
    </div>
  );
}

function HowModal({ config, onClose }) {
  return (
    <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-6 z-50" onClick={onClose}>
      <div className="bg-slate-900 border border-slate-700 rounded-none p-8 max-w-lg" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-2xl font-black">How to play</h2>
          <button onClick={onClose}><X /></button>
        </div>
        <ol className="space-y-2 text-slate-300 text-sm list-decimal list-inside">
          <li>Spin reveals a random historical squad. Reroll team/year before drafting.</li>
          <li>Load the squad and draft one player — it consumes the squad and spins the next.</li>
          <li>Build a legal XI: max {config.overseasLimit ?? "∞"} overseas, ≥{config.minKeepers} keeper.</li>
          <li>Pick a captain and keeper. The captain decides the batting order.</li>
          <li>Choose Hard Mode before your first pick. Difficulty locks once drafting begins.</li>
          <li>Choose Historical or boss-mode Legacy XI opponents.</li>
          <li>Go for the perfect {config.perfectTarget}-0 season.</li>
        </ol>
      </div>
    </div>
  );
}

export default Draft;
