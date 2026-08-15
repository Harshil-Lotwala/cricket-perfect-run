import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Crown, Shield, Trophy, Users } from "lucide-react";
import api from "../services/api";
import { GAME_MODES } from "../data/modes";

const modes = Object.values(GAME_MODES);

function Leaderboard() {
  const { mode = "ipl" } = useParams();
  const navigate = useNavigate();
  const config = GAME_MODES[mode] || GAME_MODES.ipl;
  const [board, setBoard] = useState({ mode: null, entries: [], error: "" });

  useEffect(() => {
    let cancelled = false;
    api.get(`/leaderboard/${config.id}`)
      .then((response) => {
        if (!cancelled) setBoard({ mode: config.id, entries: response.data || [], error: "" });
      })
      .catch(() => {
        if (!cancelled) setBoard({ mode: config.id, entries: [], error: "The leaderboard is unavailable right now. Please try again shortly." });
      });
    return () => { cancelled = true; };
  }, [config.id]);

  const loading = board.mode !== config.id;
  const entries = loading ? [] : board.entries;
  const error = loading ? "" : board.error;

  return (
    <main className="min-h-screen app-shell text-white">
      <div className="mx-auto max-w-6xl px-5 py-10 md:px-6">
        <div className="mb-8">
          <div>
            <p className="eyebrow mb-2">GLOBAL RECORD BOARD</p>
            <h1 className="text-4xl font-black tracking-tight md:text-5xl">Perfect Run Leaderboard</h1>
            <p className="mt-3 max-w-2xl text-slate-400">
              Unbeaten Hard Mode runs only. Play from Draft; qualifying Results unlock the upload form automatically.
            </p>
          </div>
        </div>

        <div className="mb-6 grid grid-cols-2 gap-2 md:grid-cols-4" aria-label="Leaderboard format">
          {modes.map((item) => (
            <button
              key={item.id}
              onClick={() => navigate(`/leaderboard/${item.id}`)}
              className={`border px-4 py-3 text-left font-bold ${
                item.id === config.id
                  ? "border-lime-300 bg-lime-300/10 text-lime-200"
                  : "border-slate-800 bg-slate-900 text-slate-400 hover:border-slate-600"
              }`}
            >
              <span className="block text-xs uppercase tracking-widest text-slate-500">{item.tag}</span>
              {item.perfectTarget}–0
            </button>
          ))}
        </div>

        {loading && <Status text="Loading verified runs…" />}
        {error && <Status text={error} danger />}
        {!loading && !error && entries.length === 0 && (
          <div className="border border-slate-800 bg-slate-900 p-10 text-center">
            <Users className="mx-auto mb-4 text-slate-600" size={36} />
            <h2 className="text-2xl font-black">The board is open</h2>
            <p className="mt-2 text-slate-400">Finish an unbeaten Hard Mode run to unlock a verified upload on Results.</p>
          </div>
        )}

        <div className="space-y-3">
          {entries.map((entry, index) => (
            <article id={entry.id} key={entry.id} className="border border-slate-800 bg-slate-900 scroll-mt-24">
              <div className="grid gap-4 p-5 md:grid-cols-[4rem_1fr_auto] md:items-center">
                <div className={`text-3xl font-black ${index < 3 ? "text-lime-300" : "text-slate-600"}`}>#{index + 1}</div>
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-xl font-black">{entry.displayName}</h2>
                    {entry.perfect && <span className="bg-yellow-400 px-2 py-1 text-xs font-black text-black">PERFECT</span>}
                    {entry.hardMode && <span className="border border-red-400 px-2 py-1 text-xs font-black text-red-300">HARD</span>}
                  </div>
                  <p className="mt-1 text-sm text-slate-400">
                    {entry.opponentType === "legacy" ? "Legacy XI" : "Historical"} · OVR {entry.overallRating} · {entry.champion ? "Champion" : "Contender"}
                  </p>
                </div>
                <div className="md:text-right">
                  <p className="text-3xl font-black">{entry.wins}-{entry.draws}-{entry.losses}</p>
                  <p className="text-xs uppercase tracking-widest text-slate-500">season record</p>
                </div>
              </div>

              <details className="border-t border-slate-800">
                <summary className="cursor-pointer px-5 py-3 font-bold text-slate-300 hover:text-lime-300">View shared XI</summary>
                <div className="grid gap-2 border-t border-slate-800 p-5 sm:grid-cols-2 lg:grid-cols-3">
                  {entry.team.map((player, playerIndex) => (
                    <div key={`${entry.id}-${player.name}`} className="border border-slate-800 bg-slate-950 p-3">
                      <div className="flex items-center gap-2">
                        <span className="text-xs text-slate-600">{playerIndex + 1}</span>
                        <span className="font-bold">{player.name}</span>
                        {player.name === entry.captain && <Crown size={13} className="text-yellow-400" />}
                        {player.name === entry.keeper && <Shield size={13} className="text-sky-400" />}
                      </div>
                      <p className="mt-1 text-xs text-slate-500">{player.role} · {player.year} {player.sourceTeam} · {player.rating} OVR</p>
                    </div>
                  ))}
                </div>
              </details>
            </article>
          ))}
        </div>
      </div>
    </main>
  );
}

function Status({ text, danger = false }) {
  return (
    <div className={`border p-8 text-center ${danger ? "border-red-500/40 bg-red-500/10 text-red-300" : "border-slate-800 bg-slate-900 text-slate-400"}`}>
      <Trophy className="mx-auto mb-3" size={28} /> {text}
    </div>
  );
}

export default Leaderboard;
