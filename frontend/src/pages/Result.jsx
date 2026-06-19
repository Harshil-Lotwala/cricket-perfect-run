import { Link, useNavigate } from "react-router-dom";
import { Trophy, Crown, Plane } from "lucide-react";
import useGameStore from "../store/useGameStore";

function Result() {
  const navigate = useNavigate();
  const { simulationResult, selectedTeam, gameMode, newGame } = useGameStore();

  if (!simulationResult) {
    return (
      <main className="min-h-screen bg-slate-950 text-white flex items-center justify-center">
        <div className="bg-slate-900 border border-slate-800 rounded-3xl p-8 text-center">
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

  const startNewGame = () => {
    newGame();
    navigate(`/draft/${gameMode || "ipl"}`);
  };

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <div className="max-w-6xl mx-auto px-6 py-12">
        <p className="text-blue-400 font-semibold mb-2">SEASON RESULT</p>
        <div className="flex items-center gap-3 mb-2">
          <h1 className="text-5xl font-black">{r.stage}</h1>
          {r.champion && <Trophy className="text-yellow-400" size={40} />}
        </div>
        {r.perfect && (
          <div className="inline-block bg-yellow-400 text-black font-black px-4 py-1 rounded-full mb-4">
            PERFECT RUN {r.wins}-0
          </div>
        )}
        <p className="text-slate-300 mb-8">{r.summary}</p>

        {/* Summary cards */}
        <div className="grid md:grid-cols-4 gap-5 mb-10">
          <SummaryCard label="Wins" value={r.wins} />
          <SummaryCard label="Losses" value={r.losses} />
          <SummaryCard label="Record" value={`${r.wins}-${r.losses}`} />
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
            <div className="grid md:grid-cols-2 gap-3">
              {r.awards.map((a) => (
                <div key={a.category} className="bg-slate-800 rounded-2xl p-4 flex justify-between items-center">
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
                className={`flex items-center justify-between gap-3 rounded-2xl p-4 border transition hover:border-blue-500 ${
                  m.won ? "bg-green-500/10 border-green-500/30" : "bg-red-500/10 border-red-500/30"
                }`}
              >
                <div className="flex items-center gap-3">
                  <span className={`font-black ${m.won ? "text-green-400" : "text-red-400"}`}>
                    {m.won ? "W" : "L"}
                  </span>
                  <div>
                    <p className="font-semibold">{m.stage} — vs {m.opponentLabel}</p>
                    <p className="text-slate-400 text-sm">{m.margin}</p>
                  </div>
                </div>
                <div className="text-right text-sm">
                  <p>
                    {m.teamInnings.runs}/{m.teamInnings.wickets} — {m.opponentInnings.runs}/
                    {m.opponentInnings.wickets}
                  </p>
                  <p className="text-slate-400">View scorecard →</p>
                </div>
              </Link>
            ))}
          </div>
        </Section>

        {/* Standings */}
        <Section title="Final Standings">
          <div className="overflow-hidden rounded-2xl border border-slate-800">
            <div className="grid grid-cols-[2fr_repeat(4,1fr)] bg-slate-800 text-slate-300 text-sm font-semibold px-4 py-2">
              <span>Team</span>
              <span className="text-center">P</span>
              <span className="text-center">W</span>
              <span className="text-center">L</span>
              <span className="text-center">Pts</span>
            </div>
            {r.standings.map((row, i) => (
              <div
                key={row.team + i}
                className={`grid grid-cols-[2fr_repeat(4,1fr)] px-4 py-2 text-sm ${
                  row.team === "Your XI" ? "bg-blue-500/15 font-bold" : i % 2 ? "bg-slate-900" : "bg-slate-900/50"
                }`}
              >
                <span>
                  {i + 1}. {row.team}
                </span>
                <span className="text-center">{row.played}</span>
                <span className="text-center">{row.won}</span>
                <span className="text-center">{row.lost}</span>
                <span className="text-center">{row.points}</span>
              </div>
            ))}
          </div>
        </Section>

        {/* Your XI */}
        <Section title="Your XI">
          <div className="grid md:grid-cols-2 gap-3">
            {selectedTeam.map((p, i) => (
              <div key={p.id} className="bg-slate-800 rounded-2xl p-4 flex items-center gap-2">
                <span className="text-slate-500 w-6">{i + 1}.</span>
                <Crown
                  size={14}
                  className={p.id === r?.meta?.captainId ? "text-yellow-400" : "text-transparent"}
                />
                <div>
                  <p className="font-bold flex items-center gap-1">
                    {p.name}
                    {p.overseas && <Plane size={12} className="text-sky-400" />}
                  </p>
                  <p className="text-slate-400 text-sm">{p.role} • {p.statsSummary}</p>
                </div>
              </div>
            ))}
          </div>
        </Section>

        <div className="flex gap-3 mt-8">
          <button
            onClick={startNewGame}
            className="bg-blue-600 hover:bg-blue-700 px-6 py-3 rounded-xl font-bold"
          >
            New Game
          </button>
          <Link
            to={`/draft/${gameMode || "ipl"}`}
            className="bg-slate-800 hover:bg-slate-700 px-6 py-3 rounded-xl font-bold"
          >
            Back to Draft
          </Link>
        </div>
      </div>
    </main>
  );
}

function SummaryCard({ label, value }) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6">
      <p className="text-slate-400">{label}</p>
      <p className="text-4xl font-black">{value}</p>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 mb-8">
      <h2 className="text-2xl font-bold mb-4">{title}</h2>
      {children}
    </div>
  );
}

function Metric({ label, value }) {
  return (
    <div className="bg-slate-800 rounded-2xl p-4">
      <p className="text-slate-400 text-xs uppercase">{label}</p>
      <p className="text-2xl font-black">{value ?? "—"}</p>
    </div>
  );
}

export default Result;
