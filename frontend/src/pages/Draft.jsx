import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { motion } from "framer-motion";
import { Plane, Shield, Crown, Loader2 } from "lucide-react";
import api from "../services/api";
import useGameStore from "../store/useGameStore";
import { GAME_MODES, OPPONENT_TYPES } from "../data/modes";

const randomItem = (items) => items[Math.floor(Math.random() * items.length)];

function Draft() {
  const { mode } = useParams();
  const navigate = useNavigate();
  const config = GAME_MODES[mode];

  const store = useGameStore();
  const {
    gameMode,
    selectedTeam,
    selectedCaptainId,
    selectedKeeperId,
    opponentType,
    hardMode,
    runSeed,
    currentYear,
    currentTeam,
    currentPlayers,
    isSquadLoaded,
    draftError,
    teamRerollsLeft,
    yearRerollsLeft,
    teamsByYearByMode,
    yearsByMode,
    squadCache,
  } = store;

  const [isLoadingPlayers, setIsLoadingPlayers] = useState(false);
  const [isLoadingMeta, setIsLoadingMeta] = useState(false);
  const [isSimulating, setIsSimulating] = useState(false);

  // Spin reveal local state.
  const [spinning, setSpinning] = useState(false);
  const [display, setDisplay] = useState({ year: currentYear, team: currentTeam });
  const spunKeyRef = useRef(null);

  // 1) Ensure correct mode.
  useEffect(() => {
    if (mode) store.ensureGameMode(mode);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  // 2) Fetch catalogue metadata, then roll the first reveal.
  useEffect(() => {
    if (!config) return;
    const years = yearsByMode[mode];
    if (years && years.length) {
      if (!currentTeam && selectedTeam.length < config.squadSize) store.rollReveal();
      return;
    }
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
        store.setDraftError("Could not load season catalogue. Is the backend running?");
      } finally {
        if (!cancelled) setIsLoadingMeta(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, yearsByMode[mode]]);

  // Roll the first reveal once meta is available.
  useEffect(() => {
    if (!config) return;
    const years = yearsByMode[mode];
    if (years && years.length && !currentTeam && selectedTeam.length < config.squadSize) {
      store.rollReveal();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [yearsByMode[mode], currentTeam]);

  // 3) Spin animation whenever a new reveal appears (and not yet loaded).
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
  const batCount = selectedTeam.filter((p) => p.role === "BAT").length;
  const wkCount = selectedTeam.filter((p) => p.role === "WK").length;
  const bowlCount = selectedTeam.filter((p) => p.role === "BOWL").length;
  const arCount = selectedTeam.filter((p) => p.role === "AR").length;
  const overseasFull = config.overseasLimit !== null && overseasCount >= config.overseasLimit;

  const hasKeeperSelected = selectedKeeperId !== null;
  const canSimulate =
    isDraftComplete &&
    selectedCaptainId !== null &&
    hasKeeperSelected &&
    keeperCount >= config.minKeepers &&
    (config.overseasLimit === null || overseasCount <= config.overseasLimit);

  const loadSquad = async () => {
    if (isLoadingPlayers || spinning || !currentTeam) return;
    const key = `${gameMode}-${currentYear}-${currentTeam}`;
    const cached = squadCache?.[key];
    if (cached) {
      store.loadSquadPlayers(cached);
      return;
    }
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
        team: selectedTeam,
        captainId: selectedCaptainId,
        keeperId: selectedKeeperId,
        opponentType,
        hardMode,
        seed: runSeed,
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
      <div className="max-w-7xl mx-auto px-6 py-10">
        <div className="mb-8 flex items-end justify-between flex-wrap gap-4">
          <div>
            <p className="text-blue-400 font-semibold mb-2">CRICKET PERFECT RUN</p>
            <h1 className="text-5xl font-black">{config.title}</h1>
            <p className="text-slate-400 mt-3">
              Roll a random squad, load it, then draft one player into your XI. Perfect target:{" "}
              {config.perfectTarget}-0.
            </p>
          </div>
          <button
            onClick={() => store.newGame()}
            className="bg-slate-800 hover:bg-slate-700 px-5 py-3 rounded-xl font-semibold"
          >
            New Game
          </button>
        </div>

        {!config.available && (
          <div className="bg-amber-500/10 border border-amber-500/40 text-amber-200 rounded-2xl p-4 mb-6">
            {config.title} reuses the shared draft/sim engine but is still being tuned. IPL Perfect
            Run is the fully playable mode.
          </div>
        )}

        <div className="grid lg:grid-cols-[300px_1fr_330px] gap-6">
          {/* Left: reveal + controls */}
          <aside className="bg-slate-900 border border-slate-800 rounded-3xl p-6 h-fit">
            <p className="text-slate-400 text-sm mb-2">
              {isDraftComplete ? "DRAFT COMPLETE" : "SQUAD REVEAL"}
            </p>

            {isDraftComplete ? (
              <h2 className="text-2xl font-bold mb-4">Choose your leaders</h2>
            ) : (
              <motion.div
                key={`${display.year}-${display.team}-${spinning}`}
                initial={{ opacity: 0.3, y: spinning ? -6 : 0 }}
                animate={{ opacity: 1, y: 0 }}
                className="mb-4"
              >
                <p className="text-blue-400 text-lg font-bold">
                  {isLoadingMeta ? "Loading…" : display.year ?? "—"}
                </p>
                <h2 className={`text-2xl font-bold ${spinning ? "text-slate-400" : ""}`}>
                  {display.team ?? "Rolling…"}
                </h2>
                {spinning && <p className="text-xs text-slate-500 mt-1">Spinning…</p>}
              </motion.div>
            )}

            {!isDraftComplete && (
              <div className="space-y-3">
                <button
                  onClick={loadSquad}
                  disabled={isLoadingPlayers || isSquadLoaded || spinning || !currentTeam}
                  className="w-full bg-green-600 hover:bg-green-700 disabled:bg-slate-700 disabled:cursor-not-allowed px-4 py-3 rounded-xl font-bold"
                >
                  {isLoadingPlayers
                    ? "Loading Squad…"
                    : isSquadLoaded
                    ? "Squad Loaded"
                    : "Load Squad"}
                </button>

                <button
                  onClick={() => store.rerollTeam()}
                  disabled={teamRerollsLeft === 0 || spinning}
                  className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-slate-700 disabled:cursor-not-allowed px-4 py-3 rounded-xl font-semibold"
                >
                  Reroll Team ({teamRerollsLeft})
                </button>

                <button
                  onClick={() => store.rerollYear()}
                  disabled={yearRerollsLeft === 0 || spinning}
                  className="w-full bg-purple-600 hover:bg-purple-700 disabled:bg-slate-700 disabled:cursor-not-allowed px-4 py-3 rounded-xl font-semibold"
                >
                  Reroll Year ({yearRerollsLeft})
                </button>
              </div>
            )}

            {/* Run settings */}
            <div className="mt-6 pt-6 border-t border-slate-800 space-y-4">
              <div>
                <p className="text-slate-400 text-xs mb-2">OPPONENT POOL</p>
                <div className="grid grid-cols-1 gap-2">
                  {Object.values(OPPONENT_TYPES).map((opt) => (
                    <button
                      key={opt.id}
                      onClick={() => store.setOpponentType(opt.id)}
                      className={`text-left px-3 py-2 rounded-xl border text-sm ${
                        opponentType === opt.id
                          ? "border-blue-500 bg-blue-500/10"
                          : "border-slate-700 hover:border-slate-600"
                      }`}
                    >
                      <span className="font-semibold">{opt.label}</span>
                      <span className="block text-xs text-slate-400">{opt.description}</span>
                    </button>
                  ))}
                </div>
              </div>

              <label className="flex items-center justify-between cursor-pointer">
                <span className="text-sm">
                  <span className="font-semibold">Hard Mode</span>
                  <span className="block text-xs text-slate-400">Hide OVR — draft on real stats</span>
                </span>
                <input
                  type="checkbox"
                  checked={hardMode}
                  onChange={() => store.toggleHardMode()}
                  className="w-5 h-5 accent-yellow-400"
                />
              </label>
            </div>
          </aside>

          {/* Middle: players / finalize */}
          <section>
            {!isDraftComplete ? (
              <>
                <h2 className="text-2xl font-bold mb-4">Available Players</h2>
                {draftError && (
                  <div className="bg-red-500/10 border border-red-500/40 text-red-300 rounded-2xl p-4 mb-4">
                    {draftError}
                  </div>
                )}

                {!isSquadLoaded ? (
                  <div className="bg-slate-900 border border-slate-800 rounded-3xl p-8">
                    <p className="text-slate-300 font-semibold">
                      {spinning ? "Revealing squad…" : "Click Load Squad to reveal players."}
                    </p>
                  </div>
                ) : (
                  <div className="grid md:grid-cols-2 gap-5">
                    {currentPlayers.map((player) => {
                      const alreadyPicked = selectedTeam.some((p) => p.name === player.name);
                      const blockedByOverseas = player.overseas && overseasFull;
                      const cannotDraft = alreadyPicked || blockedByOverseas;

                      return (
                        <div
                          key={player.id}
                          className={`bg-slate-900 border rounded-3xl p-5 transition ${
                            cannotDraft ? "border-slate-800 opacity-60" : "border-slate-800 hover:border-blue-500"
                          }`}
                        >
                          <div className="flex items-start justify-between gap-4 mb-3">
                            <div>
                              <h3 className="text-xl font-bold flex items-center gap-2">
                                {player.name}
                                {player.overseas && (
                                  <Plane size={16} className="text-sky-400" aria-label="Overseas" />
                                )}
                              </h3>
                              <p className="text-slate-400 text-sm">{player.country}</p>
                            </div>
                            {!hardMode && (
                              <div className="text-right">
                                <p className="text-3xl font-black text-yellow-400">{player.rating}</p>
                                <p className="text-xs text-slate-500">OVR</p>
                              </div>
                            )}
                          </div>

                          <div className="flex flex-wrap gap-2 mb-3">
                            <span className="bg-slate-800 px-3 py-1 rounded-full text-sm">{player.role}</span>
                            {player.keeperEligible && (
                              <span className="bg-blue-900/60 text-blue-200 px-3 py-1 rounded-full text-sm flex items-center gap-1">
                                <Shield size={13} /> Keeper
                              </span>
                            )}
                            {player.overseas && (
                              <span className="bg-sky-900/50 text-sky-200 px-3 py-1 rounded-full text-sm flex items-center gap-1">
                                <Plane size={13} /> Overseas
                              </span>
                            )}
                          </div>

                          <p className="text-slate-300 text-sm mb-4">{player.statsSummary}</p>

                          {blockedByOverseas && (
                            <p className="text-red-400 text-sm mb-3">4 overseas already selected</p>
                          )}
                          {alreadyPicked && (
                            <p className="text-red-400 text-sm mb-3">Already drafted</p>
                          )}

                          <button
                            onClick={() => store.draftPlayer(player)}
                            disabled={cannotDraft}
                            className="w-full bg-green-600 hover:bg-green-700 disabled:bg-slate-700 disabled:text-slate-500 py-3 rounded-xl font-bold"
                          >
                            Draft Player
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}
              </>
            ) : (
              <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6">
                <h2 className="text-3xl font-black mb-2">Finalize Your XI</h2>
                <p className="text-slate-400 mb-6">
                  Pick a captain (C) and a wicketkeeper (K). Only keeper-eligible players can keep.
                </p>

                <div className="grid md:grid-cols-2 gap-4 mb-6">
                  {selectedTeam.map((player) => (
                    <div key={player.id} className="bg-slate-800 rounded-2xl p-4">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="font-bold flex items-center gap-2">
                            {player.name}
                            {player.overseas && <Plane size={14} className="text-sky-400" />}
                          </p>
                          <p className="text-slate-400 text-sm">
                            {player.role}
                            {player.keeperEligible ? " • Keeper" : ""}
                            {!hardMode ? ` • ${player.rating} OVR` : ""}
                          </p>
                        </div>
                        <div className="flex gap-2">
                          <button
                            onClick={() => store.setCaptain(player.id)}
                            className={`w-9 h-9 rounded-full font-black ${
                              selectedCaptainId === player.id
                                ? "bg-yellow-400 text-black"
                                : "bg-slate-700 hover:bg-yellow-500 hover:text-black"
                            }`}
                          >
                            C
                          </button>
                          {player.keeperEligible && (
                            <button
                              onClick={() => store.setKeeper(player.id)}
                              className={`w-9 h-9 rounded-full font-black ${
                                selectedKeeperId === player.id
                                  ? "bg-blue-400 text-black"
                                  : "bg-slate-700 hover:bg-blue-500 hover:text-black"
                              }`}
                            >
                              K
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {selectedCaptainId === null && (
                  <p className="text-red-400 mb-2">Select a captain using C.</p>
                )}
                {selectedKeeperId === null && (
                  <p className="text-red-400 mb-2">Select a wicketkeeper using K.</p>
                )}
                {draftError && <p className="text-red-400 mb-2">{draftError}</p>}

                <button
                  onClick={handleSimulate}
                  disabled={!canSimulate || isSimulating}
                  className="w-full bg-yellow-500 hover:bg-yellow-600 disabled:bg-slate-700 disabled:cursor-not-allowed text-black py-4 rounded-2xl font-black text-lg flex items-center justify-center gap-2"
                >
                  {isSimulating && <Loader2 size={20} className="animate-spin" />}
                  {isSimulating ? "Simulating…" : "Simulate Season"}
                </button>
              </div>
            )}
          </section>

          {/* Right: your XI */}
          <aside className="bg-slate-900 border border-slate-800 rounded-3xl p-6 h-fit">
            <h2 className="text-2xl font-bold mb-2">
              Your XI ({selectedTeam.length}/{config.squadSize})
            </h2>
            <div className="w-full bg-slate-800 h-3 rounded-full mb-5">
              <div
                className="bg-green-500 h-3 rounded-full transition-all"
                style={{ width: `${(selectedTeam.length / config.squadSize) * 100}%` }}
              />
            </div>

            <div className="grid grid-cols-2 gap-3 mb-5">
              <Stat label="BAT" value={batCount} />
              <Stat label="BOWL" value={bowlCount} />
              <Stat label="AR" value={arCount} />
              <Stat label="WK" value={wkCount} />
              <Stat label="KEEPERS" value={`${keeperCount}/${config.minKeepers}`} />
              {config.overseasLimit !== null && (
                <Stat
                  label="OVERSEAS"
                  value={`${overseasCount}/${config.overseasLimit}`}
                  danger={overseasFull}
                />
              )}
            </div>

            <div className="space-y-2">
              {selectedTeam.map((player, index) => (
                <div key={player.id} className="bg-slate-800 rounded-2xl p-3">
                  <div className="flex justify-between gap-2">
                    <p className="font-semibold text-sm flex items-center gap-1">
                      {index + 1}. {player.name}
                      {player.overseas && <Plane size={12} className="text-sky-400" />}
                    </p>
                    <div className="flex gap-1">
                      {selectedCaptainId === player.id && (
                        <Crown size={16} className="text-yellow-400" />
                      )}
                      {selectedKeeperId === player.id && (
                        <Shield size={16} className="text-blue-400" />
                      )}
                    </div>
                  </div>
                </div>
              ))}
              {selectedTeam.length === 0 && (
                <p className="text-slate-500">No players drafted yet.</p>
              )}
            </div>
          </aside>
        </div>
      </div>
    </main>
  );
}

function Stat({ label, value, danger }) {
  return (
    <div className={`rounded-2xl p-3 ${danger ? "bg-red-500/15" : "bg-slate-800"}`}>
      <p className="text-slate-400 text-xs">{label}</p>
      <p className="text-xl font-bold">{value}</p>
    </div>
  );
}

export default Draft;
