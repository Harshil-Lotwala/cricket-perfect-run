import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { motion } from "framer-motion";
import { Plane, Shield, Loader2, Trophy, Info, RotateCcw, X } from "lucide-react";
import api from "../services/api";
import useGameStore from "../store/useGameStore";
import FieldFormation from "../components/FieldFormation";
import { GAME_MODES, OPPONENT_TYPES } from "../data/modes";

const randomItem = (items) => items[Math.floor(Math.random() * items.length)];

function Draft() {
  const { mode } = useParams();
  const navigate = useNavigate();
  const config = GAME_MODES[mode];

  const store = useGameStore();
  const {
    gameMode, selectedTeam, selectedCaptainId, selectedKeeperId, opponentType, hardMode,
    runSeed, currentYear, currentTeam, currentPlayers, isSquadLoaded, draftError,
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
    const years = yearsByMode[mode];
    if (years && years.length) return;
    let cancelled = false;
    (async () => {
      try {
        setIsLoadingMeta(true);
        const [teamsRes, yearsRes] = await Promise.all([
          api.get(`/meta/${mode}/teams-by-year`),
          api.get(`/meta/${mode}/years`),
        ]);
        if (cancelled) return;
        store.setMeta(mode, teamsRes.data, yearsRes.data);
      } catch (e) {
        console.error("Failed to load catalogue", e);
        store.setDraftError("Could not load season catalogue. Is the backend running on :8080?");
      } finally {
        if (!cancelled) setIsLoadingMeta(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, yearsByMode[mode]]);

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
    const years = yearsByMode[mode] || [];
    if (!years.length) return;
    setSpinning(true);
    let ticks = 0;
    const interval = setInterval(() => {
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
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentYear, currentTeam, isSquadLoaded]);

  if (!config) {
    return <div className="min-h-screen bg-slate-950 text-white p-10">Invalid draft mode.</div>;
  }

  const isDraftComplete = selectedTeam.length === config.squadSize;
  const overseasCount = selectedTeam.filter((p) => p.overseas).length;
  const keeperCount = selectedTeam.filter((p) => p.keeperEligible).length;
  const overseasFull = config.overseasLimit !== null && overseasCount >= config.overseasLimit;
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
      store.setDraftError("Could not load this squad. Try rerolling or check the backend.");
    } finally {
      setIsLoadingPlayers(false);
    }
  };

  const handleSimulate = async () => {
    if (!canSimulate || isSimulating) return;
    try {
      setIsSimulating(true);
      const res = await api.post(`/simulate/${gameMode}`, {
        team: selectedTeam, captainId: selectedCaptainId, keeperId: selectedKeeperId,
        opponentType, hardMode, seed: runSeed,
      });
      store.setSimulationResult(res.data);
      navigate("/result");
    } catch (e) {
      console.error("Simulation failed", e);
      store.setDraftError("Simulation failed. Check the backend console.");
    } finally {
      setIsSimulating(false);
    }
  };

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <div className="max-w-[1400px] mx-auto px-6 py-6">
        {/* Header */}
        <header className="flex items-center justify-between gap-4 flex-wrap mb-4">
          <div>
            <h1 className="text-3xl font-black tracking-tight">
              {config.perfectTarget}·0 <span className="text-slate-500 text-base font-semibold uppercase tracking-widest ml-2">{config.title}</span>
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => setShowHow(true)} className="flex items-center gap-1 bg-slate-800 hover:bg-slate-700 px-4 py-2 rounded-lg text-sm font-semibold">
              <Info size={15} /> How
            </button>
            <span className="flex items-center gap-1 bg-slate-800 px-4 py-2 rounded-lg text-sm font-semibold">
              <Trophy size={15} className="text-yellow-400" /> {selectedTeam.length === config.squadSize ? "Ready" : `${selectedTeam.length}/${config.squadSize}`} · Target {config.perfectTarget}-0
            </span>
            <button onClick={() => store.newGame()} className="flex items-center gap-1 bg-slate-800 hover:bg-slate-700 px-4 py-2 rounded-lg text-sm font-semibold">
              <RotateCcw size={15} /> New Game
            </button>
          </div>
        </header>

        {/* Stat strip */}
        <div className="flex items-center gap-x-6 gap-y-2 flex-wrap bg-slate-900/70 border border-slate-800 rounded-2xl px-5 py-3 mb-6 text-sm">
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
              className={`px-4 py-2 rounded-lg font-bold text-sm ${hardMode ? "bg-yellow-400 text-black" : "bg-slate-800 hover:bg-slate-700"}`}
            >
              ● Hard Mode
            </button>
          </div>
        </div>

        <div className="grid lg:grid-cols-[1fr_1fr] gap-6">
          {/* LEFT: Spin & Draft */}
          <section className="bg-slate-900 border border-slate-800 rounded-3xl p-6">
            <p className="text-slate-400 text-xs font-bold uppercase tracking-widest mb-3">Spin &amp; Draft</p>

            {/* Opponent selector */}
            <div className="grid grid-cols-2 gap-2 mb-4">
              {Object.values(OPPONENT_TYPES).map((opt) => (
                <button
                  key={opt.id}
                  onClick={() => store.setOpponentType(opt.id)}
                  className={`text-left px-3 py-2 rounded-xl border text-xs ${
                    opponentType === opt.id ? "border-blue-500 bg-blue-500/10" : "border-slate-700 hover:border-slate-600"
                  }`}
                >
                  <span className="font-bold">{opt.label}</span>
                  <span className="block text-[11px] text-slate-400">{opt.description}</span>
                </button>
              ))}
            </div>

            {!isDraftComplete ? (
              <>
                {/* Reveal card */}
                <motion.div
                  key={`${display.year}-${display.team}-${spinning}`}
                  initial={{ opacity: 0.3, y: spinning ? -6 : 0 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="bg-gradient-to-br from-slate-800 to-slate-900 border border-slate-700 rounded-2xl p-6 text-center mb-3"
                >
                  <h2 className={`text-2xl font-black ${spinning ? "text-slate-400" : ""}`}>
                    {isLoadingMeta ? "Loading…" : display.team ?? "Rolling…"}
                  </h2>
                  <p className="text-blue-400 font-bold">{display.year ?? "—"} squad</p>
                  {spinning && <p className="text-xs text-slate-500 mt-1">Spinning…</p>}
                </motion.div>

                <div className="grid grid-cols-3 gap-2 mb-4">
                  <button onClick={loadSquad} disabled={isLoadingPlayers || isSquadLoaded || spinning || !currentTeam}
                    className="bg-green-600 hover:bg-green-700 disabled:bg-slate-700 disabled:cursor-not-allowed px-3 py-2.5 rounded-xl font-bold text-sm">
                    {isLoadingPlayers ? "Loading…" : isSquadLoaded ? "Loaded" : "Load Squad"}
                  </button>
                  <button onClick={() => store.rerollTeam()} disabled={teamRerollsLeft === 0 || spinning}
                    className="bg-blue-600 hover:bg-blue-700 disabled:bg-slate-700 disabled:cursor-not-allowed px-3 py-2.5 rounded-xl font-semibold text-sm">
                    Reroll Team ({teamRerollsLeft})
                  </button>
                  <button onClick={() => store.rerollYear()} disabled={yearRerollsLeft === 0 || spinning}
                    className="bg-purple-600 hover:bg-purple-700 disabled:bg-slate-700 disabled:cursor-not-allowed px-3 py-2.5 rounded-xl font-semibold text-sm">
                    Reroll Year ({yearRerollsLeft})
                  </button>
                </div>

                {draftError && (
                  <div className="bg-red-500/10 border border-red-500/40 text-red-300 rounded-xl p-3 mb-3 text-sm">{draftError}</div>
                )}

                {!isSquadLoaded ? (
                  <div className="bg-slate-800/60 rounded-2xl p-6 text-slate-300 text-sm text-center">
                    {spinning ? "Revealing squad…" : "Load the squad to reveal players, then draft one."}
                  </div>
                ) : (
                  <div className="space-y-2 max-h-[540px] overflow-y-auto pr-1">
                    {currentPlayers.map((player) => {
                      const alreadyPicked = selectedTeam.some((p) => p.name === player.name);
                      const blockedByOverseas = player.overseas && overseasFull;
                      const cannotDraft = alreadyPicked || blockedByOverseas;
                      return (
                        <div key={player.id}
                          className={`flex items-center gap-3 rounded-xl border p-3 ${cannotDraft ? "border-slate-800 opacity-50" : "border-slate-700 hover:border-blue-500"}`}>
                          <span className={`text-[10px] font-black px-1.5 py-0.5 rounded ${player.overseas ? "bg-sky-900 text-sky-200" : "bg-slate-700 text-slate-200"}`}>
                            {player.overseas ? "OVS" : "IND"}
                          </span>
                          <div className="flex-1 min-w-0">
                            <p className="font-bold flex items-center gap-1 truncate">
                              {player.name}
                              {player.overseas && <Plane size={12} className="text-sky-400" />}
                              {player.keeperEligible && <Shield size={12} className="text-blue-400" />}
                            </p>
                            <p className="text-slate-400 text-xs truncate">{player.role} · {player.statsSummary}</p>
                            {blockedByOverseas && <p className="text-red-400 text-xs">4 overseas already selected</p>}
                            {alreadyPicked && <p className="text-red-400 text-xs">Already drafted</p>}
                          </div>
                          {!hardMode && <span className="text-2xl font-black text-yellow-400 w-10 text-right">{player.rating}</span>}
                          <button onClick={() => store.draftPlayer(player)} disabled={cannotDraft}
                            className="bg-green-600 hover:bg-green-700 disabled:bg-slate-700 disabled:text-slate-500 px-3 py-2 rounded-lg font-bold text-sm whitespace-nowrap">
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
                <p className="text-slate-400 text-sm mb-4">Your XI is complete. The captain decides the batting order — just pick your leaders.</p>
                <div className="space-y-2 max-h-[520px] overflow-y-auto pr-1">
                  {selectedTeam.map((player) => (
                    <div key={player.id} className="flex items-center justify-between gap-2 bg-slate-800 rounded-xl p-3">
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
          <section className="bg-slate-900 border border-slate-800 rounded-3xl p-6">
            <div className="flex items-center justify-between mb-3">
              <p className="text-slate-400 text-xs font-bold uppercase tracking-widest">
                Your XI · Avg {hardMode ? "—" : avgRating ?? "—"}
              </p>
              <p className="text-slate-500 text-xs">{selectedTeam.length}/{config.squadSize} drafted</p>
            </div>

            <FieldFormation team={selectedTeam} captainId={selectedCaptainId} keeperId={selectedKeeperId} />

            {isDraftComplete ? (
              <div className="mt-4">
                {selectedCaptainId === null && <p className="text-red-400 text-sm mb-2">Pick a captain (C).</p>}
                {selectedKeeperId === null && <p className="text-red-400 text-sm mb-2">Pick a wicketkeeper (K).</p>}
                {draftError && <p className="text-red-400 text-sm mb-2">{draftError}</p>}
                <button onClick={handleSimulate} disabled={!canSimulate || isSimulating}
                  className="w-full bg-yellow-500 hover:bg-yellow-600 disabled:bg-slate-700 disabled:cursor-not-allowed text-black py-4 rounded-2xl font-black text-lg flex items-center justify-center gap-2">
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
      <div className="bg-slate-900 border border-slate-700 rounded-3xl p-8 max-w-lg" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-2xl font-black">How to play</h2>
          <button onClick={onClose}><X /></button>
        </div>
        <ol className="space-y-2 text-slate-300 text-sm list-decimal list-inside">
          <li>Spin reveals a random historical squad. Reroll team/year before drafting.</li>
          <li>Load the squad and draft one player — it consumes the squad and spins the next.</li>
          <li>Build a legal XI: max {config.overseasLimit ?? "∞"} overseas, ≥{config.minKeepers} keeper.</li>
          <li>Pick a captain and keeper. The captain decides the batting order.</li>
          <li>Choose Historical or boss-mode Legacy XI opponents, optionally Hard Mode.</li>
          <li>Go for the perfect {config.perfectTarget}-0 season.</li>
        </ol>
      </div>
    </div>
  );
}

export default Draft;
