import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeftRight, Check, Copy, Trophy, Crown, Plane, Share2, Upload, Shield, Sparkles, X } from "lucide-react";
import useGameStore from "../store/useGameStore";
import api from "../services/api";
import { GAME_MODES } from "../data/modes";

function Result() {
  const navigate = useNavigate();
  const {
    simulationResult, selectedTeam, gameMode, newGame, startSwap, swapsLeft,
    selectedCaptainId, selectedKeeperId, opponentType, hardMode, runSeed, seasonCap,
  } = useGameStore();
  const [displayName, setDisplayName] = useState("");
  const [submission, setSubmission] = useState(null);
  const [submitError, setSubmitError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [resultCopied, setResultCopied] = useState(false);
  const [publishConsent, setPublishConsent] = useState(false);
  const [showSwap, setShowSwap] = useState(false);
  const [swapPlayerId, setSwapPlayerId] = useState(null);
  const availableSwaps = Number.isInteger(swapsLeft) ? swapsLeft : 2;

  if (!simulationResult) {
    return (
      <main className="min-h-screen bg-slate-950 text-white flex items-center justify-center">
        <div className="bg-slate-900 border border-slate-800 rounded-none p-8 text-center">
          <h1 className="text-4xl font-black mb-3">No Result Yet</h1>
          <Link to={`/draft/${gameMode || "ipl"}`} className="text-blue-400">
            Go to draft
          </Link>
        </div>
      </main>
    );
  }

  const r = simulationResult;
  const b = r.ratingBreakdown || {};
  const config = GAME_MODES[gameMode] || GAME_MODES.ipl;
  const leaderboardEligible = hardMode && r.losses === 0;
  const yourStandingIndex = r.standings?.findIndex((row) => row.team === "Your XI") ?? -1;
  const yourStanding = yourStandingIndex >= 0 ? r.standings[yourStandingIndex] : null;
  const finish = r.champion
    ? "1st"
    : r.stage === "Runner-up"
      ? "2nd"
      : r.stage === "Playoffs"
        ? "Playoffs"
        : yourStandingIndex >= 0 ? ordinal(yourStandingIndex + 1) : "—";
  const balanceGap = (b.batting ?? 0) - (b.bowling ?? 0);
  const xiStyle = Math.abs(balanceGap) <= 5 ? "Balanced XI" : balanceGap > 5 ? "Batting-led XI" : "Bowling-led XI";
  const verdict = r.perfect ? "PERFECT RUN" : r.champion ? "CHAMPIONS" : r.losses === 0 ? "UNBEATEN" : "SEASON COMPLETE";
  const recordText = `${r.wins}-${r.draws ?? 0}-${r.losses}`;
  const standingsTitle = gameMode === "wtc" ? "Championship Standings" : gameMode === "ipl" ? "League Standings" : "Tournament Standings";

  const startNewGame = () => {
    newGame();
    navigate(`/draft/${gameMode || "ipl"}`);
  };

  const submitRun = async () => {
    if (isSubmitting || !displayName.trim()) return;
    setIsSubmitting(true);
    setSubmitError("");
    try {
      const response = await api.post("/leaderboard", {
        displayName: displayName.trim(), mode: gameMode, opponentType, hardMode,
        seed: runSeed, maxSeason: seasonCap, captainId: selectedCaptainId,
        keeperId: selectedKeeperId, team: selectedTeam, publishConsent, claimedWins: r.wins,
        claimedDraws: r.draws ?? 0, claimedLosses: r.losses,
      });
      setSubmission(response.data);
    } catch (error) {
      setSubmitError(error.response?.data?.error || "Could not verify and post this run.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const shareRun = async () => {
    if (!submission) return;
    const url = `${window.location.origin}/leaderboard/${gameMode}#${submission.id}`;
    const text = `${submission.displayName}'s ${submission.wins}-${submission.draws}-${submission.losses} ${submission.mode} XI on Perfect Run`;
    if (navigator.share) {
      try { await navigator.share({ title: "Perfect Run XI", text, url }); } catch { /* user cancelled */ }
    } else {
      await navigator.clipboard.writeText(`${text} ${url}`);
      setSubmitError("Share link copied to clipboard.");
    }
  };

  const copyResult = async () => {
    const nrr = yourStanding?.netRunRate;
    const lines = [
      `${config.perfectTarget}–0 · ${config.title}`,
      `${r.champion ? "🏆 " : ""}${verdict}`,
      `${recordText} · finished ${finish}${gameMode !== "wtc" && Number.isFinite(nrr) ? ` · NRR ${nrr >= 0 ? "+" : ""}${nrr.toFixed(3)}` : ""} · Bat ${b.batting ?? "—"} / Bowl ${b.bowling ?? "—"}`,
      `${xiStyle} · ${hardMode ? "Hard" : "Normal"} Mode`,
      `Think your XI can go ${config.perfectTarget}–0? Play: ${window.location.origin}/`,
    ];
    await navigator.clipboard.writeText(lines.join("\n"));
    setResultCopied(true);
    window.setTimeout(() => setResultCopied(false), 1800);
  };

  const confirmSwap = () => {
    if (swapPlayerId === null || availableSwaps <= 0) return;
    startSwap(swapPlayerId);
    navigate(`/draft/${gameMode || "ipl"}`);
  };

  return (
    <main className="result-page min-h-screen bg-slate-950 text-white">
      {(r.champion || r.perfect) && <Confetti />}
      <div className="mx-auto w-full max-w-[1500px] px-4 py-8 sm:px-5 md:px-8 md:py-12 xl:px-10">
        <section className={`result-hero mb-8 border p-6 text-center md:p-10 ${r.champion ? "result-hero--champion border-yellow-400/50" : "border-slate-700"}`}>
          <div className="relative z-10">
            <div className="sport-label mb-5 flex flex-wrap items-center justify-center gap-3 text-[10px] font-bold text-slate-400">
              <span>{config.tag}</span><span className="text-slate-700">/</span><span>{hardMode ? "Hard Mode" : "Normal Mode"}</span><span className="text-slate-700">/</span><span>Season over</span>
            </div>
            <div className="mx-auto mb-5 grid max-w-md grid-cols-3 border-y border-white/10 py-4">
              <HeroMetric value={r.wins} label="Won" tone="text-emerald-400" />
              <HeroMetric value={r.draws ?? 0} label="Drawn" tone="text-sky-400" />
              <HeroMetric value={r.losses} label="Lost" tone="text-red-400" />
            </div>
            <div className="flex items-center justify-center gap-3">
              {r.champion && <Trophy className="text-yellow-400" size={46} />}
              {!r.champion && r.losses === 0 && <Sparkles className="text-lime-300" size={40} />}
              <h1 className={`sport-display text-5xl font-black leading-none md:text-7xl ${r.champion ? "text-yellow-300" : "text-white"}`}>{verdict}</h1>
            </div>
            <p className="mx-auto mt-4 max-w-2xl text-base text-slate-300 md:text-lg">{r.summary}</p>
            <div className="mx-auto mt-6 grid max-w-3xl grid-cols-2 gap-px border border-white/10 bg-white/10 md:grid-cols-4">
              <HeroFact label="Record" value={recordText} />
              <HeroFact label="Finish" value={finish} />
              <HeroFact label="Team" value={`${b.overall ?? "—"} OVR`} />
              <HeroFact label="Identity" value={xiStyle} />
            </div>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <button onClick={startNewGame} className="bg-yellow-400 px-6 py-3 font-black text-slate-950 hover:bg-yellow-300">Build another XI</button>
              {availableSwaps > 0 && (
                <button onClick={() => setShowSwap(true)} className="flex items-center gap-2 border border-red-400/70 bg-red-400/10 px-6 py-3 font-black text-red-300 hover:bg-red-400/20">
                  <ArrowLeftRight size={17} /> Try a swap
                </button>
              )}
              <button onClick={copyResult} className="flex items-center gap-2 border border-yellow-400/70 px-6 py-3 font-black text-yellow-300 hover:bg-yellow-400/10">
                {resultCopied ? <Check size={17} /> : <Copy size={17} />} {resultCopied ? "Result copied" : "Copy result"}
              </button>
            </div>
            <p className="mt-3 text-xs font-black uppercase tracking-[0.18em] text-slate-500">{availableSwaps} {availableSwaps === 1 ? "swap" : "swaps"} remaining</p>
          </div>
        </section>

        <div className={`mb-10 border p-5 ${leaderboardEligible ? "border-lime-300/30 bg-lime-300/5" : "border-slate-800 bg-slate-900"}`}>
          <div className="md:flex md:items-center md:justify-between md:gap-6">
          <div>
            <p className="eyebrow mb-1">HARD MODE LEADERBOARD</p>
            <h2 className="text-2xl font-black">{leaderboardEligible ? "Your unbeaten XI can enter the board" : "Unbeaten Hard Mode runs qualify"}</h2>
            <p className="mt-1 text-sm text-slate-400">{leaderboardEligible ? "Upload is unlocked. The server will verify and replay this exact run." : "Keep playing in Hard Mode and finish with zero losses to unlock team upload."}</p>
          </div>
          {leaderboardEligible && (!submission ? (
            <div className="mt-4 w-full max-w-md md:mt-0">
              <div className="flex flex-col gap-2 sm:flex-row">
              <input
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                maxLength={24}
                placeholder="Display name"
                className="w-full min-w-0 flex-1 border border-slate-700 bg-slate-950 px-4 py-3 outline-none focus:border-lime-300"
              />
              <button onClick={submitRun} disabled={isSubmitting || displayName.trim().length < 2 || !publishConsent}
                className="flex items-center gap-2 bg-lime-300 px-4 py-3 font-black text-slate-950 disabled:bg-slate-700 disabled:text-slate-500">
                <Upload size={16} /> {isSubmitting ? "Verifying…" : "Post run"}
              </button>
              </div>
              <label className="mt-3 flex cursor-pointer items-start gap-2 text-xs leading-5 text-slate-400">
                <input type="checkbox" checked={publishConsent} onChange={(event) => setPublishConsent(event.target.checked)} className="mt-1 accent-lime-300" />
                I approve publishing my display name, verified record, and full XI to the public leaderboard.
              </label>
            </div>
          ) : (
            <div className="mt-4 flex flex-col gap-2 sm:flex-row md:mt-0">
              <Link to={`/leaderboard/${gameMode}#${submission.id}`} className="border border-slate-700 bg-slate-900 px-4 py-3 font-bold">View rank</Link>
              <button onClick={shareRun} className="flex items-center gap-2 bg-lime-300 px-4 py-3 font-black text-slate-950"><Share2 size={16} /> Share XI</button>
            </div>
          ))}
          </div>
          {submitError && <p className="mt-3 text-sm text-yellow-300">{submitError}</p>}
        </div>

        {/* Summary cards */}
        <div className="mb-10 grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-5">
          <SummaryCard label="Wins" value={r.wins} />
          <SummaryCard label="Draws" value={r.draws ?? 0} />
          <SummaryCard label="Losses" value={r.losses} />
          <SummaryCard label="Record" value={`${r.wins}-${r.draws ?? 0}-${r.losses}`} />
          <SummaryCard label="Perfect Target" value={`${r.perfectTarget}-0`} />
        </div>

        {/* Team rating breakdown */}
        <Section title="Team Rating Breakdown">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <Metric label="Overall" value={b.overall} />
            <Metric label="Batting" value={b.batting} />
            <Metric label="Bowling" value={b.bowling} />
            <Metric label="AR Depth" value={b.allRounderDepth} />
            <Metric label="Keeping" value={b.keeping} />
            <Metric label="Captain Impact" value={b.captainImpact} />
            <Metric label="Keeper Impact" value={b.keeperImpact} />
          </div>
        </Section>

        {/* Awards */}
        {r.awards && r.awards.length > 0 && (
          <Section title="Awards">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {r.awards.map((a) => (
                <div key={a.category} className="bg-slate-800 rounded-none p-4 flex justify-between items-center">
                  <div>
                    <p className="text-slate-400 text-xs uppercase">{a.category}</p>
                    <p className="font-bold text-lg">{a.player}</p>
                  </div>
                  <p className="text-slate-300 text-sm text-right">{a.detail}</p>
                </div>
              ))}
            </div>
          </Section>
        )}

        {/* Match-by-match */}
        <Section title="Match-by-Match">
          <div className="space-y-2">
            {r.matches.map((m) => (
              <Link
                key={m.id}
                to={`/scorecard/${encodeURIComponent(m.id)}`}
                className={`flex flex-col items-start justify-between gap-3 rounded-none border p-4 transition hover:border-blue-500 sm:flex-row sm:items-center ${
                  m.drawn ? "bg-yellow-500/10 border-yellow-500/30" : m.won ? "bg-green-500/10 border-green-500/30" : "bg-red-500/10 border-red-500/30"
                }`}
              >
                <div className="flex items-center gap-3">
                  <span className={`font-black ${m.drawn ? "text-yellow-300" : m.won ? "text-green-400" : "text-red-400"}`}>
                    {m.drawn ? "D" : m.won ? "W" : "L"}
                  </span>
                  <div>
                    <p className="font-semibold">{m.stage} — vs {m.opponentLabel}</p>
                    <p className="text-slate-400 text-sm">{m.margin}</p>
                    {m.tossWinner && <p className="mt-1 text-xs text-slate-500">Toss: {m.tossWinner} chose to {m.tossDecision}</p>}
                  </div>
                </div>
                <div className="w-full text-left text-sm sm:w-auto sm:text-right">
                  <p>
                    {m.innings?.map((innings) => `${innings.team === "Your XI" ? "Your XI" : innings.team} ${innings.runs}/${innings.wickets} (${innings.overs} ov)`).join(" · ")}
                  </p>
                  <p className="text-slate-400">View scorecard →</p>
                </div>
              </Link>
            ))}
          </div>
        </Section>

        {/* Standings */}
        <Section title={standingsTitle}>
          <div className="overflow-x-auto rounded-none border border-slate-800">
            <div className="min-w-[680px]">
            <div className="grid grid-cols-[2fr_repeat(5,1fr)] bg-slate-800 px-4 py-2 text-sm font-semibold text-slate-300">
              <span>Team</span>
              <span className="text-center">P</span>
              <span className="text-center">W</span>
              <span className="text-center">D</span>
              <span className="text-center">L</span>
              <span className="text-center">Pts</span>
            </div>
            {r.standings.map((row, i) => (
              <div
                key={row.team + i}
                className={`grid grid-cols-[2fr_repeat(5,1fr)] px-4 py-2 text-sm ${
                  row.team === "Your XI" ? "bg-blue-500/15 font-bold" : i % 2 ? "bg-slate-900" : "bg-slate-900/50"
                }`}
              >
                <span>
                  {i + 1}. {row.team}
                </span>
                <span className="text-center">{row.played}</span>
                <span className="text-center">{row.won}</span>
                <span className="text-center">{row.drawn ?? 0}</span>
                <span className="text-center">{row.lost}</span>
                <span className="text-center">{row.points}</span>
              </div>
            ))}
            </div>
          </div>
        </Section>

        {/* Your XI */}
        <Section title="Your XI">
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {selectedTeam.map((p, i) => (
              <div key={p.id} className="bg-slate-800 rounded-none p-4 flex items-center gap-2">
                <span className="text-slate-500 w-6">{i + 1}.</span>
                <Crown
                  size={14}
                  className={p.id === r?.meta?.captainId ? "text-yellow-400" : "text-transparent"}
                />
                <div>
                  <p className="font-bold flex items-center gap-1">
                    {p.name}
                    {p.overseas && <Plane size={12} className="text-sky-400" />}
                    {p.id === r?.meta?.keeperId && <Shield size={12} className="text-sky-400" />}
                  </p>
                  <p className="text-slate-400 text-sm">{p.role} • {p.statsSummary}</p>
                </div>
              </div>
            ))}
          </div>
        </Section>

        <div className="mt-8 flex flex-col gap-3 sm:flex-row">
          <button
            onClick={startNewGame}
            className="bg-blue-600 hover:bg-blue-700 px-6 py-3 rounded-none font-bold"
          >
            New Game
          </button>
          <Link
            to={`/draft/${gameMode || "ipl"}`}
            className="bg-slate-800 hover:bg-slate-700 px-6 py-3 rounded-none font-bold"
          >
            Back to Draft
          </Link>
        </div>
      </div>
      {showSwap && (
        <SwapDialog
          team={selectedTeam}
          selectedId={swapPlayerId}
          swapsLeft={availableSwaps}
          onSelect={setSwapPlayerId}
          onConfirm={confirmSwap}
          onClose={() => { setShowSwap(false); setSwapPlayerId(null); }}
        />
      )}
    </main>
  );
}

function SummaryCard({ label, value }) {
  return (
    <div className="rounded-none border border-slate-800 bg-slate-900 p-4 sm:p-6">
      <p className="text-slate-400">{label}</p>
      <p className="text-3xl font-black sm:text-4xl">{value}</p>
    </div>
  );
}

function HeroMetric({ value, label, tone }) {
  return <div><p className={`text-3xl font-black md:text-4xl ${tone}`}>{value}</p><p className="mt-1 text-[10px] font-black uppercase tracking-[0.2em] text-slate-500">{label}</p></div>;
}

function HeroFact({ label, value }) {
  return <div className="bg-[#0b1115]/90 px-4 py-4"><p className="text-[10px] font-black uppercase tracking-[0.18em] text-slate-500">{label}</p><p className="mt-1 font-black text-white">{value}</p></div>;
}

function Confetti() {
  return (
    <div className="result-confetti" aria-hidden="true">
      {Array.from({ length: 52 }, (_, index) => (
        <i key={index} style={{
          "--x": `${(index * 37) % 100}vw`,
          "--delay": `${-((index * 0.17) % 4)}s`,
          "--duration": `${3.8 + (index % 7) * 0.35}s`,
          "--drift": `${(index % 2 ? 1 : -1) * (25 + (index % 5) * 12)}px`,
          "--color": ["#facc15", "#bef264", "#38bdf8", "#f43f5e", "#a855f7"][index % 5],
        }} />
      ))}
    </div>
  );
}

function SwapDialog({ team, selectedId, swapsLeft, onSelect, onConfirm, onClose }) {
  return (
    <div className="fixed inset-0 z-[90] grid place-items-center bg-black/80 p-3" onMouseDown={onClose} role="presentation">
      <section role="dialog" aria-modal="true" aria-labelledby="swap-title" onMouseDown={(event) => event.stopPropagation()}
        className="max-h-[92vh] w-full max-w-2xl overflow-y-auto border border-slate-700 bg-[#0b1115] p-4 sm:p-6">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div><p className="eyebrow mb-1">SECOND CHANCE · {swapsLeft} LEFT</p><h2 id="swap-title" className="text-2xl font-black">Choose one player to replace</h2><p className="mt-2 text-sm text-slate-400">Confirming consumes one swap. You will return to Draft with ten players and draft one replacement.</p></div>
          <button onClick={onClose} aria-label="Close swap dialog" className="border border-slate-700 p-2 text-slate-300"><X size={18} /></button>
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          {team.map((player) => (
            <button key={player.id} onClick={() => onSelect(player.id)}
              className={`border p-3 text-left ${selectedId === player.id ? "border-red-400 bg-red-400/10" : "border-slate-800 bg-slate-900 hover:border-slate-600"}`}>
              <p className="font-black">{player.name}</p>
              <p className="mt-1 text-xs text-slate-400">{player.role} · {player.rating} OVR{player.keeperEligible ? " · Keeper" : ""}</p>
            </button>
          ))}
        </div>
        <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button onClick={onClose} className="border border-slate-700 px-5 py-3 font-bold text-slate-300">Keep this XI</button>
          <button onClick={onConfirm} disabled={selectedId === null} className="bg-red-400 px-5 py-3 font-black text-slate-950 disabled:bg-slate-700 disabled:text-slate-500">Confirm swap</button>
        </div>
      </section>
    </div>
  );
}

function ordinal(number) {
  const remainder100 = number % 100;
  if (remainder100 >= 11 && remainder100 <= 13) return `${number}th`;
  return `${number}${number % 10 === 1 ? "st" : number % 10 === 2 ? "nd" : number % 10 === 3 ? "rd" : "th"}`;
}

function Section({ title, children }) {
  return (
    <div className="mb-6 rounded-none border border-slate-800 bg-slate-900 p-4 sm:p-6 md:mb-8">
      <h2 className="text-2xl font-bold mb-4">{title}</h2>
      {children}
    </div>
  );
}

function Metric({ label, value }) {
  return (
    <div className="bg-slate-800 rounded-none p-4">
      <p className="text-slate-400 text-xs uppercase">{label}</p>
      <p className="text-2xl font-black">{value ?? "—"}</p>
    </div>
  );
}

export default Result;
